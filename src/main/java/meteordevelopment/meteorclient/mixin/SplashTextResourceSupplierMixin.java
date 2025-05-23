/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.config.Config;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Random;

@Mixin(SplashTextResourceSupplier.class)
public abstract class SplashTextResourceSupplierMixin
{
    @Unique
    private static final Random random = new Random();
    @Unique
    private final List<String> meteorSplashes = getMeteorSplashes();
    @Unique
    private boolean override = true;

    @Unique
    private static List<String> getMeteorSplashes()
    {
        return List.of(
            "§bPantheraKitty :3",
            "§bMeow :3",
            "§bKittyWare :3"
        );
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void onApply(CallbackInfoReturnable<SplashTextRenderer> cir)
    {
        if (Config.get() == null || !Config.get().titleScreenSplashes.get()) return;

        if (override)
            cir.setReturnValue(new SplashTextRenderer(meteorSplashes.get(random.nextInt(meteorSplashes.size()))));
        override = !override;
    }

}
