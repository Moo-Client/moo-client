package com.mooclient.mixin;

import com.mooclient.module.modules.FpsModule;
import com.mooclient.module.modules.PotionEffectsModule;
import com.mooclient.module.modules.ScoreboardModule;
import com.mooclient.module.modules.ToggleSprintModule;
import com.mooclient.util.MooHudPositionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
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
 * Mixin to render in-game HUD modules (FPS, Sprint, Potion Effects, Scoreboard)
 * at customizable positions.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void mooClient$renderHudElements(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }

        float hudScale = com.mooclient.util.MooClientSettings.getHudScaleFactor();
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
            int boxW = (int) Math.round((textWidth + 6) * hudScale);
            int boxH = (int) Math.round(12 * hudScale);
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
            int boxW = (int) Math.round((textWidth + 6) * hudScale);
            int boxH = (int) Math.round(12 * hudScale);
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

                // Calculate max width for accurate bounding box
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

                int boxW = (int) Math.round((maxW + 4) * hudScale);
                int boxH = (int) Math.round(Math.max(26, totalH) * hudScale);
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
                    // Preview dummy effects in MooClientScreen menu
                    RegistryEntry<StatusEffect>[] sampleEffects = new RegistryEntry[] {
                            StatusEffects.SPEED,
                            StatusEffects.POISON,
                            StatusEffects.FIRE_RESISTANCE
                    };
                    String[] sampleNames = new String[] { "Speed", "Poison", "Fire Resistance" };
                    String[] sampleTimes = new String[] { "5:11", "0:25", "5:10" };
                    int[] sampleColors = new int[] { 0xFF7CAFC6, 0xFF4E9331, 0xFFE49A3A };

                    for (int i = 0; i < sampleNames.length; i++) {
                        String name = sampleNames[i];
                        String time = sampleTimes[i];
                        RegistryEntry<StatusEffect> effectEntry = sampleEffects[i];
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
                            context.fill(startX - 2, curY - 2, startX, curY + rowH, effectColor); // Colored accent bar
                                                                                                  // on left
                        } else if (bg) {
                            context.fill(startX - 2, curY - 2, startX + itemW, curY + rowH, 0x66000000);
                        }

                        // 1. Draw Potion Icon
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

                        // 2. Draw Text
                        if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                            context.drawText(client.textRenderer, name + " §7" + time, textX, curY + 2, 0xFFFFFFFF,
                                    shadow);
                        } else if (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT) {
                            context.drawText(client.textRenderer, name, textX, curY + 1, 0xFFFFFFFF, shadow);
                            context.drawText(client.textRenderer, "§7" + time, textX, curY + 10, 0xFFAAAAAA, shadow);
                        } else {
                            // SIMPLE
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
                            context.fill(startX - 2, curY - 2, startX, curY + rowH, color); // Colored accent bar on
                                                                                            // left
                        } else if (bg) {
                            context.fill(startX - 2, curY - 2, startX + itemW, curY + rowH, 0x66000000);
                        }

                        // 1. Draw Potion Icon
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

                        // 2. Draw Text
                        if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                            context.drawText(client.textRenderer, name + " §7" + duration, textX, curY + 2, 0xFFFFFFFF,
                                    shadow);
                        } else if (pStyle == PotionEffectsModule.PotionStyle.MOO_CLIENT) {
                            context.drawText(client.textRenderer, name, textX, curY + 1, 0xFFFFFFFF, shadow);
                            context.drawText(client.textRenderer, "§7" + duration, textX, curY + 10, 0xFFAAAAAA,
                                    shadow);
                        } else {
                            // SIMPLE
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
        if (com.mooclient.module.modules.PingModule.isPingEnabled()) {
            int ping = com.mooclient.module.modules.PingModule.getCurrentPing();
            com.mooclient.module.modules.PingModule.PingStyle style = com.mooclient.module.modules.PingModule
                    .getStyle();

            String pingText;
            if (style == com.mooclient.module.modules.PingModule.PingStyle.BRACKETS) {
                pingText = "[" + ping + " ms]";
            } else if (com.mooclient.module.modules.PingModule.isShowPrefix()) {
                pingText = "Ping: " + ping + " ms";
            } else {
                pingText = ping + " ms";
            }

            int textWidth = client.textRenderer.getWidth(pingText);
            int boxW = (int) Math.round((textWidth + 6) * hudScale);
            int boxH = (int) Math.round(12 * hudScale);
            com.mooclient.module.modules.PingModule.width = boxW;
            com.mooclient.module.modules.PingModule.height = boxH;

            int x = com.mooclient.module.modules.PingModule.position.calculateX(boxW, scaledWidth);
            int y = com.mooclient.module.modules.PingModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
                context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
            }

            if (style == com.mooclient.module.modules.PingModule.PingStyle.MOO_CLIENT) {
                if (com.mooclient.module.modules.PingModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 4, y + 10, 0x88000000);
                    context.fill(x - 3, y - 2, x - 2, y + 10, 0xFFFFFFFF);
                }
                context.drawText(client.textRenderer, pingText,
                        x + (com.mooclient.module.modules.PingModule.isShowBackground() ? 2 : 0), y, 0xFFFFFFFF,
                        com.mooclient.module.modules.PingModule.isTextShadow());
            } else {
                if (com.mooclient.module.modules.PingModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x66000000);
                }
                context.drawText(client.textRenderer, pingText, x, y, 0xFFFFFFFF,
                        com.mooclient.module.modules.PingModule.isTextShadow());
            }

            if (customScale) {
                context.getMatrices().pop();
            }
        }

        // 5. CPS Module Rendering
        if (com.mooclient.module.modules.CpsModule.isCpsEnabled()) {
            int leftCps = com.mooclient.module.modules.CpsModule.getLeftCps();
            int rightCps = com.mooclient.module.modules.CpsModule.getRightCps();
            com.mooclient.module.modules.CpsModule.CpsStyle style = com.mooclient.module.modules.CpsModule.getStyle();

            String cpsText = com.mooclient.module.modules.CpsModule.getFormattedText(leftCps, rightCps);

            int textWidth = client.textRenderer.getWidth(cpsText);
            int boxW = (int) Math.round((textWidth + 6) * hudScale);
            int boxH = (int) Math.round(12 * hudScale);
            com.mooclient.module.modules.CpsModule.width = boxW;
            com.mooclient.module.modules.CpsModule.height = boxH;

            int x = com.mooclient.module.modules.CpsModule.position.calculateX(boxW, scaledWidth);
            int y = com.mooclient.module.modules.CpsModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
                context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
            }

            if (style == com.mooclient.module.modules.CpsModule.CpsStyle.MOO_CLIENT) {
                if (com.mooclient.module.modules.CpsModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 4, y + 10, 0x88000000);
                    context.fill(x - 3, y - 2, x - 2, y + 10, 0xFFFFFFFF);
                }
                context.drawText(client.textRenderer, cpsText,
                        x + (com.mooclient.module.modules.CpsModule.isShowBackground() ? 2 : 0), y, 0xFFFFFFFF,
                        com.mooclient.module.modules.CpsModule.isTextShadow());
            } else {
                if (com.mooclient.module.modules.CpsModule.isShowBackground()) {
                    context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x66000000);
                }
                context.drawText(client.textRenderer, cpsText, x, y, 0xFFFFFFFF,
                        com.mooclient.module.modules.CpsModule.isTextShadow());
            }

            if (customScale) {
                context.getMatrices().pop();
            }
        }
    }

    @Inject(method = "renderMiscOverlays", at = @At("TAIL"))
    private void mooClient$renderWaypoints(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }
        // Render waypoints BEFORE crosshair and main HUD so crosshair is always visible
        // in front
        com.mooclient.waypoint.WaypointRenderer.renderHudWaypoints(context, client, tickCounter.getTickDelta(true));
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

        float hudScale = com.mooclient.util.MooClientSettings.getHudScaleFactor();
        boolean customScale = (hudScale != 1.0f);

        int boxW = (int) Math.round((totalWidth + 4) * hudScale);
        int boxH = (int) Math.round((totalHeight + 3) * hudScale);
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
            // Title background
            context.fill(startX - 2, startY - 2, startX + totalWidth + 2, startY + lineHeight - 1, titleBg);
            // Body background
            if (entryCount > 0) {
                context.fill(startX - 2, startY + lineHeight - 1, startX + totalWidth + 2, startY + totalHeight + 1,
                        bodyBg);
            }
        }

        boolean shadow = ScoreboardModule.isTextShadow();

        // Draw title centered
        int titleX = startX + (totalWidth - titleWidth) / 2;
        context.drawText(client.textRenderer, titleText, titleX, startY, 0xFFFFFFFF, shadow);

        // Draw entries
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
}
