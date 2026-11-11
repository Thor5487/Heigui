package com.iq200.mixin.mixins;

import com.iq200.heigui.events.InputEvent;
import com.iq200.heigui.events.TurnPlayerEvent;
//#if PRIVATE
import com.iq200.heigui.features.impl.floor7.I4Solver;
//#endif
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.SmoothDouble;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//#if PRIVATE
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
//#endif
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

    //#if PRIVATE
    @ModifyArgs(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void heigui$preventI4AimOvershoot(Args args) {
        double[] adjusted = I4Solver.adjustMouseTurnForOvershoot(args.get(0), args.get(1));
        args.set(0, adjusted[0]);
        args.set(1, adjusted[1]);
    }
    //#endif
}
