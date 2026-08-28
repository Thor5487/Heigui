package com.iq200.mixin.mixins;


import com.iq200.heigui.utils.render.CustomRenderType;
import com.iq200.heigui.utils.render.ESPState;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BeaconRenderer.class)
public class MixinBeaconRenderer {
    @Redirect(
            // 對準 BeaconRenderer 內部擁有 10 個參數的 submitBeaconBeam
            method = "submitBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/resources/Identifier;FFIIIFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;beaconBeam(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;"
            )
    )
    private static RenderType redirectBeaconBeamRenderType(Identifier beamLocation, boolean translucent) {
        // 如果這個光柱是我們自訂的 ESP 標記 (開關打開狀態)
        if (ESPState.isRenderingCustomESP) {
            // 直接回傳我們寫好的穿牆版 RenderType
            return CustomRenderType.BEACON_ESP;
        }

        // 否則乖乖回傳原版的光柱 RenderType，讓世界裡的烽火台維持正常
        return net.minecraft.client.renderer.rendertype.RenderTypes.beaconBeam(beamLocation, translucent);
    }
}
