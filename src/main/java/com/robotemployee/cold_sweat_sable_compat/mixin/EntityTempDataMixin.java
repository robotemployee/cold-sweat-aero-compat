package com.robotemployee.cold_sweat_sable_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.momosoftworks.coldsweat.data.codec.configuration.EntityTempData;
import com.robotemployee.cold_sweat_sable_compat.ColdSweatSableCompat;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityTempData.class)
public class EntityTempDataMixin {
    @WrapOperation(method = "getTemperatureEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;distanceTo(Lnet/minecraft/world/entity/Entity;)F"))
    private float wrapDistanceTo(Entity instance, Entity entity, Operation<Float> original) {
        SableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;
        if (!companion.isInPlotGrid(instance) && !companion.isInPlotGrid(entity)) return original.call(instance, entity);
        return (float)Math.sqrt(companion.distanceSquaredWithSubLevels(instance.level(), instance.position(), entity.position()));
    }
}
