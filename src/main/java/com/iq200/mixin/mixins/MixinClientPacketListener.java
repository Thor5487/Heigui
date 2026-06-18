package com.iq200.mixin.mixins;

import com.iq200.heigui.features.impl.skyblock.TeleportOptimization;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.iq200.heigui.events.GuiEvent;
import com.iq200.heigui.events.PacketEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.iq200.heigui.Heigui.getMc;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Shadow
    public abstract Connection getConnection();

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showNetworkCharts()Z"))
    private boolean alwaysSendPing(boolean original) {
        return true;
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onSetSlot(ClientboundContainerSetSlotPacket clientboundContainerSetSlotPacket, CallbackInfo ci) {
        if (getMc().screen instanceof AbstractContainerScreen<?> container)
            new GuiEvent.SlotUpdate(getMc().screen, clientboundContainerSetSlotPacket, container.getMenu()).postAndCatch();
    }

    @WrapOperation(method = "handleBundlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void wrapPacketHandle(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if (new PacketEvent.Receive(packet).postAndCatch()) return;
        original.call(packet, listener);
    }

    // ==========================================
    // TeleportOptimization (NoRotate) 注入點
    // ==========================================
    @Inject(
            method = "handleMovePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void onPreHandlePlayerMove(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        // 呼叫 Kotlin 模組裡 @JvmStatic 的 handleTp 方法
        TeleportOptimization.handleTp(packet, this.getConnection(), ci);
    }
}