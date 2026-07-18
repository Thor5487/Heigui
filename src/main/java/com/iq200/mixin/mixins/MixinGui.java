package com.iq200.mixin.mixins;

import com.iq200.heigui.events.HudRenderEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MixinGui {

    // 攔截 HUD 的狀態提取階段。
    // 如果編譯時報錯找不到 extractRenderState，請將 method 名稱改為 "render"
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        new HudRenderEvent(graphics).postAndCatch();
    }
}