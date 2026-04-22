package com.robotemployee.cold_sweat_aero_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.momosoftworks.coldsweat.api.temperature.modifier.EntitiesTempModifier;
import com.robotemployee.cold_sweat_aero_compat.ColdSweatAeroCompat;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntitiesTempModifier.class)
public class EntitiesTempModifierMixin {
    @SuppressWarnings("UnstableApiUsage")
    @WrapOperation(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos wrapBlockPosition(LivingEntity instance, Operation<BlockPos> original) {
        SableCompanion companion = ColdSweatAeroCompat.SABLE_COMPANION;
        BlockPos originalResult = original.call(instance);
        return companion.isInPlotGrid(instance.level(), originalResult) ? BlockPos.containing(companion.projectOutOfSubLevel(instance.level(), originalResult.getCenter())) : originalResult;
    }

    @SuppressWarnings("UnstableApiUsage")
    @WrapOperation(method = "calculate", at = @At(value = "INVOKE", target = "Lcom/momosoftworks/coldsweat/util/entity/EntityHelper;getCenterOf(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 wrapGetCenterOf(Entity entity, Operation<Vec3> original) {
        Vec3 originalResult = original.call(entity);
        SableCompanion companion = ColdSweatAeroCompat.SABLE_COMPANION;
        return companion.isInPlotGrid(entity.level(), originalResult) ? companion.projectOutOfSubLevel(entity.level(), originalResult) : originalResult;
    }

    // note that the call to EntityTempData.getTemperatureEffect cares about distance, i've mixined that class too to account
}
