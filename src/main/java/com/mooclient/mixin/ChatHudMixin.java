package com.mooclient.mixin;

import com.mooclient.module.modules.ChatModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into ChatHud to implement:
 * - Transparent Chat Background (zero background opacity)
 * - Unlimited Chat History (expands 100 message limit to 16384)
 * - Smooth Chat Animation (smooth sliding transition when new messages arrive)
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    private boolean mooClient$pushedSmoothMatrix = false;

    /**
     * Transparent chat background toggle: skip background fill when enabled.
     */
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

    /**
     * Text Shadow toggle in Chat
     */
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

    /**
     * Smooth chat animation: Push matrix and slide downwards smoothly when new messages arrive.
     */
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

    /**
     * Trigger smooth animation when a new visible line is added.
     */
    @Inject(method = "addVisibleMessage", at = @At("HEAD"))
    private void mooClient$onAddVisibleMessage(ChatHudLine message, CallbackInfo ci) {
        ChatModule.onMessageAdded();
    }

    /**
     * Unlimited chat history: modify the default limit of 100 messages to 16384.
     */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", constant = @Constant(intValue = 100), require = 0)
    private int mooClient$unlimitedHistory(int original) {
        return ChatModule.isUnlimitedChat() ? 16384 : original;
    }

    @ModifyConstant(method = "addVisibleMessage", constant = @Constant(intValue = 100), require = 0)
    private int mooClient$unlimitedVisible(int original) {
        return ChatModule.isUnlimitedChat() ? 16384 : original;
    }
}
