package com.iq200.mixin.mixins;

import com.iq200.heigui.features.impl.dungeon.icefill.IceFill;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class MixinKeyMapping {
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void heigui$blockIceFillSprintKey(CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) return;
        if ((Object) this != minecraft.options.keySprint) return;
        if (!IceFill.INSTANCE.shouldSuppressSprintKey()) return;

        cir.setReturnValue(false);
    }
}
