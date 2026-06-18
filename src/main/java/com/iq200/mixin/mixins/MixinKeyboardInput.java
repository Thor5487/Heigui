package com.iq200.mixin.mixins;


import com.iq200.heigui.utils.camera.CameraHandler;
import com.iq200.heigui.utils.camera.ClientRotationHandler;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Redirect(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/KeyboardInput;keyPresses:Lnet/minecraft/world/entity/player/Input;", opcode = Opcodes.PUTFIELD))
    private void onTick(KeyboardInput instance, Input value) {
        instance.keyPresses = ClientRotationHandler.adjustInputsForRotation(CameraHandler.onPrePollInputs(value));
    }
}
