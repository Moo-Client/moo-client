package com.mooclient;

import com.mooclient.discord.DiscordRPC;
import com.mooclient.gui.MooClientScreen;
import com.mooclient.module.ModuleManager;
import com.mooclient.module.modules.ToggleSprintModule;
import com.mooclient.util.MooConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moo Client — Main entry point.
 */
public class MooClient implements ClientModInitializer {

    public static final String MOD_ID = "mooclient";
    public static final String MOD_NAME = "Moo Client";
    public static final String VERSION = "1.6.5";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static MooClient instance;

    public static MooClient getInstance() {
        return instance;
    }

    /** Keybinding: Right Shift opens the client menu */
    private static KeyBinding menuKeyBinding;
    private static boolean sprintKeyWasDown = false;
    private static boolean freelookKeyWasDown = false;
    private static boolean zoomKeyWasDown = false;
    private static boolean waypointKeyWasDown = false;
    private static int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("===========================================");
        LOGGER.info("  {} v{} — Initializing...", MOD_NAME, VERSION);
        LOGGER.info("===========================================");

        // Initialize the module system
        ModuleManager.getInstance().init();
        LOGGER.info("Loaded {} modules.", ModuleManager.getInstance().getModules().size());

        // Load saved config from disk
        MooConfig.load();

        // Initialize Moo Client Network & Discovery Handler
        com.mooclient.network.MooNetworkHandler.init();

        // Initialize In-World Waypoint Renderer
        com.mooclient.waypoint.WaypointRenderer.init();

        // Initialize Discord Rich Presence
        DiscordRPC.getInstance().init();

        // Register the Right Shift keybinding for the client menu
        menuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mooclient.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.mooclient.general"
        ));

        // Listen for ticks and input
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Menu key press - only open when no screen is open
            if (client.currentScreen == null) {
                while (menuKeyBinding.wasPressed()) {
                    client.setScreen(new MooClientScreen());
                }
            } else {
                // Drain keypresses while in screens to prevent unwanted triggers
                while (menuKeyBinding.wasPressed()) {
                    // Do nothing
                }

                // Ensure freelook and zoom are safely disengaged when inside any menu/GUI
                if (com.mooclient.module.modules.FreelookModule.isActive()) {
                    com.mooclient.module.modules.FreelookModule.stop();
                }
                if (com.mooclient.module.modules.ZoomModule.isActive()) {
                    com.mooclient.module.modules.ZoomModule.stop();
                }
                freelookKeyWasDown = false;
                zoomKeyWasDown = false;
                sprintKeyWasDown = false;
                waypointKeyWasDown = false;
            }

            // In-game Toggle Sprint key detection
            if (client.player != null && client.currentScreen == null) {
                long window = client.getWindow().getHandle();
                int sprintKeyCode = ToggleSprintModule.getKeyCode();
                if (sprintKeyCode > 0) {
                    boolean isKeyDown = GLFW.glfwGetKey(window, sprintKeyCode) == GLFW.GLFW_PRESS;
                    if (isKeyDown && !sprintKeyWasDown) {
                        if (ToggleSprintModule.isSprintEnabled()) {
                            ToggleSprintModule.toggleSprintActive();
                        }
                    }
                    sprintKeyWasDown = isKeyDown;
                }

                // In-game Freelook key detection
                int freelookKeyCode = com.mooclient.module.modules.FreelookModule.getKeyCode();
                if (freelookKeyCode > 0 && com.mooclient.module.modules.FreelookModule.isFreelookEnabled()) {
                    boolean isKeyDown = GLFW.glfwGetKey(window, freelookKeyCode) == GLFW.GLFW_PRESS;
                    if (com.mooclient.module.modules.FreelookModule.getMode() == com.mooclient.module.modules.FreelookModule.ActivationMode.HOLD) {
                        if (isKeyDown) {
                            com.mooclient.module.modules.FreelookModule.start();
                        } else {
                            com.mooclient.module.modules.FreelookModule.stop();
                        }
                    } else { // TOGGLE mode
                        if (isKeyDown && !freelookKeyWasDown) {
                            com.mooclient.module.modules.FreelookModule.toggleFreelookActive();
                        }
                    }
                    freelookKeyWasDown = isKeyDown;
                }

                // In-game Zoom key / mouse button detection
                int zoomKeyCode = com.mooclient.module.modules.ZoomModule.getKeyCode();
                if (zoomKeyCode >= 0 && com.mooclient.module.modules.ZoomModule.isZoomEnabled()) {
                    boolean isKeyDown;
                    if (com.mooclient.module.modules.ZoomModule.isMouseButton()) {
                        isKeyDown = GLFW.glfwGetMouseButton(window, zoomKeyCode) == GLFW.GLFW_PRESS;
                    } else {
                        isKeyDown = GLFW.glfwGetKey(window, zoomKeyCode) == GLFW.GLFW_PRESS;
                    }

                    if (com.mooclient.module.modules.ZoomModule.getMode() == com.mooclient.module.modules.ZoomModule.ActivationMode.HOLD) {
                        if (isKeyDown) {
                            com.mooclient.module.modules.ZoomModule.start();
                        } else {
                            com.mooclient.module.modules.ZoomModule.stop();
                        }
                    } else { // TOGGLE mode
                        if (isKeyDown && !zoomKeyWasDown) {
                            com.mooclient.module.modules.ZoomModule.toggleZoomActive();
                        }
                    }
                    zoomKeyWasDown = isKeyDown;
                }

                // In-game Waypoint Screen Keybind detection (e.g. B key)
                int waypointKeyCode = com.mooclient.module.modules.WaypointsModule.getKeyCode();
                if (waypointKeyCode > 0 && com.mooclient.module.modules.WaypointsModule.isWaypointsEnabled()) {
                    boolean isKeyDown = GLFW.glfwGetKey(window, waypointKeyCode) == GLFW.GLFW_PRESS;
                    if (isKeyDown && !waypointKeyWasDown) {
                        client.setScreen(new com.mooclient.gui.MooWaypointScreen());
                    }
                    waypointKeyWasDown = isKeyDown;
                }

                // In-game Macro execution detection
                com.mooclient.module.modules.MacroModule.onTick(client);
            }

            // Always tick Zoom animation
            com.mooclient.module.modules.ZoomModule.onTick();

            // Update Discord RPC State every ~2 seconds (40 ticks)
            tickCounter++;
            if (tickCounter % 40 == 0) {
                if (client.world == null) {
                    DiscordRPC.getInstance().updatePresence("Moo Client v" + VERSION, "W menu głównym");
                } else if (client.isInSingleplayer()) {
                    DiscordRPC.getInstance().updatePresence("Tryb jednoosobowy", "Moo Client v" + VERSION);
                } else if (client.getCurrentServerEntry() != null) {
                    String serverIp = client.getCurrentServerEntry().address;
                    DiscordRPC.getInstance().updatePresence("Serwer: " + serverIp, "Moo Client v" + VERSION);
                } else {
                    DiscordRPC.getInstance().updatePresence("W grze", "Moo Client v" + VERSION);
                }
            }
        });

        LOGGER.info("{} initialized successfully! Press Right Shift to open menu.", MOD_NAME);
    }
}
