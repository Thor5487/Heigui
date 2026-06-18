package com.iq200.mixin.accessors;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface ILocalPlayer {

    @Accessor("yRotLast")
    void setYRotLast(float yaw);

    @Accessor("xRotLast")
    void setXRotLast(float pitch);
}
