package com.robotemployee.cold_sweat_sable_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.robotemployee.cold_sweat_sable_compat.ColdSweatSableCompat;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BiomeTempModifier.class)
public class BiomeTempModifierMixin {

    // i don't think i even have to do this but im going to anyway for the sake of it
    // .1x coder hours rn
    // (always)
    @SuppressWarnings("UnstableApiUsage")
    @WrapOperation(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos makeBlockPosBeNotPlotyard(LivingEntity instance, Operation<BlockPos> original) {
        BlockPos originalPos = original.call(instance);
        SableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;
        if (companion.isInPlotGrid(instance.level(), originalPos)) return BlockPos.containing(companion.projectOutOfSubLevel(instance.level(), originalPos.getCenter()));
        return originalPos;
    }

    // structure temp is only used in the above function where we just adjusted the position
}
