package com.robotemployee.cold_sweat_aero_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.momosoftworks.coldsweat.api.temperature.modifier.EntityClimateTempModifier;
import com.robotemployee.cold_sweat_aero_compat.ColdSweatAeroCompat;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityClimateTempModifier.class)
public class EntityClimateTempModifierMixin {

    @SuppressWarnings("UnstableApiUsage")
    @WrapOperation(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos wrapBlockPos(LivingEntity instance, Operation<BlockPos> original) {
        BlockPos originalResult = original.call(instance);
        SableCompanion companion = ColdSweatAeroCompat.SABLE_COMPANION;
        return companion.isInPlotGrid(instance.level(), originalResult) ? BlockPos.containing(companion.projectOutOfSubLevel(instance.level(), originalResult.getCenter())) : originalResult;
    }

    // todo look for that climateData.test() thing / where it goes
}
