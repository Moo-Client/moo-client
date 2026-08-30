package com.mooclient;

import com.mooclient.discord.DiscordRPC;
import com.mooclient.emote.EmoteEngine;
import com.mooclient.gui.InvitationUIManager;
import com.mooclient.gui.MooClientScreen;
import com.mooclient.interaction.Interaction;
import com.mooclient.interaction.InteractionEngine;
import com.mooclient.interaction.InteractionInputBlocker;
import com.mooclient.module.ModuleManager;
import com.mooclient.module.modules.ToggleSprintModule;
import com.mooclient.permissions.PermissionManager;
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
    public static final String VERSION = "1.9.1";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static MooClient instance;

    public static MooClient getInstance() {
        return instance;
    }

    /** Keybindings */
    private static KeyBinding menuKeyBinding;

    private static boolean sprintKeyWasDown = false;
    private static boolean freelookKeyWasDown = false;
    private static boolean zoomKeyWasDown = false;
    private static boolean waypointKeyWasDown = false;
    private static boolean emoteKeyWasDown = false;
    private static boolean wheelKeyWasDown = false;
    private static boolean acceptKeyWasDown = false;
    private static boolean declineKeyWasDown = false;
    private static int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("===========================================");
        LOGGER.info("  {} v{} — Initializing...", MOD_NAME, VERSION);
        LOGGER.info("===========================================");

        // 1. Initialize the module system
        ModuleManager.getInstance().init();
        LOGGER.info("Loaded {} modules.", ModuleManager.getInstance().getModules().size());

        // 2. Initialize Emote Engine & Registry
        EmoteEngine.init();

        // 3. Load saved config from disk
        MooConfig.load();

        // 4. Initialize Moo Client Network & Discovery Handler
        com.mooclient.network.MooNetworkHandler.init();

        // 5. Initialize In-World Waypoint Renderer
        com.mooclient.waypoint.WaypointRenderer.init();

        // 6. Initialize Discord Rich Presence
        DiscordRPC.getInstance().init();

        // 7. Fetch User Permissions and Roles asynchronously from Supabase
        PermissionManager.fetchLocalPlayerPermissions();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            PermissionManager.fetchLocalPlayerPermissions();
            com.mooclient.emote.EmoteRemoteLoader.fetchRemoteEmotesAsync();
        });

        // 8. Register Keybindings
        menuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mooclient.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.mooclient.general"
        ));

        // 9. Listen for ticks and input
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Check Accept / Decline keybinds for active invitations only when invitation is present
            if (client.player != null && client.currentScreen == null && InvitationUIManager.getInstance().hasInvitation()) {
                long window = client.getWindow().getHandle();
                int acceptKey = com.mooclient.module.modules.EmotesModule.getAcceptKeyCode();
                boolean acceptMouse = com.mooclient.module.modules.EmotesModule.isAcceptMouseButton();
                boolean isAcceptDown = isInputPressed(window, acceptKey, acceptMouse);
                if (isAcceptDown && !acceptKeyWasDown) {
                    Interaction inv = InvitationUIManager.getInstance().getCurrentInvitation();
                    if (inv != null) {
                        InteractionEngine.getInstance().acceptInvitation(inv.getInteractionId());
                    }
                }
                acceptKeyWasDown = isAcceptDown;

                int declineKey = com.mooclient.module.modules.EmotesModule.getDeclineKeyCode();
                boolean declineMouse = com.mooclient.module.modules.EmotesModule.isDeclineMouseButton();
                boolean isDeclineDown = isInputPressed(window, declineKey, declineMouse);
                if (isDeclineDown && !declineKeyWasDown) {
                    Interaction inv = InvitationUIManager.getInstance().getCurrentInvitation();
                    if (inv != null) {
                        InteractionEngine.getInstance().declineInvitation(inv.getInteractionId());
                    }
                }
                declineKeyWasDown = isDeclineDown;
            } else {
                acceptKeyWasDown = false;
                declineKeyWasDown = false;
            }

            // Menu key press - only open when no screen is open
            if (client.currentScreen == null) {
                while (menuKeyBinding.wasPressed()) {
                    client.setScreen(new MooClientScreen());
                }
            } else {
                while (menuKeyBinding.wasPressed()) {}

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
                emoteKeyWasDown = false;
                wheelKeyWasDown = false;
                if (com.mooclient.module.modules.EmotesModule.getMode() == com.mooclient.module.modules.EmotesModule.ActivationMode.HOLD) {
                    com.mooclient.module.modules.EmotesModule.setHandsUp(false);
                }
            }

            // In-game Input Handling
            if (client.player != null && client.currentScreen == null) {
                long window = client.getWindow().getHandle();

                // 1. Sprint
                int sprintKeyCode = ToggleSprintModule.getKeyCode();
                if (sprintKeyCode >= 0) {
                    boolean isKeyDown = isInputPressed(window, sprintKeyCode, ToggleSprintModule.isMouseButton());
                    if (isKeyDown && !sprintKeyWasDown) {
                        if (ToggleSprintModule.isSprintEnabled()) {
                            ToggleSprintModule.toggleSprintActive();
                        }
                    }
                    sprintKeyWasDown = isKeyDown;
                }

                // 2. Freelook
                int freelookKeyCode = com.mooclient.module.modules.FreelookModule.getKeyCode();
                if (freelookKeyCode >= 0 && com.mooclient.module.modules.FreelookModule.isFreelookEnabled()) {
                    boolean isKeyDown = isInputPressed(window, freelookKeyCode, com.mooclient.module.modules.FreelookModule.isMouseButton());
                    if (com.mooclient.module.modules.FreelookModule.getMode() == com.mooclient.module.modules.FreelookModule.ActivationMode.HOLD) {
                        if (isKeyDown) {
                            com.mooclient.module.modules.FreelookModule.start();
                        } else {
                            com.mooclient.module.modules.FreelookModule.stop();
                        }
                    } else {
                        if (isKeyDown && !freelookKeyWasDown) {
                            com.mooclient.module.modules.FreelookModule.toggleFreelookActive();
                        }
                    }
                    freelookKeyWasDown = isKeyDown;
                }

                // 3. Zoom
                int zoomKeyCode = com.mooclient.module.modules.ZoomModule.getKeyCode();
                if (zoomKeyCode >= 0 && com.mooclient.module.modules.ZoomModule.isZoomEnabled()) {
                    boolean isKeyDown = isInputPressed(window, zoomKeyCode, com.mooclient.module.modules.ZoomModule.isMouseButton());
                    if (com.mooclient.module.modules.ZoomModule.getMode() == com.mooclient.module.modules.ZoomModule.ActivationMode.HOLD) {
                        if (isKeyDown) {
                            com.mooclient.module.modules.ZoomModule.start();
                        } else {
                            com.mooclient.module.modules.ZoomModule.stop();
                        }
                    } else {
                        if (isKeyDown && !zoomKeyWasDown) {
                            com.mooclient.module.modules.ZoomModule.toggleZoomActive();
                        }
                    }
                    zoomKeyWasDown = isKeyDown;
                }

                // 4. Waypoints
                int waypointKeyCode = com.mooclient.module.modules.WaypointsModule.getKeyCode();
                if (waypointKeyCode >= 0 && com.mooclient.module.modules.WaypointsModule.isWaypointsEnabled()) {
                    boolean isKeyDown = isInputPressed(window, waypointKeyCode, com.mooclient.module.modules.WaypointsModule.isMouseButton());
                    if (isKeyDown && !waypointKeyWasDown) {
                        client.setScreen(new com.mooclient.gui.MooWaypointScreen());
                    }
                    waypointKeyWasDown = isKeyDown;
                }

                // 5. Hands Up (Default: R)
                int handsUpKey = com.mooclient.module.modules.EmotesModule.getKeyCode();
                if (handsUpKey >= 0 && com.mooclient.module.modules.EmotesModule.isEmotesEnabled()) {
                    boolean isKeyDown = isInputPressed(window, handsUpKey, com.mooclient.module.modules.EmotesModule.isMouseButton());
                    if (com.mooclient.module.modules.EmotesModule.getMode() == com.mooclient.module.modules.EmotesModule.ActivationMode.HOLD) {
                        if (isKeyDown != emoteKeyWasDown) {
                            com.mooclient.module.modules.EmotesModule.setHandsUp(isKeyDown);
                        }
                    } else {
                        if (isKeyDown && !emoteKeyWasDown) {
                            com.mooclient.module.modules.EmotesModule.toggleHandsUp();
                        }
                    }
                    emoteKeyWasDown = isKeyDown;
                }

                // 6. Emote Radial Wheel (Default: B)
                int wheelKeyCode = com.mooclient.module.modules.EmotesModule.getWheelKeyCode();
                if (wheelKeyCode >= 0 && com.mooclient.module.modules.EmotesModule.isEmotesEnabled()) {
                    boolean isMouse = com.mooclient.module.modules.EmotesModule.isWheelMouseButton();
                    boolean isKeyDown = isInputPressed(window, wheelKeyCode, isMouse);
                    if (isKeyDown && !wheelKeyWasDown) {
                        if (com.mooclient.module.modules.EmotesModule.hasActiveLoopingEmote()) {
                            com.mooclient.module.modules.EmotesModule.stopEmotesFromWheel();
                        } else {
                            client.setScreen(new com.mooclient.gui.EmoteWheelScreen(wheelKeyCode, isMouse));
                        }
                    }
                    wheelKeyWasDown = isKeyDown;
                }

                // In-game Macro execution detection
                com.mooclient.module.modules.MacroModule.onTick(client);

                // Physical interruption check (damage, knockback, death)
                InteractionInputBlocker.checkPlayerDamageAndInterruption(client.player);
            }

            // Always tick Zoom, Emote Engine & Interaction Engine
            com.mooclient.module.modules.ZoomModule.onTick();
            com.mooclient.module.modules.EmotesModule.onTick();
            EmoteEngine.getInstance().onTick(client);
            InteractionEngine.getInstance().onTick(client);

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

    public static boolean isInputPressed(long window, int code, boolean isMouse) {
        if (code < 0) return false;
        if (isMouse) {
            return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        } else {
            return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
        }
    }
}
