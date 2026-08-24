package com.mooclient.gui;

import com.mooclient.module.modules.WaypointsModule;
import com.mooclient.util.MooClientSettings;
import com.mooclient.util.MooLanguage;
import com.mooclient.waypoint.Waypoint;
import com.mooclient.waypoint.WaypointManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Lunar Client inspired Waypoint Manager Screen.
 * Left Side: Toggle filters (All Dimensions, All Servers), Search bar, and scrollable list of waypoints with quick action buttons.
 * Right Side: Create new waypoint with custom name, coordinates, dimension, preset palette, and custom RGB sliders.
 */
public class MooWaypointScreen extends Screen {

    private static final Identifier COW_LOGO = Identifier.of("minecraft", "icons/icon_128x128.png");

    // Colors
    private static final int COLOR_PANEL_BG = 0xF4111116;
    private static final int COLOR_PANEL_BORDER = 0x44FFFFFF;
    private static final int COLOR_CARD_BG = 0x88181824;
    private static final int COLOR_CARD_HOVER = 0xCC222232;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MUTED = 0xFFA0A0AB;
    private static final int COLOR_INPUT_BG = 0x990A0A10;
    private static final int COLOR_INPUT_BORDER = 0x44FFFFFF;

    // Palette presets for waypoints
    private static final int[] COLOR_PRESETS = new int[]{
            0xFF5555, // Red
            0x55FF55, // Lime Green
            0x55FFFF, // Cyan
            0xFFFF55, // Yellow
            0xFF55FF, // Purple / Magenta
            0xFFAA00, // Orange
            0x5555FF, // Blue
            0xFFFFFF  // White
    };

    // State for creating or editing a waypoint
    private String editingWaypointId = null; // null = create mode, non-null = edit mode
    private String newName = "";
    private String newX = "0";
    private String newY = "64";
    private String newZ = "0";
    private String newDimension = "minecraft:overworld";
    private int selectedColorIndex = 2; // -1 for custom RGB, 0..7 for presets
    private int customR = 85;
    private int customG = 255;
    private int customB = 255;
    private int draggingSlider = -1; // -1 = none, 0 = R, 1 = G, 2 = B

    // Active focused input: 0 = None, 1 = Search, 2 = Name, 3 = X, 4 = Y, 5 = Z
    private int activeInput = 2;
    private String searchFilter = "";
    private double scrollY = 0;

    public MooWaypointScreen() {
        super(Text.literal("Moo Client Waypoints"));
    }

    @Override
    protected void init() {
        super.init();
        resetToCreateMode();
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
        int dimColor = MooClientSettings.getBackgroundDimColor();
        context.fillGradient(0, 0, this.width, this.height, dimColor, dimColor);

        int panelW = 620;
        int panelH = 340;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        // Main Background Panel
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL_BG);
        drawBorder(context, panelX, panelY, panelW, panelH, COLOR_PANEL_BORDER);

        // Header
        int headerH = 44;
        int backX = panelX + 14;
        int backY = panelY + 12;
        int backW = 74;
        int backH = 22;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        int backTextColor = backHover ? COLOR_TEXT_WHITE : COLOR_TEXT_MUTED;
        context.drawTextWithShadow(this.textRenderer, MooLanguage.get("back"), backX, backY + 3, backTextColor);

        // Header Logo & Title
        int logoSize = 22;
        context.drawTexture(RenderLayer::getGuiTextured, COW_LOGO, panelX + 96, panelY + 12, 0.0f, 0.0f, logoSize, logoSize, logoSize, logoSize);
        context.drawTextWithShadow(this.textRenderer, "PUNKTY NAWIGACYJNE • WAYPOINTS", panelX + 124, panelY + 12, COLOR_TEXT_WHITE);
        context.drawTextWithShadow(this.textRenderer, "Zarządzaj punktami i twórz nowe cele nawigacji", panelX + 124, panelY + 24, COLOR_TEXT_MUTED);

        // Divider Line
        context.fill(panelX + 14, panelY + headerH, panelX + panelW - 14, panelY + headerH + 1, 0x22FFFFFF);

        // Split Panels
        int leftW = 320;
        int leftH = panelH - headerH - 18;
        int leftX = panelX + 14;
        int leftY = panelY + headerH + 10;

        int rightW = panelW - leftW - 38;
        int rightH = leftH;
        int rightX = leftX + leftW + 12;
        int rightY = leftY;

        // Vertical divider between left and right panel
        context.fill(rightX - 6, leftY, rightX - 5, leftY + leftH, 0x22FFFFFF);

        // Render Left Panel (Filters, Search, Waypoints List)
        renderWaypointsList(context, leftX, leftY, leftW, leftH, mouseX, mouseY);

        // Render Right Panel (Create Waypoint Form with Custom RGB Color)
        renderCreateForm(context, rightX, rightY, rightW, rightH, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Renders Left Panel: Filter toggles (All Dimensions, All Servers) + Search bar + scrollable list of waypoints.
     */
    private void renderWaypointsList(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        // --- 1. Filter Toggles Row (Above Search) ---
        int togH = 18;
        int togW = (w - 6) / 2;

        // Toggle 1: All Dimensions
        int dimTogX = x;
        boolean allDims = WaypointsModule.isShowAllDimensions();
        boolean dimHover = mouseX >= dimTogX && mouseX <= dimTogX + togW && mouseY >= y && mouseY <= y + togH;
        int dimBg = allDims ? MooClientSettings.getAccentGlowColor(0x35) : (dimHover ? 0x66252535 : 0x44141420);
        int dimBorder = allDims ? MooClientSettings.getAccentColor() : (dimHover ? 0x66FFFFFF : 0x22FFFFFF);
        context.fill(dimTogX, y, dimTogX + togW, y + togH, dimBg);
        drawBorder(context, dimTogX, y, togW, togH, dimBorder);
        String dimText = allDims ? "🌐 Wymiary: WSZYSTKIE" : "🌐 Wymiar: BIEŻĄCY";
        int dimTextCol = allDims ? COLOR_TEXT_WHITE : (dimHover ? COLOR_TEXT_WHITE : COLOR_TEXT_MUTED);
        drawCenteredText(context, dimText, dimTogX + togW / 2, y + 5, dimTextCol);

        // Toggle 2: All Servers
        int srvTogX = x + togW + 6;
        boolean allSrv = WaypointsModule.isShowAllServers();
        boolean srvHover = mouseX >= srvTogX && mouseX <= srvTogX + togW && mouseY >= y && mouseY <= y + togH;
        int srvBg = allSrv ? MooClientSettings.getAccentGlowColor(0x35) : (srvHover ? 0x66252535 : 0x44141420);
        int srvBorder = allSrv ? MooClientSettings.getAccentColor() : (srvHover ? 0x66FFFFFF : 0x22FFFFFF);
        context.fill(srvTogX, y, srvTogX + togW, y + togH, srvBg);
        drawBorder(context, srvTogX, y, togW, togH, srvBorder);
        String srvText = allSrv ? "🌍 Serwery: WSZYSTKIE" : "🌍 Serwer: BIEŻĄCY";
        int srvTextCol = allSrv ? COLOR_TEXT_WHITE : (srvHover ? COLOR_TEXT_WHITE : COLOR_TEXT_MUTED);
        drawCenteredText(context, srvText, srvTogX + togW / 2, y + 5, srvTextCol);

        // --- 2. Search bar ---
        int searchH = 18;
        int searchY = y + togH + 5;
        boolean searchFocused = (activeInput == 1);
        int searchBorder = searchFocused ? MooClientSettings.getAccentColor() : COLOR_INPUT_BORDER;

        context.fill(x, searchY, x + w, searchY + searchH, COLOR_INPUT_BG);
        drawBorder(context, x, searchY, w, searchH, searchBorder);

        String searchDisp = searchFilter.isEmpty() ? (searchFocused ? "" : "🔍 Szukaj punktu...") : searchFilter;
        int searchColor = searchFilter.isEmpty() && !searchFocused ? COLOR_TEXT_MUTED : COLOR_TEXT_WHITE;
        context.drawTextWithShadow(this.textRenderer, searchDisp, x + 8, searchY + 5, searchColor);

        // --- 3. Waypoints List Area ---
        int listY = searchY + searchH + 6;
        int listH = h - (listY - y);

        List<Waypoint> allWps = (this.client != null) ? WaypointManager.getInstance().getWaypointsForCurrentWorld(this.client) : WaypointManager.getInstance().getAllWaypoints();
        List<Waypoint> filteredWps;
        if (searchFilter == null || searchFilter.trim().isEmpty()) {
            filteredWps = allWps;
        } else {
            String query = searchFilter.trim().toLowerCase();
            filteredWps = allWps.stream().filter(wp -> wp.getName().toLowerCase().contains(query)).toList();
        }

        if (filteredWps.isEmpty()) {
            drawCenteredText(context, "Brak waypointów w tym świecie.", x + w / 2, listY + 50, COLOR_TEXT_MUTED);
            drawCenteredText(context, "Stwórz nowy punkt po prawej stronie!", x + w / 2, listY + 64, 0x88FFFFFF);
            return;
        }

        int cardH = 38;
        int cardGap = 6;
        int maxScroll = Math.max(0, filteredWps.size() * (cardH + cardGap) - listH);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        // Enable scissor for smooth scroll clipping
        context.enableScissor(x, listY, x + w, listY + listH);

        for (int i = 0; i < filteredWps.size(); i++) {
            Waypoint wp = filteredWps.get(i);
            int cardY = listY + i * (cardH + cardGap) - (int) scrollY;

            if (cardY + cardH < listY || cardY > listY + listH) continue;

            boolean isEditing = wp.getId().equals(editingWaypointId);
            boolean cardHover = mouseX >= x && mouseX <= x + w && mouseY >= cardY && mouseY <= cardY + cardH;
            int cardBg = isEditing ? 0xDD252538 : (cardHover ? COLOR_CARD_HOVER : COLOR_CARD_BG);
            int cardBorder = isEditing ? MooClientSettings.getAccentColor() : (wp.isVisible() ? (cardHover ? 0x88FFFFFF : 0x33FFFFFF) : 0x22555566);

            context.fill(x, cardY, x + w, cardY + cardH, cardBg);
            drawBorder(context, x, cardY, w, cardH, cardBorder);

            // Left color strip
            context.fill(x + 2, cardY + 2, x + 5, cardY + cardH - 2, wp.getColor() | 0xFF000000);

            // Distance calculation
            int dist = 0;
            if (this.client != null && this.client.player != null) {
                double dx = wp.getX() - this.client.player.getX();
                double dy = wp.getY() - this.client.player.getY();
                double dz = wp.getZ() - this.client.player.getZ();
                dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            }

            // Name & Distance
            String nameText = wp.getName();
            int nameColor = wp.isVisible() ? COLOR_TEXT_WHITE : COLOR_TEXT_MUTED;
            context.drawTextWithShadow(this.textRenderer, nameText, x + 10, cardY + 6, nameColor);

            String distText = dist + "m";
            int distW = this.textRenderer.getWidth(distText);
            int distBadgeX = x + 12 + this.textRenderer.getWidth(nameText);
            if (distBadgeX + distW + 6 < x + w - 80) {
                context.fill(distBadgeX, cardY + 5, distBadgeX + distW + 6, cardY + 16, 0x44000000);
                context.drawTextWithShadow(this.textRenderer, distText, distBadgeX + 3, cardY + 6, MooClientSettings.getAccentColor());
            }

            // Coordinates & Dimension Subtitle
            String subText = wp.getFormattedCoords() + " • " + wp.getDimensionDisplayName();
            if (WaypointsModule.isShowAllServers() && !"global".equalsIgnoreCase(wp.getServerOrWorld())) {
                subText += " • " + wp.getServerOrWorld();
            }
            context.drawTextWithShadow(this.textRenderer, subText, x + 10, cardY + 20, COLOR_TEXT_MUTED);

            // Right Action Buttons:
            // 1. Toggle Visibility (Eye)
            int btnSize = 18;
            int visBtnX = x + w - 50;
            int visBtnY = cardY + 10;
            boolean visHover = mouseX >= visBtnX && mouseX <= visBtnX + btnSize && mouseY >= visBtnY && mouseY <= visBtnY + btnSize;
            int visBg = wp.isVisible() ? (visHover ? 0xCC1E3A2B : 0x880E2318) : (visHover ? 0xCC3A1E1E : 0x88230E0E);
            int visBorder = wp.isVisible() ? MooClientSettings.getAccentColor() : 0x66FF5555;
            context.fill(visBtnX, visBtnY, visBtnX + btnSize, visBtnY + btnSize, visBg);
            drawBorder(context, visBtnX, visBtnY, btnSize, btnSize, visBorder);
            drawCenteredText(context, wp.isVisible() ? "👁" : "🕶", visBtnX + btnSize / 2, visBtnY + 4, COLOR_TEXT_WHITE);

            // 2. Delete Button (Trash)
            int delBtnX = x + w - 26;
            int delBtnY = cardY + 10;
            boolean delHover = mouseX >= delBtnX && mouseX <= delBtnX + btnSize && mouseY >= delBtnY && mouseY <= delBtnY + btnSize;
            int delBg = delHover ? 0xCC441A1A : 0x66220A0A;
            context.fill(delBtnX, delBtnY, delBtnX + btnSize, delBtnY + btnSize, delBg);
            drawBorder(context, delBtnX, delBtnY, btnSize, btnSize, delHover ? 0xFFFF5555 : 0x44FF5555);
            drawCenteredText(context, "✕", delBtnX + btnSize / 2, delBtnY + 4, delHover ? 0xFFFF5555 : 0xFFA0A0AB);
        }

        context.disableScissor();
    }

    /**
     * Renders Right Panel: Form for creating a new waypoint with preset palette and custom RGB sliders.
     */
    private void renderCreateForm(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean isEditing = (editingWaypointId != null);
        String headerTitle = isEditing ? "✎ EDYTUJ PUNKT" : "+ NOWY PUNKT";
        context.drawTextWithShadow(this.textRenderer, headerTitle, x, y + 1, MooClientSettings.getAccentColor());

        if (isEditing) {
            int newBtnW = 74;
            int newBtnH = 13;
            int newBtnX = x + w - newBtnW;
            int newBtnY = y;
            boolean nHover = mouseX >= newBtnX && mouseX <= newBtnX + newBtnW && mouseY >= newBtnY && mouseY <= newBtnY + newBtnH;
            context.fill(newBtnX, newBtnY, newBtnX + newBtnW, newBtnY + newBtnH, nHover ? 0xCC252535 : 0x66141420);
            drawBorder(context, newBtnX, newBtnY, newBtnW, newBtnH, nHover ? 0xAAFFFFFF : 0x44FFFFFF);
            drawCenteredText(context, "+ Nowy punkt", newBtnX + newBtnW / 2, newBtnY + 3, nHover ? COLOR_TEXT_WHITE : COLOR_TEXT_MUTED);
        }

        int curY = y + 14;

        // 1. Name Input
        context.drawTextWithShadow(this.textRenderer, "Nazwa punktu:", x, curY, COLOR_TEXT_WHITE);
        curY += 11;
        int inputH = 18;
        boolean nameFocused = (activeInput == 2);
        context.fill(x, curY, x + w, curY + inputH, COLOR_INPUT_BG);
        drawBorder(context, x, curY, w, inputH, nameFocused ? MooClientSettings.getAccentColor() : COLOR_INPUT_BORDER);
        String nameDisp = newName.isEmpty() ? (nameFocused ? "" : "Wpisz nazwę...") : newName;
        int nameCol = newName.isEmpty() && !nameFocused ? COLOR_TEXT_MUTED : COLOR_TEXT_WHITE;
        context.drawTextWithShadow(this.textRenderer, nameDisp + (nameFocused ? "_" : ""), x + 6, curY + 5, nameCol);

        curY += inputH + 7;

        // 2. Coordinates X, Y, Z + Quick "My Pos" Button
        context.drawTextWithShadow(this.textRenderer, "Współrzędne (X / Y / Z):", x, curY, COLOR_TEXT_WHITE);
        curY += 11;
        int coordW = (w - 8) / 3;

        // X Input
        boolean xFocused = (activeInput == 3);
        context.fill(x, curY, x + coordW, curY + inputH, COLOR_INPUT_BG);
        drawBorder(context, x, curY, coordW, inputH, xFocused ? MooClientSettings.getAccentColor() : COLOR_INPUT_BORDER);
        context.drawTextWithShadow(this.textRenderer, newX + (xFocused ? "_" : ""), x + 4, curY + 5, COLOR_TEXT_WHITE);

        // Y Input
        boolean yFocused = (activeInput == 4);
        int yBoxX = x + coordW + 4;
        context.fill(yBoxX, curY, yBoxX + coordW, curY + inputH, COLOR_INPUT_BG);
        drawBorder(context, yBoxX, curY, coordW, inputH, yFocused ? MooClientSettings.getAccentColor() : COLOR_INPUT_BORDER);
        context.drawTextWithShadow(this.textRenderer, newY + (yFocused ? "_" : ""), yBoxX + 4, curY + 5, COLOR_TEXT_WHITE);

        // Z Input
        boolean zFocused = (activeInput == 5);
        int zBoxX = yBoxX + coordW + 4;
        context.fill(zBoxX, curY, zBoxX + coordW, curY + inputH, COLOR_INPUT_BG);
        drawBorder(context, zBoxX, curY, coordW, inputH, zFocused ? MooClientSettings.getAccentColor() : COLOR_INPUT_BORDER);
        context.drawTextWithShadow(this.textRenderer, newZ + (zFocused ? "_" : ""), zBoxX + 4, curY + 5, COLOR_TEXT_WHITE);

        curY += inputH + 4;

        // Quick "Pobierz moją pozycję" button
        int myPosH = 15;
        boolean myPosHover = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + myPosH;
        context.fill(x, curY, x + w, curY + myPosH, myPosHover ? 0xCC252535 : 0x66141420);
        drawBorder(context, x, curY, w, myPosH, myPosHover ? 0x88FFFFFF : 0x22FFFFFF);
        drawCenteredText(context, "📍 Użyj mojej pozycji", x + w / 2, curY + 3, myPosHover ? COLOR_TEXT_WHITE : COLOR_TEXT_MUTED);

        curY += myPosH + 7;

        // 3. Dimension Selector Tabs
        context.drawTextWithShadow(this.textRenderer, "Wymiar (Dimension):", x, curY, COLOR_TEXT_WHITE);
        curY += 11;
        String[] dims = new String[]{"Overworld", "Nether", "End"};
        String[] dimKeys = new String[]{"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
        int dimTabW = (w - 4) / 3;

        for (int i = 0; i < 3; i++) {
            int tx = x + i * (dimTabW + 2);
            boolean isSel = newDimension.equalsIgnoreCase(dimKeys[i]);
            boolean dHover = mouseX >= tx && mouseX <= tx + dimTabW && mouseY >= curY && mouseY <= curY + 17;

            int bg = isSel ? MooClientSettings.getAccentColor() : (dHover ? 0xCC252535 : 0x66141420);
            int border = isSel ? MooClientSettings.getAccentHoverColor() : (dHover ? 0x88FFFFFF : 0x33FFFFFF);
            int txtCol = isSel ? 0xFF0A2514 : (dHover ? COLOR_TEXT_WHITE : COLOR_TEXT_MUTED);

            context.fill(tx, curY, tx + dimTabW, curY + 17, bg);
            drawBorder(context, tx, curY, dimTabW, 17, border);
            drawCenteredText(context, dims[i], tx + dimTabW / 2, curY + 4, txtCol);
        }

        curY += 17 + 7;

        // 4. Preset Color Palette
        context.drawTextWithShadow(this.textRenderer, "Kolor punktu (Paleta):", x, curY, COLOR_TEXT_WHITE);
        curY += 11;
        int swatchW = (w - 12) / 4;
        int swatchH = 11;

        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            int col = i % 4;
            int row = i / 4;
            int sx = x + col * (swatchW + 4);
            int sy = curY + row * (swatchH + 3);

            boolean isColSel = (selectedColorIndex == i);
            boolean sHover = mouseX >= sx && mouseX <= sx + swatchW && mouseY >= sy && mouseY <= sy + swatchH;

            context.fill(sx, sy, sx + swatchW, sy + swatchH, COLOR_PRESETS[i] | 0xFF000000);
            if (isColSel) {
                drawBorder(context, sx - 1, sy - 1, swatchW + 2, swatchH + 2, 0xFFFFFFFF);
            } else if (sHover) {
                drawBorder(context, sx, sy, swatchW, swatchH, 0x88FFFFFF);
            }
        }

        curY += (swatchH + 3) * 2 + 5;

        // 5. Custom Color RGB Sliders Section
        int activeCol = getSelectedColor();
        String hexStr = String.format("#%06X", (0xFFFFFF & activeCol));

        context.drawTextWithShadow(this.textRenderer, "Własny kolor (RGB):", x, curY + 1, COLOR_TEXT_WHITE);

        // Preview box + HEX
        int prevSize = 12;
        int prevX = x + w - 62;
        context.fill(prevX, curY - 1, prevX + prevSize, curY - 1 + prevSize, activeCol | 0xFF000000);
        drawBorder(context, prevX, curY - 1, prevSize, prevSize, selectedColorIndex == -1 ? 0xFFFFFFFF : 0x55FFFFFF);
        context.drawTextWithShadow(this.textRenderer, hexStr, prevX + prevSize + 4, curY + 1, COLOR_TEXT_MUTED);

        curY += 14;

        int sliderTrackX = x + 34;
        int sliderTrackW = w - 38;
        int sliderH = 7;

        // Slider 1: Red
        renderRgbSlider(context, x, sliderTrackX, curY, sliderTrackW, sliderH, "R:", customR, 0xFFFF4444, mouseX, mouseY);
        curY += 12;

        // Slider 2: Green
        renderRgbSlider(context, x, sliderTrackX, curY, sliderTrackW, sliderH, "G:", customG, 0xFF44FF44, mouseX, mouseY);
        curY += 12;

        // Slider 3: Blue
        renderRgbSlider(context, x, sliderTrackX, curY, sliderTrackW, sliderH, "B:", customB, 0xFF4488FF, mouseX, mouseY);
        curY += 14;

        // 6. Submit Button: [ + STWÓRZ WAYPOINT ] or [ ✓ ZAPISZ ZMIANY ]
        int subBtnH = 22;
        boolean subHover = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + subBtnH;
        int subBg = subHover ? MooClientSettings.getAccentHoverColor() : MooClientSettings.getAccentColor();
        context.fill(x, curY, x + w, curY + subBtnH, subBg);
        drawBorder(context, x, curY, w, subBtnH, 0xFFFFFFFF);
        String btnText = isEditing ? "✓ ZAPISZ ZMIANY" : "+ STWÓRZ PUNKT";
        drawCenteredText(context, btnText, x + w / 2, curY + 6, 0xFF0A2514);
    }

    private void renderRgbSlider(DrawContext context, int labelX, int trackX, int y, int trackW, int trackH, String label, int value, int colorBar, int mouseX, int mouseY) {
        context.drawTextWithShadow(this.textRenderer, label, labelX, y - 1, COLOR_TEXT_WHITE);
        String valStr = String.valueOf(value);
        context.drawTextWithShadow(this.textRenderer, valStr, labelX + 11, y - 1, 0xFFA0A0AB);

        // Track
        context.fill(trackX, y, trackX + trackW, y + trackH, 0x55181824);
        drawBorder(context, trackX, y, trackW, trackH, 0x33FFFFFF);

        // Filled bar
        int fillW = Math.round((value / 255.0f) * trackW);
        if (fillW > 0) {
            context.fill(trackX + 1, y + 1, trackX + fillW, y + trackH - 1, colorBar);
        }

        // Draggable Knob
        int knobX = trackX + fillW - 2;
        boolean hover = mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= y - 3 && mouseY <= y + trackH + 3;
        int knobBorder = hover ? MooClientSettings.getAccentColor() : 0xFF000000;
        context.fill(knobX, y - 2, knobX + 4, y + trackH + 2, COLOR_TEXT_WHITE);
        drawBorder(context, knobX, y - 2, 4, trackH + 4, knobBorder);
    }

    private int getSelectedColor() {
        if (selectedColorIndex >= 0 && selectedColorIndex < COLOR_PRESETS.length) {
            return COLOR_PRESETS[selectedColorIndex];
        }
        return ((customR & 0xFF) << 16) | ((customG & 0xFF) << 8) | (customB & 0xFF);
    }

    private void handleSliderDrag(double mouseX, int rightX, int rightW) {
        int trackX = rightX + 34;
        int trackW = rightW - 38;

        float clamped = (float) Math.max(0, Math.min(trackW, mouseX - trackX));
        int val = Math.round((clamped / (float) trackW) * 255.0f);

        selectedColorIndex = -1; // Switch to custom RGB mode

        if (draggingSlider == 0) {
            customR = val;
        } else if (draggingSlider == 1) {
            customG = val;
        } else if (draggingSlider == 2) {
            customB = val;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int panelW = 620;
            int panelH = 340;
            int panelX = (this.width - panelW) / 2;
            int panelY = (this.height - panelH) / 2;
            int headerH = 44;

            // Back Button Click
            int backX = panelX + 14;
            int backY = panelY + 12;
            int backW = 74;
            int backH = 22;
            if (mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH) {
                playClickSound();
                this.close();
                return true;
            }

            int leftW = 320;
            int leftH = panelH - headerH - 18;
            int leftX = panelX + 14;
            int leftY = panelY + headerH + 10;

            int rightW = panelW - leftW - 38;
            int rightX = leftX + leftW + 12;
            int rightY = leftY;

            // --- 0. Top Right "New Waypoint" Button in Edit Mode ---
            if (editingWaypointId != null) {
                int newBtnW = 74;
                int newBtnH = 13;
                int newBtnX = rightX + rightW - newBtnW;
                int newBtnY = rightY;
                if (mouseX >= newBtnX && mouseX <= newBtnX + newBtnW && mouseY >= newBtnY && mouseY <= newBtnY + newBtnH) {
                    playClickSound();
                    resetToCreateMode();
                    return true;
                }
            }

            // --- 1. Filter Toggles Click (Above Search) ---
            int togH = 18;
            int togW = (leftW - 6) / 2;
            int dimTogX = leftX;
            int srvTogX = leftX + togW + 6;

            if (mouseX >= dimTogX && mouseX <= dimTogX + togW && mouseY >= leftY && mouseY <= leftY + togH) {
                playClickSound();
                WaypointsModule.toggleShowAllDimensions();
                return true;
            }

            if (mouseX >= srvTogX && mouseX <= srvTogX + togW && mouseY >= leftY && mouseY <= leftY + togH) {
                playClickSound();
                WaypointsModule.toggleShowAllServers();
                return true;
            }

            // --- 2. Search Bar Click ---
            int searchH = 18;
            int searchY = leftY + togH + 5;
            if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= searchY && mouseY <= searchY + searchH) {
                activeInput = 1;
                playClickSound();
                return true;
            }

            // --- 3. Left List Waypoint Action Clicks & Card Selection ---
            int listY = searchY + searchH + 6;
            int listH = leftH - (listY - leftY);

            if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= listY && mouseY <= listY + listH) {
                List<Waypoint> allWps = (this.client != null) ? WaypointManager.getInstance().getWaypointsForCurrentWorld(this.client) : WaypointManager.getInstance().getAllWaypoints();
                List<Waypoint> filteredWps;
                if (searchFilter == null || searchFilter.trim().isEmpty()) {
                    filteredWps = allWps;
                } else {
                    String query = searchFilter.trim().toLowerCase();
                    filteredWps = allWps.stream().filter(wp -> wp.getName().toLowerCase().contains(query)).toList();
                }

                int cardH = 38;
                int cardGap = 6;

                for (int i = 0; i < filteredWps.size(); i++) {
                    Waypoint wp = filteredWps.get(i);
                    int cardY = listY + i * (cardH + cardGap) - (int) scrollY;

                    if (cardY + cardH < listY || cardY > listY + listH) continue;

                    int btnSize = 18;
                    int visBtnX = leftX + leftW - 50;
                    int delBtnX = leftX + leftW - 26;
                    int btnY = cardY + 10;

                    // Toggle Visibility
                    if (mouseX >= visBtnX && mouseX <= visBtnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                        playClickSound();
                        WaypointManager.getInstance().toggleWaypoint(wp.getId());
                        return true;
                    }

                    // Delete Waypoint
                    if (mouseX >= delBtnX && mouseX <= delBtnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                        playClickSound();
                        if (wp.getId().equals(editingWaypointId)) {
                            resetToCreateMode();
                        }
                        WaypointManager.getInstance().removeWaypoint(wp.getId());
                        return true;
                    }

                    // Select Waypoint for Editing (Clicking on card)
                    if (mouseX >= leftX && mouseX < visBtnX && mouseY >= cardY && mouseY <= cardY + cardH) {
                        playClickSound();
                        loadWaypointForEditing(wp);
                        return true;
                    }
                }
            }

            // --- 4. Right Panel Form Inputs ---
            int curY = rightY + 14 + 11;
            int inputH = 18;

            // Click Name Field
            if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= curY && mouseY <= curY + inputH) {
                activeInput = 2;
                playClickSound();
                return true;
            }

            curY += inputH + 7 + 11;
            int coordW = (rightW - 8) / 3;

            // Click X Field
            if (mouseX >= rightX && mouseX <= rightX + coordW && mouseY >= curY && mouseY <= curY + inputH) {
                activeInput = 3;
                playClickSound();
                return true;
            }

            // Click Y Field
            int yBoxX = rightX + coordW + 4;
            if (mouseX >= yBoxX && mouseX <= yBoxX + coordW && mouseY >= curY && mouseY <= curY + inputH) {
                activeInput = 4;
                playClickSound();
                return true;
            }

            // Click Z Field
            int zBoxX = yBoxX + coordW + 4;
            if (mouseX >= zBoxX && mouseX <= zBoxX + coordW && mouseY >= curY && mouseY <= curY + inputH) {
                activeInput = 5;
                playClickSound();
                return true;
            }

            curY += inputH + 4;

            // Click "Użyj mojej pozycji" button
            int myPosH = 15;
            if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= curY && mouseY <= curY + myPosH) {
                playClickSound();
                if (this.client != null && this.client.player != null) {
                    this.newX = String.valueOf((int) Math.round(this.client.player.getX()));
                    this.newY = String.valueOf((int) Math.round(this.client.player.getY()));
                    this.newZ = String.valueOf((int) Math.round(this.client.player.getZ()));
                    this.newDimension = WaypointManager.getCurrentDimension(this.client);
                }
                return true;
            }

            curY += myPosH + 7 + 11;

            // Click Dimension Tabs
            String[] dimKeys = new String[]{"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
            int dimTabW = (rightW - 4) / 3;
            for (int i = 0; i < 3; i++) {
                int tx = rightX + i * (dimTabW + 2);
                if (mouseX >= tx && mouseX <= tx + dimTabW && mouseY >= curY && mouseY <= curY + 17) {
                    playClickSound();
                    this.newDimension = dimKeys[i];
                    return true;
                }
            }

            curY += 17 + 7 + 11;

            // Click Color Preset Swatches
            int swatchW = (rightW - 12) / 4;
            int swatchH = 11;
            for (int i = 0; i < COLOR_PRESETS.length; i++) {
                int col = i % 4;
                int row = i / 4;
                int sx = rightX + col * (swatchW + 4);
                int sy = curY + row * (swatchH + 3);

                if (mouseX >= sx && mouseX <= sx + swatchW && mouseY >= sy && mouseY <= sy + swatchH) {
                    playClickSound();
                    this.selectedColorIndex = i;
                    int c = COLOR_PRESETS[i];
                    this.customR = (c >> 16) & 0xFF;
                    this.customG = (c >> 8) & 0xFF;
                    this.customB = c & 0xFF;
                    return true;
                }
            }

            curY += (swatchH + 3) * 2 + 5 + 14;

            // Click / Drag RGB Sliders
            int sliderH = 7;
            int rSliderY = curY;
            int gSliderY = curY + 12;
            int bSliderY = curY + 24;

            if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= rSliderY - 3 && mouseY <= rSliderY + sliderH + 3) {
                draggingSlider = 0;
                handleSliderDrag(mouseX, rightX, rightW);
                playClickSound();
                return true;
            }

            if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= gSliderY - 3 && mouseY <= gSliderY + sliderH + 3) {
                draggingSlider = 1;
                handleSliderDrag(mouseX, rightX, rightW);
                playClickSound();
                return true;
            }

            if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= bSliderY - 3 && mouseY <= bSliderY + sliderH + 3) {
                draggingSlider = 2;
                handleSliderDrag(mouseX, rightX, rightW);
                playClickSound();
                return true;
            }

            curY += 24 + 14;

            // Click Create / Save Waypoint Button
            int subBtnH = 22;
            if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= curY && mouseY <= curY + subBtnH) {
                if (editingWaypointId != null) {
                    saveEditedWaypoint();
                } else {
                    submitNewWaypoint();
                }
                return true;
            }

            // Clicked outside inputs -> unfocus
            activeInput = 0;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingSlider >= 0) {
            int panelW = 620;
            int panelX = (this.width - panelW) / 2;
            int leftW = 320;
            int rightW = panelW - leftW - 38;
            int rightX = panelX + 14 + leftW + 12;

            handleSliderDrag(mouseX, rightX, rightW);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingSlider = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void loadWaypointForEditing(Waypoint wp) {
        if (wp == null) return;
        this.editingWaypointId = wp.getId();
        this.newName = wp.getName();
        this.newX = String.format(java.util.Locale.ROOT, "%.0f", wp.getX());
        this.newY = String.format(java.util.Locale.ROOT, "%.0f", wp.getY());
        this.newZ = String.format(java.util.Locale.ROOT, "%.0f", wp.getZ());
        this.newDimension = wp.getDimension() != null ? wp.getDimension() : "minecraft:overworld";

        int color = wp.getColor();
        this.customR = (color >> 16) & 0xFF;
        this.customG = (color >> 8) & 0xFF;
        this.customB = color & 0xFF;

        this.selectedColorIndex = -1;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if ((COLOR_PRESETS[i] & 0xFFFFFF) == (color & 0xFFFFFF)) {
                this.selectedColorIndex = i;
                break;
            }
        }
        this.activeInput = 2; // Focus name input
    }

    private void saveEditedWaypoint() {
        if (editingWaypointId == null) return;

        Waypoint wp = null;
        for (Waypoint w : WaypointManager.getInstance().getAllWaypoints()) {
            if (w.getId().equals(editingWaypointId)) {
                wp = w;
                break;
            }
        }

        if (wp != null) {
            String name = newName.trim().isEmpty() ? wp.getName() : newName.trim();
            double x = wp.getX();
            double y = wp.getY();
            double z = wp.getZ();

            try { x = Double.parseDouble(newX.trim()); } catch (Exception ignored) {}
            try { y = Double.parseDouble(newY.trim()); } catch (Exception ignored) {}
            try { z = Double.parseDouble(newZ.trim()); } catch (Exception ignored) {}

            int color = getSelectedColor();

            wp.setName(name);
            wp.setX(x);
            wp.setY(y);
            wp.setZ(z);
            wp.setDimension(newDimension);
            wp.setColor(color);

            WaypointManager.getInstance().invalidateCache();
            WaypointManager.getInstance().save();
            playClickSound();
        }
    }

    private void resetToCreateMode() {
        this.editingWaypointId = null;
        this.newName = "";
        if (this.client != null && this.client.player != null) {
            this.newX = String.valueOf((int) Math.round(this.client.player.getX()));
            this.newY = String.valueOf((int) Math.round(this.client.player.getY()));
            this.newZ = String.valueOf((int) Math.round(this.client.player.getZ()));
            this.newDimension = WaypointManager.getCurrentDimension(this.client);
        }
        this.selectedColorIndex = 2;
        int c = COLOR_PRESETS[2];
        this.customR = (c >> 16) & 0xFF;
        this.customG = (c >> 8) & 0xFF;
        this.customB = c & 0xFF;
        this.activeInput = 2;
    }

    private void submitNewWaypoint() {
        String name = newName.trim().isEmpty() ? "Punkt #" + (WaypointManager.getInstance().getAllWaypoints().size() + 1) : newName.trim();
        double x = 0;
        double y = 64;
        double z = 0;

        try { x = Double.parseDouble(newX.trim()); } catch (Exception ignored) {}
        try { y = Double.parseDouble(newY.trim()); } catch (Exception ignored) {}
        try { z = Double.parseDouble(newZ.trim()); } catch (Exception ignored) {}

        int color = getSelectedColor();
        String server = (this.client != null) ? WaypointManager.getCurrentServerOrWorld(this.client) : "global";

        Waypoint wp = new Waypoint(name, x, y, z, newDimension, server, color, false);
        WaypointManager.getInstance().addWaypoint(wp);

        playClickSound();

        // Reset input for the next waypoint
        this.newName = "";
        this.activeInput = 2;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelW = 620;
        int panelH = 340;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        int leftW = 320;
        int leftX = panelX + 14;

        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollY -= verticalAmount * 18.0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr >= 32 && chr != 127) {
            if (activeInput == 1) {
                searchFilter += chr;
                scrollY = 0;
                return true;
            } else if (activeInput == 2) {
                newName += chr;
                return true;
            } else if (activeInput == 3) {
                if (Character.isDigit(chr) || chr == '-' || chr == '.') newX += chr;
                return true;
            } else if (activeInput == 4) {
                if (Character.isDigit(chr) || chr == '-' || chr == '.') newY += chr;
                return true;
            } else if (activeInput == 5) {
                if (Character.isDigit(chr) || chr == '-' || chr == '.') newZ += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeInput > 0) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (activeInput == 1 && !searchFilter.isEmpty()) {
                    searchFilter = searchFilter.substring(0, searchFilter.length() - 1);
                    scrollY = 0;
                    return true;
                } else if (activeInput == 2 && !newName.isEmpty()) {
                    newName = newName.substring(0, newName.length() - 1);
                    return true;
                } else if (activeInput == 3 && !newX.isEmpty()) {
                    newX = newX.substring(0, newX.length() - 1);
                    return true;
                } else if (activeInput == 4 && !newY.isEmpty()) {
                    newY = newY.substring(0, newY.length() - 1);
                    return true;
                } else if (activeInput == 5 && !newZ.isEmpty()) {
                    newZ = newZ.substring(0, newZ.length() - 1);
                    return true;
                }
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (activeInput == 2 || activeInput == 3 || activeInput == 4 || activeInput == 5) {
                    if (editingWaypointId != null) {
                        saveEditedWaypoint();
                    } else {
                        submitNewWaypoint();
                    }
                } else {
                    activeInput = 0;
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_TAB) {
                activeInput = (activeInput % 5) + 1;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                activeInput = 0;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                try {
                    if (this.client != null && this.client.keyboard != null) {
                        String clip = this.client.keyboard.getClipboard();
                        if (clip != null && !clip.isEmpty()) {
                            if (activeInput == 1) searchFilter += clip.trim();
                            else if (activeInput == 2) newName += clip.trim();
                            else if (activeInput == 3) newX += clip.trim();
                            else if (activeInput == 4) newY += clip.trim();
                            else if (activeInput == 5) newZ += clip.trim();
                        }
                    }
                } catch (Exception ignored) {}
                return true;
            }

            // Consume all other keys when typing so inventory key (E) doesn't close the GUI
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.close();
            return true;
        }

        // Check if user pressed waypoint keybind to toggle/close
        if (keyCode == com.mooclient.module.modules.WaypointsModule.getKeyCode()) {
            this.close();
            return true;
        }

        // Prevent inventory key (e.g. E) from closing the waypoint screen
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

    private void drawCenteredText(DrawContext context, String text, int centerX, int y, int color) {
        int width = this.textRenderer.getWidth(text);
        context.drawTextWithShadow(this.textRenderer, text, centerX - width / 2, y, color);
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void playClickSound() {
        if (this.client != null) {
            this.client.getSoundManager().play(
                    net.minecraft.client.sound.PositionedSoundInstance.master(
                            SoundEvents.UI_BUTTON_CLICK, 1.0f
                    )
            );
        }
    }
}
