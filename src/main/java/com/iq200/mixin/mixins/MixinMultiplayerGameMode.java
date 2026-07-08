package com.iq200.mixin.mixins;


import com.iq200.heigui.features.impl.dungeon.SATpFix;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiplayerGameMode {
    @Redirect(
        method = {
                "useItemOn",
                "startDestroyBlock",
                "continueDestroyBlock"
        },
        at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean heigui$ignoreWorldBorder(WorldBorder instance, BlockPos pos) {

        if (SATpFix.INSTANCE.getEnabled()) {
            return true;
        }

        return instance.isWithinBounds(pos);
    }
}
