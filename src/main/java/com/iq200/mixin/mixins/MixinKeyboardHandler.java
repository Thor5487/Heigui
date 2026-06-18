package com.iq200.mixin.mixins;

import com.iq200.heigui.events.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long l, @KeyEvent.Action int action, KeyEvent keyEvent, CallbackInfo ci) {
        InputConstants.Key inputKey = InputConstants.getKey(keyEvent);

        if (new InputEvent(inputKey, action).postAndCatch()) {
            ci.cancel();
        }
    }
}
