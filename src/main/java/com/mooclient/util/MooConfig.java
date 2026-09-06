package com.mooclient.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooclient.MooClient;
import com.mooclient.gui.InvitationUIManager;
import com.mooclient.module.ModuleManager;
import com.mooclient.module.modules.FpsModule;
import com.mooclient.module.modules.FullbrightModule;
import com.mooclient.module.modules.ToggleSprintModule;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles persistent config save/load for Moo Client module settings and global options.
 * Config file: .minecraft/config/mooclient.json
 */
public class MooConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path getConfigPath() {
        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
                return FabricLoader.getInstance().getConfigDir().resolve("mooclient.json");
            }
        } catch (Throwable ignored) {
        }
        return Path.of("config", "mooclient.json");
    }

    /**
     * Save all module settings to disk.
     */
    public static void save() {
        try {
            Path configPath = getConfigPath();
            if (configPath.getParent() != null && !Files.exists(configPath.getParent())) {
                Files.createDirectories(configPath.getParent());
            }

            JsonObject root = new JsonObject();
            root.addProperty("language", MooLanguage.current);

            // Gamma / Fullbright Module
            JsonObject gamma = new JsonObject();
            gamma.addProperty("enabled", FullbrightModule.isFullbrightActive());
            root.add("gamma", gamma);

            // FPS Module
            JsonObject fps = new JsonObject();
            fps.addProperty("enabled", FpsModule.isFpsEnabled());
            fps.addProperty("style", FpsModule.getStyle().name());
            fps.addProperty("showBackground", FpsModule.isShowBackground());
            fps.addProperty("textShadow", FpsModule.isTextShadow());
            fps.addProperty("showPrefix", FpsModule.isShowPrefix());
            fps.addProperty("anchorX", FpsModule.position.anchorX.name());
            fps.addProperty("anchorY", FpsModule.position.anchorY.name());
            fps.addProperty("offsetX", FpsModule.position.offsetX);
            fps.addProperty("offsetY", FpsModule.position.offsetY);
            fps.addProperty("posX", FpsModule.posX);
            fps.addProperty("posY", FpsModule.posY);
            root.add("fps", fps);

            // Sprint Module
            JsonObject sprint = new JsonObject();
            sprint.addProperty("enabled", ToggleSprintModule.isSprintEnabled());
            sprint.addProperty("style", ToggleSprintModule.getStyle().name());
            sprint.addProperty("showBackground", ToggleSprintModule.isShowBackground());
            sprint.addProperty("textShadow", ToggleSprintModule.isTextShadow());
            sprint.addProperty("keyCode", ToggleSprintModule.getKeyCode());
            sprint.addProperty("keyName", ToggleSprintModule.getKeyName());
            sprint.addProperty("isMouseButton", ToggleSprintModule.isMouseButton());
            sprint.addProperty("anchorX", ToggleSprintModule.position.anchorX.name());
            sprint.addProperty("anchorY", ToggleSprintModule.position.anchorY.name());
            sprint.addProperty("offsetX", ToggleSprintModule.position.offsetX);
            sprint.addProperty("offsetY", ToggleSprintModule.position.offsetY);
            sprint.addProperty("posX", ToggleSprintModule.posX);
            sprint.addProperty("posY", ToggleSprintModule.posY);
            root.add("sprint", sprint);

            // Freelook Module
            JsonObject freelook = new JsonObject();
            freelook.addProperty("enabled", com.mooclient.module.modules.FreelookModule.isFreelookEnabled());
            freelook.addProperty("mode", com.mooclient.module.modules.FreelookModule.getMode().name());
            freelook.addProperty("invertPitch", com.mooclient.module.modules.FreelookModule.isInvertPitch());
            freelook.addProperty("keyCode", com.mooclient.module.modules.FreelookModule.getKeyCode());
            freelook.addProperty("keyName", com.mooclient.module.modules.FreelookModule.getKeyName());
            freelook.addProperty("isMouseButton", com.mooclient.module.modules.FreelookModule.isMouseButton());
            root.add("freelook", freelook);

            // Potion Effects Module
            JsonObject potions = new JsonObject();
            potions.addProperty("enabled", com.mooclient.module.modules.PotionEffectsModule.isModuleEnabled());
            potions.addProperty("style", com.mooclient.module.modules.PotionEffectsModule.getStyle().name());
            potions.addProperty("showBackground", com.mooclient.module.modules.PotionEffectsModule.isShowBackground());
            potions.addProperty("textShadow", com.mooclient.module.modules.PotionEffectsModule.isTextShadow());
            potions.addProperty("anchorX", com.mooclient.module.modules.PotionEffectsModule.position.anchorX.name());
            potions.addProperty("anchorY", com.mooclient.module.modules.PotionEffectsModule.position.anchorY.name());
            potions.addProperty("offsetX", com.mooclient.module.modules.PotionEffectsModule.position.offsetX);
            potions.addProperty("offsetY", com.mooclient.module.modules.PotionEffectsModule.position.offsetY);
            potions.addProperty("posX", com.mooclient.module.modules.PotionEffectsModule.posX);
            potions.addProperty("posY", com.mooclient.module.modules.PotionEffectsModule.posY);
            root.add("potions", potions);

            // Nametags Module
            JsonObject nametags = new JsonObject();
            nametags.addProperty("enabled", com.mooclient.module.modules.NametagsModule.isNametagsEnabled());
            nametags.addProperty("showLogo", com.mooclient.module.modules.NametagsModule.isShowLogo());
            nametags.addProperty("showPing", com.mooclient.module.modules.NametagsModule.isShowPing());
            nametags.addProperty("showSelfPing", com.mooclient.module.modules.NametagsModule.isShowSelfPing());
            nametags.addProperty("pingPosition", com.mooclient.module.modules.NametagsModule.getPingPosition().name());
            nametags.addProperty("removeBackground", com.mooclient.module.modules.NametagsModule.isRemoveBackground());
            nametags.addProperty("textShadow", com.mooclient.module.modules.NametagsModule.isTextShadow());
            root.add("nametags", nametags);

            // Zoom Module
            JsonObject zoom = new JsonObject();
            zoom.addProperty("enabled", com.mooclient.module.modules.ZoomModule.isZoomEnabled());
            zoom.addProperty("factor", com.mooclient.module.modules.ZoomModule.getFactor().name());
            zoom.addProperty("mode", com.mooclient.module.modules.ZoomModule.getMode().name());
            zoom.addProperty("smoothZoom", com.mooclient.module.modules.ZoomModule.isSmoothZoom());
            zoom.addProperty("keyCode", com.mooclient.module.modules.ZoomModule.getKeyCode());
            zoom.addProperty("keyName", com.mooclient.module.modules.ZoomModule.getKeyName());
            zoom.addProperty("isMouseButton", com.mooclient.module.modules.ZoomModule.isMouseButton());
            root.add("zoom", zoom);

            // Macro Module
            JsonObject macroJson = new JsonObject();
            macroJson.addProperty("enabled", com.mooclient.module.modules.MacroModule.isMacroEnabled());
            com.google.gson.JsonArray macrosArray = new com.google.gson.JsonArray();
            for (com.mooclient.module.modules.MacroModule.MacroEntry m : com.mooclient.module.modules.MacroModule
                    .getMacros()) {
                JsonObject mObj = new JsonObject();
                mObj.addProperty("id", m.getId());
                mObj.addProperty("command", m.getCommand());
                mObj.addProperty("keyCode", m.getKeyCode());
                mObj.addProperty("keyName", m.getKeyName());
                mObj.addProperty("isMouseButton", m.isMouseButton());
                mObj.addProperty("enabled", m.isEnabled());
                macrosArray.add(mObj);
            }
            macroJson.add("list", macrosArray);
            root.add("macro", macroJson);

            // Chat Module
            JsonObject chat = new JsonObject();
            chat.addProperty("enabled", com.mooclient.module.modules.ChatModule.isModuleEnabled());
            chat.addProperty("transparentBackground",
                    com.mooclient.module.modules.ChatModule.isTransparentBackground());
            chat.addProperty("unlimitedChat", com.mooclient.module.modules.ChatModule.isUnlimitedChat());
            chat.addProperty("stackMessages", com.mooclient.module.modules.ChatModule.isStackMessages());
            chat.addProperty("smoothChat", com.mooclient.module.modules.ChatModule.isSmoothChat());
            chat.addProperty("textShadow", com.mooclient.module.modules.ChatModule.isTextShadow());
            root.add("chat", chat);

            // Ping Module
            JsonObject ping = new JsonObject();
            ping.addProperty("enabled", com.mooclient.module.modules.PingModule.isPingEnabled());
            ping.addProperty("style", com.mooclient.module.modules.PingModule.getStyle().name());
            ping.addProperty("showBackground", com.mooclient.module.modules.PingModule.isShowBackground());
            ping.addProperty("textShadow", com.mooclient.module.modules.PingModule.isTextShadow());
            ping.addProperty("showPrefix", com.mooclient.module.modules.PingModule.isShowPrefix());
            ping.addProperty("anchorX", com.mooclient.module.modules.PingModule.position.anchorX.name());
            ping.addProperty("anchorY", com.mooclient.module.modules.PingModule.position.anchorY.name());
            ping.addProperty("offsetX", com.mooclient.module.modules.PingModule.position.offsetX);
            ping.addProperty("offsetY", com.mooclient.module.modules.PingModule.position.offsetY);
            ping.addProperty("posX", com.mooclient.module.modules.PingModule.posX);
            ping.addProperty("posY", com.mooclient.module.modules.PingModule.posY);
            root.add("ping", ping);

            // Waypoints Module
            JsonObject waypoints = new JsonObject();
            waypoints.addProperty("enabled", com.mooclient.module.modules.WaypointsModule.isWaypointsEnabled());
            waypoints.addProperty("showDistance", com.mooclient.module.modules.WaypointsModule.isShowDistance());
            waypoints.addProperty("showBeacons", com.mooclient.module.modules.WaypointsModule.isShowBeacons());
            waypoints.addProperty("showBackground", com.mooclient.module.modules.WaypointsModule.isShowBackground());
            waypoints.addProperty("textShadow", com.mooclient.module.modules.WaypointsModule.isTextShadow());
            waypoints.addProperty("deathWaypoint", com.mooclient.module.modules.WaypointsModule.isDeathWaypoint());
            waypoints.addProperty("showAllDimensions",
                    com.mooclient.module.modules.WaypointsModule.isShowAllDimensions());
            waypoints.addProperty("showAllServers", com.mooclient.module.modules.WaypointsModule.isShowAllServers());
            waypoints.addProperty("scale", com.mooclient.module.modules.WaypointsModule.getScale());
            waypoints.addProperty("keyCode", com.mooclient.module.modules.WaypointsModule.getKeyCode());
            waypoints.addProperty("keyName", com.mooclient.module.modules.WaypointsModule.getKeyName());
            waypoints.addProperty("isMouseButton", com.mooclient.module.modules.WaypointsModule.isMouseButton());
            root.add("waypoints", waypoints);

            // Scoreboard Module
            JsonObject scoreboard = new JsonObject();
            scoreboard.addProperty("enabled", com.mooclient.module.modules.ScoreboardModule.isScoreboardEnabled());
            scoreboard.addProperty("textShadow", com.mooclient.module.modules.ScoreboardModule.isTextShadow());
            scoreboard.addProperty("showBackground", com.mooclient.module.modules.ScoreboardModule.isShowBackground());
            scoreboard.addProperty("showScores", com.mooclient.module.modules.ScoreboardModule.isShowScores());
            scoreboard.addProperty("anchorX", com.mooclient.module.modules.ScoreboardModule.position.anchorX.name());
            scoreboard.addProperty("anchorY", com.mooclient.module.modules.ScoreboardModule.position.anchorY.name());
            scoreboard.addProperty("offsetX", com.mooclient.module.modules.ScoreboardModule.position.offsetX);
            scoreboard.addProperty("offsetY", com.mooclient.module.modules.ScoreboardModule.position.offsetY);
            scoreboard.addProperty("posX", com.mooclient.module.modules.ScoreboardModule.posX);
            scoreboard.addProperty("posY", com.mooclient.module.modules.ScoreboardModule.posY);
            root.add("scoreboard", scoreboard);

            // CPS Module
            JsonObject cps = new JsonObject();
            cps.addProperty("enabled", com.mooclient.module.modules.CpsModule.isCpsEnabled());
            cps.addProperty("displayMode", com.mooclient.module.modules.CpsModule.getDisplayMode().name());
            cps.addProperty("style", com.mooclient.module.modules.CpsModule.getStyle().name());
            cps.addProperty("showBackground", com.mooclient.module.modules.CpsModule.isShowBackground());
            cps.addProperty("textShadow", com.mooclient.module.modules.CpsModule.isTextShadow());
            cps.addProperty("showPrefix", com.mooclient.module.modules.CpsModule.isShowPrefix());
            cps.addProperty("anchorX", com.mooclient.module.modules.CpsModule.position.anchorX.name());
            cps.addProperty("anchorY", com.mooclient.module.modules.CpsModule.position.anchorY.name());
            cps.addProperty("offsetX", com.mooclient.module.modules.CpsModule.position.offsetX);
            cps.addProperty("offsetY", com.mooclient.module.modules.CpsModule.position.offsetY);
            cps.addProperty("posX", com.mooclient.module.modules.CpsModule.posX);
            cps.addProperty("posY", com.mooclient.module.modules.CpsModule.posY);
            root.add("cps", cps);

            // Armor HUD Module
            JsonObject armor = new JsonObject();
            armor.addProperty("enabled", com.mooclient.module.modules.ArmorModule.isArmorEnabled());
            armor.addProperty("style", com.mooclient.module.modules.ArmorModule.getStyle().name());
            armor.addProperty("orientation", com.mooclient.module.modules.ArmorModule.getOrientation().name());
            armor.addProperty("durabilityTextMode", com.mooclient.module.modules.ArmorModule.getDurabilityTextMode().name());
            armor.addProperty("showMaxDurability", com.mooclient.module.modules.ArmorModule.isShowMaxDurability());
            armor.addProperty("showDurabilityBar", com.mooclient.module.modules.ArmorModule.isShowDurabilityBar());
            armor.addProperty("lowDurabilityWarning", com.mooclient.module.modules.ArmorModule.isLowDurabilityWarning());
            armor.addProperty("durabilityMode", com.mooclient.module.modules.ArmorModule.getDurabilityMode().name());
            armor.addProperty("showBackground", com.mooclient.module.modules.ArmorModule.isShowBackground());
            armor.addProperty("showEmptySlots", com.mooclient.module.modules.ArmorModule.isShowEmptySlots());
            armor.addProperty("showOffhand", com.mooclient.module.modules.ArmorModule.isShowOffhand());
            armor.addProperty("showMainHand", com.mooclient.module.modules.ArmorModule.isShowMainHand());
            armor.addProperty("anchorX", com.mooclient.module.modules.ArmorModule.position.anchorX.name());
            armor.addProperty("anchorY", com.mooclient.module.modules.ArmorModule.position.anchorY.name());
            armor.addProperty("offsetX", com.mooclient.module.modules.ArmorModule.position.offsetX);
            armor.addProperty("offsetY", com.mooclient.module.modules.ArmorModule.position.offsetY);
            armor.addProperty("posX", com.mooclient.module.modules.ArmorModule.posX);
            armor.addProperty("posY", com.mooclient.module.modules.ArmorModule.posY);
            root.add("armor", armor);

            // Shulker Tooltip Module
            JsonObject shulker = new JsonObject();
            shulker.addProperty("enabled", com.mooclient.module.modules.ShulkerTooltipModule.isShulkerEnabled());
            shulker.addProperty("inspectEnabled", com.mooclient.module.modules.ShulkerTooltipModule.isInspectEnabled());
            shulker.addProperty("colorMatchedBorder", com.mooclient.module.modules.ShulkerTooltipModule.isColorMatchedBorder());
            shulker.addProperty("showEmptySlots", com.mooclient.module.modules.ShulkerTooltipModule.isShowEmptySlots());
            shulker.addProperty("requireShift", com.mooclient.module.modules.ShulkerTooltipModule.isRequireShift());
            root.add("shulkerTooltip", shulker);

            // Emotes Module
            JsonObject emotes = new JsonObject();
            emotes.addProperty("enabled", com.mooclient.module.modules.EmotesModule.isEmotesEnabled());
            emotes.addProperty("keyCode", com.mooclient.module.modules.EmotesModule.getKeyCode());
            emotes.addProperty("keyName", com.mooclient.module.modules.EmotesModule.getKeyName());
            emotes.addProperty("isMouseButton", com.mooclient.module.modules.EmotesModule.isMouseButton());
            emotes.addProperty("frontflipKeyCode", com.mooclient.module.modules.EmotesModule.getFrontflipKeyCode());
            emotes.addProperty("frontflipKeyName", com.mooclient.module.modules.EmotesModule.getFrontflipKeyName());
            emotes.addProperty("backflipKeyCode", com.mooclient.module.modules.EmotesModule.getBackflipKeyCode());
            emotes.addProperty("backflipKeyName", com.mooclient.module.modules.EmotesModule.getBackflipKeyName());
            emotes.addProperty("wheelKeyCode", com.mooclient.module.modules.EmotesModule.getWheelKeyCode());
            emotes.addProperty("wheelKeyName", com.mooclient.module.modules.EmotesModule.getWheelKeyName());
            emotes.addProperty("wheelIsMouseButton", com.mooclient.module.modules.EmotesModule.isWheelMouseButton());
            emotes.addProperty("acceptKeyCode", com.mooclient.module.modules.EmotesModule.getAcceptKeyCode());
            emotes.addProperty("acceptKeyName", com.mooclient.module.modules.EmotesModule.getAcceptKeyName());
            emotes.addProperty("acceptIsMouseButton", com.mooclient.module.modules.EmotesModule.isAcceptMouseButton());
            emotes.addProperty("declineKeyCode", com.mooclient.module.modules.EmotesModule.getDeclineKeyCode());
            emotes.addProperty("declineKeyName", com.mooclient.module.modules.EmotesModule.getDeclineKeyName());
            emotes.addProperty("declineIsMouseButton", com.mooclient.module.modules.EmotesModule.isDeclineMouseButton());
            emotes.addProperty("mode", com.mooclient.module.modules.EmotesModule.getMode().name());
            emotes.addProperty("restorePerspective", com.mooclient.module.modules.EmotesModule.isRestorePerspective());
            root.add("emotes", emotes);

            // Inventory View Module
            JsonObject invView = new JsonObject();
            invView.addProperty("enabled", com.mooclient.module.modules.InventoryViewModule.isModuleEnabled());
            invView.addProperty("active", com.mooclient.module.modules.InventoryViewModule.isActive());
            invView.addProperty("keyCode", com.mooclient.module.modules.InventoryViewModule.getKeyCode());
            invView.addProperty("keyName", com.mooclient.module.modules.InventoryViewModule.getKeyName());
            invView.addProperty("isMouseButton", com.mooclient.module.modules.InventoryViewModule.isMouseButton());
            invView.addProperty("mode", com.mooclient.module.modules.InventoryViewModule.getMode().name());
            invView.addProperty("style", com.mooclient.module.modules.InventoryViewModule.getStyle().name());
            invView.addProperty("showBackground", com.mooclient.module.modules.InventoryViewModule.isShowBackground());
            invView.addProperty("showEmptySlots", com.mooclient.module.modules.InventoryViewModule.isShowEmptySlots());
            invView.addProperty("anchorX", com.mooclient.module.modules.InventoryViewModule.position.anchorX.name());
            invView.addProperty("anchorY", com.mooclient.module.modules.InventoryViewModule.position.anchorY.name());
            invView.addProperty("offsetX", com.mooclient.module.modules.InventoryViewModule.position.offsetX);
            invView.addProperty("offsetY", com.mooclient.module.modules.InventoryViewModule.position.offsetY);
            root.add("inventoryView", invView);

            // Global Client Settings
            JsonObject settings = new JsonObject();
            settings.addProperty("accentPreset", MooClientSettings.getAccentPreset().name());
            settings.addProperty("customRed", MooClientSettings.getCustomRed());
            settings.addProperty("customGreen", MooClientSettings.getCustomGreen());
            settings.addProperty("customBlue", MooClientSettings.getCustomBlue());

            settings.addProperty("hudSnapping", MooClientSettings.isHudSnapping());
            settings.addProperty("hudScale", MooClientSettings.getHudScale());
            settings.addProperty("globalTextShadow", MooClientSettings.isGlobalTextShadow());
            settings.addProperty("menuBackgroundDim", MooClientSettings.getMenuBackgroundDim());
            settings.addProperty("guiAnimations", MooClientSettings.isGuiAnimations());
            settings.addProperty("activeProfile", MooClientSettings.getActiveProfile().name());
            settings.addProperty("invitationUiVariant", MooClientSettings.getInvitationUiVariant().name());
            root.add("settings", settings);

            Files.writeString(getConfigPath(), GSON.toJson(root));
            MooClient.LOGGER.info("Saved config to {}", getConfigPath());
        } catch (Exception e) {
            MooClient.LOGGER.error("Failed to save config", e);
        }
    }

    /**
     * Load all module settings from disk.
     */
    public static void load() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            MooClient.LOGGER.info("No config file found, using defaults.");
            return;
        }

        try {
            String json = Files.readString(configPath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Language
            if (root.has("language")) {
                String lang = root.get("language").getAsString();
                if (MooLanguage.EN.equalsIgnoreCase(lang) || MooLanguage.PL.equalsIgnoreCase(lang)) {
                    MooLanguage.current = lang.toLowerCase();
                }
            }

            // Gamma Module
            if (root.has("gamma")) {
                JsonObject gamma = root.getAsJsonObject("gamma");
                if (gamma.has("enabled")) {
                    boolean state = gamma.get("enabled").getAsBoolean();
                    FullbrightModule.setFullbrightActive(state);
                    ModuleManager.getInstance().getModule("Gamma").ifPresent(m -> m.setEnabled(state, false));
                }
            }

            // FPS Module
            if (root.has("fps")) {
                JsonObject fps = root.getAsJsonObject("fps");
                if (fps.has("enabled")) {
                    boolean state = fps.get("enabled").getAsBoolean();
                    FpsModule.setFpsEnabled(state);
                    ModuleManager.getInstance().getModule("FPS").ifPresent(m -> m.setEnabled(state, false));
                }
                if (fps.has("style")) {
                    try {
                        FpsModule.setStyle(FpsModule.FpsStyle.valueOf(fps.get("style").getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (fps.has("showBackground"))
                    FpsModule.setShowBackground(fps.get("showBackground").getAsBoolean());
                if (fps.has("textShadow"))
                    FpsModule.setTextShadow(fps.get("textShadow").getAsBoolean());
                if (fps.has("showPrefix"))
                    FpsModule.setShowPrefix(fps.get("showPrefix").getAsBoolean());
                if (fps.has("anchorX") && fps.has("anchorY") && fps.has("offsetX") && fps.has("offsetY")) {
                    try {
                        FpsModule.position.anchorX = MooHudPositionHelper.HudAnchorX.valueOf(fps.get("anchorX").getAsString());
                        FpsModule.position.anchorY = MooHudPositionHelper.HudAnchorY.valueOf(fps.get("anchorY").getAsString());
                        FpsModule.position.offsetX = fps.get("offsetX").getAsInt();
                        FpsModule.position.offsetY = fps.get("offsetY").getAsInt();
                    } catch (Exception ignored) {}
                } else if (fps.has("posX") && fps.has("posY")) {
                    int px = fps.get("posX").getAsInt();
                    int py = fps.get("posY").getAsInt();
                    if (px <= 150) {
                        FpsModule.position.anchorX = MooHudPositionHelper.HudAnchorX.LEFT;
                        FpsModule.position.offsetX = px;
                        FpsModule.position.anchorY = MooHudPositionHelper.HudAnchorY.TOP;
                        FpsModule.position.offsetY = py;
                    } else {
                        FpsModule.position.setFromScreenCoords(px, py, FpsModule.width, FpsModule.height, 960, 540);
                    }
                }
                if (fps.has("posX"))
                    FpsModule.posX = fps.get("posX").getAsInt();
                if (fps.has("posY"))
                    FpsModule.posY = fps.get("posY").getAsInt();
            }

            // Sprint Module
            if (root.has("sprint")) {
                JsonObject sprint = root.getAsJsonObject("sprint");
                if (sprint.has("enabled")) {
                    boolean state = sprint.get("enabled").getAsBoolean();
                    ToggleSprintModule.setSprintEnabled(state);
                    ModuleManager.getInstance().getModule("Sprint").ifPresent(m -> m.setEnabled(state));
                }
                if (sprint.has("style")) {
                    try {
                        ToggleSprintModule
                                .setStyle(ToggleSprintModule.SprintStyle.valueOf(sprint.get("style").getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (sprint.has("showBackground"))
                    ToggleSprintModule.setShowBackground(sprint.get("showBackground").getAsBoolean());
                if (sprint.has("textShadow"))
                    ToggleSprintModule.setTextShadow(sprint.get("textShadow").getAsBoolean());
                if (sprint.has("keyCode") && sprint.has("keyName")) {
                    boolean isMouse = sprint.has("isMouseButton") && sprint.get("isMouseButton").getAsBoolean();
                    ToggleSprintModule.setKeybind(sprint.get("keyCode").getAsInt(),
                            sprint.get("keyName").getAsString(), isMouse);
                }
                if (sprint.has("anchorX") && sprint.has("anchorY") && sprint.has("offsetX") && sprint.has("offsetY")) {
                    try {
                        ToggleSprintModule.position.anchorX = MooHudPositionHelper.HudAnchorX.valueOf(sprint.get("anchorX").getAsString());
                        ToggleSprintModule.position.anchorY = MooHudPositionHelper.HudAnchorY.valueOf(sprint.get("anchorY").getAsString());
                        ToggleSprintModule.position.offsetX = sprint.get("offsetX").getAsInt();
                        ToggleSprintModule.position.offsetY = sprint.get("offsetY").getAsInt();
                    } catch (Exception ignored) {}
                } else if (sprint.has("posX") && sprint.has("posY")) {
                    int px = sprint.get("posX").getAsInt();
                    int py = sprint.get("posY").getAsInt();
                    if (px <= 150) {
                        ToggleSprintModule.position.anchorX = MooHudPositionHelper.HudAnchorX.LEFT;
                        ToggleSprintModule.position.offsetX = px;
                        ToggleSprintModule.position.anchorY = MooHudPositionHelper.HudAnchorY.TOP;
                        ToggleSprintModule.position.offsetY = py;
                    } else {
                        ToggleSprintModule.position.setFromScreenCoords(px, py, ToggleSprintModule.width, ToggleSprintModule.height, 960, 540);
                    }
                }
                if (sprint.has("posX"))
                    ToggleSprintModule.posX = sprint.get("posX").getAsInt();
                if (sprint.has("posY"))
                    ToggleSprintModule.posY = sprint.get("posY").getAsInt();
            }

            // Freelook Module
            if (root.has("freelook")) {
                JsonObject freelook = root.getAsJsonObject("freelook");
                if (freelook.has("enabled")) {
                    boolean state = freelook.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.FreelookModule.setFreelookEnabled(state);
                    ModuleManager.getInstance().getModule("Freelook").ifPresent(m -> m.setEnabled(state));
                }
                if (freelook.has("mode")) {
                    try {
                        com.mooclient.module.modules.FreelookModule
                                .setMode(com.mooclient.module.modules.FreelookModule.ActivationMode
                                        .valueOf(freelook.get("mode").getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (freelook.has("invertPitch")) {
                    com.mooclient.module.modules.FreelookModule
                            .setInvertPitch(freelook.get("invertPitch").getAsBoolean());
                }
                if (freelook.has("keyCode") && freelook.has("keyName")) {
                    boolean isMouse = freelook.has("isMouseButton") && freelook.get("isMouseButton").getAsBoolean();
                    com.mooclient.module.modules.FreelookModule.setKeybind(freelook.get("keyCode").getAsInt(),
                            freelook.get("keyName").getAsString(), isMouse);
                }
            }

            // Potion Effects Module
            if (root.has("potions")) {
                JsonObject potions = root.getAsJsonObject("potions");
                if (potions.has("enabled")) {
                    boolean state = potions.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.PotionEffectsModule.setModuleEnabled(state);
                    ModuleManager.getInstance().getModule("Potion Effects").ifPresent(m -> m.setEnabled(state));
                }
                if (potions.has("style")) {
                    try {
                        com.mooclient.module.modules.PotionEffectsModule
                                .setStyle(com.mooclient.module.modules.PotionEffectsModule.PotionStyle
                                        .valueOf(potions.get("style").getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (potions.has("showBackground")) {
                    com.mooclient.module.modules.PotionEffectsModule
                                .setShowBackground(potions.get("showBackground").getAsBoolean());
                }
                if (potions.has("textShadow")) {
                    com.mooclient.module.modules.PotionEffectsModule
                            .setTextShadow(potions.get("textShadow").getAsBoolean());
                }
                if (potions.has("anchorX") && potions.has("anchorY") && potions.has("offsetX") && potions.has("offsetY")) {
                    try {
                        com.mooclient.module.modules.PotionEffectsModule.position.anchorX = MooHudPositionHelper.HudAnchorX.valueOf(potions.get("anchorX").getAsString());
                        com.mooclient.module.modules.PotionEffectsModule.position.anchorY = MooHudPositionHelper.HudAnchorY.valueOf(potions.get("anchorY").getAsString());
                        com.mooclient.module.modules.PotionEffectsModule.position.offsetX = potions.get("offsetX").getAsInt();
                        com.mooclient.module.modules.PotionEffectsModule.position.offsetY = potions.get("offsetY").getAsInt();
                    } catch (Exception ignored) {}
                } else if (potions.has("posX") && potions.has("posY")) {
                    int px = potions.get("posX").getAsInt();
                    int py = potions.get("posY").getAsInt();
                    if (px <= 150) {
                        com.mooclient.module.modules.PotionEffectsModule.position.anchorX = MooHudPositionHelper.HudAnchorX.LEFT;
                        com.mooclient.module.modules.PotionEffectsModule.position.offsetX = px;
                        com.mooclient.module.modules.PotionEffectsModule.position.anchorY = MooHudPositionHelper.HudAnchorY.TOP;
                        com.mooclient.module.modules.PotionEffectsModule.position.offsetY = py;
                    } else {
                        com.mooclient.module.modules.PotionEffectsModule.position.setFromScreenCoords(px, py, com.mooclient.module.modules.PotionEffectsModule.width, com.mooclient.module.modules.PotionEffectsModule.height, 960, 540);
                    }
                }
                if (potions.has("posX"))
                    com.mooclient.module.modules.PotionEffectsModule.posX = potions.get("posX").getAsInt();
                if (potions.has("posY"))
                    com.mooclient.module.modules.PotionEffectsModule.posY = potions.get("posY").getAsInt();
            }

            // Nametags Module
            if (root.has("nametags")) {
                JsonObject nametags = root.getAsJsonObject("nametags");
                if (nametags.has("enabled")) {
                    boolean state = nametags.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.NametagsModule.setNametagsEnabled(state);
                    ModuleManager.getInstance().getModule("Nametags").ifPresent(m -> m.setEnabled(state));
                }
                if (nametags.has("showLogo")) {
                    com.mooclient.module.modules.NametagsModule.setShowLogo(nametags.get("showLogo").getAsBoolean());
                }
                if (nametags.has("showPing")) {
                    com.mooclient.module.modules.NametagsModule.setShowPing(nametags.get("showPing").getAsBoolean());
                }
                if (nametags.has("showSelfPing")) {
                    com.mooclient.module.modules.NametagsModule
                            .setShowSelfPing(nametags.get("showSelfPing").getAsBoolean());
                }
                if (nametags.has("pingPosition")) {
                    try {
                        com.mooclient.module.modules.NametagsModule
                                .setPingPosition(com.mooclient.module.modules.NametagsModule.PingPosition
                                        .valueOf(nametags.get("pingPosition").getAsString()));
                    } catch (Exception ignored) {
                    }
                }
                if (nametags.has("removeBackground")) {
                    com.mooclient.module.modules.NametagsModule
                            .setRemoveBackground(nametags.get("removeBackground").getAsBoolean());
                }
                if (nametags.has("textShadow")) {
                    com.mooclient.module.modules.NametagsModule
                            .setTextShadow(nametags.get("textShadow").getAsBoolean());
                }
            }

            // Zoom Module
            if (root.has("zoom")) {
                JsonObject zoom = root.getAsJsonObject("zoom");
                if (zoom.has("enabled")) {
                    boolean state = zoom.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.ZoomModule.setZoomEnabled(state);
                    ModuleManager.getInstance().getModule("Zoom").ifPresent(m -> m.setEnabled(state));
                }
                if (zoom.has("factor")) {
                    try {
                        com.mooclient.module.modules.ZoomModule
                                .setFactor(com.mooclient.module.modules.ZoomModule.ZoomFactor
                                        .valueOf(zoom.get("factor").getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (zoom.has("mode")) {
                    try {
                        com.mooclient.module.modules.ZoomModule
                                .setMode(com.mooclient.module.modules.ZoomModule.ActivationMode
                                        .valueOf(zoom.get("mode").getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (zoom.has("smoothZoom")) {
                    com.mooclient.module.modules.ZoomModule.setSmoothZoom(zoom.get("smoothZoom").getAsBoolean());
                }
                if (zoom.has("keyCode") && zoom.has("keyName")) {
                    boolean isMouse = zoom.has("isMouseButton") && zoom.get("isMouseButton").getAsBoolean();
                    com.mooclient.module.modules.ZoomModule.setKeybind(zoom.get("keyCode").getAsInt(),
                            zoom.get("keyName").getAsString(), isMouse);
                }
            }

            // Macro Module
            if (root.has("macro")) {
                JsonObject macroJson = root.getAsJsonObject("macro");
                if (macroJson.has("enabled")) {
                    boolean state = macroJson.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.MacroModule.setMacroEnabled(state);
                    ModuleManager.getInstance().getModule("Macro").ifPresent(m -> m.setEnabled(state));
                }
                if (macroJson.has("list")) {
                    com.google.gson.JsonArray list = macroJson.getAsJsonArray("list");
                    java.util.List<com.mooclient.module.modules.MacroModule.MacroEntry> existing = com.mooclient.module.modules.MacroModule
                            .getMacros();
                    existing.clear();
                    for (int i = 0; i < Math.min(com.mooclient.module.modules.MacroModule.MAX_MACROS, list.size()); i++) {
                        JsonObject mObj = list.get(i).getAsJsonObject();
                        String id = mObj.has("id") ? mObj.get("id").getAsString() : ("macro_" + (i + 1));
                        String cmd = mObj.has("command") ? mObj.get("command").getAsString() : "";
                        int kCode = mObj.has("keyCode") ? mObj.get("keyCode").getAsInt() : 0;
                        String kName = mObj.has("keyName") ? mObj.get("keyName").getAsString() : "[NONE]";
                        boolean isMouse = mObj.has("isMouseButton") && mObj.get("isMouseButton").getAsBoolean();
                        boolean mEnabled = mObj.has("enabled") && mObj.get("enabled").getAsBoolean();
                        existing.add(new com.mooclient.module.modules.MacroModule.MacroEntry(id, cmd, kCode, kName,
                                isMouse, mEnabled));
                    }
                }
            }

            // Chat Module
            if (root.has("chat")) {
                JsonObject chat = root.getAsJsonObject("chat");
                if (chat.has("enabled")) {
                    boolean state = chat.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.ChatModule.setModuleEnabled(state);
                    ModuleManager.getInstance().getModule("Chat").ifPresent(m -> m.setEnabled(state));
                }
                if (chat.has("transparentBackground")) {
                    com.mooclient.module.modules.ChatModule
                            .setTransparentBackground(chat.get("transparentBackground").getAsBoolean());
                }
                if (chat.has("unlimitedChat")) {
                    com.mooclient.module.modules.ChatModule.setUnlimitedChat(chat.get("unlimitedChat").getAsBoolean());
                }
                if (chat.has("stackMessages")) {
                    com.mooclient.module.modules.ChatModule.setStackMessages(chat.get("stackMessages").getAsBoolean());
                }
                if (chat.has("smoothChat")) {
                    com.mooclient.module.modules.ChatModule.setSmoothChat(chat.get("smoothChat").getAsBoolean());
                }
                if (chat.has("textShadow")) {
                    com.mooclient.module.modules.ChatModule.setTextShadow(chat.get("textShadow").getAsBoolean());
                }
            }

            // Ping Module
            if (root.has("ping")) {
                JsonObject ping = root.getAsJsonObject("ping");
                if (ping.has("enabled")) {
                    boolean state = ping.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.PingModule.setPingEnabled(state);
                    ModuleManager.getInstance().getModule("Ping").ifPresent(m -> m.setEnabled(state));
                }
                if (ping.has("style")) {
                    try {
                        com.mooclient.module.modules.PingModule
                                .setStyle(com.mooclient.module.modules.PingModule.PingStyle
                                        .valueOf(ping.get("style").getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (ping.has("showBackground")) {
                    com.mooclient.module.modules.PingModule
                            .setShowBackground(ping.get("showBackground").getAsBoolean());
                }
                if (ping.has("textShadow")) {
                    com.mooclient.module.modules.PingModule.setTextShadow(ping.get("textShadow").getAsBoolean());
                }
                if (ping.has("showPrefix")) {
                    com.mooclient.module.modules.PingModule.setShowPrefix(ping.get("showPrefix").getAsBoolean());
                }
                if (ping.has("anchorX") && ping.has("anchorY") && ping.has("offsetX") && ping.has("offsetY")) {
                    try {
                        com.mooclient.module.modules.PingModule.position.anchorX = MooHudPositionHelper.HudAnchorX.valueOf(ping.get("anchorX").getAsString());
                        com.mooclient.module.modules.PingModule.position.anchorY = MooHudPositionHelper.HudAnchorY.valueOf(ping.get("anchorY").getAsString());
                        com.mooclient.module.modules.PingModule.position.offsetX = ping.get("offsetX").getAsInt();
                        com.mooclient.module.modules.PingModule.position.offsetY = ping.get("offsetY").getAsInt();
                    } catch (Exception ignored) {}
                } else if (ping.has("posX") && ping.has("posY")) {
                    int px = ping.get("posX").getAsInt();
                    int py = ping.get("posY").getAsInt();
                    if (px <= 150) {
                        com.mooclient.module.modules.PingModule.position.anchorX = MooHudPositionHelper.HudAnchorX.LEFT;
                        com.mooclient.module.modules.PingModule.position.offsetX = px;
                        com.mooclient.module.modules.PingModule.position.anchorY = MooHudPositionHelper.HudAnchorY.TOP;
                        com.mooclient.module.modules.PingModule.position.offsetY = py;
                    } else {
                        com.mooclient.module.modules.PingModule.position.setFromScreenCoords(px, py, com.mooclient.module.modules.PingModule.width, com.mooclient.module.modules.PingModule.height, 960, 540);
                    }
                }
                if (ping.has("posX")) {
                    com.mooclient.module.modules.PingModule.posX = ping.get("posX").getAsInt();
                }
                if (ping.has("posY")) {
                    com.mooclient.module.modules.PingModule.posY = ping.get("posY").getAsInt();
                }
            }

            // Waypoints Module
            if (root.has("waypoints")) {
                JsonObject waypoints = root.getAsJsonObject("waypoints");
                if (waypoints.has("enabled")) {
                    boolean state = waypoints.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.WaypointsModule.setWaypointsEnabled(state);
                    ModuleManager.getInstance().getModule("Waypoints").ifPresent(m -> m.setEnabled(state));
                }
                if (waypoints.has("showDistance")) {
                    com.mooclient.module.modules.WaypointsModule
                            .setShowDistance(waypoints.get("showDistance").getAsBoolean());
                }
                if (waypoints.has("showBeacons")) {
                    com.mooclient.module.modules.WaypointsModule
                            .setShowBeacons(waypoints.get("showBeacons").getAsBoolean());
                }
                if (waypoints.has("showBackground")) {
                    com.mooclient.module.modules.WaypointsModule
                            .setShowBackground(waypoints.get("showBackground").getAsBoolean());
                }
                if (waypoints.has("textShadow")) {
                    com.mooclient.module.modules.WaypointsModule
                            .setTextShadow(waypoints.get("textShadow").getAsBoolean());
                }
                if (waypoints.has("deathWaypoint")) {
                    com.mooclient.module.modules.WaypointsModule
                            .setDeathWaypoint(waypoints.get("deathWaypoint").getAsBoolean());
                }
                if (waypoints.has("showAllDimensions")) {
                    com.mooclient.module.modules.WaypointsModule
                            .setShowAllDimensions(waypoints.get("showAllDimensions").getAsBoolean());
                }
                if (waypoints.has("showAllServers")) {
                    com.mooclient.module.modules.WaypointsModule
                            .setShowAllServers(waypoints.get("showAllServers").getAsBoolean());
                }
                if (waypoints.has("scale")) {
                    com.mooclient.module.modules.WaypointsModule.setScale(waypoints.get("scale").getAsFloat());
                }
                if (waypoints.has("keyCode") && waypoints.has("keyName")) {
                    boolean isMouse = waypoints.has("isMouseButton") && waypoints.get("isMouseButton").getAsBoolean();
                    com.mooclient.module.modules.WaypointsModule.setKeybind(waypoints.get("keyCode").getAsInt(),
                            waypoints.get("keyName").getAsString(), isMouse);
                }
            }

            // Scoreboard Module
            if (root.has("scoreboard")) {
                JsonObject scoreboard = root.getAsJsonObject("scoreboard");
                if (scoreboard.has("enabled")) {
                    boolean state = scoreboard.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.ScoreboardModule.setScoreboardEnabled(state);
                    ModuleManager.getInstance().getModule("Scoreboard").ifPresent(m -> m.setEnabled(state));
                }
                if (scoreboard.has("textShadow")) {
                    com.mooclient.module.modules.ScoreboardModule
                            .setTextShadow(scoreboard.get("textShadow").getAsBoolean());
                }
                if (scoreboard.has("showBackground")) {
                    com.mooclient.module.modules.ScoreboardModule
                            .setShowBackground(scoreboard.get("showBackground").getAsBoolean());
                }
                if (scoreboard.has("showScores")) {
                    com.mooclient.module.modules.ScoreboardModule
                            .setShowScores(scoreboard.get("showScores").getAsBoolean());
                }
                if (scoreboard.has("anchorX") && scoreboard.has("anchorY") && scoreboard.has("offsetX") && scoreboard.has("offsetY")) {
                    try {
                        com.mooclient.module.modules.ScoreboardModule.position.anchorX = MooHudPositionHelper.HudAnchorX.valueOf(scoreboard.get("anchorX").getAsString());
                        com.mooclient.module.modules.ScoreboardModule.position.anchorY = MooHudPositionHelper.HudAnchorY.valueOf(scoreboard.get("anchorY").getAsString());
                        com.mooclient.module.modules.ScoreboardModule.position.offsetX = scoreboard.get("offsetX").getAsInt();
                        com.mooclient.module.modules.ScoreboardModule.position.offsetY = scoreboard.get("offsetY").getAsInt();
                    } catch (Exception ignored) {}
                } else if (scoreboard.has("posX") && scoreboard.has("posY")) {
                    int px = scoreboard.get("posX").getAsInt();
                    int py = scoreboard.get("posY").getAsInt();
                    if (px < 0) {
                        com.mooclient.module.modules.ScoreboardModule.resetPosition();
                    } else {
                        com.mooclient.module.modules.ScoreboardModule.position.setFromScreenCoords(px, py, com.mooclient.module.modules.ScoreboardModule.width, com.mooclient.module.modules.ScoreboardModule.height, 960, 540);
                    }
                }
                if (scoreboard.has("posX")) {
                    com.mooclient.module.modules.ScoreboardModule.posX = scoreboard.get("posX").getAsInt();
                }
                if (scoreboard.has("posY")) {
                    com.mooclient.module.modules.ScoreboardModule.posY = scoreboard.get("posY").getAsInt();
                }
            }

            // CPS Module
            if (root.has("cps")) {
                JsonObject cps = root.getAsJsonObject("cps");
                if (cps.has("enabled")) {
                    boolean state = cps.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.CpsModule.setCpsEnabled(state);
                    ModuleManager.getInstance().getModule("CPS").ifPresent(m -> m.setEnabled(state));
                }
                if (cps.has("displayMode")) {
                    try {
                        com.mooclient.module.modules.CpsModule.setDisplayMode(com.mooclient.module.modules.CpsModule.CpsDisplayMode.valueOf(cps.get("displayMode").getAsString()));
                    } catch (Exception ignored) {}
                }
                if (cps.has("style")) {
                    try {
                        com.mooclient.module.modules.CpsModule.setStyle(com.mooclient.module.modules.CpsModule.CpsStyle.valueOf(cps.get("style").getAsString()));
                    } catch (Exception ignored) {}
                }
                if (cps.has("showBackground")) {
                    com.mooclient.module.modules.CpsModule.setShowBackground(cps.get("showBackground").getAsBoolean());
                }
                if (cps.has("textShadow")) {
                    com.mooclient.module.modules.CpsModule.setTextShadow(cps.get("textShadow").getAsBoolean());
                }
                if (cps.has("showPrefix")) {
                    com.mooclient.module.modules.CpsModule.setShowPrefix(cps.get("showPrefix").getAsBoolean());
                }
                if (cps.has("anchorX") && cps.has("anchorY") && cps.has("offsetX") && cps.has("offsetY")) {
                    try {
                        com.mooclient.module.modules.CpsModule.position.anchorX = MooHudPositionHelper.HudAnchorX.valueOf(cps.get("anchorX").getAsString());
                        com.mooclient.module.modules.CpsModule.position.anchorY = MooHudPositionHelper.HudAnchorY.valueOf(cps.get("anchorY").getAsString());
                        com.mooclient.module.modules.CpsModule.position.offsetX = cps.get("offsetX").getAsInt();
                        com.mooclient.module.modules.CpsModule.position.offsetY = cps.get("offsetY").getAsInt();
                    } catch (Exception ignored) {}
                } else if (cps.has("posX") && cps.has("posY")) {
                    int px = cps.get("posX").getAsInt();
                    int py = cps.get("posY").getAsInt();
                    if (px <= 150) {
                        com.mooclient.module.modules.CpsModule.position.anchorX = MooHudPositionHelper.HudAnchorX.LEFT;
                        com.mooclient.module.modules.CpsModule.position.offsetX = px;
                        com.mooclient.module.modules.CpsModule.position.anchorY = MooHudPositionHelper.HudAnchorY.TOP;
                        com.mooclient.module.modules.CpsModule.position.offsetY = py;
                    } else {
                        com.mooclient.module.modules.CpsModule.position.setFromScreenCoords(px, py, com.mooclient.module.modules.CpsModule.width, com.mooclient.module.modules.CpsModule.height, 960, 540);
                    }
                }
                if (cps.has("posX")) {
                    com.mooclient.module.modules.CpsModule.posX = cps.get("posX").getAsInt();
                }
                if (cps.has("posY")) {
                    com.mooclient.module.modules.CpsModule.posY = cps.get("posY").getAsInt();
                }
            }

            // Armor HUD Module
            if (root.has("armor")) {
                JsonObject armor = root.getAsJsonObject("armor");
                if (armor.has("enabled")) {
                    boolean state = armor.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.ArmorModule.setArmorEnabled(state);
                    ModuleManager.getInstance().getModule("Armor HUD").ifPresent(m -> m.setEnabled(state));
                    ModuleManager.getInstance().getModule("Armor").ifPresent(m -> m.setEnabled(state));
                }
                if (armor.has("style")) {
                    try {
                        com.mooclient.module.modules.ArmorModule.setStyle(
                                com.mooclient.module.modules.ArmorModule.ArmorStyle.valueOf(armor.get("style").getAsString()));
                    } catch (Exception ignored) {}
                }
                if (armor.has("orientation")) {
                    try {
                        com.mooclient.module.modules.ArmorModule.setOrientation(
                                com.mooclient.module.modules.ArmorModule.ArmorOrientation.valueOf(armor.get("orientation").getAsString()));
                    } catch (Exception ignored) {}
                }
                if (armor.has("durabilityTextMode")) {
                    try {
                        com.mooclient.module.modules.ArmorModule.setDurabilityTextMode(
                                com.mooclient.module.modules.ArmorModule.DurabilityTextMode.valueOf(armor.get("durabilityTextMode").getAsString()));
                    } catch (Exception ignored) {}
                } else if (armor.has("durabilityMode")) {
                    try {
                        com.mooclient.module.modules.ArmorModule.setDurabilityMode(
                                com.mooclient.module.modules.ArmorModule.DurabilityMode.valueOf(armor.get("durabilityMode").getAsString()));
                    } catch (Exception ignored) {}
                }
                if (armor.has("showMaxDurability")) {
                    com.mooclient.module.modules.ArmorModule.setShowMaxDurability(armor.get("showMaxDurability").getAsBoolean());
                }
                if (armor.has("showDurabilityBar")) {
                    com.mooclient.module.modules.ArmorModule.setShowDurabilityBar(armor.get("showDurabilityBar").getAsBoolean());
                }
                if (armor.has("lowDurabilityWarning")) {
                    com.mooclient.module.modules.ArmorModule.setLowDurabilityWarning(armor.get("lowDurabilityWarning").getAsBoolean());
                }
                if (armor.has("showBackground")) {
                    com.mooclient.module.modules.ArmorModule.setShowBackground(armor.get("showBackground").getAsBoolean());
                }
                if (armor.has("showEmptySlots")) {
                    com.mooclient.module.modules.ArmorModule.setShowEmptySlots(armor.get("showEmptySlots").getAsBoolean());
                }
                if (armor.has("showOffhand")) {
                    com.mooclient.module.modules.ArmorModule.setShowOffhand(armor.get("showOffhand").getAsBoolean());
                }
                if (armor.has("showMainHand")) {
                    com.mooclient.module.modules.ArmorModule.setShowMainHand(armor.get("showMainHand").getAsBoolean());
                }
                if (armor.has("anchorX") && armor.has("anchorY") && armor.has("offsetX") && armor.has("offsetY")) {
                    try {
                        com.mooclient.module.modules.ArmorModule.position.anchorX = MooHudPositionHelper.HudAnchorX.valueOf(armor.get("anchorX").getAsString());
                        com.mooclient.module.modules.ArmorModule.position.anchorY = MooHudPositionHelper.HudAnchorY.valueOf(armor.get("anchorY").getAsString());
                        com.mooclient.module.modules.ArmorModule.position.offsetX = armor.get("offsetX").getAsInt();
                        com.mooclient.module.modules.ArmorModule.position.offsetY = armor.get("offsetY").getAsInt();
                    } catch (Exception ignored) {}
                } else if (armor.has("posX") && armor.has("posY")) {
                    int px = armor.get("posX").getAsInt();
                    int py = armor.get("posY").getAsInt();
                    com.mooclient.module.modules.ArmorModule.position.setFromScreenCoords(px, py, com.mooclient.module.modules.ArmorModule.width, com.mooclient.module.modules.ArmorModule.height, 960, 540);
                }
                if (armor.has("posX")) {
                    com.mooclient.module.modules.ArmorModule.posX = armor.get("posX").getAsInt();
                }
                if (armor.has("posY")) {
                    com.mooclient.module.modules.ArmorModule.posY = armor.get("posY").getAsInt();
                }
            }

            // Shulker Tooltip Module
            if (root.has("shulkerTooltip")) {
                JsonObject shulker = root.getAsJsonObject("shulkerTooltip");
                if (shulker.has("enabled")) {
                    boolean state = shulker.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.ShulkerTooltipModule.setShulkerEnabled(state);
                    ModuleManager.getInstance().getModule("Shulker Tooltip").ifPresent(m -> m.setEnabled(state, false));
                }
                if (shulker.has("inspectEnabled")) {
                    com.mooclient.module.modules.ShulkerTooltipModule.setInspectEnabled(shulker.get("inspectEnabled").getAsBoolean());
                }
                if (shulker.has("colorMatchedBorder")) {
                    com.mooclient.module.modules.ShulkerTooltipModule.setColorMatchedBorder(shulker.get("colorMatchedBorder").getAsBoolean());
                }
                if (shulker.has("showEmptySlots")) {
                    com.mooclient.module.modules.ShulkerTooltipModule.setShowEmptySlots(shulker.get("showEmptySlots").getAsBoolean());
                }
                if (shulker.has("requireShift")) {
                    com.mooclient.module.modules.ShulkerTooltipModule.setRequireShift(shulker.get("requireShift").getAsBoolean());
                }
            }

            // Emotes Module
            if (root.has("emotes")) {
                JsonObject emotes = root.getAsJsonObject("emotes");
                if (emotes.has("enabled")) {
                    boolean state = emotes.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.EmotesModule.setEmotesEnabled(state);
                    ModuleManager.getInstance().getModule("Emotes").ifPresent(m -> m.setEnabled(state));
                    ModuleManager.getInstance().getModule("Emotki").ifPresent(m -> m.setEnabled(state));
                }
                if (emotes.has("keyCode") && emotes.has("keyName")) {
                    boolean isMouse = emotes.has("isMouseButton") && emotes.get("isMouseButton").getAsBoolean();
                    com.mooclient.module.modules.EmotesModule.setKeybind(emotes.get("keyCode").getAsInt(),
                            emotes.get("keyName").getAsString(), isMouse);
                }
                if (emotes.has("frontflipKeyCode") && emotes.has("frontflipKeyName")) {
                    com.mooclient.module.modules.EmotesModule.setFrontflipKeybind(
                            emotes.get("frontflipKeyCode").getAsInt(),
                            emotes.get("frontflipKeyName").getAsString());
                }
                if (emotes.has("backflipKeyCode") && emotes.has("backflipKeyName")) {
                    com.mooclient.module.modules.EmotesModule.setBackflipKeybind(
                            emotes.get("backflipKeyCode").getAsInt(),
                            emotes.get("backflipKeyName").getAsString());
                }
                if (emotes.has("wheelKeyCode") && emotes.has("wheelKeyName")) {
                    boolean wheelIsMouse = emotes.has("wheelIsMouseButton") && emotes.get("wheelIsMouseButton").getAsBoolean();
                    com.mooclient.module.modules.EmotesModule.setWheelKeybind(
                            emotes.get("wheelKeyCode").getAsInt(),
                            emotes.get("wheelKeyName").getAsString(), wheelIsMouse);
                }
                if (emotes.has("acceptKeyCode") && emotes.has("acceptKeyName")) {
                    boolean acceptIsMouse = emotes.has("acceptIsMouseButton") && emotes.get("acceptIsMouseButton").getAsBoolean();
                    com.mooclient.module.modules.EmotesModule.setAcceptKeybind(
                            emotes.get("acceptKeyCode").getAsInt(),
                            emotes.get("acceptKeyName").getAsString(), acceptIsMouse);
                }
                if (emotes.has("declineKeyCode") && emotes.has("declineKeyName")) {
                    boolean declineIsMouse = emotes.has("declineIsMouseButton") && emotes.get("declineIsMouseButton").getAsBoolean();
                    com.mooclient.module.modules.EmotesModule.setDeclineKeybind(
                            emotes.get("declineKeyCode").getAsInt(),
                            emotes.get("declineKeyName").getAsString(), declineIsMouse);
                }
                if (emotes.has("mode")) {
                    try {
                        com.mooclient.module.modules.EmotesModule.setMode(
                                com.mooclient.module.modules.EmotesModule.ActivationMode.valueOf(emotes.get("mode").getAsString()));
                    } catch (Exception ignored) {}
                }
                if (emotes.has("restorePerspective")) {
                    com.mooclient.module.modules.EmotesModule.setRestorePerspective(emotes.get("restorePerspective").getAsBoolean());
                }
            }

            // Inventory View Module
            if (root.has("inventoryView")) {
                JsonObject invView = root.getAsJsonObject("inventoryView");
                if (invView.has("enabled")) {
                    boolean state = invView.get("enabled").getAsBoolean();
                    com.mooclient.module.modules.InventoryViewModule.setModuleEnabled(state);
                    ModuleManager.getInstance().getModule("Inventory View").ifPresent(m -> m.setEnabled(state));
                }
                if (invView.has("active")) {
                    com.mooclient.module.modules.InventoryViewModule.setActive(invView.get("active").getAsBoolean());
                }
                if (invView.has("keyCode")) {
                    com.mooclient.module.modules.InventoryViewModule.setKeyCode(invView.get("keyCode").getAsInt());
                }
                if (invView.has("keyName")) {
                    com.mooclient.module.modules.InventoryViewModule.setKeyName(invView.get("keyName").getAsString());
                }
                if (invView.has("isMouseButton")) {
                    com.mooclient.module.modules.InventoryViewModule.setMouseButton(invView.get("isMouseButton").getAsBoolean());
                }
                if (invView.has("mode")) {
                    try {
                        com.mooclient.module.modules.InventoryViewModule.setMode(
                                com.mooclient.module.modules.InventoryViewModule.ActivationMode.valueOf(invView.get("mode").getAsString()));
                    } catch (Exception ignored) {}
                }
                if (invView.has("style")) {
                    try {
                        com.mooclient.module.modules.InventoryViewModule.setStyle(
                                com.mooclient.module.modules.InventoryViewModule.InventoryStyle.valueOf(invView.get("style").getAsString()));
                    } catch (Exception ignored) {}
                }
                if (invView.has("showBackground")) {
                    com.mooclient.module.modules.InventoryViewModule.setShowBackground(invView.get("showBackground").getAsBoolean());
                }
                if (invView.has("showEmptySlots")) {
                    com.mooclient.module.modules.InventoryViewModule.setShowEmptySlots(invView.get("showEmptySlots").getAsBoolean());
                }
                if (invView.has("anchorX") && invView.has("anchorY") && invView.has("offsetX") && invView.has("offsetY")) {
                    try {
                        com.mooclient.module.modules.InventoryViewModule.position.anchorX =
                                MooHudPositionHelper.HudAnchorX.valueOf(invView.get("anchorX").getAsString());
                        com.mooclient.module.modules.InventoryViewModule.position.anchorY =
                                MooHudPositionHelper.HudAnchorY.valueOf(invView.get("anchorY").getAsString());
                        com.mooclient.module.modules.InventoryViewModule.position.offsetX = invView.get("offsetX").getAsInt();
                        com.mooclient.module.modules.InventoryViewModule.position.offsetY = invView.get("offsetY").getAsInt();
                    } catch (Exception ignored) {}
                }
            }

            // Global Client Settings
            if (root.has("settings")) {
                JsonObject settings = root.getAsJsonObject("settings");
                if (settings.has("accentPreset")) {
                    try {
                        MooClientSettings.setAccentPreset(MooClientSettings.AccentColorPreset
                                .valueOf(settings.get("accentPreset").getAsString()));
                    } catch (Exception ignored) {
                    }
                }
                if (settings.has("customRed"))
                    MooClientSettings.setCustomRed(settings.get("customRed").getAsInt());
                if (settings.has("customGreen"))
                    MooClientSettings.setCustomGreen(settings.get("customGreen").getAsInt());
                if (settings.has("customBlue"))
                    MooClientSettings.setCustomBlue(settings.get("customBlue").getAsInt());
                if (settings.has("hudSnapping"))
                    MooClientSettings.setHudSnapping(settings.get("hudSnapping").getAsBoolean());
                if (settings.has("hudScale")) {
                    int scale = settings.get("hudScale").getAsInt();
                    if (scale <= 2) {
                        scale = (scale == 0 ? 85 : (scale == 1 ? 100 : 115));
                    }
                    MooClientSettings.setHudScale(scale);
                }
                if (settings.has("globalTextShadow"))
                    MooClientSettings.setGlobalTextShadow(settings.get("globalTextShadow").getAsBoolean());
                if (settings.has("menuBackgroundDim"))
                    MooClientSettings.setMenuBackgroundDim(settings.get("menuBackgroundDim").getAsInt());
                if (settings.has("guiAnimations"))
                    MooClientSettings.setGuiAnimations(settings.get("guiAnimations").getAsBoolean());
                if (settings.has("invitationUiVariant")) {
                    try {
                        MooClientSettings.setInvitationUiVariant(InvitationUIManager.UiVariant.valueOf(settings.get("invitationUiVariant").getAsString()));
                    } catch (Exception ignored) {}
                }
            }

            MooClient.LOGGER.info("Loaded config from {}", getConfigPath());
        } catch (Exception e) {
            MooClient.LOGGER.error("Failed to load config", e);
        }
    }
}
