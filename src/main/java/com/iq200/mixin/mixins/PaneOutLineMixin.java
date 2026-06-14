package com.iq200.mixin.mixins;


import com.iq200.heigui.features.impl.mining.BigPane;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossCollisionBlock.class)
public class PaneOutLineMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void injectOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape voxelShape = BigPane.INSTANCE.getShape(state);

        if (voxelShape != null) {
            cir.setReturnValue(voxelShape);
        }
    }

}
