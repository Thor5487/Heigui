package com.iq200.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.iq200.heigui.events.EntityDataUpdateEvent;
import com.iq200.heigui.events.GuiEvent;
import com.iq200.heigui.events.PacketEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.iq200.heigui.Heigui.getMc;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showNetworkCharts()Z"))
    private boolean alwaysSendPing(boolean original) {
        return true;
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onSetSlot(ClientboundContainerSetSlotPacket clientboundContainerSetSlotPacket, CallbackInfo ci) {
        var screen = getMc().gui.screen();
        if (screen instanceof AbstractContainerScreen<?> container)
            new GuiEvent.SlotUpdate(screen, clientboundContainerSetSlotPacket, container.getMenu()).postAndCatch();
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void heigui$onEntityDataUpdate(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        var level = getMc().level;
        if (level == null) return;

        var entity = level.getEntity(packet.id());
        if (entity != null) new EntityDataUpdateEvent(entity).postAndCatch();
    }

    @WrapOperation(method = "handleBundlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void wrapPacketHandle(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if (new PacketEvent.Receive(packet).postAndCatch()) return;
        original.call(packet, listener);
    }
}
