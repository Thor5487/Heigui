package com.iq200.mixin.mixins;

import com.iq200.heigui.events.InputEvent;
import com.iq200.heigui.events.TurnPlayerEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.SmoothDouble;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Shadow
    @Final
    private SmoothDouble smoothTurnX;

    @Shadow
    @Final
    private SmoothDouble smoothTurnY;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMousePress(long windowHandle, MouseButtonInfo mouseButtonInfo, int action, CallbackInfo ci) {
        int buttonId = mouseButtonInfo.button();

        InputConstants.Key inputKey = InputConstants.Type.MOUSE.getOrCreate(buttonId);

        if (new InputEvent(inputKey, action).postAndCatch()) {
            ci.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(double d, CallbackInfo ci) {
        if (new TurnPlayerEvent(d, this.accumulatedDX, this.accumulatedDY, this.smoothTurnX, this.smoothTurnY).postAndCatch()) {
            ci.cancel();
        }
    }
}
