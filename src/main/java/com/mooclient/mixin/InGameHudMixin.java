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
import com.mooclient.util.ScoreboardCache;
import com.mooclient.util.ScoreboardCache.CachedScoreboardData;
import com.mooclient.util.ScoreboardCache.CachedScoreboardRow;
import com.mooclient.waypoint.WaypointRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
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

    // High-performance caches for HUD modules
    private static int lastFpsValue = -1;
    private static FpsModule.FpsStyle lastFpsStyle = null;
    private static boolean lastFpsPrefix = false;
    private static String cachedFpsText = "";
    private static int cachedFpsTextWidth = 0;

    private static String lastSprintKey = "";
    private static String cachedSprintText = "";
    private static int cachedSprintTextWidth = 0;

    private static int lastPingValue = -1;
    private static PingModule.PingStyle lastPingStyle = null;
    private static boolean lastPingPrefix = false;
    private static String cachedPingText = "";
    private static int cachedPingTextWidth = 0;

    private static int lastLeftCps = -1;
    private static int lastRightCps = -1;
    private static CpsModule.CpsStyle lastCpsStyle = null;
    private static String cachedCpsText = "";
    private static int cachedCpsTextWidth = 0;

    private static final CachedScoreboardData SCOREBOARD_CACHE = ScoreboardCache.SCOREBOARD_CACHE;

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
            boolean prefix = FpsModule.isShowPrefix();

            if (fps != lastFpsValue || style != lastFpsStyle || prefix != lastFpsPrefix) {
                lastFpsValue = fps;
                lastFpsStyle = style;
                lastFpsPrefix = prefix;
                if (style == FpsModule.FpsStyle.BRACKETS) {
                    cachedFpsText = "[" + fps + " FPS]";
                } else if (prefix) {
                    cachedFpsText = "FPS: " + fps;
                } else {
                    cachedFpsText = fps + " FPS";
                }
                cachedFpsTextWidth = client.textRenderer.getWidth(cachedFpsText);
            }

            String fpsText = cachedFpsText;
            int textWidth = cachedFpsTextWidth;
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            FpsModule.width = boxW;
            FpsModule.height = boxH;

            int x = FpsModule.position.calculateX(boxW, scaledWidth);
            int y = FpsModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
//? if >=1.21.11 {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate((float) x, (float) y);
                context.getMatrices().scale(hudScale, hudScale);
                context.getMatrices().translate((float) -x, (float) -y);
//?} else {
              /*context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
*///?}
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
//? if >=1.21.11 {
                context.getMatrices().popMatrix();
//?} else {
              /*context.getMatrices().pop();
*///?}
            }
        }

        // 2. Sprint Module Rendering
        if (ToggleSprintModule.isSprintEnabled() && (ToggleSprintModule.shouldSprint()
                || client.currentScreen instanceof com.mooclient.gui.MooClientScreen)) {
            ToggleSprintModule.SprintStyle style = ToggleSprintModule.getStyle();
            boolean isSprinting = ToggleSprintModule.shouldSprint();
            String sprintKey = style.name() + "_" + isSprinting;

            if (!sprintKey.equals(lastSprintKey)) {
                lastSprintKey = sprintKey;
                if (style == ToggleSprintModule.SprintStyle.BRACKETS) {
                    cachedSprintText = "[Sprinting]";
                } else if (style == ToggleSprintModule.SprintStyle.SIMPLE) {
                    cachedSprintText = "Sprinting";
                } else {
                    cachedSprintText = "Sprinting (Toggled)";
                }
                cachedSprintTextWidth = client.textRenderer.getWidth(cachedSprintText);
            }

            String sprintText = cachedSprintText;
            int textWidth = cachedSprintTextWidth;
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            ToggleSprintModule.width = boxW;
            ToggleSprintModule.height = boxH;

            int x = ToggleSprintModule.position.calculateX(boxW, scaledWidth);
            int y = ToggleSprintModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
//? if >=1.21.11 {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate((float) x, (float) y);
                context.getMatrices().scale(hudScale, hudScale);
                context.getMatrices().translate((float) -x, (float) -y);
//?} else {
              /*context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
*///?}
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
//? if >=1.21.11 {
                context.getMatrices().popMatrix();
//?} else {
              /*context.getMatrices().pop();
*///?}
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
//? if >=1.21.11 {
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate((float) startX, (float) startY);
                    context.getMatrices().scale(hudScale, hudScale);
                    context.getMatrices().translate((float) -startX, (float) -startY);
//?} else {
                  /*context.getMatrices().push();
                    context.getMatrices().translate(startX, startY, 0);
                    context.getMatrices().scale(hudScale, hudScale, 1.0f);
                    context.getMatrices().translate(-startX, -startY, 0);
*///?}
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
//? if >=1.21.11 {
                                Identifier effectTexture = net.minecraft.client.gui.hud.InGameHud.getEffectTexture(effectEntry);
                                if (effectTexture != null) {
                                    if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                                        context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, effectTexture, textX, curY + 1, 12, 12);
                                    } else {
                                        context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, effectTexture, textX, curY + 1, 18, 18);
                                    }
                                }
//?} else {
                              /*Sprite sprite = client.getStatusEffectSpriteManager().getSprite(effectEntry);
                                if (sprite != null) {
                                    if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                                        context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, textX,
                                                curY + 1, 12, 12);
                                    } else {
                                        context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, textX,
                                                curY + 1, 18, 18);
                                    }
                                }
*///?}
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
//? if >=1.21.11 {
                                Identifier effectTexture = net.minecraft.client.gui.hud.InGameHud.getEffectTexture(effectEntry);
                                if (effectTexture != null) {
                                    if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                                        context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, effectTexture, textX, curY + 1, 12, 12);
                                    } else {
                                        context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, effectTexture, textX, curY + 1, 18, 18);
                                    }
                                }
//?} else {
                              /*Sprite sprite = client.getStatusEffectSpriteManager().getSprite(effectEntry);
                                if (sprite != null) {
                                    if (pStyle == PotionEffectsModule.PotionStyle.COMPACT) {
                                        context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, textX,
                                                curY + 1, 12, 12);
                                    } else {
                                        context.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, textX,
                                                curY + 1, 18, 18);
                                    }
                                }
*///?}
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
//? if >=1.21.11 {
                    context.getMatrices().popMatrix();
//?} else {
                  /*context.getMatrices().pop();
*///?}
                }
            }
        }

        // 4. Ping Module Rendering
        if (PingModule.isPingEnabled()) {
            int ping = PingModule.getCurrentPing();
            PingModule.PingStyle style = PingModule.getStyle();
            boolean prefix = PingModule.isShowPrefix();

            if (ping != lastPingValue || style != lastPingStyle || prefix != lastPingPrefix) {
                lastPingValue = ping;
                lastPingStyle = style;
                lastPingPrefix = prefix;
                if (style == PingModule.PingStyle.BRACKETS) {
                    cachedPingText = "[" + ping + " ms]";
                } else if (prefix) {
                    cachedPingText = "Ping: " + ping + " ms";
                } else {
                    cachedPingText = ping + " ms";
                }
                cachedPingTextWidth = client.textRenderer.getWidth(cachedPingText);
            }

            String pingText = cachedPingText;
            int textWidth = cachedPingTextWidth;
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            PingModule.width = boxW;
            PingModule.height = boxH;

            int x = PingModule.position.calculateX(boxW, scaledWidth);
            int y = PingModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
//? if >=1.21.11 {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate((float) x, (float) y);
                context.getMatrices().scale(hudScale, hudScale);
                context.getMatrices().translate((float) -x, (float) -y);
//?} else {
              /*context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
*///?}
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
//? if >=1.21.11 {
                context.getMatrices().popMatrix();
//?} else {
              /*context.getMatrices().pop();
*///?}
            }
        }

        // 5. CPS Module Rendering
        if (CpsModule.isCpsEnabled()) {
            int leftCps = CpsModule.getLeftCps();
            int rightCps = CpsModule.getRightCps();
            CpsModule.CpsStyle style = CpsModule.getStyle();

            if (leftCps != lastLeftCps || rightCps != lastRightCps || style != lastCpsStyle) {
                lastLeftCps = leftCps;
                lastRightCps = rightCps;
                lastCpsStyle = style;
                cachedCpsText = CpsModule.getFormattedText(leftCps, rightCps);
                cachedCpsTextWidth = client.textRenderer.getWidth(cachedCpsText);
            }

            String cpsText = cachedCpsText;
            int textWidth = cachedCpsTextWidth;
            int boxW = Math.round((textWidth + 6) * hudScale);
            int boxH = Math.round(12 * hudScale);
            CpsModule.width = boxW;
            CpsModule.height = boxH;

            int x = CpsModule.position.calculateX(boxW, scaledWidth);
            int y = CpsModule.position.calculateY(boxH, scaledHeight);

            if (customScale) {
//? if >=1.21.11 {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate((float) x, (float) y);
                context.getMatrices().scale(hudScale, hudScale);
                context.getMatrices().translate((float) -x, (float) -y);
//?} else {
              /*context.getMatrices().push();
                context.getMatrices().translate(x, y, 0);
                context.getMatrices().scale(hudScale, hudScale, 1.0f);
                context.getMatrices().translate(-x, -y, 0);
*///?}
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
//? if >=1.21.11 {
                context.getMatrices().popMatrix();
//?} else {
              /*context.getMatrices().pop();
*///?}
            }
        }

        // 6. Armor HUD Module Rendering (Set Display & Durability)
        if (ArmorModule.isArmorEnabled()) {
            renderArmorHud(context, client, hudScale, customScale, scaledWidth, scaledHeight);
        }

        // 7. Inventory View Module Rendering (3 main inventory rows)
        if (com.mooclient.module.modules.InventoryViewModule.shouldRender()) {
            com.mooclient.util.InventoryViewRenderer.render(context, client, scaledWidth, scaledHeight, hudScale, customScale, false);
        }

        // 8. Keystrokes HUD Module Rendering (WSAD + Space)
        if (com.mooclient.module.modules.KeystrokesModule.isKeystrokesEnabled()) {
            com.mooclient.util.KeystrokesRenderer.render(context, client, scaledWidth, scaledHeight, hudScale, customScale, false);
        }

        // 9. Multiplayer Invitation UI (4 switchable HUD variants)
//? if >=1.21.11 {
        InvitationUIManager.getInstance().renderHud(context, scaledWidth, scaledHeight, tickCounter.getTickProgress(true));
//?} else {
      /*InvitationUIManager.getInstance().renderHud(context, scaledWidth, scaledHeight, tickCounter.getTickDelta(true));
*///?}
    }

    @Inject(method = "renderMiscOverlays", at = @At("TAIL"))
    private void mooClient$renderWaypoints(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }
//? if >=1.21.11 {
        WaypointRenderer.renderHudWaypoints(context, client, tickCounter.getTickProgress(true));
//?} else {
      /*WaypointRenderer.renderHudWaypoints(context, client, tickCounter.getTickDelta(true));
*///?}
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
        Collection<ScoreboardEntry> rawEntries = scoreboard.getScoreboardEntries(objective);
        boolean showScores = ScoreboardModule.isShowScores();
        long currentTick = client.world != null ? client.world.getTime() : System.currentTimeMillis() / 50;

        CachedScoreboardData cache = SCOREBOARD_CACHE;
        if (cache.objective != objective || cache.rawSize != rawEntries.size() || cache.lastUpdateTick != currentTick) {
            NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);
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

            List<CachedScoreboardRow> rows = new java.util.ArrayList<>(filtered.size());
            for (ScoreboardEntry entry : filtered) {
                Team team = scoreboard.getScoreHolderTeam(entry.owner());
                Text nameText = Team.decorateName(team, entry.name());
                int nameWidth = client.textRenderer.getWidth(nameText);
                int rowW = nameWidth;
                Text scoreText = null;
                int scoreWidth = 0;
                if (showScores) {
                    scoreText = entry.formatted(numberFormat);
                    scoreWidth = client.textRenderer.getWidth(scoreText);
                    if (scoreWidth > 0) {
                        rowW += colonWidth + scoreWidth;
                    }
                }
                maxEntryWidth = Math.max(maxEntryWidth, rowW);
                rows.add(new CachedScoreboardRow(nameText, scoreText, scoreWidth));
            }

            cache.objective = objective;
            cache.rawSize = rawEntries.size();
            cache.lastUpdateTick = currentTick;
            cache.titleText = titleText;
            cache.titleWidth = titleWidth;
            cache.totalWidth = maxEntryWidth;
            cache.totalHeight = (rows.size() + 1) * 9;
            cache.rows = rows;
        }

        int totalWidth = cache.totalWidth;
        int lineHeight = 9;
        int entryCount = (cache.rows != null) ? cache.rows.size() : 0;
        int totalHeight = cache.totalHeight;

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
//? if >=1.21.11 {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) startX, (float) startY);
            context.getMatrices().scale(hudScale, hudScale);
            context.getMatrices().translate((float) -startX, (float) -startY);
//?} else {
          /*context.getMatrices().push();
            context.getMatrices().translate(startX, startY, 0);
            context.getMatrices().scale(hudScale, hudScale, 1.0f);
            context.getMatrices().translate(-startX, -startY, 0);
*///?}
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

        int titleX = startX + (totalWidth - cache.titleWidth) / 2;
        context.drawText(client.textRenderer, cache.titleText, titleX, startY, 0xFFFFFFFF, shadow);

        for (int i = 0; i < entryCount; i++) {
            CachedScoreboardRow row = cache.rows.get(i);
            int rowY = startY + (i + 1) * lineHeight;

            context.drawText(client.textRenderer, row.nameText, startX, rowY, 0xFFFFFFFF, shadow);

            if (showScores && row.scoreText != null && row.scoreWidth > 0) {
                int scoreX = startX + totalWidth - row.scoreWidth;
                context.drawText(client.textRenderer, row.scoreText, scoreX, rowY, 0xFFFF5555, shadow);
            }
        }

        if (customScale) {
//? if >=1.21.11 {
            context.getMatrices().popMatrix();
//?} else {
          /*context.getMatrices().pop();
*///?}
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
//? if >=1.21.11 {
            stacks.add(client.player.getEquippedStack(EquipmentSlot.HEAD));
            stacks.add(client.player.getEquippedStack(EquipmentSlot.CHEST));
            stacks.add(client.player.getEquippedStack(EquipmentSlot.LEGS));
            stacks.add(client.player.getEquippedStack(EquipmentSlot.FEET));
//?} else {
          /*net.minecraft.entity.player.PlayerInventory inv = client.player.getInventory();
            stacks.add(inv.getArmorStack(3)); // Helmet
            stacks.add(inv.getArmorStack(2)); // Chestplate
            stacks.add(inv.getArmorStack(1)); // Leggings
            stacks.add(inv.getArmorStack(0)); // Boots
*///?}
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

        // Low Durability Cow Sound Alert - Triggers whenever an armor piece drops into critical red zone (<= 25% or <= 60 dur)
        if (client.player != null && !isMenu) {
            if (ArmorModule.checkAndTriggerWarning(stacks)) {
                try {
//? if >=1.21.11 {
                    client.getSoundManager().play(
                            net.minecraft.client.sound.PositionedSoundInstance.ui(
                                    com.mooclient.sound.MooSounds.COW_MOO, 1.0f, 1.0f));
//?} else {
                  /*client.getSoundManager().play(
                            net.minecraft.client.sound.PositionedSoundInstance.master(
                                    com.mooclient.sound.MooSounds.COW_MOO, 1.0f, 1.0f));
*///?}
                } catch (Exception ignored) {
                    try {
//? if >=1.21.11 {
                        client.getSoundManager().play(
                                net.minecraft.client.sound.PositionedSoundInstance.ui(
                                        net.minecraft.sound.SoundEvents.ENTITY_COW_AMBIENT, 1.0f, 1.0f));
//?} else {
                      /*client.getSoundManager().play(
                                net.minecraft.client.sound.PositionedSoundInstance.master(
                                        net.minecraft.sound.SoundEvents.ENTITY_COW_AMBIENT, 1.0f, 1.0f));
*///?}
                    } catch (Exception ignored2) {}
                }
            }
        }

        if (customScale) {
//? if >=1.21.11 {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) startX, (float) startY);
            context.getMatrices().scale(hudScale, hudScale);
            context.getMatrices().translate((float) -startX, (float) -startY);
//?} else {
          /*context.getMatrices().push();
            context.getMatrices().translate(startX, startY, 0);
            context.getMatrices().scale(hudScale, hudScale, 1.0f);
            context.getMatrices().translate(-startX, -startY, 0);
*///?}
        }

        int extraTop = 0;
        if (orientation == ArmorModule.ArmorOrientation.HORIZONTAL && textMode != ArmorModule.DurabilityTextMode.NONE) {
            extraTop = (textMode == ArmorModule.DurabilityTextMode.VALUE && ArmorModule.isShowMaxDurability()) ? 18 : 10;
        }
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

                float ratio = 1.0f;
                int remaining = 100;
                int maxDamage = 100;
                int durColor = 0xFF00FF00;

                if (stack.isDamageable() && stack.getMaxDamage() > 0) {
                    maxDamage = stack.getMaxDamage();
                    ratio = (float) (maxDamage - stack.getDamage()) / (float) maxDamage;
                    ratio = Math.max(0.0f, Math.min(1.0f, ratio));
                    remaining = Math.max(0, maxDamage - stack.getDamage());
                    durColor = 0xFF000000 | stack.getItemBarColor();
                }

                // 3. Durability Bar at the bottom - matching vanilla item durability bars and colors
                if (showBar) {
                    int barX = curX + 2;
                    int barY = curY + 18;
                    int barW = 16;
                    int filledW = Math.max(1, Math.round(ratio * barW));

                    context.fill(barX, barY, barX + barW, barY + 1, 0xFF000000);
                    context.fill(barX, barY, barX + filledW, barY + 1, durColor);
                }

                // 4. Durability Text (nad slotem w poziomie lub obok w pionie)
                if (textMode != ArmorModule.DurabilityTextMode.NONE) {
                    if (orientation == ArmorModule.ArmorOrientation.HORIZONTAL) {
                        if (textMode == ArmorModule.DurabilityTextMode.VALUE && ArmorModule.isShowMaxDurability()) {
                            // Linia 1 (góra): Maksymalna wytrzymałość w jednolitym szarym kolorze
                            String maxTxt = String.valueOf(maxDamage);
                            int maxTw = client.textRenderer.getWidth(maxTxt);
                            int maxTextX = curX + (slotSize - maxTw) / 2;
                            int maxTextY = curY - 18;
                            context.drawText(client.textRenderer, maxTxt, maxTextX, maxTextY, 0xFFA0A0AB, true);

                            // Linia 2 (dół): Obecna wytrzymałość w kolorze paska/wytrzymałości
                            String curTxt = String.valueOf(remaining);
                            int curTw = client.textRenderer.getWidth(curTxt);
                            int curTextX = curX + (slotSize - curTw) / 2;
                            int curTextY = curY - 9;
                            context.drawText(client.textRenderer, curTxt, curTextX, curTextY, durColor, true);
                        } else {
                            int pct = Math.max(0, Math.min(100, Math.round(ratio * 100)));
                            String txt = (textMode == ArmorModule.DurabilityTextMode.PERCENT) ? (pct + "%") : String.valueOf(remaining);
                            int tw = client.textRenderer.getWidth(txt);
                            int textX = curX + (slotSize - tw) / 2;
                            int textY = curY - 9;
                            context.drawText(client.textRenderer, txt, textX, textY, durColor, true);
                        }
                    } else {
                        // Orientacja pionowa (tekst po prawej stronie slotu)
                        if (textMode == ArmorModule.DurabilityTextMode.VALUE && ArmorModule.isShowMaxDurability()) {
                            String curTxt = String.valueOf(remaining);
                            int curTw = client.textRenderer.getWidth(curTxt);
                            int textX = curX + slotSize + 4;
                            int textY = curY + (slotSize - 8) / 2;
                            context.drawText(client.textRenderer, curTxt, textX, textY, durColor, true);
                            context.drawText(client.textRenderer, "§7/" + maxDamage, textX + curTw, textY, 0xFFA0A0AB, true);
                        } else {
                            int pct = Math.max(0, Math.min(100, Math.round(ratio * 100)));
                            String txt = (textMode == ArmorModule.DurabilityTextMode.PERCENT) ? (pct + "%") : String.valueOf(remaining);
                            int textX = curX + slotSize + 4;
                            int textY = curY + (slotSize - 8) / 2;
                            context.drawText(client.textRenderer, txt, textX, textY, durColor, true);
                        }
                    }
                }
            } else if (ArmorModule.isShowEmptySlots() && style != ArmorModule.ArmorStyle.SIMPLE) {
                // Empty slot indicator
                context.fill(curX + 6, curY + 6, curX + slotSize - 6, curY + slotSize - 6, 0x15FFFFFF);
            }
        }

        if (customScale) {
//? if >=1.21.11 {
            context.getMatrices().popMatrix();
//?} else {
          /*context.getMatrices().pop();
*///?}
        }
    }

    private static void drawSlotBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
