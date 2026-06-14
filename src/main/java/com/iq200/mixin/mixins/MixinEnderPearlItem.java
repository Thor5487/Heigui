package com.iq200.mixin.mixins;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack; // 🌟 補上 ItemStack 的 Import
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderpearlItem.class)
public class MixinEnderPearlItem {

    // 攔截 use 方法
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void preventClientSidePearlDecrement(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {

        // 🌟 只干涉客戶端 (Client) 的邏輯
        if (level.isClientSide()) {
            EnderpearlItem self = (EnderpearlItem) (Object) this;

            // 🌟 關鍵修復：抓取玩家手上的珍珠 (ItemStack)
            ItemStack itemStack = player.getItemInHand(hand);

            // 2. 觸發物品冷卻時間 (🌟 這裡正確傳入 itemStack)
            player.getCooldowns().addCooldown(itemStack, 20);

            // 3. 增加統計數據
            player.awardStat(Stats.ITEM_USED.get(self));

            // 🔥 4. 新版 1.21 寫法：直接回傳 SUCCESS！
            // 我們跳過了扣除珍珠數量的程式碼，讓數量完全交由伺服器決定
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}