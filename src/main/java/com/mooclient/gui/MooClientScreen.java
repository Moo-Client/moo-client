package com.mooclient.gui;

import com.mooclient.module.Module;
import com.mooclient.module.ModuleManager;
import com.mooclient.module.modules.CpsModule;
import com.mooclient.module.modules.FpsModule;
import com.mooclient.module.modules.FreelookModule;
import com.mooclient.module.modules.PingModule;
import com.mooclient.module.modules.PotionEffectsModule;
import com.mooclient.module.modules.ScoreboardModule;
import com.mooclient.module.modules.ToggleSprintModule;
import com.mooclient.util.MooClientSettings;
import com.mooclient.util.MooConfig;
import com.mooclient.util.MooHudPositionHelper;
import com.mooclient.util.MooHudPositionHelper.GuideLine;
import com.mooclient.util.MooHudPositionHelper.WidgetRect;
import com.mooclient.util.MooLanguage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Lunar Client inspired in-game HUD, Draggable Widgets, Mods Grid & Options
 * Screen for Moo Client.
 */
public class MooClientScreen extends Screen {

    private static final Identifier COW_LOGO = Identifier.of("minecraft", "icons/icon_128x128.png");

    private enum View {
        HUB,
        MODS,
        OPTIONS,
        SETTINGS
    }

    private View currentView = View.HUB;
    private Module selectedModule = null;
    private boolean listeningForKeybind = false;
    private int listeningEmoteSlot = 0; // 0 = Hands Up, 1 = Frontflip, 2 = Backflip
    private int listeningMacroIndex = -1;
    private int editingMacroIndex = -1;
    private double scrollY = 0;

    // Search bar state in Mods view
    private String searchFilter = "";
    private boolean searching = false;

    // Settings screen state
    private int settingsTab = 0; // 0 = Accent, 1 = HUD, 2 = GUI
    private int draggingSlider = -1; // 0 = Red, 1 = Green, 2 = Blue
    private long resetHudFeedbackTime = 0;

    // Draggable HUD widget state
    private String draggingWidget = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private List<GuideLine> activeGuideLines = new ArrayList<>();

    // Palette
    private static final int COLOR_HUB_OVERLAY = 0x55000000;
    private static final int COLOR_PANEL_BG = 0xF4111116;
    private static final int COLOR_PANEL_BORDER = 0x44FFFFFF;
    private static final int COLOR_CARD_BG = 0xAA181822;
    private static final int COLOR_CARD_HOVER = 0xDD22222E;
    private static final int COLOR_CARD_BORDER = 0x33FFFFFF;
    private static final int COLOR_CARD_BORDER_HOVER = 0x88FFFFFF;
    private static final int COLOR_OPTIONS_BG = 0x990A0A0F;
    private static final int COLOR_OPTIONS_HOVER = 0xCC1A1A26;
    private static final int COLOR_DISABLED = 0xFF353540;
    private static final int COLOR_DISABLED_HOVER = 0xFF454552;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MUTED = 0xFFA0A0AB;

    public MooClientScreen() {
        super(Text.literal("Moo Client HUD"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Disabled to prevent vanilla screen blur
    }

    @Override
    public void renderInGameBackground(DrawContext context) {
        // Disabled to prevent vanilla screen blur
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int dimColor = com.mooclient.util.MooClientSettings.getBackgroundDimColor();

        if (currentView == View.HUB) {
            context.fillGradient(0, 0, this.width, this.height, COLOR_HUB_OVERLAY, COLOR_HUB_OVERLAY);
            renderLunarHub(context, mouseX, mouseY, delta);
            renderDraggableHudWidgets(context, mouseX, mouseY);
        } else if (currentView == View.MODS) {
            context.fillGradient(0, 0, this.width, this.height, dimColor, dimColor);
            renderModsWindow(context, mouseX, mouseY, delta);
        } else if (currentView == View.OPTIONS) {
            context.fillGradient(0, 0, this.width, this.height, dimColor, dimColor);
            renderOptionsWindow(context, mouseX, mouseY, delta);
        } else if (currentView == View.SETTINGS) {
            context.fillGradient(0, 0, this.width, this.height, dimColor, dimColor);
            renderSettingsWindow(context, mouseX, mouseY, delta);
        }

        // Language Switcher in top-right corner of screen
        renderLanguageSwitcher(context, this.width - 66, 12, mouseX, mouseY);
    }

    /**
     * Draggable HUD widgets with clean, minimalist preview
     */
    private void renderDraggableHudWidgets(DrawContext context, int mouseX, int mouseY) {
        if (this.client == null)
            return;
        float hudScale = com.mooclient.util.MooClientSettings.getHudScaleFactor();

        // 1. Draggable FPS Widget
        if (FpsModule.isFpsEnabled()) {
            int fps = this.client.getCurrentFps();
            String fpsText = FpsModule.getStyle() == FpsModule.FpsStyle.BRACKETS ? "[" + fps + " FPS]"
                    : (FpsModule.isShowPrefix() ? "FPS: " + fps : fps + " FPS");
            int textWidth = this.textRenderer.getWidth(fpsText);
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            FpsModule.width = boxW;
            FpsModule.height = boxH;

            int x = FpsModule.position.calculateX(boxW, this.width);
            int y = FpsModule.position.calculateY(boxH, this.height);

            boolean hovered = mouseX >= x - 2 && mouseX <= x - 2 + boxW && mouseY >= y - 2 && mouseY <= y - 2 + boxH;
            boolean isDragging = "FPS".equals(draggingWidget);

            renderWidgetBoundingBox(context, x - 2, y - 2, boxW, boxH, hovered, isDragging);
        }

        // 2. Draggable Sprint Widget Preview
        if (ToggleSprintModule.isSprintEnabled()) {
            ToggleSprintModule.SprintStyle style = ToggleSprintModule.getStyle();
            String sprintText;
            if (style == ToggleSprintModule.SprintStyle.BRACKETS) {
                sprintText = "[Sprinting]";
            } else if (style == ToggleSprintModule.SprintStyle.SIMPLE) {
                sprintText = "Sprinting";
            } else {
                sprintText = "Sprinting (Toggled)";
            }
            int textWidth = this.textRenderer.getWidth(sprintText);
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            ToggleSprintModule.width = boxW;
            ToggleSprintModule.height = boxH;

            int x = ToggleSprintModule.position.calculateX(boxW, this.width);
            int y = ToggleSprintModule.position.calculateY(boxH, this.height);

            boolean hovered = mouseX >= x - 2 && mouseX <= x - 2 + boxW && mouseY >= y - 2 && mouseY <= y - 2 + boxH;
            boolean isDragging = "SPRINT".equals(draggingWidget);

            renderWidgetBoundingBox(context, x - 2, y - 2, boxW, boxH, hovered, isDragging);
        }

        // 3. Draggable Potion Effects Widget Preview
        if (PotionEffectsModule.isModuleEnabled()) {
            int boxW = PotionEffectsModule.width > 0 ? PotionEffectsModule.width : Math.round(110 * hudScale);
            int boxH = PotionEffectsModule.height > 0 ? PotionEffectsModule.height : Math.round(50 * hudScale);

            int x = PotionEffectsModule.position.calculateX(boxW, this.width);
            int y = PotionEffectsModule.position.calculateY(boxH, this.height);

            boolean hovered = mouseX >= x - 2 && mouseX <= x - 2 + boxW && mouseY >= y - 2 && mouseY <= y - 2 + boxH;
            boolean isDragging = "POTIONS".equals(draggingWidget);

            renderWidgetBoundingBox(context, x - 2, y - 2, boxW, boxH, hovered, isDragging);
        }

        // 4. Draggable Ping Widget Preview
        if (PingModule.isPingEnabled()) {
            int ping = PingModule.getCurrentPing();
            String pingText = PingModule.getStyle() == PingModule.PingStyle.BRACKETS ? "[" + ping + " ms]"
                    : (PingModule.isShowPrefix() ? "Ping: " + ping + " ms" : ping + " ms");
            int textWidth = this.textRenderer.getWidth(pingText);
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            PingModule.width = boxW;
            PingModule.height = boxH;

            int x = PingModule.position.calculateX(boxW, this.width);
            int y = PingModule.position.calculateY(boxH, this.height);

            boolean hovered = mouseX >= x - 2 && mouseX <= x - 2 + boxW && mouseY >= y - 2 && mouseY <= y - 2 + boxH;
            boolean isDragging = "PING".equals(draggingWidget);

            renderWidgetBoundingBox(context, x - 2, y - 2, boxW, boxH, hovered, isDragging);
        }

        // 5. Draggable CPS Widget Preview
        if (CpsModule.isCpsEnabled()) {
            int leftCps = CpsModule.getLeftCps();
            int rightCps = CpsModule.getRightCps();
            if (leftCps == 0 && rightCps == 0) {
                leftCps = 12;
                rightCps = 14;
            }
            String cpsText = CpsModule.getFormattedText(leftCps, rightCps);
            int textWidth = this.textRenderer.getWidth(cpsText);
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            CpsModule.width = boxW;
            CpsModule.height = boxH;

            int x = CpsModule.position.calculateX(boxW, this.width);
            int y = CpsModule.position.calculateY(boxH, this.height);

            boolean hovered = mouseX >= x - 2 && mouseX <= x - 2 + boxW && mouseY >= y - 2 && mouseY <= y - 2 + boxH;
            boolean isDragging = "CPS".equals(draggingWidget);

            renderWidgetBoundingBox(context, x - 2, y - 2, boxW, boxH, hovered, isDragging);
        }

        // 6. Draggable Scoreboard Widget Preview
        if (ScoreboardModule.isScoreboardEnabled()) {
            net.minecraft.scoreboard.ScoreboardObjective obj = null;
            if (this.client != null && this.client.world != null && this.client.world.getScoreboard() != null) {
                obj = this.client.world.getScoreboard()
                        .getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
            }

            int totalWidth;
            int totalHeight;
            int lineHeight = 9;

            if (obj != null) {
                net.minecraft.scoreboard.Scoreboard scoreboard = obj.getScoreboard();
                net.minecraft.scoreboard.number.NumberFormat numberFormat = obj
                        .getNumberFormatOr(net.minecraft.scoreboard.number.StyledNumberFormat.RED);

                java.util.Collection<net.minecraft.scoreboard.ScoreboardEntry> rawEntries = scoreboard
                        .getScoreboardEntries(obj);
                java.util.List<net.minecraft.scoreboard.ScoreboardEntry> filtered = rawEntries.stream()
                        .filter(e -> !e.hidden())
                        .sorted(java.util.Comparator.comparing(net.minecraft.scoreboard.ScoreboardEntry::value)
                                .reversed().thenComparing(net.minecraft.scoreboard.ScoreboardEntry::owner,
                                        String.CASE_INSENSITIVE_ORDER))
                        .limit(15)
                        .toList();

                net.minecraft.text.Text titleText = obj.getDisplayName();
                int titleWidth = this.textRenderer.getWidth(titleText);
                int maxEntryWidth = titleWidth;
                int colonWidth = this.textRenderer.getWidth(": ");
                boolean showScores = ScoreboardModule.isShowScores();

                for (net.minecraft.scoreboard.ScoreboardEntry entry : filtered) {
                    net.minecraft.scoreboard.Team team = scoreboard.getScoreHolderTeam(entry.owner());
                    net.minecraft.text.Text nameText = net.minecraft.scoreboard.Team.decorateName(team, entry.name());
                    int nameWidth = this.textRenderer.getWidth(nameText);
                    int rowW = nameWidth;
                    if (showScores) {
                        net.minecraft.text.Text scoreText = entry.formatted(numberFormat);
                        int scoreWidth = this.textRenderer.getWidth(scoreText);
                        if (scoreWidth > 0) {
                            rowW += colonWidth + scoreWidth;
                        }
                    }
                    maxEntryWidth = Math.max(maxEntryWidth, rowW);
                }

                totalWidth = maxEntryWidth;
                totalHeight = (filtered.size() + 1) * lineHeight;
            } else {
                totalWidth = 110;
                totalHeight = 7 * lineHeight;
            }

            int boxW = Math.round((totalWidth + 4) * hudScale);
            int boxH = Math.round((totalHeight + 3) * hudScale);
            ScoreboardModule.width = boxW;
            ScoreboardModule.height = boxH;

            int x = ScoreboardModule.position.calculateX(boxW, this.width);
            int y = ScoreboardModule.position.calculateY(boxH, this.height);

            if (obj == null) {
                renderScoreboardPreview(context, x, y, totalWidth, lineHeight);
            }

            boolean hovered = mouseX >= x - 2 && mouseX <= x - 2 + boxW && mouseY >= y - 2 && mouseY <= y - 2 + boxH;
            boolean isDragging = "SCOREBOARD".equals(draggingWidget);

            renderWidgetBoundingBox(context, x - 2, y - 2, boxW, boxH, hovered, isDragging);
        }

        // 7. Render Active Alignment Guidelines (Smart Magnetic Snapping)
        if (draggingWidget != null && activeGuideLines != null
                && com.mooclient.util.MooClientSettings.isHudSnapping()) {
            for (com.mooclient.util.MooHudPositionHelper.GuideLine line : activeGuideLines) {
                if (line.x1 == line.x2) {
                    int top = Math.min(line.y1, line.y2);
                    int bot = Math.max(line.y1, line.y2);
                    context.fill(line.x1, top, line.x1 + 1, bot, line.color);
                } else if (line.y1 == line.y2) {
                    int left = Math.min(line.x1, line.x2);
                    int right = Math.max(line.x1, line.x2);
                    context.fill(left, line.y1, right, line.y1 + 1, line.color);
                }
            }
        }
    }

    private List<WidgetRect> getOtherActiveWidgetRects(String currentWidgetId) {
        List<WidgetRect> list = new ArrayList<>();
        if (!"FPS".equals(currentWidgetId) && FpsModule.isFpsEnabled()) {
            list.add(new WidgetRect("FPS",
                    FpsModule.position.calculateX(FpsModule.width, this.width),
                    FpsModule.position.calculateY(FpsModule.height, this.height),
                    FpsModule.width, FpsModule.height));
        }
        if (!"SPRINT".equals(currentWidgetId) && ToggleSprintModule.isSprintEnabled()) {
            list.add(new WidgetRect("SPRINT",
                    ToggleSprintModule.position.calculateX(ToggleSprintModule.width, this.width),
                    ToggleSprintModule.position.calculateY(ToggleSprintModule.height, this.height),
                    ToggleSprintModule.width, ToggleSprintModule.height));
        }
        if (!"POTIONS".equals(currentWidgetId) && PotionEffectsModule.isModuleEnabled()) {
            list.add(new WidgetRect("POTIONS",
                    PotionEffectsModule.position.calculateX(PotionEffectsModule.width, this.width),
                    PotionEffectsModule.position.calculateY(PotionEffectsModule.height, this.height),
                    PotionEffectsModule.width, PotionEffectsModule.height));
        }
        if (!"PING".equals(currentWidgetId) && PingModule.isPingEnabled()) {
            list.add(new WidgetRect("PING",
                    PingModule.position.calculateX(PingModule.width, this.width),
                    PingModule.position.calculateY(PingModule.height, this.height),
                    PingModule.width, PingModule.height));
        }
        if (!"CPS".equals(currentWidgetId) && CpsModule.isCpsEnabled()) {
            list.add(new WidgetRect("CPS",
                    CpsModule.position.calculateX(CpsModule.width, this.width),
                    CpsModule.position.calculateY(CpsModule.height, this.height),
                    CpsModule.width, CpsModule.height));
        }
        if (!"SCOREBOARD".equals(currentWidgetId) && ScoreboardModule.isScoreboardEnabled()) {
            list.add(new WidgetRect("SCOREBOARD",
                    ScoreboardModule.position.calculateX(ScoreboardModule.width, this.width),
                    ScoreboardModule.position.calculateY(ScoreboardModule.height, this.height),
                    ScoreboardModule.width, ScoreboardModule.height));
        }
        return list;
    }

    private void renderWidgetBoundingBox(DrawContext context, int x, int y, int w, int h, boolean hovered,
            boolean isDragging) {
        int accent = com.mooclient.util.MooClientSettings.getAccentColor();
        int accentHover = com.mooclient.util.MooClientSettings.getAccentHoverColor();

        int bg;
        int border;

        if (isDragging) {
            bg = com.mooclient.util.MooClientSettings.getAccentGlowColor(0x45);
            border = accentHover;
        } else if (hovered) {
            bg = com.mooclient.util.MooClientSettings.getAccentGlowColor(0x28);
            border = accent;
        } else {
            // Clean lines always visible when Right Shift HUD editor is open
            bg = 0x15FFFFFF;
            border = 0x88FFFFFF;
        }

        context.fill(x, y, x + w, y + h, bg);
        drawBorder(context, x, y, w, h, border);
    }

    private void renderScoreboardPreview(DrawContext context, int x, int y, int totalWidth, int lineHeight) {
        if (this.client != null && this.client.world != null && this.client.world.getScoreboard() != null) {
            net.minecraft.scoreboard.ScoreboardObjective obj = this.client.world.getScoreboard()
                    .getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
            if (obj != null) {
                return;
            }
        }

        float hudScale = com.mooclient.util.MooClientSettings.getHudScaleFactor();
        boolean customScale = (hudScale != 1.0f);

        if (customScale) {
            context.getMatrices().push();
            context.getMatrices().translate(x, y, 0);
            context.getMatrices().scale(hudScale, hudScale, 1.0f);
            context.getMatrices().translate(-x, -y, 0);
        }

        boolean showBg = com.mooclient.module.modules.ScoreboardModule.isShowBackground();
        boolean shadow = com.mooclient.module.modules.ScoreboardModule.isTextShadow();
        boolean showScores = com.mooclient.module.modules.ScoreboardModule.isShowScores();

        String title = "§e§lMOO CLIENT";
        int titleW = this.textRenderer.getWidth(title);
        String[] dummyLines = new String[] {
                "§720/08/26  m144",
                "§fOnline: §a1,337",
                "§fKills: §a42",
                "§fDeaths: §c3",
                "§fPing: §a12ms",
                "§ewww.mooclient.com"
        };
        int[] dummyScores = new int[] { 6, 5, 4, 3, 2, 1 };

        int totalH = (dummyLines.length + 1) * lineHeight;

        if (showBg) {
            int titleBg = 0x66000000;
            int bodyBg = 0x44000000;
            context.fill(x - 2, y - 2, x + totalWidth + 2, y + lineHeight - 1, titleBg);
            context.fill(x - 2, y + lineHeight - 1, x + totalWidth + 2, y + totalH + 1, bodyBg);
        }

        context.drawText(this.textRenderer, title, x + (totalWidth - titleW) / 2, y, 0xFFFFFFFF, shadow);

        for (int i = 0; i < dummyLines.length; i++) {
            int rowY = y + (i + 1) * lineHeight;
            context.drawText(this.textRenderer, dummyLines[i], x, rowY, 0xFFFFFFFF, shadow);
            if (showScores) {
                String sc = String.valueOf(dummyScores[i]);
                int scW = this.textRenderer.getWidth(sc);
                context.drawText(this.textRenderer, sc, x + totalWidth - scW, rowY, 0xFFFF5555, shadow);
            }
        }

        if (customScale) {
            context.getMatrices().pop();
        }
    }

    /**
     * Top-right PL / EN Language Switcher
     */
    private void renderLanguageSwitcher(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int pillW = 26;
        int pillH = 18;
        int gap = 2;

        int plX = x;
        int enX = x + pillW + gap;

        boolean isPl = MooLanguage.current == MooLanguage.PL;
        boolean plHover = mouseX >= plX && mouseX <= plX + pillW && mouseY >= y && mouseY <= y + pillH;
        boolean enHover = mouseX >= enX && mouseX <= enX + pillW && mouseY >= y && mouseY <= y + pillH;

        context.fill(x - 2, y - 2, x + (pillW * 2) + gap + 2, y + pillH + 2, 0x990E0E14);
        drawBorder(context, x - 2, y - 2, (pillW * 2) + gap + 4, pillH + 4, 0x33FFFFFF);

        int plBg = isPl ? 0x44FFFFFF : (plHover ? 0x22FFFFFF : 0x00000000);
        context.fill(plX, y, plX + pillW, y + pillH, plBg);
        if (isPl)
            drawBorder(context, plX, y, pillW, pillH, 0x66FFFFFF);
        int plTextColor = isPl ? COLOR_TEXT_WHITE : (plHover ? COLOR_TEXT_WHITE : 0x88AAAAAA);
        drawCenteredText(context, "PL", plX + pillW / 2, y + 5, plTextColor);

        int enBg = !isPl ? 0x44FFFFFF : (enHover ? 0x22FFFFFF : 0x00000000);
        context.fill(enX, y, enX + pillW, y + pillH, enBg);
        if (!isPl)
            drawBorder(context, enX, y, pillW, pillH, 0x66FFFFFF);
        int enTextColor = !isPl ? COLOR_TEXT_WHITE : (enHover ? COLOR_TEXT_WHITE : 0x88AAAAAA);
        drawCenteredText(context, "EN", enX + pillW / 2, y + 5, enTextColor);
    }

    /**
     * HUB VIEW: Centered Cow Logo, MOO CLIENT name, and single [ MODS ] button
     */
    private void renderLunarHub(DrawContext context, int mouseX, int mouseY, float delta) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int btnW = 140;
        int btnH = 32;
        int btnX = centerX - btnW / 2;
        int btnY = centerY - btnH / 2;

        int titleY = btnY - 18;
        String title = "MOO CLIENT";
        int titleWidth = this.textRenderer.getWidth(title);

        int logoSize = 64;
        int logoY = titleY - logoSize - 8;
        context.drawTexture(RenderLayer::getGuiTextured, COW_LOGO, centerX - logoSize / 2, logoY, 0.0f, 0.0f, logoSize,
                logoSize, logoSize, logoSize);
        context.drawTextWithShadow(this.textRenderer, title, centerX - titleWidth / 2, titleY, COLOR_TEXT_WHITE);

        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        int bg = hovered ? 0x99202028 : 0x55000000;
        int border = hovered ? 0xEEFFFFFF : 0x55FFFFFF;
        context.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        drawBorder(context, btnX, btnY, btnW, btnH, border);
        drawCenteredText(context, "MODS", centerX, btnY + (btnH - 8) / 2, COLOR_TEXT_WHITE);
    }

    /**
     * MODS WINDOW: 3-column scrollable grid of mod cards with OPTIONS bar and
     * search box
     */
    private void renderModsWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelW = 560;
        int panelH = 265;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL_BG);
        drawBorder(context, panelX, panelY, panelW, panelH, COLOR_PANEL_BORDER);

        int headerH = 56;
        int backX = panelX + 14;
        int backY = panelY + 12;
        int backW = 74;
        int backH = 22;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        int backTextColor = backHover ? COLOR_TEXT_WHITE : 0xFFA0A0AB;
        context.drawTextWithShadow(this.textRenderer, MooLanguage.get("back"), backX, backY + 3, backTextColor);

        // Header Title
        String headerTitle = "MOO CLIENT";
        int titleW = this.textRenderer.getWidth(headerTitle);
        context.drawTextWithShadow(this.textRenderer, headerTitle, panelX + (panelW - titleW) / 2, panelY + 10,
                COLOR_TEXT_WHITE);

        // Settings Button on right side of header
        int setBtnW = 96;
        int setBtnH = 20;
        int setBtnX = panelX + panelW - setBtnW - 14;
        int setBtnY = panelY + 12;
        boolean setBtnHover = mouseX >= setBtnX && mouseX <= setBtnX + setBtnW && mouseY >= setBtnY
                && mouseY <= setBtnY + setBtnH;
        int setBg = setBtnHover ? com.mooclient.util.MooClientSettings.getAccentGlowColor(0x35) : 0x44141420;
        int setBorder = setBtnHover ? com.mooclient.util.MooClientSettings.getAccentColor() : 0x33FFFFFF;
        context.fill(setBtnX, setBtnY, setBtnX + setBtnW, setBtnY + setBtnH, setBg);
        drawBorder(context, setBtnX, setBtnY, setBtnW, setBtnH, setBorder);
        String setLabel = "⚙ " + MooLanguage.get("settings");
        int setLabelW = this.textRenderer.getWidth(setLabel);
        context.drawTextWithShadow(this.textRenderer, setLabel, setBtnX + (setBtnW - setLabelW) / 2, setBtnY + 6,
                setBtnHover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

        // Search Bar under "MOO CLIENT"
        int searchW = 200;
        int searchH = 18;
        int searchX = panelX + (panelW - searchW) / 2;
        int searchY = panelY + 26;
        boolean searchHover = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY
                && mouseY <= searchY + searchH;

        context.fill(searchX, searchY, searchX + searchW, searchY + searchH,
                searching ? 0x99101018 : (searchHover ? 0x88181824 : 0x550A0A10));
        drawBorder(context, searchX, searchY, searchW, searchH,
                searching ? com.mooclient.util.MooClientSettings.getAccentColor()
                        : (searchHover ? 0x88FFFFFF : 0x33FFFFFF));

        context.drawTextWithShadow(this.textRenderer, "🔍", searchX + 5, searchY + 4,
                searching ? com.mooclient.util.MooClientSettings.getAccentColor() : 0xFFA0A0AB);

        if (searchFilter.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                    MooLanguage.current == MooLanguage.PL ? "Szukaj modów..." : "Search mods...", searchX + 20,
                    searchY + 5, 0x66FFFFFF);
        } else {
            String cursor = searching && (System.currentTimeMillis() % 1000 < 500) ? "_" : "";
            context.drawTextWithShadow(this.textRenderer, searchFilter + cursor, searchX + 20, searchY + 5,
                    COLOR_TEXT_WHITE);
            // Clear icon '✕'
            boolean clearHover = mouseX >= searchX + searchW - 16 && mouseX <= searchX + searchW - 2
                    && mouseY >= searchY && mouseY <= searchY + searchH;
            context.drawTextWithShadow(this.textRenderer, "✕", searchX + searchW - 13, searchY + 5,
                    clearHover ? 0xFFFF5555 : 0x88FFFFFF);
        }

        context.fill(panelX + 14, panelY + headerH, panelX + panelW - 14, panelY + headerH + 1, 0x22FFFFFF);

        List<Module> allModules = ModuleManager.getInstance().getModules();
        List<Module> modules;
        if (searchFilter == null || searchFilter.trim().isEmpty()) {
            modules = allModules;
        } else {
            String query = searchFilter.trim().toLowerCase();
            modules = allModules.stream().filter(m -> {
                String name = m.getName().toLowerCase();
                String desc = getModuleDescText(m.getName()).toLowerCase();
                return name.contains(query) || desc.contains(query);
            }).toList();
        }

        int cols = 3;
        int cardW = 160;
        int cardH = 135;
        int cardGap = 16;

        int totalGridW = cols * cardW + (cols - 1) * cardGap;
        int startX = panelX + (panelW - totalGridW) / 2;
        int startY = panelY + headerH + 14;

        int totalRows = Math.max(1, (modules.size() + cols - 1) / cols);
        int totalContentH = totalRows * cardH + (totalRows - 1) * cardGap;
        int visibleAreaH = panelH - headerH - 20;
        int maxScroll = Math.max(0, totalContentH - visibleAreaH + 8);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        // Scissor clipping for scrollable area
        context.enableScissor(panelX + 4, panelY + headerH + 2, panelX + panelW - 4, panelY + panelH - 4);

        if (modules.isEmpty()) {
            String noResults = (MooLanguage.current == MooLanguage.PL ? "Brak wyników dla: " : "No mods found for: ")
                    + "\"" + searchFilter + "\"";
            drawCenteredText(context, noResults, panelX + panelW / 2, startY + 40, 0x88FFFFFF);
        }

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int col = i % cols;
            int row = i / cols;
            int cardX = startX + col * (cardW + cardGap);
            int cardY = startY + row * (cardH + cardGap) - (int) scrollY;

            boolean cardHover = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH
                    && mouseY >= panelY + headerH + 2 && mouseY <= panelY + panelH - 4;

            int bg = cardHover ? COLOR_CARD_HOVER : COLOR_CARD_BG;
            int border = cardHover ? COLOR_CARD_BORDER_HOVER : COLOR_CARD_BORDER;
            context.fill(cardX, cardY, cardX + cardW, cardY + cardH, bg);
            drawBorder(context, cardX, cardY, cardW, cardH, border);

            // Icon
            String icon;
            if (module.getName().equalsIgnoreCase("Gamma")) {
                icon = "☀";
            } else if (module.getName().equalsIgnoreCase("FPS")) {
                icon = "⚡";
            } else if (module.getName().equalsIgnoreCase("Sprint")) {
                icon = "🏃";
            } else if (module.getName().equalsIgnoreCase("Freelook")) {
                icon = "👁";
            } else if (module.getName().equalsIgnoreCase("Potion Effects")) {
                icon = "🧪";
            } else if (module.getName().equalsIgnoreCase("Nametags")) {
                icon = "🏷";
            } else if (module.getName().equalsIgnoreCase("Zoom")) {
                icon = "🔍";
            } else if (module.getName().equalsIgnoreCase("Chat")) {
                icon = "💬";
            } else if (module.getName().equalsIgnoreCase("Ping")) {
                icon = "📡";
            } else if (module.getName().equalsIgnoreCase("Waypoints")) {
                icon = "📍";
            } else if (module.getName().equalsIgnoreCase("Scoreboard")) {
                icon = "📋";
            } else if (module.getName().equalsIgnoreCase("CPS")) {
                icon = "🖱";
            } else if (module.getName().equalsIgnoreCase("Emotki") || module.getName().equalsIgnoreCase("Emotes")) {
                icon = "🙋";
            } else {
                icon = "⌨";
            }
            drawCenteredText(context, icon, cardX + cardW / 2, cardY + 20, COLOR_TEXT_WHITE);
            String cardTitle = (module.getName().equalsIgnoreCase("Emotki") || module.getName().equalsIgnoreCase("Emotes"))
                    ? MooLanguage.get("emotes_name") : module.getName();
            drawCenteredText(context, cardTitle, cardX + cardW / 2, cardY + 44, COLOR_TEXT_WHITE);

            // OPTIONS Bar
            int optH = 20;
            int optY = cardY + cardH - 52;
            int optX = cardX + 8;
            int optW = cardW - 16;
            boolean optHover = mouseX >= optX && mouseX <= optX + optW && mouseY >= optY && mouseY <= optY + optH
                    && mouseY >= panelY + headerH + 2 && mouseY <= panelY + panelH - 4;
            context.fill(optX, optY, optX + optW, optY + optH, optHover ? COLOR_OPTIONS_HOVER : COLOR_OPTIONS_BG);
            drawBorder(context, optX, optY, optW, optH, optHover ? 0x88FFFFFF : 0x22FFFFFF);
            context.drawTextWithShadow(this.textRenderer, MooLanguage.get("options"), optX + 6, optY + 6, COLOR_TEXT_WHITE);
            context.drawTextWithShadow(this.textRenderer, "⚙", optX + optW - 14, optY + 6, COLOR_TEXT_WHITE);

            // ENABLED / DISABLED Button
            int btnH = 22;
            int btnY = cardY + cardH - 28;
            int btnX = cardX + 8;
            int btnW = cardW - 16;

            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH
                    && mouseY >= panelY + headerH + 2 && mouseY <= panelY + panelH - 4;
            int accentCol = com.mooclient.util.MooClientSettings.getAccentColor();
            int accentHover = com.mooclient.util.MooClientSettings.getAccentHoverColor();
            int statusBg = module.isEnabled() ? (btnHover ? accentHover : accentCol)
                    : (btnHover ? COLOR_DISABLED_HOVER : COLOR_DISABLED);

            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, statusBg);
            drawBorder(context, btnX, btnY, btnW, btnH, 0x44FFFFFF);

            String statusText = module.isEnabled() ? MooLanguage.get("enabled") : MooLanguage.get("disabled");
            int statusTextColor = module.isEnabled() ? 0xFF082212 : 0xFFA0A0AB;
            drawCenteredText(context, statusText, cardX + cardW / 2, btnY + 7, statusTextColor);
        }

        context.disableScissor();

        // Draw vertical scrollbar if needed
        if (maxScroll > 0) {
            int scrollTrackX = panelX + panelW - 8;
            int scrollTrackY = panelY + headerH + 10;
            int scrollTrackH = visibleAreaH;
            int thumbH = Math.max(22, (int) ((float) visibleAreaH / (visibleAreaH + maxScroll) * scrollTrackH));
            int thumbY = scrollTrackY + (int) ((scrollY / (float) maxScroll) * (scrollTrackH - thumbH));
            context.fill(scrollTrackX, scrollTrackY, scrollTrackX + 3, scrollTrackY + scrollTrackH, 0x33000000);
            context.fill(scrollTrackX, thumbY, scrollTrackX + 3, thumbY + thumbH, 0x88FFFFFF);
        }
    }

    private String getModuleDescText(String name) {
        if (name.equalsIgnoreCase("Gamma"))
            return MooLanguage.get("gamma_desc");
        if (name.equalsIgnoreCase("FPS"))
            return MooLanguage.get("fps_desc");
        if (name.equalsIgnoreCase("Sprint"))
            return MooLanguage.get("sprint_desc");
        if (name.equalsIgnoreCase("Freelook"))
            return MooLanguage.get("freelook_desc");
        if (name.equalsIgnoreCase("Potion Effects"))
            return MooLanguage.get("potions_desc");
        if (name.equalsIgnoreCase("Nametags"))
            return MooLanguage.get("nametags_desc");
        if (name.equalsIgnoreCase("Zoom"))
            return MooLanguage.get("zoom_desc");
        if (name.equalsIgnoreCase("Chat"))
            return MooLanguage.get("chat_desc");
        if (name.equalsIgnoreCase("Ping"))
            return MooLanguage.get("ping_desc");
        if (name.equalsIgnoreCase("CPS"))
            return MooLanguage.get("cps_desc");
        if (name.equalsIgnoreCase("Waypoints"))
            return MooLanguage.get("waypoints_desc");
        if (name.equalsIgnoreCase("Scoreboard"))
            return MooLanguage.get("scoreboard_desc");
        if (name.equalsIgnoreCase("Emotki") || name.equalsIgnoreCase("Emotes"))
            return MooLanguage.get("emotes_desc");
        return MooLanguage.get("macro_desc");
    }

    /**
     * MOD OPTIONS SCREEN: Lunar Client styled options window
     */
    private void renderOptionsWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelW = 480;
        int panelH = 330;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL_BG);
        drawBorder(context, panelX, panelY, panelW, panelH, COLOR_PANEL_BORDER);

        int headerH = 46;
        int backX = panelX + 14;
        int backY = panelY + 12;
        int backW = 74;
        int backH = 22;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        int backTextColor = backHover ? COLOR_TEXT_WHITE : 0xFFA0A0AB;
        context.drawTextWithShadow(this.textRenderer, MooLanguage.get("back"), backX, backY + 3, backTextColor);

        String modName = selectedModule != null ? selectedModule.getName() : "FPS";
        String optTitle;
        String optSubtitle;
        if (modName.equalsIgnoreCase("FPS")) {
            optTitle = MooLanguage.get("fps_opt_title");
            optSubtitle = MooLanguage.get("fps_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Ping")) {
            optTitle = MooLanguage.get("ping_opt_title");
            optSubtitle = MooLanguage.get("ping_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Sprint")) {
            optTitle = MooLanguage.get("sprint_opt_title");
            optSubtitle = MooLanguage.get("sprint_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Freelook")) {
            optTitle = MooLanguage.get("freelook_opt_title");
            optSubtitle = MooLanguage.get("freelook_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Potion Effects")) {
            optTitle = MooLanguage.get("potions_opt_title");
            optSubtitle = MooLanguage.get("potions_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Nametags")) {
            optTitle = MooLanguage.get("nametags_opt_title");
            optSubtitle = MooLanguage.get("nametags_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Zoom")) {
            optTitle = MooLanguage.get("zoom_opt_title");
            optSubtitle = MooLanguage.get("zoom_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Chat")) {
            optTitle = MooLanguage.get("chat_opt_title");
            optSubtitle = MooLanguage.get("chat_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Macro")) {
            optTitle = MooLanguage.get("macro_opt_title");
            optSubtitle = MooLanguage.get("macro_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Waypoints")) {
            optTitle = MooLanguage.get("waypoints_opt_title");
            optSubtitle = MooLanguage.get("waypoints_opt_subtitle");
        } else if (modName.equalsIgnoreCase("CPS")) {
            optTitle = MooLanguage.get("cps_opt_title");
            optSubtitle = MooLanguage.get("cps_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Scoreboard")) {
            optTitle = MooLanguage.get("scoreboard_opt_title");
            optSubtitle = MooLanguage.get("scoreboard_opt_subtitle");
        } else if (modName.equalsIgnoreCase("Emotki") || modName.equalsIgnoreCase("Emotes")) {
            optTitle = MooLanguage.get("emotes_opt_title");
            optSubtitle = MooLanguage.get("emotes_opt_subtitle");
        } else {
            optTitle = MooLanguage.get("gamma_opt_title");
            optSubtitle = MooLanguage.get("gamma_opt_subtitle");
        }

        context.drawTextWithShadow(this.textRenderer, optTitle, panelX + 100, panelY + 14, COLOR_TEXT_WHITE);
        context.drawTextWithShadow(this.textRenderer, optSubtitle, panelX + 100, panelY + 28, COLOR_TEXT_MUTED);

        context.fill(panelX + 14, panelY + headerH, panelX + panelW - 14, panelY + headerH + 1, 0x22FFFFFF);

        int rowY = panelY + headerH + 14;
        int rowH = 34;
        int rowW = panelW - 32;
        int rowX = panelX + 16;

        if (modName.equalsIgnoreCase("FPS")) {
            // Row 1: Appearance Style Tabs
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("style_label"));
            renderStyleSelector(context, rowX + rowW - 206, rowY + 6, mouseX, mouseY, FpsModule.getStyle().ordinal());

            // Row 2: Show Background
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("bg_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY, FpsModule.isShowBackground());

            // Row 3: Text Shadow
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("shadow_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY, FpsModule.isTextShadow());

            // Row 4: Show Prefix 'FPS:'
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("fps_prefix_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY, FpsModule.isShowPrefix());

        } else if (modName.equalsIgnoreCase("Ping")) {
            // Row 1: Appearance Style Tabs
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("style_label"));
            renderStyleSelector(context, rowX + rowW - 206, rowY + 6, mouseX, mouseY,
                    com.mooclient.module.modules.PingModule.getStyle().ordinal());

            // Row 2: Show Background
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("bg_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.PingModule.isShowBackground());

            // Row 3: Text Shadow
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("shadow_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.PingModule.isTextShadow());

            // Row 4: Show Prefix 'Ping:'
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("ping_prefix_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.PingModule.isShowPrefix());

        } else if (modName.equalsIgnoreCase("CPS")) {
            // Row 1: Display Mode Selector (LPM | PPM / Tylko LPM / Tylko PPM)
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("cps_buttons_label"));
            renderCpsDisplayModeSelector(context, rowX + rowW - 230, rowY + 6, mouseX, mouseY,
                    com.mooclient.module.modules.CpsModule.getDisplayMode().ordinal());

            // Row 2: Appearance Style Tabs
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("style_label"));
            renderStyleSelector(context, rowX + rowW - 206, rowY + 6, mouseX, mouseY,
                    com.mooclient.module.modules.CpsModule.getStyle().ordinal());

            // Row 3: Show Background
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("bg_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.CpsModule.isShowBackground());

            // Row 4: Text Shadow
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("shadow_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.CpsModule.isTextShadow());

            // Row 5: Show Prefix 'CPS:'
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("cps_prefix_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.CpsModule.isShowPrefix());

        } else if (modName.equalsIgnoreCase("Sprint")) {
            // Row 1: Interactive Keybind Selector (Click to change keybind!)
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("keybind_label"));
            String keyText = listeningForKeybind ? MooLanguage.get("press_key_hint")
                    : "[ " + ToggleSprintModule.getKeyName() + " ]";
            int btnW = 140;
            int btnH = 22;
            int btnX = rowX + rowW - btnW - 10;
            int btnY = rowY + 6;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            int btnBg = listeningForKeybind ? 0xEE334466 : (btnHover ? 0xCC252535 : 0x88181824);
            int btnBorder = listeningForKeybind ? 0xFF55FFFF : (btnHover ? 0xAAFFFFFF : 0x44FFFFFF);
            int textColor = listeningForKeybind ? 0xFFFFFF55 : 0xFF55FFFF;

            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
            drawBorder(context, btnX, btnY, btnW, btnH, btnBorder);
            drawCenteredText(context, keyText, btnX + btnW / 2, btnY + 7, textColor);

            // Row 2: Sprint Style Tabs
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("style_label"));
            renderStyleSelector(context, rowX + rowW - 206, rowY + 6, mouseX, mouseY,
                    ToggleSprintModule.getStyle().ordinal());

            // Row 3: Show Background
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("bg_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    ToggleSprintModule.isShowBackground());

            // Row 4: Text Shadow
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("shadow_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY, ToggleSprintModule.isTextShadow());

        } else if (modName.equalsIgnoreCase("Freelook")) {
            // Row 1: Keybind
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("keybind_label"));
            String keyText = listeningForKeybind ? MooLanguage.get("press_key_hint") : "[ " + FreelookModule.getKeyName() + " ]";
            int btnW = 140;
            int btnH = 22;
            int btnX = rowX + rowW - btnW - 10;
            int btnY = rowY + 6;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            int btnBg = listeningForKeybind ? 0xEE334466 : (btnHover ? 0xCC252535 : 0x88181824);
            int btnBorder = listeningForKeybind ? 0xFF55FFFF : (btnHover ? 0xAAFFFFFF : 0x44FFFFFF);
            int textColor = listeningForKeybind ? 0xFFFFFF55 : 0xFF55FFFF;

            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
            drawBorder(context, btnX, btnY, btnW, btnH, btnBorder);
            drawCenteredText(context, keyText, btnX + btnW / 2, btnY + 7, textColor);

            // Row 2: Activation Mode (Hold vs Toggle)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("mode_label"));
            renderModeSelector(context, rowX + rowW - 206, rowY + 6, mouseX, mouseY,
                    FreelookModule.getMode().ordinal());

            // Row 3: Invert Pitch
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("invert_pitch_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY, FreelookModule.isInvertPitch());

        } else if (modName.equalsIgnoreCase("Potion Effects")) {
            // Row 1: Style Selector (Moo Client / Simple / Compact)
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("style_label"));
            renderPotionStyleSelector(context, rowX + rowW - 248, rowY + 6, mouseX, mouseY,
                    PotionEffectsModule.getStyle().ordinal());

            // Row 2: Show Background
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("bg_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    PotionEffectsModule.isShowBackground());

            // Row 3: Text Shadow
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("shadow_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY, PotionEffectsModule.isTextShadow());

        } else if (modName.equalsIgnoreCase("Nametags")) {
            // Row 1: Show Ping
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("show_ping_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.NametagsModule.isShowPing());

            // Row 2: Show Self Ping
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("show_self_ping_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.NametagsModule.isShowSelfPing());

            // Row 3: Ping Position (Beside vs Above)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("ping_pos_label"));
            renderPingPositionSelector(context, rowX + rowW - 206, rowY + 6, mouseX, mouseY,
                    com.mooclient.module.modules.NametagsModule.getPingPosition().ordinal());

            // Row 4: Remove Background
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("remove_bg_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.NametagsModule.isRemoveBackground());

            // Row 5: Text Shadow
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("shadow_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.NametagsModule.isTextShadow());

        } else if (modName.equalsIgnoreCase("Zoom")) {
            // Row 1: Keybind
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("keybind_label"));
            String btnText = (this.listeningForKeybind && selectedModule.getName().equalsIgnoreCase("Zoom"))
                    ? MooLanguage.get("press_key_hint")
                    : "[" + com.mooclient.module.modules.ZoomModule.getKeyName() + "]";
            int btnW = 140;
            int btnH = 22;
            int btnX = rowX + rowW - btnW - 10;
            int btnY = rowY + 6;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            context.fill(btnX, btnY, btnX + btnW, btnY + btnH,
                    this.listeningForKeybind ? 0xDD22C55E : (btnHover ? 0xCC252535 : 0x66141420));
            drawBorder(context, btnX, btnY, btnW, btnH,
                    this.listeningForKeybind ? 0xFF4ADE80 : (btnHover ? 0xAAFFFFFF : 0x33FFFFFF));
            drawCenteredText(context, btnText, btnX + btnW / 2, btnY + 7,
                    this.listeningForKeybind ? 0xFF0A2514 : (btnHover ? COLOR_TEXT_WHITE : 0xFFA0A0AB));

            // Row 2: Zoom Factor (2x, 3x, 4x, 5x, 6x)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("factor_label"));
            renderFactorSelector(context, rowX + rowW - 248, rowY + 6, mouseX, mouseY,
                    com.mooclient.module.modules.ZoomModule.getFactor().ordinal());

            // Row 3: Activation Mode (Hold vs Toggle)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("mode_label"));
            renderModeSelector(context, rowX + rowW - 206, rowY + 6, mouseX, mouseY,
                    com.mooclient.module.modules.ZoomModule.getMode().ordinal());

            // Row 4: Smooth Zoom
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("smooth_zoom_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.ZoomModule.isSmoothZoom());

        } else if (modName.equalsIgnoreCase("Chat")) {
            // Row 1: Transparent Background
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("chat_transparent_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.ChatModule.isTransparentBackground());

            // Row 2: Unlimited Chat
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("chat_unlimited_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.ChatModule.isUnlimitedChat());

            // Row 3: Smooth Chat Animation
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("chat_smooth_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.ChatModule.isSmoothChat());

            // Row 4: Text Shadow
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("shadow_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.ChatModule.isTextShadow());

        } else if (modName.equalsIgnoreCase("Macro")) {
            java.util.List<com.mooclient.module.modules.MacroModule.MacroEntry> macroList = com.mooclient.module.modules.MacroModule
                    .getMacros();
            int mRowH = 28;
            int curY = panelY + headerH + 10;

            for (int i = 0; i < Math.min(5, macroList.size()); i++) {
                com.mooclient.module.modules.MacroModule.MacroEntry m = macroList.get(i);
                context.fill(rowX, curY, rowX + rowW, curY + mRowH, 0x5515151E);
                drawBorder(context, rowX, curY, rowW, mRowH, 0x22FFFFFF);

                // Slot title
                String slotName = "Slot " + (i + 1);
                context.drawTextWithShadow(this.textRenderer, slotName, rowX + 8, curY + (mRowH - 8) / 2,
                        m.isEnabled() ? COLOR_TEXT_WHITE : COLOR_TEXT_MUTED);

                // Command Box (Editable)
                int cmdBoxX = rowX + 54;
                int cmdBoxW = rowW - 54 - 110 - 44;
                int cmdBoxY = curY + 4;
                int cmdBoxH = mRowH - 8;
                boolean isEditingCmd = (this.editingMacroIndex == i);
                boolean cmdHover = mouseX >= cmdBoxX && mouseX <= cmdBoxX + cmdBoxW && mouseY >= cmdBoxY
                        && mouseY <= cmdBoxY + cmdBoxH;

                context.fill(cmdBoxX, cmdBoxY, cmdBoxX + cmdBoxW, cmdBoxY + cmdBoxH,
                        isEditingCmd ? 0xEE1E293B : (cmdHover ? 0xCC252535 : 0x88181824));
                drawBorder(context, cmdBoxX, cmdBoxY, cmdBoxW, cmdBoxH,
                        isEditingCmd ? 0xFF38BDF8 : (cmdHover ? 0xAAFFFFFF : 0x33FFFFFF));

                String cmdDisplay = m.getCommand().isEmpty() ? "(kliknij by wpisać)" : m.getCommand();
                if (isEditingCmd) {
                    cmdDisplay = "> " + m.getCommand() + (System.currentTimeMillis() % 1000 > 500 ? "_" : "");
                }
                if (this.textRenderer.getWidth(cmdDisplay) > cmdBoxW - 8) {
                    cmdDisplay = this.textRenderer.trimToWidth(cmdDisplay, cmdBoxW - 14) + "..";
                }
                context.drawTextWithShadow(this.textRenderer, cmdDisplay, cmdBoxX + 6, cmdBoxY + (cmdBoxH - 8) / 2,
                        isEditingCmd ? 0xFF38BDF8 : (m.isEnabled() ? 0xFF55FFFF : COLOR_TEXT_MUTED));

                // Keybind Button
                int kBtnX = cmdBoxX + cmdBoxW + 6;
                int kBtnW = 96;
                int kBtnY = curY + 4;
                int kBtnH = mRowH - 8;
                boolean isListeningKey = (this.listeningMacroIndex == i);
                boolean kBtnHover = mouseX >= kBtnX && mouseX <= kBtnX + kBtnW && mouseY >= kBtnY
                        && mouseY <= kBtnY + kBtnH;

                context.fill(kBtnX, kBtnY, kBtnX + kBtnW, kBtnY + kBtnH,
                        isListeningKey ? 0xEE334466 : (kBtnHover ? 0xCC252535 : 0x88181824));
                drawBorder(context, kBtnX, kBtnY, kBtnW, kBtnH,
                        isListeningKey ? 0xFF55FFFF : (kBtnHover ? 0xAAFFFFFF : 0x33FFFFFF));

                String kText = isListeningKey ? "> KLAWISZ <" : "[ " + m.getKeyName() + " ]";
                drawCenteredText(context, kText, kBtnX + kBtnW / 2, kBtnY + (kBtnH - 8) / 2,
                        isListeningKey ? 0xFFFFFF55 : (m.isEnabled() ? 0xFF55FFFF : COLOR_TEXT_MUTED));

                // Enable / Disable toggle
                int tX = rowX + rowW - 40;
                int tY = curY + 5;
                drawOptionToggle(context, tX, tY, mouseX, mouseY, m.isEnabled());

                curY += mRowH + 4;
            }

        } else if (modName.equalsIgnoreCase("Waypoints")) {
            // Row 1: Keybind (Interactive Keybind Selector)
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("keybind_label"));
            String keyText = listeningForKeybind ? MooLanguage.get("press_key_hint")
                    : "[ " + com.mooclient.module.modules.WaypointsModule.getKeyName() + " ]";
            int btnW = 140;
            int btnH = 22;
            int btnX = rowX + rowW - btnW - 10;
            int btnY = rowY + 6;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
            int btnBg = listeningForKeybind ? 0xEE334466 : (btnHover ? 0xCC252535 : 0x88181824);
            int btnBorder = listeningForKeybind ? 0xFF55FFFF : (btnHover ? 0xAAFFFFFF : 0x44FFFFFF);
            int textColor = listeningForKeybind ? 0xFFFFFF55 : 0xFF55FFFF;

            context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
            drawBorder(context, btnX, btnY, btnW, btnH, btnBorder);
            drawCenteredText(context, keyText, btnX + btnW / 2, btnY + 7, textColor);

            // Row 2: Open Waypoints Manager GUI button
            rowY += rowH + 6;
            int openBtnW = 200;
            int openBtnH = 22;
            int openBtnX = rowX + rowW - openBtnW - 10;
            int openBtnY = rowY + 6;
            boolean openHover = mouseX >= openBtnX && mouseX <= openBtnX + openBtnW && mouseY >= openBtnY
                    && mouseY <= openBtnY + openBtnH;
            int openBg = openHover ? com.mooclient.util.MooClientSettings.getAccentHoverColor()
                    : com.mooclient.util.MooClientSettings.getAccentColor();

            drawOptionRow(context, rowX, rowY, rowW, rowH, "Zarządzanie punktami");
            context.fill(openBtnX, openBtnY, openBtnX + openBtnW, openBtnY + openBtnH, openBg);
            drawBorder(context, openBtnX, openBtnY, openBtnW, openBtnH, 0xFFFFFFFF);
            drawCenteredText(context, MooLanguage.get("waypoints_open_gui"), openBtnX + openBtnW / 2, openBtnY + 7,
                    0xFF0A2514);

            // Row 3: Waypoint Scale Slider (0-100%)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("waypoint_scale_label"));
            int wpSliderW = 180;
            int wpSliderX = rowX + rowW - wpSliderW - 8;
            int wpPercent = com.mooclient.module.modules.WaypointsModule.getScalePercent();
            renderPercentageSlider(context, wpSliderX, rowY + 6, wpSliderW, 22, wpPercent, mouseX, mouseY);

            // Row 4: Auto Death Waypoint
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("death_waypoint_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.WaypointsModule.isDeathWaypoint());

            // Row 5: Show Distance
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("show_distance_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.WaypointsModule.isShowDistance());

            // Row 6: Background (Tło)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("bg_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.WaypointsModule.isShowBackground());

            // Row 7: Text Shadow (Cień tekstu)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("shadow_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.WaypointsModule.isTextShadow());

        } else if (modName.equalsIgnoreCase("Scoreboard")) {
            // Row 1: Enable / Disable Toggle
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("enabled"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.ScoreboardModule.isScoreboardEnabled());

            // Row 2: Text Shadow (Cień tekstu)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("shadow_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.ScoreboardModule.isTextShadow());

            // Row 3: Background (Tło)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("bg_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.ScoreboardModule.isShowBackground());

            // Row 4: Show Scores / Numbers (Cyfry po prawej)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("scoreboard_scores_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.ScoreboardModule.isShowScores());

            // Row 5: Reset Position
            rowY += rowH + 6;
            int rBtnW = 160;
            int rBtnH = 22;
            int rBtnX = rowX + rowW - rBtnW - 10;
            int rBtnY = rowY + 6;
            boolean rHover = mouseX >= rBtnX && mouseX <= rBtnX + rBtnW && mouseY >= rBtnY && mouseY <= rBtnY + rBtnH;
            int rBg = rHover ? 0xCC252535 : 0x66141420;
            drawOptionRow(context, rowX, rowY, rowW, rowH, "Pozycja na ekranie");
            context.fill(rBtnX, rBtnY, rBtnX + rBtnW, rBtnY + rBtnH, rBg);
            drawBorder(context, rBtnX, rBtnY, rBtnW, rBtnH, rHover ? 0xAAFFFFFF : 0x33FFFFFF);
            drawCenteredText(context, MooLanguage.get("reset_pos_btn"), rBtnX + rBtnW / 2, rBtnY + 7,
                    rHover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

        } else if (modName.equalsIgnoreCase("Emotki") || modName.equalsIgnoreCase("Emotes")) {
            int btnW = 140;
            int btnH = 22;

            // Row 1: Hands Up Keybind (default R)
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("emotes_hands_up_label"));
            boolean listening0 = this.listeningForKeybind && this.listeningEmoteSlot == 0;
            String keyText0 = listening0 ? MooLanguage.get("press_key_hint")
                    : "[ " + com.mooclient.module.modules.EmotesModule.getKeyName() + " ]";
            int btnX0 = rowX + rowW - btnW - 10;
            int btnY0 = rowY + 6;
            boolean btnHover0 = mouseX >= btnX0 && mouseX <= btnX0 + btnW && mouseY >= btnY0 && mouseY <= btnY0 + btnH;
            context.fill(btnX0, btnY0, btnX0 + btnW, btnY0 + btnH, listening0 ? 0xEE334466 : (btnHover0 ? 0xCC252535 : 0x88181824));
            drawBorder(context, btnX0, btnY0, btnW, btnH, listening0 ? 0xFF55FFFF : (btnHover0 ? 0xAAFFFFFF : 0x44FFFFFF));
            drawCenteredText(context, keyText0, btnX0 + btnW / 2, btnY0 + 7, listening0 ? 0xFFFFFF55 : 0xFF55FFFF);

            // Row 2: Emote Radial Wheel Keybind (default B)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("emotes_wheel_key_label"));
            boolean listening1 = this.listeningForKeybind && this.listeningEmoteSlot == 1;
            String keyText1 = listening1 ? MooLanguage.get("press_key_hint")
                    : "[ " + com.mooclient.module.modules.EmotesModule.getWheelKeyName() + " ]";
            int btnX1 = rowX + rowW - btnW - 10;
            int btnY1 = rowY + 6;
            boolean btnHover1 = mouseX >= btnX1 && mouseX <= btnX1 + btnW && mouseY >= btnY1 && mouseY <= btnY1 + btnH;
            context.fill(btnX1, btnY1, btnX1 + btnW, btnY1 + btnH, listening1 ? 0xEE334466 : (btnHover1 ? 0xCC252535 : 0x88181824));
            drawBorder(context, btnX1, btnY1, btnW, btnH, listening1 ? 0xFF55FFFF : (btnHover1 ? 0xAAFFFFFF : 0x44FFFFFF));
            drawCenteredText(context, keyText1, btnX1 + btnW / 2, btnY1 + 7, listening1 ? 0xFFFFFF55 : 0xFF55FFFF);

            // Row 3: Activation Mode (Hold vs Toggle for Hands Up)
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("mode_label"));
            renderModeSelector(context, rowX + rowW - 206, rowY + 6, mouseX, mouseY,
                    com.mooclient.module.modules.EmotesModule.getMode().ordinal());

            // Row 4: Enable / Disable toggle
            rowY += rowH + 6;
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("enabled"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    com.mooclient.module.modules.EmotesModule.isEmotesEnabled());

        } else {
            // Gamma Options
            drawOptionRow(context, rowX, rowY, rowW, rowH, MooLanguage.get("fullbright_label"));
            drawOptionToggle(context, rowX + rowW - 44, rowY + 8, mouseX, mouseY,
                    selectedModule != null && selectedModule.isEnabled());
        }

        String hint = MooLanguage.get("esc_hint");
        drawCenteredText(context, hint, this.width / 2, this.height - 20, 0x66FFFFFF);
    }

    /**
     * CLIENT SETTINGS SCREEN: Accent color picker (presets + RGB sliders), HUD
     * options & Snapping, GUI theme, Profiles.
     */
    private void renderSettingsWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelW = 560;
        int panelH = 275;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL_BG);
        drawBorder(context, panelX, panelY, panelW, panelH, COLOR_PANEL_BORDER);

        int headerH = 34;
        int backX = panelX + 14;
        int backY = panelY + 8;
        int backW = 74;
        int backH = 20;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        int backTextColor = backHover ? COLOR_TEXT_WHITE : 0xFFA0A0AB;
        context.drawTextWithShadow(this.textRenderer, MooLanguage.get("back"), backX, backY + 2, backTextColor);

        String title = MooLanguage.get("client_settings_title");
        int titleW = this.textRenderer.getWidth(title);
        context.drawTextWithShadow(this.textRenderer, title, panelX + (panelW - titleW) / 2, panelY + 9,
                COLOR_TEXT_WHITE);

        // --- Tabs Bar ---
        int tabY = panelY + headerH;
        int tabH = 22;
        String[] tabs = new String[] {
                MooLanguage.get("tab_accent"),
                MooLanguage.get("tab_hud"),
                MooLanguage.get("tab_gui")
        };
        int tabW = (panelW - 28) / 3;

        for (int i = 0; i < tabs.length; i++) {
            int tX = panelX + 14 + i * tabW;
            boolean active = (settingsTab == i);
            boolean hover = mouseX >= tX && mouseX <= tX + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            int bg = active ? com.mooclient.util.MooClientSettings.getAccentGlowColor(0x35)
                    : (hover ? 0x33252535 : 0x22111118);
            int border = active ? com.mooclient.util.MooClientSettings.getAccentColor() : 0x22FFFFFF;
            int textCol = active ? COLOR_TEXT_WHITE : (hover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

            context.fill(tX, tabY, tX + tabW, tabY + tabH, bg);
            drawBorder(context, tX, tabY, tabW, tabH, border);
            if (active) {
                context.fill(tX + 1, tabY + tabH - 2, tX + tabW - 1, tabY + tabH,
                        com.mooclient.util.MooClientSettings.getAccentColor());
            }

            int labelW = this.textRenderer.getWidth(tabs[i]);
            context.drawTextWithShadow(this.textRenderer, tabs[i], tX + (tabW - labelW) / 2, tabY + 6, textCol);
        }

        context.fill(panelX + 14, tabY + tabH + 4, panelX + panelW - 14, tabY + tabH + 5, 0x22FFFFFF);

        int contentY = tabY + tabH + 10;

        // --- Tab 0: Accent Color ---
        if (settingsTab == 0) {
            renderAccentColorTab(context, panelX, contentY, panelW, mouseX, mouseY);
        }
        // --- Tab 1: HUD Management ---
        else if (settingsTab == 1) {
            renderHudSettingsTab(context, panelX, contentY, panelW, mouseX, mouseY);
        }
        // --- Tab 2: GUI Appearance ---
        else if (settingsTab == 2) {
            renderGuiSettingsTab(context, panelX, contentY, panelW, mouseX, mouseY);
        }

        String hint = MooLanguage.get("esc_hint");
        drawCenteredText(context, hint, this.width / 2, this.height - 14, 0x66FFFFFF);
    }

    private void renderAccentColorTab(DrawContext context, int panelX, int contentY, int panelW, int mouseX,
            int mouseY) {
        context.drawTextWithShadow(this.textRenderer,
                MooLanguage.current == MooLanguage.PL ? "Wybierz gotowy motyw kolorystyczny:"
                        : "Choose accent color preset:",
                panelX + 18, contentY + 2, 0xFFA0A0AB);

        com.mooclient.util.MooClientSettings.AccentColorPreset[] presets = com.mooclient.util.MooClientSettings.AccentColorPreset
                .values();
        com.mooclient.util.MooClientSettings.AccentColorPreset activePreset = com.mooclient.util.MooClientSettings
                .getAccentPreset();

        int cols = 5;
        int pW = 100;
        int pH = 22;
        int gap = 6;
        int startX = panelX + 18;
        int startY = contentY + 16;

        for (int i = 0; i < presets.length; i++) {
            com.mooclient.util.MooClientSettings.AccentColorPreset p = presets[i];
            int col = i % cols;
            int row = i / cols;
            int bx = startX + col * (pW + gap);
            int by = startY + row * (pH + gap);

            boolean selected = (p == activePreset);
            boolean hover = mouseX >= bx && mouseX <= bx + pW && mouseY >= by && mouseY <= by + pH;

            int bg = selected ? 0x55252538 : (hover ? 0x33252535 : 0x22151520);
            int border = selected ? com.mooclient.util.MooClientSettings.getAccentColor()
                    : (hover ? 0x66FFFFFF : 0x22FFFFFF);

            context.fill(bx, by, bx + pW, by + pH, bg);
            drawBorder(context, bx, by, pW, pH, border);

            // Color circle / indicator
            int dotX = bx + 6;
            int dotY = by + 5;
            int dotSize = 12;
            int dotColor;
            if (p == com.mooclient.util.MooClientSettings.AccentColorPreset.CHROMA) {
                dotColor = com.mooclient.util.MooClientSettings.getAccentColor();
            } else if (p == com.mooclient.util.MooClientSettings.AccentColorPreset.CUSTOM) {
                dotColor = 0xFF000000 | (com.mooclient.util.MooClientSettings.getCustomRed() << 16)
                        | (com.mooclient.util.MooClientSettings.getCustomGreen() << 8)
                        | com.mooclient.util.MooClientSettings.getCustomBlue();
            } else {
                dotColor = p.getColor();
            }

            context.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, dotColor);
            drawBorder(context, dotX, dotY, dotSize, dotSize, 0x55FFFFFF);

            String name = p.getDisplayName();
            if (selected)
                name += " ✓";
            context.drawTextWithShadow(this.textRenderer, name, dotX + dotSize + 5, by + 6,
                    selected ? COLOR_TEXT_WHITE : (hover ? COLOR_TEXT_WHITE : 0xFFA0A0AB));
        }

        // Custom RGB Sliders Section & Live Preview
        int customY = startY + 2 * (pH + gap) + 8;
        context.fill(panelX + 18, customY, panelX + panelW - 18, customY + 1, 0x22FFFFFF);

        boolean isCustom = (activePreset == com.mooclient.util.MooClientSettings.AccentColorPreset.CUSTOM);

        if (isCustom) {
            context.drawTextWithShadow(this.textRenderer, MooLanguage.get("custom_rgb_label"), panelX + 18, customY + 6,
                    0xFFA0A0AB);

            int sliderStartX = panelX + 50;
            int sliderW = 210;
            int sliderH = 10;
            int sY = customY + 20;

            // Slider 1: Red
            renderRgbSlider(context, panelX + 18, sliderStartX, sY, sliderW, sliderH, "R:",
                    com.mooclient.util.MooClientSettings.getCustomRed(), 0xFFFF4444, mouseX, mouseY);
            // Slider 2: Green
            renderRgbSlider(context, panelX + 18, sliderStartX, sY + 20, sliderW, sliderH, "G:",
                    com.mooclient.util.MooClientSettings.getCustomGreen(), 0xFF44FF44, mouseX, mouseY);
            // Slider 3: Blue
            renderRgbSlider(context, panelX + 18, sliderStartX, sY + 40, sliderW, sliderH, "B:",
                    com.mooclient.util.MooClientSettings.getCustomBlue(), 0xFF4488FF, mouseX, mouseY);

            // Preview Box on the right
            int prevX = panelX + 290;
            int prevY = customY + 16;
            int prevW = panelW - 308;
            int prevH = 68;

            context.fill(prevX, prevY, prevX + prevW, prevY + prevH, 0x44101018);
            drawBorder(context, prevX, prevY, prevW, prevH, com.mooclient.util.MooClientSettings.getAccentColor());

            drawCenteredText(context,
                    MooLanguage.current == MooLanguage.PL ? "Podgląd akcentu UI:" : "Live UI Preview:",
                    prevX + prevW / 2, prevY + 8, 0xFFA0A0AB);

            // Live mock ENABLED button
            int mBtnW = 100;
            int mBtnH = 20;
            int mBtnX = prevX + (prevW - mBtnW) / 2;
            int mBtnY = prevY + 24;
            context.fill(mBtnX, mBtnY, mBtnX + mBtnW, mBtnY + mBtnH,
                    com.mooclient.util.MooClientSettings.getAccentColor());
            drawBorder(context, mBtnX, mBtnY, mBtnW, mBtnH, 0x55FFFFFF);
            drawCenteredText(context, MooLanguage.get("enabled"), mBtnX + mBtnW / 2, mBtnY + 6, 0xFF082212);

            // Hex / RGB Code display
            int hexCol = com.mooclient.util.MooClientSettings.getAccentColor();
            String codeText = (activePreset == com.mooclient.util.MooClientSettings.AccentColorPreset.CHROMA) ? "RGB"
                    : ("HEX: " + String.format("#%06X", (0xFFFFFF & hexCol)));
            drawCenteredText(context, codeText, prevX + prevW / 2, prevY + 50, 0x88FFFFFF);
        } else {
            // Centered Live Preview Box when a standard preset is selected
            int prevW = 320;
            int prevH = 68;
            int prevX = panelX + (panelW - prevW) / 2;
            int prevY = customY + 14;

            context.fill(prevX, prevY, prevX + prevW, prevY + prevH, 0x44101018);
            drawBorder(context, prevX, prevY, prevW, prevH, com.mooclient.util.MooClientSettings.getAccentColor());

            drawCenteredText(context,
                    MooLanguage.current == MooLanguage.PL ? "Podgląd akcentu UI:" : "Live UI Preview:",
                    prevX + prevW / 2, prevY + 8, 0xFFA0A0AB);

            // Live mock ENABLED button
            int mBtnW = 100;
            int mBtnH = 20;
            int mBtnX = prevX + (prevW - mBtnW) / 2;
            int mBtnY = prevY + 24;
            context.fill(mBtnX, mBtnY, mBtnX + mBtnW, mBtnY + mBtnH,
                    com.mooclient.util.MooClientSettings.getAccentColor());
            drawBorder(context, mBtnX, mBtnY, mBtnW, mBtnH, 0x55FFFFFF);
            drawCenteredText(context, MooLanguage.get("enabled"), mBtnX + mBtnW / 2, mBtnY + 6, 0xFF082212);

            // Hex / RGB Code display
            int hexCol = com.mooclient.util.MooClientSettings.getAccentColor();
            String codeText = (activePreset == com.mooclient.util.MooClientSettings.AccentColorPreset.CHROMA) ? "RGB"
                    : ("HEX: " + String.format("#%06X", (0xFFFFFF & hexCol)));
            drawCenteredText(context, codeText, prevX + prevW / 2, prevY + 50, 0x88FFFFFF);
        }
    }

    private void renderRgbSlider(DrawContext context, int labelX, int trackX, int y, int trackW, int trackH,
            String label, int value, int colorBar, int mouseX, int mouseY) {
        context.drawTextWithShadow(this.textRenderer, label, labelX, y + 1, COLOR_TEXT_WHITE);
        context.drawTextWithShadow(this.textRenderer, String.valueOf(value), labelX + 14, y + 1, 0xFFA0A0AB);

        // Track
        context.fill(trackX, y + 1, trackX + trackW, y + trackH - 1, 0x44222230);
        drawBorder(context, trackX, y + 1, trackW, trackH - 2, 0x33FFFFFF);

        // Filled bar
        int fillW = Math.round((value / 255.0f) * trackW);
        if (fillW > 0) {
            context.fill(trackX + 1, y + 2, trackX + fillW, y + trackH - 2, colorBar);
        }

        // Knob
        int knobX = trackX + fillW - 3;
        context.fill(knobX, y - 2, knobX + 6, y + trackH + 2, COLOR_TEXT_WHITE);
        drawBorder(context, knobX, y - 2, 6, trackH + 4, 0xFF000000);
    }

    private void renderHudSettingsTab(DrawContext context, int panelX, int contentY, int panelW, int mouseX,
            int mouseY) {
        int rowX = panelX + 20;
        int rowW = panelW - 40;
        int rowH = 34;
        int curY = contentY + 2;

        // Reset HUD Button
        int btnW = 240;
        int btnH = 24;
        int btnX = panelX + (panelW - btnW) / 2;
        boolean resetHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= curY && mouseY <= curY + btnH;
        boolean justReset = (System.currentTimeMillis() - resetHudFeedbackTime < 2500);

        int btnBg = justReset ? com.mooclient.util.MooClientSettings.getAccentColor()
                : (resetHover ? 0xCC2A2A3A : 0x88181826);
        int btnBorder = justReset ? 0xFFFFFFFF
                : (resetHover ? com.mooclient.util.MooClientSettings.getAccentColor() : 0x44FFFFFF);
        context.fill(btnX, curY, btnX + btnW, curY + btnH, btnBg);
        drawBorder(context, btnX, curY, btnW, btnH, btnBorder);

        String btnText = justReset ? MooLanguage.get("reset_hud_done") : MooLanguage.get("reset_hud_btn");
        int btnTextCol = justReset ? 0xFF0A2514 : COLOR_TEXT_WHITE;
        drawCenteredText(context, btnText, btnX + btnW / 2, curY + 7, btnTextCol);

        curY += btnH + 12;

        // Row 1: Snapping
        drawOptionRow(context, rowX, curY, rowW, rowH, MooLanguage.get("hud_snapping_label"));
        drawOptionToggle(context, rowX + rowW - 44, curY + 8, mouseX, mouseY,
                com.mooclient.util.MooClientSettings.isHudSnapping());

        // Row 2: Scale (0-100%)
        curY += rowH + 6;
        drawOptionRow(context, rowX, curY, rowW, rowH, MooLanguage.get("hud_scale_label"));
        int hudSliderW = 180;
        int hudSliderX = rowX + rowW - hudSliderW - 8;
        renderPercentageSlider(context, hudSliderX, curY + 6, hudSliderW, 22,
                com.mooclient.util.MooClientSettings.getHudScale(), mouseX, mouseY);

        // Row 3: Global Text Shadows
        curY += rowH + 6;
        drawOptionRow(context, rowX, curY, rowW, rowH, MooLanguage.get("global_shadow_label"));
        drawOptionToggle(context, rowX + rowW - 44, curY + 8, mouseX, mouseY,
                com.mooclient.util.MooClientSettings.isGlobalTextShadow());
    }

    private void renderGuiSettingsTab(DrawContext context, int panelX, int contentY, int panelW, int mouseX,
            int mouseY) {
        int rowX = panelX + 20;
        int rowW = panelW - 40;
        int rowH = 34;
        int curY = contentY + 12;

        // Row 1: Background Dim
        drawOptionRow(context, rowX, curY, rowW, rowH, MooLanguage.get("bg_dim_label"));
        renderDimSelector(context, rowX + rowW - 206, curY + 6, mouseX, mouseY,
                com.mooclient.util.MooClientSettings.getMenuBackgroundDim());

        // Row 2: GUI Animations
        curY += rowH + 8;
        drawOptionRow(context, rowX, curY, rowW, rowH, MooLanguage.get("gui_anim_label"));
        drawOptionToggle(context, rowX + rowW - 44, curY + 8, mouseX, mouseY,
                com.mooclient.util.MooClientSettings.isGuiAnimations());
    }

    private void renderDimSelector(DrawContext context, int startX, int y, int mouseX, int mouseY,
            int selectedOrdinal) {
        String[] labels = new String[] { "30%", "50%", "75%" };
        int[] widths = new int[] { 64, 66, 64 };
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < labels.length; i++) {
            int w = widths[i];
            boolean selected = (i == selectedOrdinal);
            boolean hover = mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h;

            int bg = selected ? com.mooclient.util.MooClientSettings.getAccentColor()
                    : (hover ? 0xCC252535 : 0x66141420);
            int border = selected ? com.mooclient.util.MooClientSettings.getAccentHoverColor()
                    : (hover ? 0xAAFFFFFF : 0x33FFFFFF);
            int textColor = selected ? 0xFF0A2514 : (hover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

            context.fill(curX, y, curX + w, y + h, bg);
            drawBorder(context, curX, y, w, h, border);
            drawCenteredText(context, labels[i], curX + w / 2, y + 7, textColor);

            curX += w + gap;
        }
    }

    private void drawOptionRow(DrawContext context, int x, int y, int w, int h, String title) {
        context.fill(x, y, x + w, y + h, 0x5515151E);
        drawBorder(context, x, y, w, h, 0x22FFFFFF);
        context.drawTextWithShadow(this.textRenderer, title, x + 12, y + (h - 8) / 2, COLOR_TEXT_WHITE);
    }

    private void drawOptionToggle(DrawContext context, int x, int y, int mouseX, int mouseY, boolean enabled) {
        int w = 34;
        int h = 18;
        int bg = enabled ? com.mooclient.util.MooClientSettings.getAccentColor() : COLOR_DISABLED;
        context.fill(x, y, x + w, y + h, bg);
        drawBorder(context, x, y, w, h, 0x44FFFFFF);

        int knobSize = h - 4;
        int knobX = enabled ? x + w - knobSize - 2 : x + 2;
        int knobY = y + 2;
        int knobColor = enabled ? 0xFF082212 : 0xFFA0A0AB;
        context.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, knobColor);
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void renderStyleSelector(DrawContext context, int startX, int y, int mouseX, int mouseY,
            int selectedOrdinal) {
        String[] labels = new String[] { "Moo Client", "Simple", "Brackets" };
        if (MooLanguage.current.equals(MooLanguage.PL)) {
            labels = new String[] { "Moo Client", "Prosty", "Nawiasy" };
        }
        int[] widths = MooLanguage.current.equals(MooLanguage.PL) ? new int[] { 74, 56, 66 } : new int[] { 74, 54, 64 };
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < labels.length; i++) {
            int w = widths[i];
            boolean selected = (i == selectedOrdinal);
            boolean hover = mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h;

            int bg = selected ? com.mooclient.util.MooClientSettings.getAccentColor()
                    : (hover ? 0xCC252535 : 0x66141420);
            int border = selected ? com.mooclient.util.MooClientSettings.getAccentHoverColor()
                    : (hover ? 0xAAFFFFFF : 0x33FFFFFF);
            int textColor = selected ? 0xFF0A2514 : (hover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

            context.fill(curX, y, curX + w, y + h, bg);
            drawBorder(context, curX, y, w, h, border);
            drawCenteredText(context, labels[i], curX + w / 2, y + 7, textColor);

            curX += w + gap;
        }
    }

    private int getStyleSelectorClick(int startX, int y, int mouseX, int mouseY) {
        int[] widths = MooLanguage.current.equals(MooLanguage.PL) ? new int[] { 74, 56, 66 } : new int[] { 74, 54, 64 };
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < widths.length; i++) {
            int w = widths[i];
            if (mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h) {
                return i;
            }
            curX += w + gap;
        }
        return -1;
    }

    private void renderModeSelector(DrawContext context, int startX, int y, int mouseX, int mouseY,
            int selectedOrdinal) {
        String[] labels = new String[] { MooLanguage.get("mode_hold"), MooLanguage.get("mode_toggle") };
        int[] widths = new int[] { 100, 100 };
        int gap = 6;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < labels.length; i++) {
            int w = widths[i];
            boolean selected = (i == selectedOrdinal);
            boolean hover = mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h;

            int bg = selected ? com.mooclient.util.MooClientSettings.getAccentColor()
                    : (hover ? 0xCC252535 : 0x66141420);
            int border = selected ? com.mooclient.util.MooClientSettings.getAccentHoverColor()
                    : (hover ? 0xAAFFFFFF : 0x33FFFFFF);
            int textColor = selected ? 0xFF0A2514 : (hover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

            context.fill(curX, y, curX + w, y + h, bg);
            drawBorder(context, curX, y, w, h, border);
            drawCenteredText(context, labels[i], curX + w / 2, y + 7, textColor);

            curX += w + gap;
        }
    }

    private void renderPingPositionSelector(DrawContext context, int startX, int y, int mouseX, int mouseY,
            int selectedOrdinal) {
        String[] labels = new String[] { MooLanguage.get("ping_pos_beside"), MooLanguage.get("ping_pos_above") };
        int[] widths = new int[] { 100, 100 };
        int gap = 6;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < labels.length; i++) {
            int w = widths[i];
            boolean selected = (i == selectedOrdinal);
            boolean hover = mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h;

            int bg = selected ? com.mooclient.util.MooClientSettings.getAccentColor()
                    : (hover ? 0xCC252535 : 0x66141420);
            int border = selected ? com.mooclient.util.MooClientSettings.getAccentHoverColor()
                    : (hover ? 0xAAFFFFFF : 0x33FFFFFF);
            int textColor = selected ? 0xFF0A2514 : (hover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

            context.fill(curX, y, curX + w, y + h, bg);
            drawBorder(context, curX, y, w, h, border);
            drawCenteredText(context, labels[i], curX + w / 2, y + 7, textColor);

            curX += w + gap;
        }
    }

    private void renderCpsDisplayModeSelector(DrawContext context, int startX, int y, int mouseX, int mouseY,
            int selectedOrdinal) {
        String[] labels = new String[] { "LPM | PPM", "Tylko LPM", "Tylko PPM" };
        if (MooLanguage.current == MooLanguage.EN) {
            labels = new String[] { "LMB | RMB", "Left Only", "Right Only" };
        }
        int[] widths = new int[] { 78, 68, 68 };
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < labels.length; i++) {
            int w = widths[i];
            boolean selected = (i == selectedOrdinal);
            boolean hover = mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h;

            int bg = selected ? com.mooclient.util.MooClientSettings.getAccentColor()
                    : (hover ? 0xCC252535 : 0x66141420);
            int border = selected ? com.mooclient.util.MooClientSettings.getAccentHoverColor()
                    : (hover ? 0xAAFFFFFF : 0x33FFFFFF);
            int textColor = selected ? 0xFF0A2514 : (hover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

            context.fill(curX, y, curX + w, y + h, bg);
            drawBorder(context, curX, y, w, h, border);
            drawCenteredText(context, labels[i], curX + w / 2, y + 7, textColor);

            curX += w + gap;
        }
    }

    private int getCpsDisplayModeClick(int startX, int y, int mouseX, int mouseY) {
        int[] widths = new int[] { 78, 68, 68 };
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < widths.length; i++) {
            int w = widths[i];
            if (mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h) {
                return i;
            }
            curX += w + gap;
        }
        return -1;
    }

    private void renderPotionStyleSelector(DrawContext context, int startX, int y, int mouseX, int mouseY,
            int selectedOrdinal) {
        String[] labels = new String[] { "Moo Client", "Simple", "Compact" };
        if (MooLanguage.current.equals(MooLanguage.PL)) {
            labels = new String[] { "Moo Client", "Prosty", "Kompaktowy" };
        }
        int[] widths = MooLanguage.current.equals(MooLanguage.PL) ? new int[] { 78, 56, 84 } : new int[] { 78, 56, 72 };
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < labels.length; i++) {
            int w = widths[i];
            boolean selected = (i == selectedOrdinal);
            boolean hover = mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h;

            int bg = selected ? com.mooclient.util.MooClientSettings.getAccentColor()
                    : (hover ? 0xCC252535 : 0x66141420);
            int border = selected ? com.mooclient.util.MooClientSettings.getAccentHoverColor()
                    : (hover ? 0xAAFFFFFF : 0x33FFFFFF);
            int textColor = selected ? 0xFF0A2514 : (hover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

            context.fill(curX, y, curX + w, y + h, bg);
            drawBorder(context, curX, y, w, h, border);
            drawCenteredText(context, labels[i], curX + w / 2, y + 7, textColor);

            curX += w + gap;
        }
    }

    private int getPotionStyleClick(int startX, int y, int mouseX, int mouseY) {
        int[] widths = MooLanguage.current.equals(MooLanguage.PL) ? new int[] { 78, 56, 84 } : new int[] { 78, 56, 72 };
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < widths.length; i++) {
            int w = widths[i];
            if (mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h) {
                return i;
            }
            curX += w + gap;
        }
        return -1;
    }

    private void renderFactorSelector(DrawContext context, int startX, int y, int mouseX, int mouseY,
            int selectedOrdinal) {
        String[] labels = new String[] { "2x", "3x", "4x", "5x", "6x" };
        int w = 44;
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < labels.length; i++) {
            boolean selected = (i == selectedOrdinal);
            boolean hover = mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h;

            int bg = selected ? 0xDD22C55E : (hover ? 0xCC252535 : 0x66141420);
            int border = selected ? 0xFF4ADE80 : (hover ? 0xAAFFFFFF : 0x33FFFFFF);
            int textColor = selected ? 0xFF0A2514 : (hover ? COLOR_TEXT_WHITE : 0xFFA0A0AB);

            context.fill(curX, y, curX + w, y + h, bg);
            drawBorder(context, curX, y, w, h, border);
            drawCenteredText(context, labels[i], curX + w / 2, y + 7, textColor);

            curX += w + gap;
        }
    }

    private int getFactorSelectorClick(int startX, int y, int mouseX, int mouseY) {
        int w = 44;
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < 5; i++) {
            if (mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h) {
                return i;
            }
            curX += w + gap;
        }
        return -1;
    }

    private int getModeSelectorClick(int startX, int y, int mouseX, int mouseY) {
        int[] widths = new int[] { 100, 100 };
        int gap = 6;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < widths.length; i++) {
            int w = widths[i];
            if (mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h) {
                return i;
            }
            curX += w + gap;
        }
        return -1;
    }

    private void renderPercentageSlider(DrawContext context, int x, int y, int w, int h, int percent, int mouseX,
            int mouseY) {
        percent = Math.max(0, Math.min(100, percent));
        String text = percent + "%";

        int trackW = w - 36;
        int trackX = x;
        int trackH = 6;
        int trackY = y + (h - trackH) / 2;

        // Track background
        context.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x55181824);
        drawBorder(context, trackX, trackY, trackW, trackH, 0x33FFFFFF);

        // Filled bar (Accent color)
        int fillW = Math.round((percent / 100.0f) * trackW);
        if (fillW > 0) {
            context.fill(trackX + 1, trackY + 1, trackX + fillW, trackY + trackH - 1,
                    com.mooclient.util.MooClientSettings.getAccentColor());
        }

        // Draggable Knob
        int knobX = trackX + fillW - 2;
        boolean hover = mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= trackY - 4
                && mouseY <= trackY + trackH + 4;
        int knobBorder = hover ? 0xFFFFFFFF : com.mooclient.util.MooClientSettings.getAccentColor();
        context.fill(knobX, trackY - 4, knobX + 5, trackY + trackH + 4, COLOR_TEXT_WHITE);
        drawBorder(context, knobX, trackY - 4, 5, trackH + 8, knobBorder);

        // Percentage text display on the right
        int textX = trackX + trackW + 6;
        int textY = y + (h - 8) / 2;
        context.drawTextWithShadow(this.textRenderer, text, textX, textY, COLOR_TEXT_WHITE);
    }

    private void drawCenteredText(DrawContext context, String text, int centerX, int y, int color) {
        int width = this.textRenderer.getWidth(text);
        context.drawTextWithShadow(this.textRenderer, text, centerX - width / 2, y, color);
    }

    private void playClickSound() {
        if (this.client != null) {
            this.client.getSoundManager().play(
                    net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentView == View.MODS) {
            List<Module> modules = ModuleManager.getInstance().getModules();
            int cols = 3;
            int cardH = 150;
            int cardGap = 16;
            int headerH = 42;
            int panelH = 260;
            int totalRows = (modules.size() + cols - 1) / cols;
            int totalContentH = totalRows * cardH + (totalRows - 1) * cardGap;
            int visibleAreaH = panelH - headerH - 24;
            int maxScroll = Math.max(0, totalContentH - visibleAreaH + 8);

            if (maxScroll > 0) {
                scrollY = Math.max(0, Math.min(maxScroll, scrollY - verticalAmount * 24.0));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // If listening for new keybind/mouse bind in options
        if (currentView == View.OPTIONS && listeningForKeybind) {
            String mouseName;
            if (button == 0) {
                mouseName = "LMB";
            } else if (button == 1) {
                mouseName = "RMB";
            } else if (button == 2) {
                mouseName = "SCROLL";
            } else if (button == 3) {
                mouseName = "MOUSE 4";
            } else if (button == 4) {
                mouseName = "MOUSE 5";
            } else {
                mouseName = "BUTTON " + (button + 1);
            }

            if (selectedModule != null && selectedModule.getName().equalsIgnoreCase("Freelook")) {
                FreelookModule.setKeybind(button, mouseName, true);
            } else if (selectedModule != null && selectedModule.getName().equalsIgnoreCase("Zoom")) {
                com.mooclient.module.modules.ZoomModule.setKeybind(button, mouseName, true);
            } else if (selectedModule != null && selectedModule.getName().equalsIgnoreCase("Waypoints")) {
                com.mooclient.module.modules.WaypointsModule.setKeybind(button, mouseName, true);
            } else if (selectedModule != null && (selectedModule.getName().equalsIgnoreCase("Emotki") || selectedModule.getName().equalsIgnoreCase("Emotes"))) {
                if (listeningEmoteSlot == 1) {
                    com.mooclient.module.modules.EmotesModule.setWheelKeybind(button, mouseName, true);
                } else {
                    com.mooclient.module.modules.EmotesModule.setKeybind(button, mouseName, true);
                }
            } else {
                ToggleSprintModule.setKeybind(button, mouseName, true);
            }

            com.mooclient.util.MooConfig.save();
            listeningForKeybind = false;
            playClickSound();
            return true;
        }

        if (button == 0) { // Left click
            // 1. Language Switcher Click
            int langX = this.width - 66;
            int langY = 12;
            int pillW = 26;
            int pillH = 18;
            int langGap = 2;

            if (mouseX >= langX && mouseX <= langX + pillW && mouseY >= langY && mouseY <= langY + pillH) {
                playClickSound();
                MooLanguage.current = MooLanguage.PL;
                return true;
            }
            if (mouseX >= langX + pillW + langGap && mouseX <= langX + (pillW * 2) + langGap && mouseY >= langY
                    && mouseY <= langY + pillH) {
                playClickSound();
                MooLanguage.current = MooLanguage.EN;
                return true;
            }

            // 2. Hub View Clicks & Draggable HUD Handling
            if (currentView == View.HUB) {
                if (FpsModule.isFpsEnabled()) {
                    int w = FpsModule.width;
                    int h = FpsModule.height;
                    int x = FpsModule.position.calculateX(w, this.width);
                    int y = FpsModule.position.calculateY(h, this.height);
                    if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 4 && mouseY <= y + h + 4) {
                        draggingWidget = "FPS";
                        dragOffsetX = (int) mouseX - x;
                        dragOffsetY = (int) mouseY - y;
                        return true;
                    }
                }

                if (ToggleSprintModule.isSprintEnabled()) {
                    int w = ToggleSprintModule.width;
                    int h = ToggleSprintModule.height;
                    int x = ToggleSprintModule.position.calculateX(w, this.width);
                    int y = ToggleSprintModule.position.calculateY(h, this.height);
                    if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 4 && mouseY <= y + h + 4) {
                        draggingWidget = "SPRINT";
                        dragOffsetX = (int) mouseX - x;
                        dragOffsetY = (int) mouseY - y;
                        return true;
                    }
                }

                if (PotionEffectsModule.isModuleEnabled()) {
                    int w = PotionEffectsModule.width;
                    int h = PotionEffectsModule.height;
                    int x = PotionEffectsModule.position.calculateX(w, this.width);
                    int y = PotionEffectsModule.position.calculateY(h, this.height);
                    if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 4 && mouseY <= y + h + 4) {
                        draggingWidget = "POTIONS";
                        dragOffsetX = (int) mouseX - x;
                        dragOffsetY = (int) mouseY - y;
                        return true;
                    }
                }

                if (com.mooclient.module.modules.PingModule.isPingEnabled()) {
                    int w = com.mooclient.module.modules.PingModule.width;
                    int h = com.mooclient.module.modules.PingModule.height;
                    int x = com.mooclient.module.modules.PingModule.position.calculateX(w, this.width);
                    int y = com.mooclient.module.modules.PingModule.position.calculateY(h, this.height);
                    if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 4 && mouseY <= y + h + 4) {
                        draggingWidget = "PING";
                        dragOffsetX = (int) mouseX - x;
                        dragOffsetY = (int) mouseY - y;
                        return true;
                    }
                }

                if (com.mooclient.module.modules.CpsModule.isCpsEnabled()) {
                    int w = com.mooclient.module.modules.CpsModule.width;
                    int h = com.mooclient.module.modules.CpsModule.height;
                    int x = com.mooclient.module.modules.CpsModule.position.calculateX(w, this.width);
                    int y = com.mooclient.module.modules.CpsModule.position.calculateY(h, this.height);
                    if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 4 && mouseY <= y + h + 4) {
                        draggingWidget = "CPS";
                        dragOffsetX = (int) mouseX - x;
                        dragOffsetY = (int) mouseY - y;
                        return true;
                    }
                }

                if (com.mooclient.module.modules.ScoreboardModule.isScoreboardEnabled()) {
                    int w = com.mooclient.module.modules.ScoreboardModule.width;
                    int h = com.mooclient.module.modules.ScoreboardModule.height;
                    int x = com.mooclient.module.modules.ScoreboardModule.position.calculateX(w, this.width);
                    int y = com.mooclient.module.modules.ScoreboardModule.position.calculateY(h, this.height);
                    if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 4 && mouseY <= y + h + 4) {
                        draggingWidget = "SCOREBOARD";
                        dragOffsetX = (int) mouseX - x;
                        dragOffsetY = (int) mouseY - y;
                        return true;
                    }
                }

                int centerX = this.width / 2;
                int centerY = this.height / 2;
                int btnW = 140;
                int btnH = 32;
                int btnX = centerX - btnW / 2;
                int btnY = centerY - btnH / 2;

                if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    playClickSound();
                    this.currentView = View.MODS;
                    return true;
                }
            }
            // 3. Mods View Clicks
            else if (currentView == View.MODS) {
                int panelW = 560;
                int panelH = 265;
                int panelX = (this.width - panelW) / 2;
                int panelY = (this.height - panelH) / 2;

                // Back Button Click
                int backX = panelX + 14;
                int backY = panelY + 12;
                int backW = 74;
                int backH = 22;
                if (mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH) {
                    playClickSound();
                    this.currentView = View.HUB;
                    this.searching = false;
                    return true;
                }

                // Settings Button Click
                int setBtnW = 96;
                int setBtnH = 20;
                int setBtnX = panelX + panelW - setBtnW - 14;
                int setBtnY = panelY + 12;
                if (mouseX >= setBtnX && mouseX <= setBtnX + setBtnW && mouseY >= setBtnY
                        && mouseY <= setBtnY + setBtnH) {
                    playClickSound();
                    this.currentView = View.SETTINGS;
                    this.searching = false;
                    return true;
                }

                // Search Bar Click
                int searchW = 200;
                int searchH = 18;
                int searchX = panelX + (panelW - searchW) / 2;
                int searchY = panelY + 26;
                if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY
                        && mouseY <= searchY + searchH) {
                    playClickSound();
                    if (!searchFilter.isEmpty() && mouseX >= searchX + searchW - 18) {
                        searchFilter = "";
                        searching = true;
                    } else {
                        searching = true;
                    }
                    return true;
                } else {
                    searching = false;
                }

                List<Module> allModules = ModuleManager.getInstance().getModules();
                List<Module> modules;
                if (searchFilter == null || searchFilter.trim().isEmpty()) {
                    modules = allModules;
                } else {
                    String query = searchFilter.trim().toLowerCase();
                    modules = allModules.stream().filter(m -> {
                        String name = m.getName().toLowerCase();
                        String desc = getModuleDescText(m.getName()).toLowerCase();
                        return name.contains(query) || desc.contains(query);
                    }).toList();
                }

                int cols = 3;
                int cardW = 160;
                int cardH = 135;
                int cardGap = 16;
                int totalGridW = cols * cardW + (cols - 1) * cardGap;
                int startX = panelX + (panelW - totalGridW) / 2;
                int startY = panelY + 56 + 14;

                for (int i = 0; i < modules.size(); i++) {
                    Module module = modules.get(i);
                    int col = i % cols;
                    int row = i / cols;
                    int cardX = startX + col * (cardW + cardGap);
                    int cardY = startY + row * (cardH + cardGap) - (int) scrollY;

                    // Ensure click is inside visible area
                    if (mouseY < panelY + 56 + 2 || mouseY > panelY + panelH - 4) {
                        continue;
                    }

                    // ENABLED / DISABLED Button Click
                    int btnH = 22;
                    int btnY = cardY + cardH - 28;
                    int btnX = cardX + 8;
                    int btnW = cardW - 16;
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                        playClickSound();
                        module.toggle();
                        return true;
                    }

                    // OPTIONS Bar Click OR Card Body Click
                    int optH = 20;
                    int optY = cardY + cardH - 52;
                    int optX = cardX + 8;
                    int optW = cardW - 16;
                    boolean optClicked = mouseX >= optX && mouseX <= optX + optW && mouseY >= optY
                            && mouseY <= optY + optH;
                    boolean cardBodyClicked = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY
                            && mouseY <= optY;

                    if (optClicked || cardBodyClicked) {
                        playClickSound();
                        this.selectedModule = module;
                        this.listeningForKeybind = false;
                        this.currentView = View.OPTIONS;
                        return true;
                    }
                }
            }
            // 4. Options View Clicks
            else if (currentView == View.OPTIONS) {
                int panelW = 480;
                int panelH = 330;
                int panelX = (this.width - panelW) / 2;
                int panelY = (this.height - panelH) / 2;

                int backX = panelX + 14;
                int backY = panelY + 12;
                int backW = 74;
                int backH = 22;
                if (mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH) {
                    playClickSound();
                    this.listeningForKeybind = false;
                    this.currentView = View.MODS;
                    return true;
                }

                String modName = selectedModule != null ? selectedModule.getName() : "FPS";
                int headerH = 46;
                int rowY = panelY + headerH + 14;
                int rowH = 34;
                int rowW = panelW - 32;
                int rowX = panelX + 16;

                if (modName.equalsIgnoreCase("FPS")) {
                    int styleClick = getStyleSelectorClick(rowX + rowW - 206, rowY + 6, (int) mouseX, (int) mouseY);
                    if (styleClick >= 0) {
                        playClickSound();
                        FpsModule.setStyle(FpsModule.FpsStyle.values()[styleClick]);
                        return true;
                    }

                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        FpsModule.toggleShowBackground();
                        return true;
                    }

                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        FpsModule.toggleTextShadow();
                        return true;
                    }

                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        FpsModule.toggleShowPrefix();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Ping")) {
                    int styleClick = getStyleSelectorClick(rowX + rowW - 206, rowY + 6, (int) mouseX, (int) mouseY);
                    if (styleClick >= 0) {
                        playClickSound();
                        com.mooclient.module.modules.PingModule
                                .setStyle(com.mooclient.module.modules.PingModule.PingStyle.values()[styleClick]);
                        return true;
                    }

                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.PingModule.toggleShowBackground();
                        return true;
                    }

                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.PingModule.toggleTextShadow();
                        return true;
                    }

                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.PingModule.toggleShowPrefix();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("CPS")) {
                    int modeClick = getCpsDisplayModeClick(rowX + rowW - 230, rowY + 6, (int) mouseX, (int) mouseY);
                    if (modeClick >= 0) {
                        playClickSound();
                        com.mooclient.module.modules.CpsModule
                                .setDisplayMode(
                                        com.mooclient.module.modules.CpsModule.CpsDisplayMode.values()[modeClick]);
                        return true;
                    }

                    rowY += rowH + 6;
                    int styleClick = getStyleSelectorClick(rowX + rowW - 206, rowY + 6, (int) mouseX, (int) mouseY);
                    if (styleClick >= 0) {
                        playClickSound();
                        com.mooclient.module.modules.CpsModule
                                .setStyle(com.mooclient.module.modules.CpsModule.CpsStyle.values()[styleClick]);
                        return true;
                    }

                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.CpsModule.toggleShowBackground();
                        return true;
                    }

                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.CpsModule.toggleTextShadow();
                        return true;
                    }

                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.CpsModule.toggleShowPrefix();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Sprint")) {
                    int btnW = 140;
                    int btnH = 22;
                    int btnX = rowX + rowW - btnW - 10;
                    int btnY = rowY + 6;

                    // Row 1: Click to toggle interactive Keybind listening mode
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                        playClickSound();
                        this.listeningForKeybind = !this.listeningForKeybind;
                        return true;
                    }

                    // Row 2: Sprint Style Tab Selection
                    rowY += rowH + 6;
                    int styleClick = getStyleSelectorClick(rowX + rowW - 206, rowY + 6, (int) mouseX, (int) mouseY);
                    if (styleClick >= 0) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        ToggleSprintModule.setStyle(ToggleSprintModule.SprintStyle.values()[styleClick]);
                        return true;
                    }

                    // Row 3: Show Background
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        ToggleSprintModule.toggleShowBackground();
                        return true;
                    }

                    // Row 4: Text Shadow
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        ToggleSprintModule.toggleTextShadow();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Freelook")) {
                    int btnW = 140;
                    int btnH = 22;
                    int btnX = rowX + rowW - btnW - 10;
                    int btnY = rowY + 6;

                    // Row 1: Keybind
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                        playClickSound();
                        this.listeningForKeybind = !this.listeningForKeybind;
                        return true;
                    }

                    // Row 2: Mode Selector
                    rowY += rowH + 6;
                    int modeClick = getModeSelectorClick(rowX + rowW - 206, rowY + 6, (int) mouseX, (int) mouseY);
                    if (modeClick >= 0) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        FreelookModule.setMode(FreelookModule.ActivationMode.values()[modeClick]);
                        return true;
                    }

                    // Row 3: Invert Pitch Toggle
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        FreelookModule.toggleInvertPitch();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Potion Effects")) {
                    // Row 1: Style Selector
                    int styleClick = getPotionStyleClick(rowX + rowW - 248, rowY + 6, (int) mouseX, (int) mouseY);
                    if (styleClick >= 0) {
                        playClickSound();
                        PotionEffectsModule.setStyle(PotionEffectsModule.PotionStyle.values()[styleClick]);
                        return true;
                    }

                    // Row 2: Show Background
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        PotionEffectsModule.toggleShowBackground();
                        return true;
                    }

                    // Row 3: Text Shadow
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        PotionEffectsModule.toggleTextShadow();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Nametags")) {
                    // Row 1: Show Ping
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.NametagsModule.toggleShowPing();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 2: Show Self Ping
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.NametagsModule.toggleShowSelfPing();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 3: Ping Position (Beside vs Above)
                    rowY += rowH + 6;
                    int pingPosClick = getModeSelectorClick(rowX + rowW - 206, rowY + 6, (int) mouseX, (int) mouseY);
                    if (pingPosClick >= 0) {
                        playClickSound();
                        com.mooclient.module.modules.NametagsModule.setPingPosition(
                                com.mooclient.module.modules.NametagsModule.PingPosition.values()[pingPosClick]);
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 4: Remove Background
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.NametagsModule.toggleRemoveBackground();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 5: Text Shadow
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.NametagsModule.toggleTextShadow();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Zoom")) {
                    int btnW = 140;
                    int btnH = 22;
                    int btnX = rowX + rowW - btnW - 10;
                    int btnY = rowY + 6;

                    // Row 1: Keybind
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                        playClickSound();
                        this.listeningForKeybind = !this.listeningForKeybind;
                        return true;
                    }

                    // Row 2: Zoom Factor (2x, 3x, 4x, 5x, 6x)
                    rowY += rowH + 6;
                    int factorClick = getFactorSelectorClick(rowX + rowW - 248, rowY + 6, (int) mouseX, (int) mouseY);
                    if (factorClick >= 0) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        com.mooclient.module.modules.ZoomModule
                                .setFactor(com.mooclient.module.modules.ZoomModule.ZoomFactor.values()[factorClick]);
                        return true;
                    }

                    // Row 3: Activation Mode (Hold vs Toggle)
                    rowY += rowH + 6;
                    int modeClick = getModeSelectorClick(rowX + rowW - 206, rowY + 6, (int) mouseX, (int) mouseY);
                    if (modeClick >= 0) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        com.mooclient.module.modules.ZoomModule
                                .setMode(com.mooclient.module.modules.ZoomModule.ActivationMode.values()[modeClick]);
                        return true;
                    }

                    // Row 4: Smooth Zoom Toggle
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        com.mooclient.module.modules.ZoomModule.toggleSmoothZoom();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Chat")) {
                    // Row 1: Transparent Background
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.ChatModule.toggleTransparentBackground();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 2: Unlimited Chat
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.ChatModule.toggleUnlimitedChat();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 3: Smooth Chat
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.ChatModule.toggleSmoothChat();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 4: Text Shadow
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.ChatModule.toggleTextShadow();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Macro")) {
                    java.util.List<com.mooclient.module.modules.MacroModule.MacroEntry> macroList = com.mooclient.module.modules.MacroModule
                            .getMacros();
                    int mRowH = 28;
                    int curY = panelY + headerH + 10;

                    // If listening for mouse button keybind
                    if (button != 0 && this.listeningMacroIndex >= 0 && this.listeningMacroIndex < macroList.size()) {
                        com.mooclient.module.modules.MacroModule.MacroEntry m = macroList.get(this.listeningMacroIndex);
                        m.setKeyCode(button);
                        m.setKeyName(button == 2 ? "SCROLL" : (button == 1 ? "RMB" : "MOUSE " + (button + 1)));
                        m.setMouseButton(true);
                        this.listeningMacroIndex = -1;
                        com.mooclient.util.MooConfig.save();
                        playClickSound();
                        return true;
                    }

                    for (int i = 0; i < Math.min(5, macroList.size()); i++) {
                        com.mooclient.module.modules.MacroModule.MacroEntry m = macroList.get(i);
                        int cmdBoxX = rowX + 54;
                        int cmdBoxW = rowW - 54 - 110 - 44;
                        int cmdBoxY = curY + 4;
                        int cmdBoxH = mRowH - 8;

                        int kBtnX = cmdBoxX + cmdBoxW + 6;
                        int kBtnW = 96;
                        int kBtnY = curY + 4;
                        int kBtnH = mRowH - 8;

                        int tX = rowX + rowW - 40;
                        int tY = curY + 5;

                        // Click Command Box
                        if (mouseX >= cmdBoxX && mouseX <= cmdBoxX + cmdBoxW && mouseY >= cmdBoxY
                                && mouseY <= cmdBoxY + cmdBoxH) {
                            playClickSound();
                            this.editingMacroIndex = (this.editingMacroIndex == i ? -1 : i);
                            this.listeningMacroIndex = -1;
                            return true;
                        }

                        // Click Keybind Button
                        if (mouseX >= kBtnX && mouseX <= kBtnX + kBtnW && mouseY >= kBtnY && mouseY <= kBtnY + kBtnH) {
                            playClickSound();
                            this.listeningMacroIndex = (this.listeningMacroIndex == i ? -1 : i);
                            this.editingMacroIndex = -1;
                            return true;
                        }

                        // Click Enable Toggle
                        if (mouseX >= tX && mouseX <= tX + 34 && mouseY >= tY && mouseY <= tY + 18) {
                            playClickSound();
                            m.setEnabled(!m.isEnabled());
                            com.mooclient.util.MooConfig.save();
                            return true;
                        }

                        curY += mRowH + 4;
                    }
                } else if (modName.equalsIgnoreCase("Waypoints")) {
                    int btnW = 140;
                    int btnH = 22;
                    int btnX = rowX + rowW - btnW - 10;
                    int btnY = rowY + 6;

                    // Row 1: Keybind click
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                        playClickSound();
                        this.listeningForKeybind = !this.listeningForKeybind;
                        return true;
                    }

                    // Row 2: Open Waypoints Screen
                    rowY += rowH + 6;
                    int openBtnW = 200;
                    int openBtnH = 22;
                    int openBtnX = rowX + rowW - openBtnW - 10;
                    int openBtnY = rowY + 6;
                    if (mouseX >= openBtnX && mouseX <= openBtnX + openBtnW && mouseY >= openBtnY
                            && mouseY <= openBtnY + openBtnH) {
                        playClickSound();
                        if (this.client != null) {
                            this.client.setScreen(new MooWaypointScreen());
                        }
                        return true;
                    }

                    // Row 3: Waypoint Scale Slider (0-100%)
                    rowY += rowH + 6;
                    int wpSliderW = 180;
                    int wpSliderX = rowX + rowW - wpSliderW - 8;
                    if (mouseX >= wpSliderX - 4 && mouseX <= wpSliderX + wpSliderW + 4 && mouseY >= rowY + 2
                            && mouseY <= rowY + 24) {
                        this.draggingSlider = 4;
                        handleSliderDrag(mouseX);
                        playClickSound();
                        return true;
                    }

                    // Row 4: Auto Death Waypoint Toggle
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.WaypointsModule.toggleDeathWaypoint();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 5: Show Distance
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.WaypointsModule.toggleShowDistance();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 6: Background Toggle
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.WaypointsModule.toggleShowBackground();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 7: Text Shadow Toggle
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.WaypointsModule.toggleTextShadow();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Scoreboard")) {
                    // Row 1: Enable / Disable
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        boolean newState = !com.mooclient.module.modules.ScoreboardModule.isScoreboardEnabled();
                        com.mooclient.module.modules.ScoreboardModule.setScoreboardEnabled(newState);
                        selectedModule.setEnabled(newState);
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 2: Text Shadow
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.ScoreboardModule.toggleTextShadow();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 3: Background
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.ScoreboardModule.toggleShowBackground();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 4: Show Scores / Numbers
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        com.mooclient.module.modules.ScoreboardModule.toggleShowScores();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 5: Reset Position
                    rowY += rowH + 6;
                    int rBtnW = 160;
                    int rBtnH = 22;
                    int rBtnX = rowX + rowW - rBtnW - 10;
                    int rBtnY = rowY + 6;
                    if (mouseX >= rBtnX && mouseX <= rBtnX + rBtnW && mouseY >= rBtnY && mouseY <= rBtnY + rBtnH) {
                        playClickSound();
                        com.mooclient.module.modules.ScoreboardModule.resetPosition();
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }
                } else if (modName.equalsIgnoreCase("Emotki") || modName.equalsIgnoreCase("Emotes")) {
                    int btnW = 140;
                    int btnH = 22;
                    int btnX = rowX + rowW - btnW - 10;
                    int btnY = rowY + 6;

                    // Row 1: Hands Up Keybind click
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                        playClickSound();
                        boolean wasListening = this.listeningForKeybind && this.listeningEmoteSlot == 0;
                        this.listeningForKeybind = !wasListening;
                        this.listeningEmoteSlot = 0;
                        return true;
                    }

                    // Row 2: Emote Wheel Keybind click
                    rowY += rowH + 6;
                    btnY = rowY + 6;
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                        playClickSound();
                        boolean wasListening = this.listeningForKeybind && this.listeningEmoteSlot == 1;
                        this.listeningForKeybind = !wasListening;
                        this.listeningEmoteSlot = 1;
                        return true;
                    }

                    // Row 3: Mode Selector (Hold vs Toggle)
                    rowY += rowH + 6;
                    int modeClick = getModeSelectorClick(rowX + rowW - 206, rowY + 6, (int) mouseX, (int) mouseY);
                    if (modeClick >= 0) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        com.mooclient.module.modules.EmotesModule.setMode(
                                com.mooclient.module.modules.EmotesModule.ActivationMode.values()[modeClick]);
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }

                    // Row 4: Enable Toggle
                    rowY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        this.listeningForKeybind = false;
                        boolean newState = !com.mooclient.module.modules.EmotesModule.isEmotesEnabled();
                        com.mooclient.module.modules.EmotesModule.setEmotesEnabled(newState);
                        if (selectedModule != null) {
                            selectedModule.setEnabled(newState);
                        }
                        com.mooclient.util.MooConfig.save();
                        return true;
                    }
                } else {
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= rowY + 8
                            && mouseY <= rowY + 26) {
                        playClickSound();
                        if (selectedModule != null)
                            selectedModule.toggle();
                        return true;
                    }
                }
            }
            // 5. Settings View Clicks
            else if (currentView == View.SETTINGS) {
                int panelW = 560;
                int panelH = 275;
                int panelX = (this.width - panelW) / 2;
                int panelY = (this.height - panelH) / 2;

                // Back Button Click
                int backX = panelX + 14;
                int backY = panelY + 8;
                int backW = 74;
                int backH = 20;
                if (mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH) {
                    playClickSound();
                    this.currentView = View.MODS;
                    return true;
                }

                // Tabs Click
                int tabY = panelY + 34;
                int tabH = 22;
                int tabW = (panelW - 28) / 3;
                for (int i = 0; i < 3; i++) {
                    int tX = panelX + 14 + i * tabW;
                    if (mouseX >= tX && mouseX <= tX + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                        playClickSound();
                        this.settingsTab = i;
                        return true;
                    }
                }

                int contentY = tabY + tabH + 10;

                // Tab 0: Accent Color
                if (settingsTab == 0) {
                    com.mooclient.util.MooClientSettings.AccentColorPreset[] presets = com.mooclient.util.MooClientSettings.AccentColorPreset
                            .values();
                    int cols = 5;
                    int pW = 100;
                    int pH = 22;
                    int gap = 6;
                    int startX = panelX + 18;
                    int startY = contentY + 16;

                    // Preset swatch clicks
                    for (int i = 0; i < presets.length; i++) {
                        int col = i % cols;
                        int row = i / cols;
                        int bx = startX + col * (pW + gap);
                        int by = startY + row * (pH + gap);

                        if (mouseX >= bx && mouseX <= bx + pW && mouseY >= by && mouseY <= by + pH) {
                            playClickSound();
                            com.mooclient.util.MooClientSettings.setAccentPreset(presets[i]);
                            return true;
                        }
                    }

                    // RGB Sliders Click (only if CUSTOM preset is active)
                    if (com.mooclient.util.MooClientSettings
                            .getAccentPreset() == com.mooclient.util.MooClientSettings.AccentColorPreset.CUSTOM) {
                        int customY = startY + 2 * (pH + gap) + 8;
                        int sliderStartX = panelX + 50;
                        int sliderW = 210;
                        int sliderH = 14;
                        int sY = customY + 18;

                        // Red slider click
                        if (mouseX >= sliderStartX - 5 && mouseX <= sliderStartX + sliderW + 5 && mouseY >= sY
                                && mouseY <= sY + sliderH) {
                            this.draggingSlider = 0;
                            handleSliderDrag(mouseX);
                            playClickSound();
                            return true;
                        }
                        // Green slider click
                        sY += 20;
                        if (mouseX >= sliderStartX - 5 && mouseX <= sliderStartX + sliderW + 5 && mouseY >= sY
                                && mouseY <= sY + sliderH) {
                            this.draggingSlider = 1;
                            handleSliderDrag(mouseX);
                            playClickSound();
                            return true;
                        }
                        // Blue slider click
                        sY += 20;
                        if (mouseX >= sliderStartX - 5 && mouseX <= sliderStartX + sliderW + 5 && mouseY >= sY
                                && mouseY <= sY + sliderH) {
                            this.draggingSlider = 2;
                            handleSliderDrag(mouseX);
                            playClickSound();
                            return true;
                        }
                    }
                }
                // Tab 1: HUD Management
                else if (settingsTab == 1) {
                    int rowX = panelX + 20;
                    int rowW = panelW - 40;
                    int rowH = 34;
                    int curY = contentY + 2;

                    // Reset HUD Button
                    int btnW = 240;
                    int btnH = 24;
                    int btnX = panelX + (panelW - btnW) / 2;
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= curY && mouseY <= curY + btnH) {
                        playClickSound();
                        com.mooclient.util.MooClientSettings.resetHudPositions();
                        this.resetHudFeedbackTime = System.currentTimeMillis();
                        return true;
                    }

                    curY += btnH + 12;

                    // Snapping Toggle
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= curY + 8
                            && mouseY <= curY + 26) {
                        playClickSound();
                        com.mooclient.util.MooClientSettings.toggleHudSnapping();
                        return true;
                    }

                    // Scale Slider (0-100%)
                    curY += rowH + 6;
                    int hudSliderW = 180;
                    int hudSliderX = rowX + rowW - hudSliderW - 8;
                    if (mouseX >= hudSliderX - 4 && mouseX <= hudSliderX + hudSliderW + 4 && mouseY >= curY + 2
                            && mouseY <= curY + 24) {
                        this.draggingSlider = 3;
                        handleSliderDrag(mouseX);
                        playClickSound();
                        return true;
                    }

                    // Global Shadows Toggle
                    curY += rowH + 6;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= curY + 8
                            && mouseY <= curY + 26) {
                        playClickSound();
                        com.mooclient.util.MooClientSettings.toggleGlobalTextShadow();
                        return true;
                    }
                }
                // Tab 2: GUI Appearance
                else if (settingsTab == 2) {
                    int rowX = panelX + 20;
                    int rowW = panelW - 40;
                    int rowH = 34;
                    int curY = contentY + 12;

                    // Dim Selector
                    int dimClick = getSelector3Click(rowX + rowW - 206, curY + 6, (int) mouseX, (int) mouseY);
                    if (dimClick >= 0) {
                        playClickSound();
                        com.mooclient.util.MooClientSettings.setMenuBackgroundDim(dimClick);
                        return true;
                    }

                    // Animations Toggle
                    curY += rowH + 8;
                    if (mouseX >= rowX + rowW - 44 && mouseX <= rowX + rowW - 10 && mouseY >= curY + 8
                            && mouseY <= curY + 26) {
                        playClickSound();
                        com.mooclient.util.MooClientSettings.toggleGuiAnimations();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getSelector3Click(int startX, int y, int mouseX, int mouseY) {
        int[] widths = new int[] { 64, 66, 64 };
        int gap = 4;
        int curX = startX;
        int h = 22;

        for (int i = 0; i < widths.length; i++) {
            int w = widths[i];
            if (mouseX >= curX && mouseX <= curX + w && mouseY >= y && mouseY <= y + h) {
                return i;
            }
            curX += w + gap;
        }
        return -1;
    }

    private void handleSliderDrag(double mouseX) {
        if (currentView == View.SETTINGS) {
            if (settingsTab == 0 && draggingSlider >= 0 && draggingSlider <= 2) {
                int panelW = 560;
                int panelX = (this.width - panelW) / 2;
                int sliderStartX = panelX + 50;
                int sliderW = 210;

                float clamped = (float) Math.max(0, Math.min(sliderW, mouseX - sliderStartX));
                int val = Math.round((clamped / (float) sliderW) * 255);

                if (draggingSlider == 0) {
                    com.mooclient.util.MooClientSettings.setCustomRed(val);
                    com.mooclient.util.MooClientSettings
                            .setAccentPreset(com.mooclient.util.MooClientSettings.AccentColorPreset.CUSTOM);
                } else if (draggingSlider == 1) {
                    com.mooclient.util.MooClientSettings.setCustomGreen(val);
                    com.mooclient.util.MooClientSettings
                            .setAccentPreset(com.mooclient.util.MooClientSettings.AccentColorPreset.CUSTOM);
                } else if (draggingSlider == 2) {
                    com.mooclient.util.MooClientSettings.setCustomBlue(val);
                    com.mooclient.util.MooClientSettings
                            .setAccentPreset(com.mooclient.util.MooClientSettings.AccentColorPreset.CUSTOM);
                }
            } else if (settingsTab == 1 && draggingSlider == 3) {
                int panelW = 560;
                int panelX = (this.width - panelW) / 2;
                int rowX = panelX + 20;
                int rowW = panelW - 40;
                int sliderW = 180;
                int sliderX = rowX + rowW - sliderW - 8;
                int trackW = sliderW - 36;

                float clamped = (float) Math.max(0, Math.min(trackW, mouseX - sliderX));
                int val = Math.round((clamped / (float) trackW) * 100);
                com.mooclient.util.MooClientSettings.setHudScale(val);
            }
        } else if (currentView == View.OPTIONS && draggingSlider == 4) {
            int panelW = 440;
            int panelX = (this.width - panelW) / 2;
            int rowX = panelX + 16;
            int rowW = panelW - 32;
            int sliderW = 180;
            int sliderX = rowX + rowW - sliderW - 8;
            int trackW = sliderW - 36;

            float clamped = (float) Math.max(0, Math.min(trackW, mouseX - sliderX));
            int val = Math.round((clamped / (float) trackW) * 100);
            com.mooclient.module.modules.WaypointsModule.setScalePercent(val);
            com.mooclient.util.MooConfig.save();
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (currentView == View.MODS && searching) {
            if (chr >= 32 && chr != 127) {
                searchFilter += chr;
                scrollY = 0;
                return true;
            }
        }
        if (currentView == View.OPTIONS && editingMacroIndex >= 0) {
            java.util.List<com.mooclient.module.modules.MacroModule.MacroEntry> macroList = com.mooclient.module.modules.MacroModule
                    .getMacros();
            if (editingMacroIndex < macroList.size()) {
                com.mooclient.module.modules.MacroModule.MacroEntry m = macroList.get(editingMacroIndex);
                if (chr >= 32 && chr != 127) { // printable character
                    m.setCommand(m.getCommand() + chr);
                    com.mooclient.util.MooConfig.save();
                    return true;
                }
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (currentView == View.HUB && draggingWidget != null) {
            int rawX = (int) mouseX - dragOffsetX;
            int rawY = (int) mouseY - dragOffsetY;
            boolean snapping = MooClientSettings.isHudSnapping();
            int accent = MooClientSettings.getAccentColor();
            List<WidgetRect> others = getOtherActiveWidgetRects(draggingWidget);

            if ("FPS".equals(draggingWidget)) {
                MooHudPositionHelper.SnapResult res = MooHudPositionHelper.calculateSmartSnap(
                        rawX, rawY, FpsModule.width, FpsModule.height, this.width, this.height, others,
                        snapping, accent);
                this.activeGuideLines = res.guideLines;
                FpsModule.position.setFromScreenCoords(res.snappedX, res.snappedY, FpsModule.width, FpsModule.height,
                        this.width, this.height);
                FpsModule.posX = res.snappedX;
                FpsModule.posY = res.snappedY;
                return true;
            } else if ("SPRINT".equals(draggingWidget)) {
                MooHudPositionHelper.SnapResult res = MooHudPositionHelper.calculateSmartSnap(
                        rawX, rawY, ToggleSprintModule.width, ToggleSprintModule.height, this.width,
                        this.height, others, snapping, accent);
                this.activeGuideLines = res.guideLines;
                ToggleSprintModule.position.setFromScreenCoords(res.snappedX, res.snappedY, ToggleSprintModule.width,
                        ToggleSprintModule.height, this.width, this.height);
                ToggleSprintModule.posX = res.snappedX;
                ToggleSprintModule.posY = res.snappedY;
                return true;
            } else if ("POTIONS".equals(draggingWidget)) {
                MooHudPositionHelper.SnapResult res = MooHudPositionHelper.calculateSmartSnap(
                        rawX, rawY, PotionEffectsModule.width, PotionEffectsModule.height, this.width,
                        this.height, others, snapping, accent);
                this.activeGuideLines = res.guideLines;
                PotionEffectsModule.position.setFromScreenCoords(res.snappedX, res.snappedY, PotionEffectsModule.width,
                        PotionEffectsModule.height, this.width, this.height);
                PotionEffectsModule.posX = res.snappedX;
                PotionEffectsModule.posY = res.snappedY;
                return true;
            } else if ("PING".equals(draggingWidget)) {
                MooHudPositionHelper.SnapResult res = MooHudPositionHelper.calculateSmartSnap(
                        rawX, rawY, PingModule.width, PingModule.height, this.width, this.height, others,
                        snapping, accent);
                this.activeGuideLines = res.guideLines;
                PingModule.position.setFromScreenCoords(res.snappedX, res.snappedY,
                        PingModule.width, PingModule.height, this.width, this.height);
                PingModule.posX = res.snappedX;
                PingModule.posY = res.snappedY;
                return true;
            } else if ("CPS".equals(draggingWidget)) {
                MooHudPositionHelper.SnapResult res = MooHudPositionHelper.calculateSmartSnap(
                        rawX, rawY, CpsModule.width, CpsModule.height, this.width, this.height, others,
                        snapping, accent);
                this.activeGuideLines = res.guideLines;
                CpsModule.position.setFromScreenCoords(res.snappedX, res.snappedY,
                        CpsModule.width, CpsModule.height, this.width, this.height);
                CpsModule.posX = res.snappedX;
                CpsModule.posY = res.snappedY;
                return true;
            } else if ("SCOREBOARD".equals(draggingWidget)) {
                MooHudPositionHelper.SnapResult res = MooHudPositionHelper.calculateSmartSnap(
                        rawX, rawY, ScoreboardModule.width, ScoreboardModule.height, this.width, this.height, others,
                        snapping, accent);
                this.activeGuideLines = res.guideLines;
                ScoreboardModule.position.setFromScreenCoords(res.snappedX, res.snappedY,
                        ScoreboardModule.width, ScoreboardModule.height, this.width, this.height);
                ScoreboardModule.posX = res.snappedX;
                ScoreboardModule.posY = res.snappedY;
                return true;
            }
        }
        if ((currentView == View.SETTINGS || currentView == View.OPTIONS) && draggingSlider >= 0) {
            handleSliderDrag(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingWidget != null) {
            MooConfig.save();
        }
        draggingWidget = null;
        draggingSlider = -1;
        if (activeGuideLines != null) {
            activeGuideLines.clear();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // If searching in Mods view
        if (currentView == View.MODS && searching) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchFilter.isEmpty()) {
                    searchFilter = searchFilter.substring(0, searchFilter.length() - 1);
                    scrollY = 0;
                }
                return true;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                searching = false;
                return true;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V
                    && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                try {
                    if (this.client != null && this.client.keyboard != null) {
                        String clip = this.client.keyboard.getClipboard();
                        if (clip != null && !clip.isEmpty()) {
                            searchFilter += clip.trim();
                            scrollY = 0;
                        }
                    }
                } catch (Exception ignored) {
                }
                return true;
            }
            // Consume key when searching so inventory key (E) doesn't close the screen
            return true;
        }

        // If editing macro command text
        if (currentView == View.OPTIONS && editingMacroIndex >= 0) {
            java.util.List<com.mooclient.module.modules.MacroModule.MacroEntry> macroList = com.mooclient.module.modules.MacroModule
                    .getMacros();
            if (editingMacroIndex < macroList.size()) {
                com.mooclient.module.modules.MacroModule.MacroEntry m = macroList.get(editingMacroIndex);
                if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                    String cmd = m.getCommand();
                    if (cmd != null && !cmd.isEmpty()) {
                        m.setCommand(cmd.substring(0, cmd.length() - 1));
                        com.mooclient.util.MooConfig.save();
                    }
                    return true;
                } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                        || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                        || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                    editingMacroIndex = -1;
                    com.mooclient.util.MooConfig.save();
                    playClickSound();
                    return true;
                } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V
                        && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                    try {
                        if (this.client != null && this.client.keyboard != null) {
                            String clip = this.client.keyboard.getClipboard();
                            if (clip != null && !clip.isEmpty()) {
                                m.setCommand(m.getCommand() + clip.trim());
                                com.mooclient.util.MooConfig.save();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    return true;
                }
            }
            // Consume key when editing macro text
            return true;
        }

        // If listening for macro keybind
        if (currentView == View.OPTIONS && listeningMacroIndex >= 0) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                listeningMacroIndex = -1;
                return true;
            }

            java.util.List<com.mooclient.module.modules.MacroModule.MacroEntry> macroList = com.mooclient.module.modules.MacroModule
                    .getMacros();
            if (listeningMacroIndex < macroList.size()) {
                com.mooclient.module.modules.MacroModule.MacroEntry m = macroList.get(listeningMacroIndex);
                String kName;
                try {
                    kName = net.minecraft.client.util.InputUtil.fromKeyCode(keyCode, scanCode).getLocalizedText()
                            .getString().toUpperCase();
                } catch (Exception e) {
                    kName = "KEY " + keyCode;
                }
                if (kName == null || kName.isEmpty() || kName.startsWith("KEY.")) {
                    kName = "KEY " + keyCode;
                }
                m.setKeyCode(keyCode);
                m.setKeyName(kName);
                m.setMouseButton(false);
                com.mooclient.util.MooConfig.save();
                listeningMacroIndex = -1;
                playClickSound();
                return true;
            }
            return true;
        }

        // If listening for new keybind in Sprint, Freelook, Zoom options
        if (currentView == View.OPTIONS && listeningForKeybind) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listeningForKeybind = false;
                return true;
            }

            String keyName;
            try {
                keyName = InputUtil.fromKeyCode(keyCode, scanCode).getLocalizedText().getString().toUpperCase();
            } catch (Exception e) {
                keyName = "KEY " + keyCode;
            }

            if (keyName == null || keyName.isEmpty() || keyName.startsWith("KEY.")) {
                keyName = "KEY " + keyCode;
            }

            if (selectedModule != null && selectedModule.getName().equalsIgnoreCase("Freelook")) {
                FreelookModule.setKeybind(keyCode, keyName, false);
            } else if (selectedModule != null && selectedModule.getName().equalsIgnoreCase("Zoom")) {
                com.mooclient.module.modules.ZoomModule.setKeybind(keyCode, keyName, false);
            } else if (selectedModule != null && selectedModule.getName().equalsIgnoreCase("Waypoints")) {
                com.mooclient.module.modules.WaypointsModule.setKeybind(keyCode, keyName, false);
            } else if (selectedModule != null && (selectedModule.getName().equalsIgnoreCase("Emotki") || selectedModule.getName().equalsIgnoreCase("Emotes"))) {
                if (listeningEmoteSlot == 1) {
                    com.mooclient.module.modules.EmotesModule.setWheelKeybind(keyCode, keyName, false);
                } else {
                    com.mooclient.module.modules.EmotesModule.setKeybind(keyCode, keyName, false);
                }
            } else {
                ToggleSprintModule.setKeybind(keyCode, keyName, false);
            }

            com.mooclient.util.MooConfig.save();
            listeningForKeybind = false;
            playClickSound();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (currentView == View.OPTIONS || currentView == View.SETTINGS) {
                this.currentView = View.MODS;
                return true;
            } else if (currentView == View.MODS) {
                this.currentView = View.HUB;
                return true;
            } else {
                this.close();
                return true;
            }
        }

        // Prevent inventory key (e.g. E) from closing MooClientScreen
        if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            return false;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void close() {
        com.mooclient.util.MooConfig.save();
        super.close();
    }
}
