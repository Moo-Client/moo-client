package com.mooclient.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooclient.util.MooUserManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ultra-fast, real-time bi-directional player discovery via HiveMQ MQTT Broker.
 * Zero lag, zero rate limits, instantaneous synchronization for all Moo Client players.
 */
public class MooNetworkHandler {

    private static final String BROKER_HOST = "broker.hivemq.com";
    private static final int BROKER_PORT = 1883;
    private static final String TOPIC = "mooclient/presence_v3";

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MooClient-MQTT");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static Socket socket = null;
    private static OutputStream out = null;
    private static final AtomicBoolean CONNECTED = new AtomicBoolean(false);
    private static final AtomicBoolean CONNECTING = new AtomicBoolean(false);

    public static void init() {
        // Start background connection thread
        EXECUTOR.schedule(MooNetworkHandler::ensureConnection, 1000, TimeUnit.MILLISECONDS);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sendPresence();
            EXECUTOR.schedule(MooNetworkHandler::sendPresence, 1000, TimeUnit.MILLISECONDS);
            EXECUTOR.schedule(MooNetworkHandler::sendPresence, 3000, TimeUnit.MILLISECONDS);
        });

        // Periodic presence heartbeat every 5 seconds
        EXECUTOR.scheduleAtFixedRate(MooNetworkHandler::sendPresence, 3000, 5000, TimeUnit.MILLISECONDS);

        // Keep-alive connection watchdog every 10 seconds
        EXECUTOR.scheduleAtFixedRate(MooNetworkHandler::ensureConnection, 5000, 10000, TimeUnit.MILLISECONDS);
    }

    public static void sendBroadcast() {
        sendPresence();
    }

    private static synchronized void ensureConnection() {
        if (CONNECTED.get() || CONNECTING.get()) return;
        CONNECTING.set(true);

        new Thread(() -> {
            try {
                if (socket != null && !socket.isClosed()) {
                    try { socket.close(); } catch (Exception ignored) {}
                }

                socket = new Socket(BROKER_HOST, BROKER_PORT);
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(0); // non-blocking read
                out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                // 1. Send MQTT CONNECT
                String clientId = "Moo_" + UUID.randomUUID().toString().substring(0, 8);
                byte[] clientBytes = clientId.getBytes(StandardCharsets.UTF_8);
                byte[] varHeader = new byte[]{0x00, 0x04, 'M', 'Q', 'T', 'T', 0x04, 0x02, 0x00, 0x3C}; // MQTT 3.1.1, Clean Session, 60s
                int remainLen = varHeader.length + 2 + clientBytes.length;

                ByteArrayOutputStream connectPacket = new ByteArrayOutputStream();
                connectPacket.write(0x10); // CONNECT
                connectPacket.write(remainLen);
                connectPacket.write(varHeader);
                connectPacket.write((clientBytes.length >> 8) & 0xFF);
                connectPacket.write(clientBytes.length & 0xFF);
                connectPacket.write(clientBytes);
                out.write(connectPacket.toByteArray());
                out.flush();

                // Read CONNACK (4 bytes: 0x20, 0x02, 0x00, 0x00)
                byte[] connack = new byte[4];
                int read = in.read(connack);
                if (read < 4 || connack[0] != 0x20 || connack[3] != 0x00) {
                    throw new IllegalStateException("CONNACK rejected");
                }

                // 2. Send SUBSCRIBE to TOPIC
                byte[] topicBytes = TOPIC.getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream subPacket = new ByteArrayOutputStream();
                subPacket.write(0x82); // SUBSCRIBE
                subPacket.write(2 + 2 + topicBytes.length + 1); // remainLen
                subPacket.write(0x00); subPacket.write(0x01); // Packet ID 1
                subPacket.write((topicBytes.length >> 8) & 0xFF);
                subPacket.write(topicBytes.length & 0xFF);
                subPacket.write(topicBytes);
                subPacket.write(0x00); // QoS 0
                out.write(subPacket.toByteArray());
                out.flush();

                CONNECTED.set(true);
                CONNECTING.set(false);

                // Broadcast immediately after connection
                sendPresence();

                // 3. Listener loop
                byte[] buffer = new byte[4096];
                while (!socket.isClosed() && socket.isConnected()) {
                    int len = in.read(buffer);
                    if (len <= 0) break;

                    // Check for PUBLISH packet (0x30 QoS 0)
                    for (int i = 0; i < len; i++) {
                        if ((buffer[i] & 0xF0) == 0x30) {
                            if (i + 1 >= len) break;
                            int packetRemain = buffer[i + 1] & 0xFF;
                            if (i + 2 + packetRemain <= len) {
                                int topicLen = ((buffer[i + 2] & 0xFF) << 8) | (buffer[i + 3] & 0xFF);
                                int payloadStart = i + 4 + topicLen;
                                int payloadLen = (i + 2 + packetRemain) - payloadStart;

                                if (payloadLen > 0 && payloadStart + payloadLen <= len) {
                                    String jsonStr = new String(buffer, payloadStart, payloadLen, StandardCharsets.UTF_8);
                                    handleIncomingPresence(jsonStr);
                                }
                                i += 1 + packetRemain;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                CONNECTED.set(false);
                CONNECTING.set(false);
                try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            }
        }, "MooClient-MQTT-Listener").start();
    }

    private static void handleIncomingPresence(String jsonStr) {
        try {
            if (jsonStr == null || jsonStr.trim().isEmpty()) return;
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();

            if (obj.has("u")) {
                String username = obj.get("u").getAsString();
                UUID uuid = null;
                if (obj.has("uuid")) {
                    try {
                        String idStr = obj.get("uuid").getAsString();
                        if (!idStr.isEmpty()) uuid = UUID.fromString(idStr);
                    } catch (Exception ignored) {}
                }
                if (username != null && !username.trim().isEmpty()) {
                    MooUserManager.registerUser(username, uuid);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void sendPresence() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            String username = "";
            UUID uuid = null;

            if (client.getSession() != null && client.getSession().getUsername() != null) {
                username = client.getSession().getUsername().trim();
                uuid = client.getSession().getUuidOrNull();
            } else if (client.player != null && client.player.getName() != null) {
                username = client.player.getName().getString().trim();
                uuid = client.player.getUuid();
            }

            if (username.isEmpty()) return;

            // Register locally
            MooUserManager.registerUser(username, uuid);

            if (!CONNECTED.get() || out == null) {
                ensureConnection();
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("u", username);
            payload.addProperty("uuid", uuid != null ? uuid.toString() : "");
            payload.addProperty("t", System.currentTimeMillis());

            byte[] topicBytes = TOPIC.getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            int remain = 2 + topicBytes.length + payloadBytes.length;

            ByteArrayOutputStream pubPacket = new ByteArrayOutputStream();
            pubPacket.write(0x30); // PUBLISH (QoS 0)
            pubPacket.write(remain);
            pubPacket.write((topicBytes.length >> 8) & 0xFF);
            pubPacket.write(topicBytes.length & 0xFF);
            pubPacket.write(topicBytes);
            pubPacket.write(payloadBytes);

            synchronized (MooNetworkHandler.class) {
                out.write(pubPacket.toByteArray());
                out.flush();
            }
        } catch (Exception e) {
            CONNECTED.set(false);
            ensureConnection();
        }
    }
}
