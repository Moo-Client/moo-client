package com.mooclient.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooclient.MooClient;
import com.mooclient.emote.EmoteEngine;
import com.mooclient.interaction.DynamicLatencySynchronizer;
import com.mooclient.interaction.InteractionEngine;
import com.mooclient.security.MooSessionValidator;
import com.mooclient.security.RateLimiter;
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
 * Implementacja transportu w czasie rzeczywistym oparta o broker MQTT (HiveMQ).
 * Transportuje sygnały obecności, emotki solo i interakcje multiplayer.
 */
public class MooNetworkHandler implements IMooTransport {

    private static final String BROKER_HOST = "broker.hivemq.com";
    private static final int BROKER_PORT = 1883;

    private static final String TOPIC_PRESENCE = "mooclient/presence_v3";
    private static final String TOPIC_EMOTES = "mooclient/emotes_v3";
    private static final String TOPIC_INTERACTIONS = "mooclient/interactions_v3";
    private static final String TOPIC_WILDCARD = "mooclient/#";

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MooClient-Transport");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static MooNetworkHandler instance;
    private static Socket socket = null;
    private static OutputStream out = null;
    private static final AtomicBoolean CONNECTED = new AtomicBoolean(false);
    private static final AtomicBoolean CONNECTING = new AtomicBoolean(false);

    public static MooNetworkHandler getInstance() {
        return instance;
    }

    public static void init() {
        instance = new MooNetworkHandler();

        EXECUTOR.schedule(MooNetworkHandler::ensureConnection, 1000, TimeUnit.MILLISECONDS);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            instance.sendPresence();
            EXECUTOR.schedule(() -> instance.sendPresence(), 1000, TimeUnit.MILLISECONDS);
            EXECUTOR.schedule(() -> instance.sendPresence(), 3000, TimeUnit.MILLISECONDS);
        });

        EXECUTOR.scheduleAtFixedRate(() -> instance.sendPresence(), 3000, 5000, TimeUnit.MILLISECONDS);
        EXECUTOR.scheduleAtFixedRate(MooNetworkHandler::ensureConnection, 5000, 10000, TimeUnit.MILLISECONDS);
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
                socket.setSoTimeout(0);
                out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                // 1. MQTT CONNECT
                String clientId = "Moo_" + UUID.randomUUID().toString().substring(0, 8);
                byte[] clientBytes = clientId.getBytes(StandardCharsets.UTF_8);
                byte[] varHeader = new byte[]{0x00, 0x04, 'M', 'Q', 'T', 'T', 0x04, 0x02, 0x00, 0x3C};
                int remainLen = varHeader.length + 2 + clientBytes.length;

                ByteArrayOutputStream connectPacket = new ByteArrayOutputStream();
                connectPacket.write(0x10);
                connectPacket.write(remainLen);
                connectPacket.write(varHeader);
                connectPacket.write((clientBytes.length >> 8) & 0xFF);
                connectPacket.write(clientBytes.length & 0xFF);
                connectPacket.write(clientBytes);
                out.write(connectPacket.toByteArray());
                out.flush();

                byte[] connack = new byte[4];
                int read = in.read(connack);
                if (read < 4 || connack[0] != 0x20 || connack[3] != 0x00) {
                    throw new IllegalStateException("CONNACK rejected");
                }

                // 2. MQTT SUBSCRIBE
                byte[] topicBytes = TOPIC_WILDCARD.getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream subPacket = new ByteArrayOutputStream();
                subPacket.write(0x82);
                subPacket.write(2 + 2 + topicBytes.length + 1);
                subPacket.write(0x00); subPacket.write(0x01);
                subPacket.write((topicBytes.length >> 8) & 0xFF);
                subPacket.write(topicBytes.length & 0xFF);
                subPacket.write(topicBytes);
                subPacket.write(0x00);
                out.write(subPacket.toByteArray());
                out.flush();

                CONNECTED.set(true);
                CONNECTING.set(false);

                if (instance != null) instance.sendPresence();

                // 3. Listener loop
                byte[] buffer = new byte[8192];
                while (!socket.isClosed() && socket.isConnected()) {
                    int len = in.read(buffer);
                    if (len <= 0) break;

                    for (int i = 0; i < len; i++) {
                        if ((buffer[i] & 0xF0) == 0x30) {
                            if (i + 1 >= len) break;
                            int packetRemain = buffer[i + 1] & 0xFF;
                            if (i + 2 + packetRemain <= len) {
                                int topicLen = ((buffer[i + 2] & 0xFF) << 8) | (buffer[i + 3] & 0xFF);
                                if (i + 4 + topicLen <= len) {
                                    String topic = new String(buffer, i + 4, topicLen, StandardCharsets.UTF_8);
                                    int payloadStart = i + 4 + topicLen;
                                    int payloadLen = (i + 2 + packetRemain) - payloadStart;

                                    if (payloadLen > 0 && payloadStart + payloadLen <= len) {
                                        String jsonStr = new String(buffer, payloadStart, payloadLen, StandardCharsets.UTF_8);
                                        dispatchIncomingPacket(topic, jsonStr);
                                    }
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
        }, "MooClient-Transport-Listener").start();
    }

    private static void dispatchIncomingPacket(String topic, String jsonStr) {
        try {
            if (jsonStr == null || jsonStr.trim().isEmpty()) return;
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();

            if (topic.contains("presence")) {
                handlePresence(obj);
            } else if (topic.contains("emotes")) {
                handleEmotes(obj);
            } else if (topic.contains("interactions")) {
                handleInteractions(obj);
            }
        } catch (Exception ignored) {}
    }

    private static void handlePresence(JsonObject obj) {
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
    }

    private static void handleEmotes(JsonObject obj) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        UUID senderUuid = null;
        if (obj.has("uuid")) {
            try {
                senderUuid = UUID.fromString(obj.get("uuid").getAsString());
            } catch (Exception ignored) {}
        }
        if (senderUuid == null) return;

        // Ignoruj własne pakiety
        UUID localUuid = MooSessionValidator.getLocalPlayerUuid();
        if (senderUuid.equals(localUuid)) return;

        String emoteId = obj.has("e") ? obj.get("e").getAsString() : null;
        boolean play = !obj.has("play") || obj.get("play").getAsBoolean();

        UUID finalSenderUuid = senderUuid;
        client.execute(() -> {
            if (play && emoteId != null) {
                EmoteEngine.getInstance().playRemoteEmote(finalSenderUuid, emoteId, 0, System.currentTimeMillis());
            } else {
                EmoteEngine.getInstance().stopRemoteEmote(finalSenderUuid);
            }
        });
    }

    private static void handleInteractions(JsonObject obj) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        String type = obj.has("type") ? obj.get("type").getAsString() : "";
        UUID localUuid = MooSessionValidator.getLocalPlayerUuid();
        String localName = MooSessionValidator.getLocalPlayerName();

        if ("req".equals(type)) {
            UUID targetUuid = null;
            if (obj.has("to_uuid") && !obj.get("to_uuid").isJsonNull()) {
                try {
                    String s = obj.get("to_uuid").getAsString();
                    if (!s.isEmpty()) targetUuid = UUID.fromString(s);
                } catch (Exception ignored) {}
            }
            String toName = obj.has("to_name") && !obj.get("to_name").isJsonNull() ? obj.get("to_name").getAsString() : null;

            boolean isForUs = (targetUuid != null && targetUuid.equals(localUuid))
                    || (toName != null && localName != null && toName.equalsIgnoreCase(localName));
            if (!isForUs) return; // Nie do nas

            UUID interactionId = UUID.fromString(obj.get("id").getAsString());
            String emoteId = obj.get("e").getAsString();
            UUID fromUuid = null;
            if (obj.has("from_uuid") && !obj.get("from_uuid").isJsonNull()) {
                try {
                    String s = obj.get("from_uuid").getAsString();
                    if (!s.isEmpty()) fromUuid = UUID.fromString(s);
                } catch (Exception ignored) {}
            }
            String fromName = obj.has("from_name") && !obj.get("from_name").isJsonNull() ? obj.get("from_name").getAsString() : "Player";

            // Rejestracja obecności nadawcy
            if (fromName != null && fromUuid != null) {
                MooUserManager.registerUser(fromName, fromUuid);
            }

            // Rejestracja pingu
            if (obj.has("t") && fromUuid != null) {
                long sentTime = obj.get("t").getAsLong();
                long rtt = Math.max(10L, System.currentTimeMillis() - sentTime);
                DynamicLatencySynchronizer.recordPingRtt(fromUuid, rtt);
            }

            UUID finalFromUuid = fromUuid;
            client.execute(() -> {
                InteractionEngine.getInstance().onIncomingRequest(interactionId, emoteId, finalFromUuid, fromName);
            });
        } else if ("resp".equals(type)) {
            UUID interactionId = UUID.fromString(obj.get("id").getAsString());
            boolean accepted = obj.get("accepted").getAsBoolean();
            long startAt = obj.has("start_at") ? obj.get("start_at").getAsLong() : System.currentTimeMillis();

            client.execute(() -> {
                InteractionEngine.getInstance().onIncomingResponse(interactionId, accepted, startAt);
            });
        } else if ("cancel".equals(type)) {
            UUID interactionId = UUID.fromString(obj.get("id").getAsString());
            String reason = obj.has("reason") ? obj.get("reason").getAsString() : "UNKNOWN";

            client.execute(() -> {
                InteractionEngine.getInstance().onIncomingCancel(interactionId, reason);
            });
        }
    }

    public void sendPresence() {
        sendPresence(MooSessionValidator.getLocalPlayerName(), MooSessionValidator.getLocalPlayerUuid());
    }

    @Override
    public void sendPresence(String username, UUID uuid) {
        try {
            if (username == null || username.isEmpty()) return;
            MooUserManager.registerUser(username, uuid);

            if (!CONNECTED.get() || out == null) {
                ensureConnection();
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("u", username);
            payload.addProperty("uuid", uuid != null ? uuid.toString() : "");
            payload.addProperty("t", System.currentTimeMillis());

            publishMqtt(TOPIC_PRESENCE, payload.toString());
        } catch (Exception e) {
            CONNECTED.set(false);
            ensureConnection();
        }
    }

    @Override
    public void sendSoloEmote(String emoteId, boolean start) {
        EXECUTOR.execute(() -> {
            try {
                if (!CONNECTED.get() || out == null) return;
                UUID uuid = MooSessionValidator.getLocalPlayerUuid();
                String name = MooSessionValidator.getLocalPlayerName();

                JsonObject payload = new JsonObject();
                if (uuid != null) payload.addProperty("uuid", uuid.toString());
                if (name != null) payload.addProperty("u", name);
                payload.addProperty("e", emoteId);
                payload.addProperty("play", start);
                payload.addProperty("t", System.currentTimeMillis());

                publishMqtt(TOPIC_EMOTES, payload.toString());
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void sendInteractionRequest(UUID interactionId, String emoteId, UUID targetUuid, String targetName) {
        EXECUTOR.execute(() -> {
            try {
                if (!CONNECTED.get() || out == null) return;
                UUID localUuid = MooSessionValidator.getLocalPlayerUuid();
                String localName = MooSessionValidator.getLocalPlayerName();

                JsonObject payload = new JsonObject();
                payload.addProperty("type", "req");
                payload.addProperty("id", interactionId.toString());
                payload.addProperty("from_uuid", localUuid != null ? localUuid.toString() : "");
                payload.addProperty("from_name", localName);
                payload.addProperty("to_uuid", targetUuid.toString());
                payload.addProperty("to_name", targetName);
                payload.addProperty("e", emoteId);
                payload.addProperty("t", System.currentTimeMillis());

                publishMqtt(TOPIC_INTERACTIONS, payload.toString());
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void sendInteractionResponse(UUID interactionId, boolean accepted, long startEpochMs) {
        EXECUTOR.execute(() -> {
            try {
                if (!CONNECTED.get() || out == null) return;
                UUID localUuid = MooSessionValidator.getLocalPlayerUuid();

                JsonObject payload = new JsonObject();
                payload.addProperty("type", "resp");
                payload.addProperty("id", interactionId.toString());
                payload.addProperty("from_uuid", localUuid != null ? localUuid.toString() : "");
                payload.addProperty("accepted", accepted);
                payload.addProperty("start_at", startEpochMs);
                payload.addProperty("t", System.currentTimeMillis());

                publishMqtt(TOPIC_INTERACTIONS, payload.toString());
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void sendInteractionCancel(UUID interactionId, String reason) {
        EXECUTOR.execute(() -> {
            try {
                if (!CONNECTED.get() || out == null) return;
                UUID localUuid = MooSessionValidator.getLocalPlayerUuid();

                JsonObject payload = new JsonObject();
                payload.addProperty("type", "cancel");
                payload.addProperty("id", interactionId.toString());
                payload.addProperty("from_uuid", localUuid != null ? localUuid.toString() : "");
                payload.addProperty("reason", reason);
                payload.addProperty("t", System.currentTimeMillis());

                publishMqtt(TOPIC_INTERACTIONS, payload.toString());
            } catch (Exception ignored) {}
        });
    }

    private static synchronized void publishMqtt(String topic, String payloadStr) {
        try {
            if (out == null || !CONNECTED.get()) return;

            byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = payloadStr.getBytes(StandardCharsets.UTF_8);
            int remain = 2 + topicBytes.length + payloadBytes.length;

            ByteArrayOutputStream pubPacket = new ByteArrayOutputStream();
            pubPacket.write(0x30);
            pubPacket.write(remain);
            pubPacket.write((topicBytes.length >> 8) & 0xFF);
            pubPacket.write(topicBytes.length & 0xFF);
            pubPacket.write(topicBytes);
            pubPacket.write(payloadBytes);

            out.write(pubPacket.toByteArray());
            out.flush();
        } catch (Exception e) {
            CONNECTED.set(false);
            ensureConnection();
        }
    }
}
