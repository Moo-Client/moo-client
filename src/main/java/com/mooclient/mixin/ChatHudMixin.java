package com.mooclient.mixin;

import com.mooclient.module.modules.ChatModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin into ChatHud to implement:
 * - Transparent Chat Background (zero background opacity)
 * - Unlimited Chat History (expands 100 message limit to Integer.MAX_VALUE, persists across servers)
 * - Stack Messages (collapses consecutive identical messages into [xN])
 * - Anti-ClearChat (prevents server from wiping chat via blank spam)
 * - Smooth Chat Animation (smooth sliding transition when new messages arrive)
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Unique
    private boolean mooClient$pushedSmoothMatrix = false;

    /**
     * Session-persistent message backup. Survives clear() calls and server reconnects.
     */
    @Unique
    private static final List<ChatHudLine> mooClient$sessionMessages = new ArrayList<>();
    @Unique
    private static final List<ChatHudLine.Visible> mooClient$sessionVisible = new ArrayList<>();

    /**
     * Track consecutive identical messages for stacking.
     */
    @Unique
    private static String mooClient$lastMessageText = "";
    @Unique
    private static int mooClient$stackCount = 0;

    // ── Transparent Background ──────────────────────────────────────────────

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"),
        require = 0
    )
    private void mooClient$redirectChatBackgroundFill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (ChatModule.isTransparentBackground()) {
            return;
        }
        context.fill(x1, y1, x2, y2, color);
    }

    // ── Text Shadow Toggle ──────────────────────────────────────────────────

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"),
        require = 0
    )
    private int mooClient$redirectChatTextShadowOrdered(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer, net.minecraft.text.OrderedText text, int x, int y, int color) {
        return context.drawText(textRenderer, text, x, y, color, ChatModule.isTextShadow());
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"),
        require = 0
    )
    private int mooClient$redirectChatTextShadow(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer, net.minecraft.text.Text text, int x, int y, int color) {
        return context.drawText(textRenderer, text, x, y, color, ChatModule.isTextShadow());
    }

    // ── Smooth Chat Animation ───────────────────────────────────────────────

    @Inject(method = "render", at = @At("HEAD"))
    private void mooClient$smoothChatPre(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        ChatModule.updateAnimation(1.0f);
        if (ChatModule.isSmoothChat() && ChatModule.getAnimOffset() > 0.001f) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, ChatModule.getAnimOffset(), 0.0f);
            this.mooClient$pushedSmoothMatrix = true;
        } else {
            this.mooClient$pushedSmoothMatrix = false;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mooClient$smoothChatPost(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (this.mooClient$pushedSmoothMatrix) {
            context.getMatrices().pop();
            this.mooClient$pushedSmoothMatrix = false;
        }
    }

    @Inject(method = "addVisibleMessage", at = @At("HEAD"))
    private void mooClient$onAddVisibleMessage(ChatHudLine message, CallbackInfo ci) {
        ChatModule.onMessageAdded();
    }

    // ── Unlimited Chat History ──────────────────────────────────────────────

    @ModifyConstant(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", constant = @Constant(intValue = 100), require = 0)
    private int mooClient$unlimitedHistory(int original) {
        return ChatModule.isUnlimitedChat() ? Integer.MAX_VALUE : original;
    }

    @ModifyConstant(method = "addVisibleMessage", constant = @Constant(intValue = 100), require = 0)
    private int mooClient$unlimitedVisible(int original) {
        return ChatModule.isUnlimitedChat() ? Integer.MAX_VALUE : original;
    }

    @ModifyConstant(method = "addToMessageHistory", constant = @Constant(intValue = 100), require = 0)
    private int mooClient$unlimitedInputHistory(int original) {
        return ChatModule.isUnlimitedChat() ? Integer.MAX_VALUE : original;
    }

    // ── Anti-ClearChat & Stack Messages ─────────────────────────────────────

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void mooClient$onAddMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        String plain = message.getString().trim();

        // Anti-ClearChat: suppress empty/whitespace-only messages (server spam to clear chat)
        if (ChatModule.isUnlimitedChat() && plain.isEmpty()) {
            ci.cancel();
            return;
        }

        // Stack Messages: collapse consecutive identical messages into [xN]
        if (ChatModule.isStackMessages() && !plain.isEmpty()) {
            if (plain.equals(mooClient$lastMessageText) && !this.messages.isEmpty()) {
                mooClient$stackCount++;

                // Build stacked text: original message + gray [xN]
                MutableText stacked = message.copy().append(
                    Text.literal(" [x" + mooClient$stackCount + "]")
                        .setStyle(Style.EMPTY.withColor(0xAAAAAA))
                );

                MinecraftClient client = MinecraftClient.getInstance();
                int currentTick = (client != null && client.inGameHud != null) ? client.inGameHud.getTicks() : 0;

                // Replace top entry in messages with the updated text and refreshed currentTick
                if (!this.messages.isEmpty()) {
                    this.messages.remove(0);
                    this.messages.add(0, new ChatHudLine(currentTick, stacked, signatureData, indicator));
                }

                // Remove all visible lines belonging to the previous version of this message
                if (!this.visibleMessages.isEmpty()) {
                    int linesToRemove = 1;
                    while (linesToRemove < this.visibleMessages.size() && !this.visibleMessages.get(linesToRemove).endOfEntry()) {
                        linesToRemove++;
                    }
                    for (int i = 0; i < linesToRemove; i++) {
                        this.visibleMessages.remove(0);
                    }
                }

                // Re-wrap the stacked text and insert all lines with updated currentTick
                if (client != null && client.textRenderer != null) {
                    int chatWidth = ChatHud.getWidth(client.options.getChatWidth().getValue());
                    if (indicator != null && indicator.icon() != null) {
                        chatWidth -= indicator.icon().width + 4 + 2;
                    }
                    List<OrderedText> wrapped = net.minecraft.client.util.ChatMessages.breakRenderedChatMessageLines(
                        stacked, chatWidth, client.textRenderer
                    );
                    for (int j = 0; j < wrapped.size(); ++j) {
                        boolean endOfEntry = (j == wrapped.size() - 1);
                        this.visibleMessages.add(0, new ChatHudLine.Visible(
                            currentTick, wrapped.get(j), indicator, endOfEntry
                        ));
                    }
                }

                ChatModule.onMessageAdded();
                ci.cancel();
                return;
            }

            // New unique message: reset stack counter
            mooClient$lastMessageText = plain;
            mooClient$stackCount = 1;
        } else if (!plain.isEmpty()) {
            mooClient$lastMessageText = plain;
            mooClient$stackCount = 1;
        } else {
            mooClient$lastMessageText = "";
            mooClient$stackCount = 0;
        }
    }

    // ── Persist across clear() / server reconnects ──────────────────────────

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void mooClient$onClear(boolean clearHistory, CallbackInfo ci) {
        mooClient$lastMessageText = "";
        mooClient$stackCount = 0;

        if (ChatModule.isUnlimitedChat() && clearHistory) {
            // Save current messages before clear
            if (!this.messages.isEmpty()) {
                mooClient$sessionMessages.clear();
                mooClient$sessionMessages.addAll(this.messages);
                mooClient$sessionVisible.clear();
                mooClient$sessionVisible.addAll(this.visibleMessages);
            }
        }
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void mooClient$afterClear(boolean clearHistory, CallbackInfo ci) {
        if (ChatModule.isUnlimitedChat() && clearHistory) {
            // Restore saved messages after clear
            if (!mooClient$sessionMessages.isEmpty()) {
                this.messages.addAll(mooClient$sessionMessages);
                this.visibleMessages.addAll(mooClient$sessionVisible);
            }
        }
    }

    // ── Prevent server-side message removal ─────────────────────────────────

    @Inject(method = "removeMessage", at = @At("HEAD"), cancellable = true)
    private void mooClient$onRemoveMessage(MessageSignatureData signature, CallbackInfo ci) {
        if (ChatModule.isUnlimitedChat()) {
            ci.cancel();
        }
    }
}
