package com.robotemployee.cold_sweat_sable_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.momosoftworks.coldsweat.api.temperature.modifier.compat.StormTempModifier;
import com.robotemployee.cold_sweat_sable_compat.ColdSweatSableCompat;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(StormTempModifier.class)
public class StormTempModifierMixin {
    // conditionally loaded in mixin plugin based on whether or not weather2 is loaded

    @SuppressWarnings("UnstableApiUsage")
    @WrapOperation(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos wrapBlockPos(LivingEntity instance, Operation<BlockPos> original) {
        SableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;
        BlockPos originalResult = original.call(instance);
        return companion.isInPlotGrid(instance.level(), originalResult) ? BlockPos.containing(companion.projectOutOfSubLevel(instance.level(), originalResult.getCenter())) : originalResult;
    }

    @SuppressWarnings("UnstableApiUsage")
    @WrapOperation(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 wrapPosition(LivingEntity instance, Operation<Vec3> original) {
        SableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;
        Vec3 originalResult = original.call(instance);
        return companion.isInPlotGrid(instance.level(), originalResult) ? companion.projectOutOfSubLevel(instance.level(), originalResult) : originalResult;
    }
}
