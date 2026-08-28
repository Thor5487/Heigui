package com.iq200.mixin.accessors;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BeaconRenderer.class)
public interface BeaconBeamAccessor {
    @Invoker("submitBeaconBeam") // 請對應原版 BeaconRenderer 內部實際執行繪製的方法名稱
    static void invokeRenderBeam(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            Identifier beamLocation,
            float scale,
            float animationTime,
            int beamStart,
            int height,
            int color,
            float solidBeamRadius,
            float beamGlowRadius
    ) {
        throw new UnsupportedOperationException();
    }
}
