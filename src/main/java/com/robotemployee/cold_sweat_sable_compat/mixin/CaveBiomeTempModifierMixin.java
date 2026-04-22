package com.robotemployee.cold_sweat_sable_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.momosoftworks.coldsweat.api.temperature.modifier.CaveBiomeTempModifier;
import com.robotemployee.cold_sweat_sable_compat.ColdSweatSableCompat;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CaveBiomeTempModifier.class)
public class CaveBiomeTempModifierMixin {
    // so this more or less just checks the biome you're in
    // we want to make sure that this isn't like a plotyard position or anything ( i have no idea how it works )

    @SuppressWarnings("UnstableApiUsage")
    @WrapOperation(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos wrapBlockPos(LivingEntity instance, Operation<BlockPos> original) {
        BlockPos originalResult = original.call(instance);
        SableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;
        return companion.isInPlotGrid(instance.level(), originalResult) ? BlockPos.containing(companion.projectOutOfSubLevel(instance.level(), originalResult.getCenter())) : originalResult;
    }

    @SuppressWarnings("UnstableApiUsage")
    @WrapOperation(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY()D"))
    private double wrapGetY(LivingEntity instance, Operation<Double> original) {
        SableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;
        return companion.isInPlotGrid(instance) ? companion.projectOutOfSubLevel(instance.level(), instance.position()).y() : original.call(instance);
    }
}
