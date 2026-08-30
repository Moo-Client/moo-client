package com.mooclient.mixin;

import com.mooclient.gui.InvitationUIManager;
import com.mooclient.module.modules.ArmorModule;
import com.mooclient.module.modules.CpsModule;
import com.mooclient.module.modules.FpsModule;
import com.mooclient.module.modules.PingModule;
import com.mooclient.module.modules.PotionEffectsModule;
import com.mooclient.module.modules.ScoreboardModule;
import com.mooclient.module.modules.ToggleSprintModule;
import com.mooclient.util.MooClientSettings;
import com.mooclient.waypoint.WaypointRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Mixin to render in-game HUD modules (FPS, Sprint, Potion Effects, Scoreboard, Multiplayer Invitations)
 * at customizable positions.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void mooClient$cancelVanillaStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void mooClient$renderHudElements(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }

        float hudScale = MooClientSettings.getHudScaleFactor();
        boolean customScale = (hudScale != 1.0f);
        int scaledWidth = context.getScaledWindowWidth();
        int scaledHeight = context.getScaledWindowHeight();

        // 1. FPS Module Rendering
        if (FpsModule.isFpsEnabled()) {
            int fps = client.getCurrentFps();
            FpsModule.FpsStyle style = FpsModule.getStyle();

            String fpsText;
            if (style == FpsModule.FpsStyle.BRACKETS) {
                fpsText = "[" + fps + " FPS]";
            } else if (FpsModule.isShowPrefix()) {
                fpsText = "FPS: " + fps;
            } else {
                fpsText = fps + " FPS";
            }

            int textWidth = client.textRenderer.getWidth(fpsText);
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            FpsModule.width = boxW;
            FpsModule.height = boxH;

            int x = FpsModule.position.calculateX(boxW, scaledWidth);
            int y = FpsModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
                context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
            }

            if (style == FpsModule.FpsStyle.MOO_CLIENT) {
                if (FpsModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 4, y + 10, 0x88000000);
                    context.fill(x - 3, y - 2, x - 2, y + 10, 0xFFFFFFFF);
                }
                context.drawText(client.textRenderer, fpsText, x + (FpsModule.isShowBackground() ? 2 : 0), y,
                        0xFFFFFFFF, FpsModule.isTextShadow());
            } else {
                if (FpsModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x66000000);
                }
                context.drawText(client.textRenderer, fpsText, x, y, 0xFFFFFFFF, FpsModule.isTextShadow());
            }

            if (customScale) {
                context.getMatrices().pop();
            }
        }

        // 2. Sprint Module Rendering
        if (ToggleSprintModule.isSprintEnabled() && (ToggleSprintModule.shouldSprint()
                || client.currentScreen instanceof com.mooclient.gui.MooClientScreen)) {
            ToggleSprintModule.SprintStyle style = ToggleSprintModule.getStyle();

            String sprintText;
            if (style == ToggleSprintModule.SprintStyle.BRACKETS) {
                sprintText = "[Sprinting]";
            } else if (style == ToggleSprintModule.SprintStyle.SIMPLE) {
                sprintText = "Sprinting";
            } else {
                sprintText = "Sprinting (Toggled)";
            }

            int textWidth = client.textRenderer.getWidth(sprintText);
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            ToggleSprintModule.width = boxW;
            ToggleSprintModule.height = boxH;

            int x = ToggleSprintModule.position.calculateX(boxW, scaledWidth);
            int y = ToggleSprintModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
                context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
            }

            if (style == ToggleSprintModule.SprintStyle.MOO_CLIENT) {
                context.fill(x - 2, y - 2, x + textWidth + 4, y + 10, 0x88000000);
                context.fill(x - 3, y - 2, x - 2, y + 10, 0xFFFFFFFF);
                context.drawText(client.textRenderer, sprintText, x + 2, y, 0xFFFFFFFF, true);
            } else {
                if (ToggleSprintModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x66000000);
                }
                context.drawText(client.textRenderer, sprintText, x, y, 0xFFFFFFFF, ToggleSprintModule.isTextShadow());
            }

            if (customScale) {
                context.getMatrices().pop();
            }
        }

        // 3. Potion Effects HUD Rendering (Moo Client / Simple / Compact)
        if (PotionEffectsModule.isModuleEnabled() && client.player != null) {
            Collection<StatusEffectInstance> effects = client.player.getStatusEffects();
            boolean isMenu = client.currentScreen instanceof com.mooclient.gui.MooClientScreen;

            if (!effects.isEmpty() || isMenu) {
                PotionEffectsModule.PotionStyle pStyle = PotionEffectsModule.getStyle();
                boolean bg = PotionEffectsModule.isShowBackground();
                boolean shadow = PotionEffectsModule.isTextShadow();
                boolean showIcon = PotionEffectsModule.isShowIcon();

                int maxW = 100;
                int rowH = (pStyle == PotionEffectsModule.PotionStyle.COMPACT) ? 14 : 22;
                int rowGap = (pStyle == PotionEffectsModule.PotionStyle.COMPACT) ? 3 : 4;
                int count = effects.isEmpty() ? 3 : effects.size();
                int totalH = count * rowH + (count - 1) * rowGap;

                if (effects.isEmpty() && isMenu) {
                    maxW = 110;
                } else {
                    for (StatusEffectInstance effect : effects) {
                        RegistryEntry<StatusEffect> effectEntry = effect.getEffectType();
                        StatusEffect statusEffect = effectEntry.value();
                        String name = statusEffect.getName().getString()
                                + PotionEffectsModule.getAmplifierString(effect.getAmplifier());
                        String duration = PotionEffectsModule.formatDuration(effect);
                        int itemW;
                        if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                            String compactLine = name + " §7" + duration;
                            itemW = (showIcon ? 18 : 0) + client.textRenderer.getWidth(compactLine) + 8;
                        } else {
                            int nameW = client.textRenderer.getWidth(name);
                            int timeW = client.textRenderer.getWidth(duration);
                            itemW = (showIcon ? 22 : 0) + Math.max(nameW, timeW)
                                     + (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT ? 12 : 6);
                        }
                        maxW = Math.max(maxW, itemW);
                    }
                }

                int boxW = Math.round((maxW + 4) * hudScale);
                int boxH = Math.round(Math.max(26, totalH) * hudScale);
                PotionEffectsModule.width = boxW;
                PotionEffectsModule.height = boxH;

                int startX = PotionEffectsModule.position.calculateX(boxW, scaledWidth);
                int startY = PotionEffectsModule.position.calculateY(boxH, scaledHeight);
                int curY = startY;

                if (customScale) {
                    context.getMatrices().push();
                    context.getMatrices().translate(startX, startY, 0);
                    context.getMatrices().scale(hudScale, hudScale, 1.0f);
                    context.getMatrices().translate(-startX, -startY, 0);
                }

                if (effects.isEmpty() && isMenu) {
                    List<RegistryEntry<StatusEffect>> sampleEffects = List.of(
                            StatusEffects.SPEED,
                            StatusEffects.POISON,
                            StatusEffects.FIRE_RESISTANCE
                    );
                    String[] sampleNames = new String[] { "Speed", "Poison", "Fire Resistance" };
                    String[] sampleTimes = new String[] { "5:11", "0:25", "5:10" };
                    int[] sampleColors = new int[] { 0xFF7CAFC6, 0xFF4E9331, 0xFFE49A3A };

                    for (int i = 0; i < sampleNames.length; i++) {
                        String name = sampleNames[i];
                        String time = sampleTimes[i];
                        RegistryEntry<StatusEffect> effectEntry = sampleEffects.get(i);
                        int effectColor = sampleColors[i];

                        int itemW;
                        if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                            String compactLine = name + " §7" + time;
                            itemW = (showIcon ? 18 : 0) + client.textRenderer.getWidth(compactLine) + 8;
                        } else {
                            int nameW = client.textRenderer.getWidth(name);
                            int timeW = client.textRenderer.getWidth(time);
                            itemW = (showIcon ? 22 : 0) + Math.max(nameW, timeW)
                                    + (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT ? 12 : 6);
                        }

                        if (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT) {
                            context.fill(startX - 2, curY - 2, startX + itemW, curY + rowH, 0x77000000);
                            context.fill(startX - 2, curY - 2, startX, curY + rowH, effectColor);
                        } else if (bg) {
                            context.fill(startX - 2, curY - 2, startX + itemW, curY + rowH, 0x66000000);
                        }

                        int textX = startX + (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT ? 4 : 0);
                        if (showIcon) {
                            try {
                                Sprite sprite = client.getStatusEffectSpriteManager().getSprite(effectEntry);
                                if (sprite != null) {
                                    if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                                        context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, textX,
                                                curY + 1, 12, 12);
                                    } else {
                                        context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, textX,
                                                curY + 1, 18, 18);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                            textX += (pStyle == PotionEffectsModule.PotionStyle.COMPACT) ? 16 : 22;
                        }

                        if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                            context.drawText(client.textRenderer, name + " §7" + time, textX, curY + 2, 0xFFFFFFFF,
                                    shadow);
                        } else if (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT) {
                            context.drawText(client.textRenderer, name, textX, curY + 1, 0xFFFFFFFF, shadow);
                            context.drawText(client.textRenderer, "§7" + time, textX, curY + 10, 0xFFAAAAAA, shadow);
                        } else {
                            context.drawText(client.textRenderer, name, textX, curY + 1, 0xFFFFFFFF, shadow);
                            context.drawText(client.textRenderer, time, textX, curY + 10, 0xFFFFFFFF, shadow);
                        }

                        curY += rowH + rowGap;
                    }
                } else {
                    for (StatusEffectInstance effect : effects) {
                        RegistryEntry<StatusEffect> effectEntry = effect.getEffectType();
                        StatusEffect statusEffect = effectEntry.value();
                        String name = statusEffect.getName().getString()
                                + PotionEffectsModule.getAmplifierString(effect.getAmplifier());
                        String duration = PotionEffectsModule.formatDuration(effect);
                        int color = 0xFF000000 | statusEffect.getColor();

                        int itemW;
                        if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                            String compactLine = name + " §7" + duration;
                            itemW = (showIcon ? 18 : 0) + client.textRenderer.getWidth(compactLine) + 8;
                        } else {
                            int nameW = client.textRenderer.getWidth(name);
                            int timeW = client.textRenderer.getWidth(duration);
                            itemW = (showIcon ? 22 : 0) + Math.max(nameW, timeW)
                                    + (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT ? 12 : 6);
                        }

                        if (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT) {
                            context.fill(startX - 2, curY - 2, startX + itemW, curY + rowH, 0x77000000);
                            context.fill(startX - 2, curY - 2, startX, curY + rowH, color);
                        } else if (bg) {
                            context.fill(startX - 2, curY - 2, startX + itemW, curY + rowH, 0x66000000);
                        }

                        int textX = startX + (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT ? 4 : 0);
                        if (showIcon) {
                            try {
                                Sprite sprite = client.getStatusEffectSpriteManager().getSprite(effectEntry);
                                if (sprite != null) {
                                    if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                                        context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, textX,
                                                curY + 1, 12, 12);
                                    } else {
                                        context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, textX,
                                                curY + 1, 18, 18);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                            textX += (pStyle == PotionEffectsModule.PotionStyle.COMPACT) ? 16 : 22;
                        }

                        if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                            context.drawText(client.textRenderer, name + " §7" + duration, textX, curY + 2, 0xFFFFFFFF,
                                    shadow);
                        } else if (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT) {
                            context.drawText(client.textRenderer, name, textX, curY + 1, 0xFFFFFFFF, shadow);
                            context.drawText(client.textRenderer, "§7" + duration, textX, curY + 10, 0xFFAAAAAA,
                                    shadow);
                        } else {
                            context.drawText(client.textRenderer, name, textX, curY + 1, 0xFFFFFFFF, shadow);
                            context.drawText(client.textRenderer, duration, textX, curY + 10, 0xFFFFFFFF, shadow);
                        }

                        curY += rowH + rowGap;
                    }
                }

                if (customScale) {
                    context.getMatrices().pop();
                }
            }
        }

        // 4. Ping Module Rendering
        if (PingModule.isPingEnabled()) {
            int ping = PingModule.getCurrentPing();
            PingModule.PingStyle style = PingModule.getStyle();

            String pingText;
            if (style == PingModule.PingStyle.BRACKETS) {
                pingText = "[" + ping + " ms]";
            } else if (PingModule.isShowPrefix()) {
                pingText = "Ping: " + ping + " ms";
            } else {
                pingText = ping + " ms";
            }

            int textWidth = client.textRenderer.getWidth(pingText);
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            PingModule.width = boxW;
            PingModule.height = boxH;

            int x = PingModule.position.calculateX(boxW, scaledWidth);
            int y = PingModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
                context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
            }

            if (style == PingModule.PingStyle.MOO_CLIENT) {
                if (PingModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 4, y + 10, 0x88000000);
                    context.fill(x - 3, y - 2, x - 2, y + 10, 0xFFFFFFFF);
                }
                context.drawText(client.textRenderer, pingText,
                        x + (PingModule.isShowBackground() ? 2 : 0), y, 0xFFFFFFFF,
                        PingModule.isTextShadow());
            } else {
                if (PingModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x66000000);
                }
                context.drawText(client.textRenderer, pingText, x, y, 0xFFFFFFFF,
                        PingModule.isTextShadow());
            }

            if (customScale) {
                context.getMatrices().pop();
            }
        }

        // 5. CPS Module Rendering
        if (CpsModule.isCpsEnabled()) {
            int leftCps = CpsModule.getLeftCps();
            int rightCps = CpsModule.getRightCps();
            CpsModule.CpsStyle style = CpsModule.getStyle();

            String cpsText = CpsModule.getFormattedText(leftCps, rightCps);

            int textWidth = client.textRenderer.getWidth(cpsText);
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            CpsModule.width = boxW;
            CpsModule.height = boxH;

            int x = CpsModule.position.calculateX(boxW, scaledWidth);
            int y = CpsModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
                context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
            }

            if (style == CpsModule.CpsStyle.MOO_CLIENT) {
                if (CpsModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 4, y + 10, 0x88000000);
                    context.fill(x - 3, y - 2, x - 2, y + 10, 0xFFFFFFFF);
                }
                context.drawText(client.textRenderer, cpsText,
                        x + (CpsModule.isShowBackground() ? 2 : 0), y, 0xFFFFFFFF,
                        CpsModule.isTextShadow());
            } else {
                if (CpsModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x66000000);
                }
                context.drawText(client.textRenderer, cpsText, x, y, 0xFFFFFFFF,
                        CpsModule.isTextShadow());
            }

            if (customScale) {
                context.getMatrices().pop();
            }
        }

        // 6. Armor HUD Module Rendering (Set Display & Durability)
        if (ArmorModule.isArmorEnabled()) {
            renderArmorHud(context, client, hudScale, customScale, scaledWidth, scaledHeight);
        }

        // 7. Multiplayer Invitation UI (4 switchable HUD variants)
        InvitationUIManager.getInstance().renderHud(context, scaledWidth, scaledHeight, tickCounter.getTickDelta(true));
    }

    @Inject(method = "renderMiscOverlays", at = @At("TAIL"))
    private void mooClient$renderWaypoints(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }
        WaypointRenderer.renderHudWaypoints(context, client, tickCounter.getTickDelta(true));
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void mooClient$renderScoreboardSidebar(DrawContext context, ScoreboardObjective objective,
            CallbackInfo ci) {
        ci.cancel();
        if (!ScoreboardModule.isScoreboardEnabled()) {
            return;
        }

        renderCustomScoreboard(context, objective);
    }

    private void renderCustomScoreboard(DrawContext context, ScoreboardObjective objective) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }

        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

        Collection<ScoreboardEntry> rawEntries = scoreboard.getScoreboardEntries(objective);
        List<ScoreboardEntry> filtered = rawEntries.stream()
                .filter(e -> !e.hidden())
                .sorted(Comparator.comparing(ScoreboardEntry::value).reversed().thenComparing(ScoreboardEntry::owner,
                        String.CASE_INSENSITIVE_ORDER))
                .limit(15)
                .toList();

        Text titleText = objective.getDisplayName();
        int titleWidth = client.textRenderer.getWidth(titleText);
        int maxEntryWidth = titleWidth;
        int colonWidth = client.textRenderer.getWidth(": ");
        boolean showScores = ScoreboardModule.isShowScores();

        for (ScoreboardEntry entry : filtered) {
            Team team = scoreboard.getScoreHolderTeam(entry.owner());
            Text nameText = Team.decorateName(team, entry.name());
            int nameWidth = client.textRenderer.getWidth(nameText);
            int rowW = nameWidth;
            if (showScores) {
                Text scoreText = entry.formatted(numberFormat);
                int scoreWidth = client.textRenderer.getWidth(scoreText);
                if (scoreWidth > 0) {
                    rowW += colonWidth + scoreWidth;
                }
            }
            maxEntryWidth = Math.max(maxEntryWidth, rowW);
        }

        int totalWidth = maxEntryWidth;
        int lineHeight = 9;
        int entryCount = filtered.size();
        int totalHeight = (entryCount + 1) * lineHeight;

        int scaledWidth = context.getScaledWindowWidth();
        int scaledHeight = context.getScaledWindowHeight();

        float hudScale = MooClientSettings.getHudScaleFactor();
        boolean customScale = (hudScale != 1.0f);

        int boxW = Math.round((totalWidth + 4) * hudScale);
        int boxH = Math.round((totalHeight + 3) * hudScale);
        ScoreboardModule.width = boxW;
        ScoreboardModule.height = boxH;

        int startX = ScoreboardModule.position.calculateX(boxW, scaledWidth);
        int startY = ScoreboardModule.position.calculateY(boxH, scaledHeight);

        if (customScale) {
            context.getMatrices().push();
            context.getMatrices().translate(startX, startY, 0);
            context.getMatrices().scale(hudScale, hudScale, 1.0f);
            context.getMatrices().translate(-startX, -startY, 0);
        }

        boolean showBg = ScoreboardModule.isShowBackground();
        int titleBg = client.options.getTextBackgroundColor(0.4F);
        int bodyBg = client.options.getTextBackgroundColor(0.3F);

        if (showBg) {
            context.fill(startX - 2, startY - 2, startX + totalWidth + 2, startY + lineHeight - 1, titleBg);
            if (entryCount > 0) {
                context.fill(startX - 2, startY + lineHeight - 1, startX + totalWidth + 2, startY + totalHeight + 1,
                        bodyBg);
            }
        }

        boolean shadow = ScoreboardModule.isTextShadow();

        int titleX = startX + (totalWidth - titleWidth) / 2;
        context.drawText(client.textRenderer, titleText, titleX, startY, 0xFFFFFFFF, shadow);

        for (int i = 0; i < entryCount; i++) {
            ScoreboardEntry entry = filtered.get(i);
            Team team = scoreboard.getScoreHolderTeam(entry.owner());
            Text nameText = Team.decorateName(team, entry.name());
            int rowY = startY + (i + 1) * lineHeight;

            context.drawText(client.textRenderer, nameText, startX, rowY, 0xFFFFFFFF, shadow);

            if (showScores) {
                Text scoreText = entry.formatted(numberFormat);
                int scoreWidth = client.textRenderer.getWidth(scoreText);
                int scoreX = startX + totalWidth - scoreWidth;
                context.drawText(client.textRenderer, scoreText, scoreX, rowY, 0xFFFF5555, shadow);
            }
        }

        if (customScale) {
            context.getMatrices().pop();
        }
    }

    private void renderArmorHud(DrawContext context, MinecraftClient client, float hudScale, boolean customScale,
            int scaledWidth, int scaledHeight) {
        boolean isMenu = client.currentScreen instanceof com.mooclient.gui.MooClientScreen;
        if (client.player == null && !isMenu) {
            return;
        }

        List<ItemStack> stacks = new java.util.ArrayList<>();
        if (client.player != null) {
            net.minecraft.entity.player.PlayerInventory inv = client.player.getInventory();
            stacks.add(inv.getArmorStack(3)); // Helmet
            stacks.add(inv.getArmorStack(2)); // Chestplate
            stacks.add(inv.getArmorStack(1)); // Leggings
            stacks.add(inv.getArmorStack(0)); // Boots
            if (ArmorModule.isShowOffhand()) {
                stacks.add(client.player.getOffHandStack());
            }
            if (ArmorModule.isShowMainHand()) {
                stacks.add(client.player.getMainHandStack());
            }
        }

        boolean allEmpty = true;
        for (ItemStack st : stacks) {
            if (st != null && !st.isEmpty()) {
                allEmpty = false;
                break;
            }
        }

        if (allEmpty && isMenu) {
            stacks.clear();
            stacks.add(new ItemStack(net.minecraft.item.Items.NETHERITE_HELMET));
            stacks.add(new ItemStack(net.minecraft.item.Items.NETHERITE_CHESTPLATE));
            stacks.add(new ItemStack(net.minecraft.item.Items.NETHERITE_LEGGINGS));
            stacks.add(new ItemStack(net.minecraft.item.Items.NETHERITE_BOOTS));
            if (ArmorModule.isShowOffhand()) {
                stacks.add(new ItemStack(net.minecraft.item.Items.SHIELD));
            }
            if (ArmorModule.isShowMainHand()) {
                stacks.add(new ItemStack(net.minecraft.item.Items.NETHERITE_SWORD));
            }
            allEmpty = false;
        }

        if (allEmpty && !ArmorModule.isShowEmptySlots()) {
            return;
        }

        int slotCount = stacks.isEmpty() ? ArmorModule.getSlotCount() : stacks.size();
        int slotSize = ArmorModule.getSlotSize();
        int gap = ArmorModule.getSlotGap();
        ArmorModule.ArmorOrientation orientation = ArmorModule.getOrientation();
        ArmorModule.ArmorStyle style = ArmorModule.getStyle();
        ArmorModule.DurabilityTextMode textMode = ArmorModule.getDurabilityTextMode();
        boolean showBar = ArmorModule.isShowDurabilityBar();

        int unscaledW = ArmorModule.calculateUnscaledWidth();
        int unscaledH = ArmorModule.calculateUnscaledHeight();

        int boxW = ArmorModule.calculateBoxWidth(hudScale);
        int boxH = ArmorModule.calculateBoxHeight(hudScale);
        ArmorModule.width = boxW;
        ArmorModule.height = boxH;

        int startX = ArmorModule.position.calculateX(boxW, scaledWidth);
        int startY = ArmorModule.position.calculateY(boxH, scaledHeight);

        // Low Durability Cow Sound Alert (<= 50) - Plays ONCE when an item drops to <= 50
        if (client.player != null && !isMenu) {
            if (ArmorModule.checkAndTriggerWarning(stacks)) {
                try {
                    client.getSoundManager().play(
                            net.minecraft.client.sound.PositionedSoundInstance.master(
                                    com.mooclient.sound.MooSounds.COW_MOO, 1.0f, 1.5f));
                    client.player.playSound(com.mooclient.sound.MooSounds.COW_MOO, 1.5f, 1.0f);
                } catch (Exception ignored) {
                    try {
                        client.getSoundManager().play(
                                net.minecraft.client.sound.PositionedSoundInstance.master(
                                        net.minecraft.sound.SoundEvents.ENTITY_COW_AMBIENT, 1.0f, 1.5f));
                    } catch (Exception ignored2) {}
                }
            }
        }

        if (customScale) {
            context.getMatrices().push();
            context.getMatrices().translate(startX, startY, 0);
            context.getMatrices().scale(hudScale, hudScale, 1.0f);
            context.getMatrices().translate(-startX, -startY, 0);
        }

        int extraTop = (orientation == ArmorModule.ArmorOrientation.HORIZONTAL && textMode != ArmorModule.DurabilityTextMode.NONE) ? 10 : 0;
        int slotStartY = startY + extraTop;

        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = (i < stacks.size()) ? stacks.get(i) : ItemStack.EMPTY;
            int curX = (orientation == ArmorModule.ArmorOrientation.HORIZONTAL)
                    ? startX + i * (slotSize + gap) : startX;
            int curY = (orientation == ArmorModule.ArmorOrientation.HORIZONTAL)
                    ? slotStartY : slotStartY + i * (slotSize + gap);

            // 1. Draw Slot Background & Border
            if (style == ArmorModule.ArmorStyle.MOO_CLIENT) {
                if (ArmorModule.isShowBackground()) {
                    // Translucent dark Moo Client background
                    context.fill(curX, curY, curX + slotSize, curY + slotSize, 0x66000000);
                    // Sleek 1px light border matching Moo Client HUD style
                    drawSlotBorder(context, curX, curY, slotSize, slotSize, 0x88B0D8EA);
                }
            } else if (style == ArmorModule.ArmorStyle.COMPACT) {
                if (ArmorModule.isShowBackground()) {
                    context.fill(curX, curY, curX + slotSize, curY + slotSize, 0x55000000);
                    drawSlotBorder(context, curX, curY, slotSize, slotSize, 0x44FFFFFF);
                }
            }

            // 2. Draw Item & Durability
            if (stack != null && !stack.isEmpty()) {
                context.drawItem(stack, curX + 2, curY + 2);

                // 3. Durability Bar at the bottom (ALWAYS on) - moved 2px down, sleek 1px thickness
                if (showBar) {
                    int barX = curX + 2;
                    int barY = curY + 18;
                    int barW = 16;

                    float ratio = 1.0f;
                    if (stack.isDamageable() && stack.getMaxDamage() > 0) {
                        ratio = (float) (stack.getMaxDamage() - stack.getDamage()) / (float) stack.getMaxDamage();
                        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
                    }

                    int filledW = Math.max(1, Math.round(ratio * barW));
                    int durColor = (ratio > 0.5f) ? 0xFF00FF00 : ((ratio > 0.2f) ? 0xFFFFAA00 : 0xFFFF3333);

                    context.fill(barX, barY, barX + barW, barY + 1, 0x88000000);
                    context.fill(barX, barY, barX + filledW, barY + 1, durColor);
                }

                // 4. Durability Text DELIKATNIE NAD (slightly above the slot)
                if (textMode != ArmorModule.DurabilityTextMode.NONE) {
                    float ratio = 1.0f;
                    int remaining = 100;
                    if (stack.isDamageable() && stack.getMaxDamage() > 0) {
                        ratio = (float) (stack.getMaxDamage() - stack.getDamage()) / (float) stack.getMaxDamage();
                        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
                        remaining = Math.max(0, stack.getMaxDamage() - stack.getDamage());
                    }
                    int pct = Math.max(0, Math.min(100, Math.round(ratio * 100)));

                    String txt = (textMode == ArmorModule.DurabilityTextMode.PERCENT)
                            ? (pct + "%")
                            : String.valueOf(remaining);

                    int tw = client.textRenderer.getWidth(txt);
                    int textColor = (ratio > 0.5f) ? 0xFFFFFFFF : ((ratio > 0.2f) ? 0xFFFFFF55 : 0xFFFF5555);

                    if (orientation == ArmorModule.ArmorOrientation.HORIZONTAL) {
                        int textX = curX + (slotSize - tw) / 2;
                        int textY = curY - 9;
                        context.drawText(client.textRenderer, txt, textX, textY, textColor, true);
                    } else {
                        int textX = curX + slotSize + 4;
                        int textY = curY + (slotSize - 8) / 2;
                        context.drawText(client.textRenderer, txt, textX, textY, textColor, true);
                    }
                }
            } else if (ArmorModule.isShowEmptySlots() && style != ArmorModule.ArmorStyle.SIMPLE) {
                // Empty slot indicator
                context.fill(curX + 6, curY + 6, curX + slotSize - 6, curY + slotSize - 6, 0x15FFFFFF);
            }
        }

        if (customScale) {
            context.getMatrices().pop();
        }
    }

    private static void drawSlotBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
