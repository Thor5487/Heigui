package com.iq200.mixin.mixins;

import com.iq200.heigui.events.BlockInteractEvent;
import com.iq200.heigui.events.EntityInteractEvent;
import com.iq200.heigui.events.PlayerInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"), cancellable = true)
    private void cancelBlockUse(CallbackInfo ci) {
        if (!(this.hitResult instanceof BlockHitResult blockHitResult)) return;
        if ((new BlockInteractEvent(blockHitResult.getBlockPos()).postAndCatch())) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/EntityHitResult;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"), cancellable = true)
    private void cancelEntityUse(CallbackInfo ci) {
        if (!(this.hitResult instanceof EntityHitResult entityHitResult)) return;
        if (new EntityInteractEvent(entityHitResult.getLocation(), entityHitResult.getEntity()).postAndCatch()) ci.cancel();
    }


    @Shadow
    public LocalPlayer player;

    @Shadow
    public MultiPlayerGameMode gameMode;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onAttack(CallbackInfoReturnable<Boolean> cir) {
        if (player != null && !player.isHandsBusy()) {
            // 發布 Attack 事件，如果被模組 cancel，就直接攔截原版攻擊
            if (new PlayerInputEvent.Attack(hitResult).postAndCatch()) {
                cir.setReturnValue(true); // 根據原版邏輯，這裡回傳 true 來中止後續判定
            }
        }
    }

    // 攔截右鍵使用
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void onUseItem(CallbackInfo ci) {
        if (player != null && !gameMode.isDestroying() && !player.isHandsBusy()) {
            // 發布 Use 事件，帶入當前玩家的視角
            if (new PlayerInputEvent.Use(hitResult, player.getYRot(), player.getXRot()).postAndCatch()) {
                ci.cancel(); // 如果被模組 cancel，就直接取消原版右鍵動作
            }
        }
    }

}