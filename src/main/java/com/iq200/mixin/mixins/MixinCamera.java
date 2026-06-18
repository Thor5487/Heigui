package com.iq200.mixin.mixins;

import com.iq200.heigui.events.CameraSetupEvent;
import com.iq200.heigui.utils.camera.CameraHandler;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow
    protected abstract void setRotation(float f, float g);

    @Shadow
    protected abstract void setPosition(Vec3 vec3);

    @Shadow
    private Vec3 position;

    @Shadow
    private float partialTickTime;

    @Shadow
    private float eyeHeightOld;

    @Shadow
    private float eyeHeight;

    @Inject(method = "setup", at = @At("HEAD"))
    private void onCameraSetup(Level level, Entity entity, boolean bl, boolean bl2, float f, CallbackInfo ci) {
        new CameraSetupEvent().postAndCatch(); // 沒有 @JvmStatic
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void setCameraYawPitch(Camera camera, float yaw, float pitch) {
        this.setRotation(CameraHandler.getYaw(yaw), CameraHandler.getPitch(pitch));
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void setCameraPos(Level level, Entity entity, boolean bl, boolean bl2, float f, CallbackInfo ci) {
        this.setPosition(CameraHandler.getPos(new Vec3(this.position.x, this.position.y, this.position.z), this.partialTickTime, this.eyeHeightOld, this.eyeHeight));
    }
}