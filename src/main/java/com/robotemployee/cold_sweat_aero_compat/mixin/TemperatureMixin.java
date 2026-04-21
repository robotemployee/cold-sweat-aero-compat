package com.robotemployee.cold_sweat_aero_compat.mixin;

import com.mojang.logging.LogUtils;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Temperature.class)
public class TemperatureMixin {

    @Inject(
            method = "apply(DLnet/minecraft/world/entity/LivingEntity;Lcom/momosoftworks/coldsweat/api/util/Temperature$Trait;Z[Lcom/momosoftworks/coldsweat/api/temperature/modifier/TempModifier;)D",
            at = @At("TAIL")
    )
    private static void onApply(double currentTemp, LivingEntity entity, Temperature.Trait trait, boolean ignoreTickMultiplier, TempModifier[] modifiers, CallbackInfoReturnable<Double> cir) {
        //LogUtils.getLogger().info("applying temperature: {}, {}, {}, {}, {}", currentTemp, entity, trait, ignoreTickMultiplier, modifiers);
    }
}
