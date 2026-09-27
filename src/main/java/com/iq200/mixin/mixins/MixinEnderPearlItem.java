package com.iq200.mixin.mixins;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderpearlItem.class)
public class MixinEnderPearlItem {


    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void preventClientSidePearlDecrement(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {


        if (level.isClientSide()) {
            EnderpearlItem self = (EnderpearlItem) (Object) this;


            ItemStack itemStack = player.getItemInHand(hand);


            player.getCooldowns().addCooldown(itemStack, 20);


            player.awardStat(Stats.ITEM_USED.get(self));



            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}