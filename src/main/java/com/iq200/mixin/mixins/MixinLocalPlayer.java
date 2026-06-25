package com.iq200.mixin.mixins;


import com.iq200.heigui.utils.camera.CameraHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;


@Mixin(value = LocalPlayer.class, priority = 2000)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {
    public MixinLocalPlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }


    @ModifyExpressionValue(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float spoofPitch(float original) {
        return CameraHandler.getPitch(original);
    }

    @ModifyExpressionValue(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float spoofYaw(float original) {
        return CameraHandler.getYaw(original);
    }

    // Modify the position used for pick
    @ModifyVariable(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At("STORE"), ordinal = 0)
    private static Vec3 pickPosition(Vec3 positionVector) {
        return CameraHandler.onGetPositionForHit(positionVector);
    }

    // Modify the rotation used for pick
    @ModifyVariable(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At("STORE"), ordinal = 1)
    private static Vec3 pickRotation(Vec3 rotationVector) {
        return CameraHandler.onGetRotationForHit(rotationVector);
    }

}
