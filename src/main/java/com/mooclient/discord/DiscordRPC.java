package com.mooclient.discord;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pure Java Discord Rich Presence (RPC) IPC client for Moo Client.
 * Connects directly to the Discord local Named Pipe without native DLLs.
 */
public class DiscordRPC {

    private static final Logger LOGGER = LoggerFactory.getLogger("MooClient-DiscordRPC");
    private static DiscordRPC instance;

    // Discord Application ID
    public static String CLIENT_ID = "1537761004983816222";

    private RandomAccessFile pipe;
    private boolean connected = false;
    private long startTimestamp = System.currentTimeMillis() / 1000L;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private String currentDetails = "Moo Client " + com.mooclient.MooClient.getMinecraftVersion();
    private String currentState = "W menu głównym";

    private DiscordRPC() {
    }

    public static DiscordRPC getInstance() {
        if (instance == null) {
            instance = new DiscordRPC();
        }
        return instance;
    }

    /**
     * Initializes background connection loop to Discord IPC.
     */
    public void init() {
        startTimestamp = System.currentTimeMillis() / 1000L;
        scheduler.scheduleWithFixedDelay(this::tick, 1, 5, TimeUnit.SECONDS);
        LOGGER.info("Discord RPC initialized.");
    }

    private synchronized void tick() {
        if (!connected) {
            connect();
        } else {
            // Keep presence updated
            sendActivity(currentDetails, currentState);
        }
    }

    private synchronized void connect() {
        for (int i = 0; i < 10; i++) {
            try {
                String pipePath = System.getProperty("os.name").toLowerCase().contains("win")
                        ? "\\\\.\\pipe\\discord-ipc-" + i
                        : System.getenv("XDG_RUNTIME_DIR") != null
                        ? System.getenv("XDG_RUNTIME_DIR") + "/discord-ipc-" + i
                        : "/tmp/discord-ipc-" + i;

                File pipeFile = new File(pipePath);
                if (System.getProperty("os.name").toLowerCase().contains("win") || pipeFile.exists()) {
                    pipe = new RandomAccessFile(pipePath, "rw");
                    sendHandshake();
                    connected = true;
                    LOGGER.info("Connected to Discord IPC at {}", pipePath);
                    sendActivity(currentDetails, currentState);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void sendHandshake() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("v", 1);
            json.addProperty("client_id", CLIENT_ID);
            sendPacket(0, json.toString()); // Opcode 0 = Handshake
        } catch (Exception e) {
            close();
        }
    }

    public synchronized void updatePresence(String details, String state) {
        this.currentDetails = details;
        this.currentState = state;
        if (connected) {
            sendActivity(details, state);
        }
    }

    private void sendActivity(String details, String state) {
        try {
            JsonObject activity = new JsonObject();
            activity.addProperty("details", details);
            activity.addProperty("state", state);

            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", startTimestamp);
            activity.add("timestamps", timestamps);

            String mcVer = com.mooclient.MooClient.getMinecraftVersion();
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", "logo");
            assets.addProperty("large_text", "Moo Client v" + com.mooclient.MooClient.VERSION + " (Fabric " + mcVer + ")");
            assets.addProperty("small_image", "minecraft");
            assets.addProperty("small_text", "Minecraft " + mcVer);
            activity.add("assets", assets);

            JsonObject args = new JsonObject();
            args.addProperty("pid", (int) ProcessHandle.current().pid());
            args.add("activity", activity);

            JsonObject root = new JsonObject();
            root.addProperty("cmd", "SET_ACTIVITY");
            root.add("args", args);
            root.addProperty("nonce", UUID.randomUUID().toString());

            sendPacket(1, root.toString()); // Opcode 1 = Frame
        } catch (Exception e) {
            close();
        }
    }

    private void sendPacket(int opcode, String payload) throws Exception {
        if (pipe == null) return;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        pipe.write(buffer.array());
    }

    private synchronized void close() {
        connected = false;
        if (pipe != null) {
            try {
                pipe.close();
            } catch (Exception ignored) {
            }
            pipe = null;
        }
    }

    public void shutdown() {
        close();
        scheduler.shutdownNow();
    }
}
