package com.mooclient.gui;

import com.mooclient.util.MooAccountManager;
import com.mooclient.util.MooClientSettings;
import com.mooclient.util.MooLanguage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom modern monochrome Main Menu for Moo Client.
 * Features an interactive Account Switcher synchronized with Moo Launcher.
 */
public class MooMainMenuScreen extends Screen {

    private static final Identifier COW_LOGO = Identifier.of("minecraft", "icons/icon_128x128.png");

    // Colors matching the launcher theme
    private static final int COLOR_CARD_BG = 0x80141418;
    private static final int COLOR_CARD_HOVER = 0xD0202026;
    private static final int COLOR_BORDER = 0x40FFFFFF;
    private static final int COLOR_BORDER_HOVER = 0xAAFFFFFF;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MUTED = 0xFFA0A0AB;

    private static class MenuButton {
        final String label;
        final String icon;
        final Runnable action;
        int x, y, width, height;
        float hoverAnim = 0.0f;

        MenuButton(String icon, String label, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.action = action;
        }

        boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private final List<MenuButton> buttons = new ArrayList<>();
    private float openAnim = 0.0f;

    // Account Switcher State
    private boolean accountsPopupOpen = false;

    public MooMainMenuScreen() {
        super(Text.literal("Moo Client Main Menu"));
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();

        // Refresh accounts on menu open
        MooAccountManager.getInstance().load();

        buttons.add(new MenuButton("▶", MooLanguage.get("singleplayer"), () -> {
            if (this.client != null) this.client.setScreen(new SelectWorldScreen(this));
        }));

        buttons.add(new MenuButton("◈", MooLanguage.get("multiplayer"), () -> {
            if (this.client != null) this.client.setScreen(new MultiplayerScreen(this));
        }));

        buttons.add(new MenuButton("⚙", MooLanguage.get("settings"), () -> {
            if (this.client != null) this.client.setScreen(new OptionsScreen(this, this.client.options));
        }));

        buttons.add(new MenuButton("✕", MooLanguage.get("quit"), () -> {
            if (this.client != null) this.client.scheduleStop();
        }));

        int btnWidth = 220;
        int btnHeight = 28;
        int spacing = 7;
        int startY = this.height / 2 - 5;
        int startX = (this.width - btnWidth) / 2;

        for (int i = 0; i < buttons.size(); i++) {
            MenuButton b = buttons.get(i);
            b.width = btnWidth;
            b.height = btnHeight;
            b.x = startX;
            b.y = startY + i * (btnHeight + spacing);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        openAnim = Math.min(1.0f, openAnim + delta * 0.08f);

        // Render vanilla rotating background panorama if available
        super.render(context, mouseX, mouseY, delta);

        // Dark modern vignette gradient overlay
        context.fillGradient(0, 0, this.width, this.height, 0xEE09090C, 0xDD0D0D12);

        int centerX = this.width / 2;

        // Render Logo (Cow Icon)
        int logoSize = 52;
        int logoY = this.height / 2 - 88;
        context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, COW_LOGO, centerX - logoSize / 2, logoY, 0.0f, 0.0f, logoSize, logoSize, logoSize, logoSize);

        // Title: MOO CLIENT
        String title = "MOO CLIENT";
        int titleWidth = this.textRenderer.getWidth(title);
        context.drawTextWithShadow(this.textRenderer, title, centerX - titleWidth / 2, logoY + logoSize + 6, COLOR_TEXT_PRIMARY);

        // Render Menu Buttons
        for (MenuButton b : buttons) {
            boolean hovered = b.isHovered(mouseX, mouseY);
            if (hovered) {
                b.hoverAnim = Math.min(1.0f, b.hoverAnim + delta * 0.2f);
            } else {
                b.hoverAnim = Math.max(0.0f, b.hoverAnim - delta * 0.2f);
            }

            int bgCol = interpolateColor(COLOR_CARD_BG, COLOR_CARD_HOVER, b.hoverAnim);
            int borderCol = interpolateColor(COLOR_BORDER, COLOR_BORDER_HOVER, b.hoverAnim);
            int textCol = interpolateColor(COLOR_TEXT_MUTED, COLOR_TEXT_PRIMARY, b.hoverAnim);

            // Button Box
            context.fill(b.x, b.y, b.x + b.width, b.y + b.height, bgCol);
            drawBorder(context, b.x, b.y, b.width, b.height, borderCol);

            // Icon + Label
            String fullLabel = b.icon + "  " + b.label;
            int textX = b.x + (b.width - this.textRenderer.getWidth(fullLabel)) / 2;
            int textY = b.y + (b.height - 8) / 2;
            context.drawTextWithShadow(this.textRenderer, fullLabel, textX, textY, textCol);
        }

        // Render Top-Left Player Card
        renderPlayerCard(context, mouseX, mouseY);

        // Render Accounts Switcher Popup if open
        if (accountsPopupOpen) {
            renderAccountsPopup(context, mouseX, mouseY);
        }

        // Footer: Left
        context.drawTextWithShadow(this.textRenderer, "Moo Client v" + com.mooclient.MooClient.VERSION, 14, this.height - 18, 0x88FFFFFF);
    }

    /**
     * Top-Left Interactive Player Card (matching Screenshot 1)
     */
    private void renderPlayerCard(DrawContext context, int mouseX, int mouseY) {
        if (this.client == null || this.client.getSession() == null) return;

        String username = this.client.getSession().getUsername();
        int nameWidth = this.textRenderer.getWidth(username);
        int cardW = Math.max(160, nameWidth + 72);
        int cardH = 34;
        int cardX = 14;
        int cardY = 12;

        boolean cardHover = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH;

        // Card background & glass border
        int cardBg = cardHover ? 0xDD181822 : 0xAA101016;
        int cardBorder = (cardHover || accountsPopupOpen) ? MooClientSettings.getAccentColor() : 0x33FFFFFF;
        context.fill(cardX, cardY, cardX + cardW, cardY + cardH, cardBg);
        drawBorder(context, cardX, cardY, cardW, cardH, cardBorder);

        // Player Avatar (Skin head)
        try {
            SkinTextures skin = this.client.getSkinProvider().getSkinTextures(this.client.getGameProfile());
            PlayerSkinDrawer.draw(context, skin, cardX + 5, cardY + 5, 24);
        } catch (Throwable ignored) {}

        // Player Name & Status
        context.drawTextWithShadow(this.textRenderer, username, cardX + 34, cardY + 6, COLOR_TEXT_PRIMARY);
        context.drawTextWithShadow(this.textRenderer, "Zalogowano §a✓", cardX + 34, cardY + 18, COLOR_TEXT_MUTED);

        // Signal / Ping Bars (Green)
        int pingX = cardX + cardW - 28;
        int pingY = cardY + cardH - 10;
        int pingColor = MooClientSettings.getAccentColor();
        context.fill(pingX, pingY - 3, pingX + 2, pingY, pingColor);
        context.fill(pingX + 3, pingY - 6, pingX + 5, pingY, pingColor);
        context.fill(pingX + 6, pingY - 9, pingX + 8, pingY, pingColor);
        context.fill(pingX + 9, pingY - 12, pingX + 11, pingY, pingColor);

        // Chevron Arrow indicator
        String arrow = accountsPopupOpen ? "▲" : "▼";
        context.drawTextWithShadow(this.textRenderer, arrow, cardX + cardW - 12, cardY + 13, cardHover ? COLOR_TEXT_PRIMARY : 0xFFA0A0AB);
    }

    /**
     * Accounts Popup Modal (matching Screenshot 2 - Synchronized with Launcher)
     */
    private void renderAccountsPopup(DrawContext context, int mouseX, int mouseY) {
        List<MooAccountManager.Account> accountList = MooAccountManager.getInstance().getAccounts();

        int popupX = 14;
        int popupY = 50;
        int popupW = 230;

        int headerH = 26;
        int itemH = 34;
        int listH = accountList.size() * (itemH + 4);
        int footerH = 34;
        int popupH = headerH + listH + footerH + 6;

        // Popup Container
        context.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xF6111117);
        drawBorder(context, popupX, popupY, popupW, popupH, 0x44FFFFFF);

        // Header: "👤 KONTA PREMIUM" + count badge
        String headerTitle = "👤 " + MooLanguage.get("accounts_title");
        context.drawTextWithShadow(this.textRenderer, headerTitle, popupX + 10, popupY + 9, COLOR_TEXT_PRIMARY);

        String countText = accountList.size() == 1 ? "1 konto" : (accountList.size() + " kont");
        int countW = this.textRenderer.getWidth(countText);
        int badgeX = popupX + popupW - countW - 16;
        int badgeY = popupY + 6;
        context.fill(badgeX, badgeY, badgeX + countW + 8, badgeY + 14, 0x33FFFFFF);
        drawBorder(context, badgeX, badgeY, countW + 8, 14, 0x22FFFFFF);
        context.drawTextWithShadow(this.textRenderer, countText, badgeX + 4, badgeY + 3, 0xFFA0A0AB);

        int curY = popupY + headerH;

        // Account Cards List
        for (int i = 0; i < accountList.size(); i++) {
            MooAccountManager.Account acc = accountList.get(i);
            int cardX = popupX + 8;
            int cardY = curY;
            int cardW = popupW - 16;

            boolean isActive = MooAccountManager.getInstance().isActive(acc);
            boolean isItemHover = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + itemH;

            int bg = isActive ? MooClientSettings.getAccentGlowColor(0x35) : (isItemHover ? 0x44222230 : 0x2214141E);
            int border = isActive ? MooClientSettings.getAccentColor() : (isItemHover ? 0x66FFFFFF : 0x22FFFFFF);

            context.fill(cardX, cardY, cardX + cardW, cardY + itemH, bg);
            drawBorder(context, cardX, cardY, cardW, itemH, border);

            // Skin Head Avatar (uses player's actual skin)
            try {
                SkinTextures skin = getSkinForAccount(acc);
                if (skin != null) {
                    PlayerSkinDrawer.draw(context, skin, cardX + 5, cardY + 5, 24);
                }
            } catch (Throwable ignored) {}

            // Account Name & Status
            context.drawTextWithShadow(this.textRenderer, acc.getName(), cardX + 34, cardY + 6, COLOR_TEXT_PRIMARY);
            String status = isActive ? "✓ " + MooLanguage.get("active_account") : "Konto Microsoft";
            int statusCol = isActive ? MooClientSettings.getAccentColor() : COLOR_TEXT_MUTED;
            context.drawTextWithShadow(this.textRenderer, status, cardX + 34, cardY + 18, statusCol);

            // Checkmark (if active)
            if (isActive) {
                context.drawTextWithShadow(this.textRenderer, "✓", cardX + cardW - 32, cardY + 12, MooClientSettings.getAccentColor());
            }

            // Delete button '✕' (if more than 1 account)
            if (accountList.size() > 1) {
                int delX = cardX + cardW - 16;
                int delY = cardY + 10;
                boolean delHover = mouseX >= delX - 2 && mouseX <= delX + 12 && mouseY >= delY - 2 && mouseY <= delY + 14;
                context.drawTextWithShadow(this.textRenderer, "✕", delX, delY + 1, delHover ? 0xFFFF5555 : 0x66FFFFFF);
            }

            curY += itemH + 4;
        }

        // Footer: "+ Dodaj konto" button (Directly opens Microsoft Login)
        int btnX = popupX + 8;
        int btnY = curY + 4;
        int btnW = popupW - 16;
        int btnH = 24;
        boolean addHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        MooAccountManager.LoginState loginState = MooAccountManager.getInstance().getLoginState();

        int bg = addHover ? MooClientSettings.getAccentGlowColor(0x35) : 0x22181824;
        int border = (loginState.inProgress || addHover) ? MooClientSettings.getAccentColor() : 0x44FFFFFF;
        context.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        drawBorder(context, btnX, btnY, btnW, btnH, border);

        String addLabel = loginState.inProgress ? "Logowanie w oknie..." : (loginState.success ? "✓ Zalogowano!" : "+ Dodaj konto");
        int labelW = this.textRenderer.getWidth(addLabel);
        int labelCol = (loginState.inProgress || loginState.success) ? MooClientSettings.getAccentColor() : (addHover ? COLOR_TEXT_PRIMARY : 0xFFA0A0AB);
        context.drawTextWithShadow(this.textRenderer, addLabel, btnX + (btnW - labelW) / 2, btnY + 8, labelCol);
    }

    private SkinTextures getSkinForAccount(MooAccountManager.Account acc) {
        if (this.client == null || acc == null) return null;
        if (acc.getName().equalsIgnoreCase(this.client.getSession().getUsername()) || MooAccountManager.getInstance().isActive(acc)) {
            return this.client.getSkinProvider().getSkinTextures(this.client.getGameProfile());
        }
        try {
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(acc.getParsedUuid(), acc.getName());
            return this.client.getSkinProvider().getSkinTextures(profile);
        } catch (Throwable ignored) {}
        return this.client.getSkinProvider().getSkinTextures(this.client.getGameProfile());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            // 1. Check Player Card Click
            if (this.client != null && this.client.getSession() != null) {
                String username = this.client.getSession().getUsername();
                int nameWidth = this.textRenderer.getWidth(username);
                int cardW = Math.max(160, nameWidth + 72);
                int cardH = 34;
                int cardX = 14;
                int cardY = 12;

                if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
                    playClickSound();
                    accountsPopupOpen = !accountsPopupOpen;
                    if (accountsPopupOpen) {
                        MooAccountManager.getInstance().load();
                    }
                    return true;
                }
            }

            // 2. Check Accounts Popup Clicks
            if (accountsPopupOpen) {
                List<MooAccountManager.Account> accountList = MooAccountManager.getInstance().getAccounts();

                int popupX = 14;
                int popupY = 50;
                int popupW = 230;
                int headerH = 26;
                int itemH = 34;
                int curY = popupY + headerH;

                // Check clicks on account items
                for (int i = 0; i < accountList.size(); i++) {
                    MooAccountManager.Account acc = accountList.get(i);
                    int cardX = popupX + 8;
                    int cardY = curY;
                    int cardW = popupW - 16;

                    if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + itemH) {
                        // Check delete button click
                        if (accountList.size() > 1) {
                            int delX = cardX + cardW - 16;
                            int delY = cardY + 10;
                            if (mouseX >= delX - 2 && mouseX <= delX + 12 && mouseY >= delY - 2 && mouseY <= delY + 14) {
                                playClickSound();
                                MooAccountManager.getInstance().removeAccount(acc.getUuid());
                                return true;
                            }
                        }

                        // Select account
                        playClickSound();
                        MooAccountManager.getInstance().selectAccount(acc);
                        return true;
                    }
                    curY += itemH + 4;
                }

                // Check "+ Dodaj konto" button click (Directly starts Microsoft Login)
                int btnX = popupX + 8;
                int btnY = curY + 4;
                int btnW = popupW - 16;
                int btnH = 24;

                if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    playClickSound();
                    MooAccountManager.getInstance().startMicrosoftLogin();
                    return true;
                }

                // Check if click is inside the popup area
                int totalPopupH = (curY - popupY) + 34;
                if (mouseX >= popupX && mouseX <= popupX + popupW && mouseY >= popupY && mouseY <= popupY + totalPopupH) {
                    return true;
                } else {
                    // Click outside closes popup
                    accountsPopupOpen = false;
                }
            }

            // 3. Menu Buttons Click
            for (MenuButton b : buttons) {
                if (b.isHovered((int) mouseX, (int) mouseY)) {
                    playClickSound();
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (accountsPopupOpen) {
                accountsPopupOpen = false;
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void playClickSound() {
        if (this.client != null) {
            this.client.getSoundManager().play(
                net.minecraft.client.sound.PositionedSoundInstance.master(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f
                )
            );
        }
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private int interpolateColor(int c1, int c2, float ratio) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
