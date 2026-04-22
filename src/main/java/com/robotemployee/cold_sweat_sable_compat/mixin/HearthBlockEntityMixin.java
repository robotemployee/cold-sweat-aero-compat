package com.robotemployee.cold_sweat_sable_compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager;
import com.robotemployee.cold_sweat_sable_compat.ColdSweatSableCompat;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

@Mixin(HearthBlockEntity.class)
public class HearthBlockEntityMixin {

    @Unique
    private static final Logger csac$LOGGER = LogUtils.getLogger();

    // we want it to check that AABB against aero
    // also
    // dude
    // this wrap operation thing
    // is fucking goated
    // anyways

    // this first one is to make sure it's considering its worldly / non-plotyard position when getting entities
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<Entity> getEntities(Level instance, Entity entity, AABB boundingBox, Predicate<? super Entity> predicate, Operation<List<Entity>> original) {
        Vec3 position = boundingBox.getCenter();
        // suppressed because Vec3 inherits from Position and they just changed it to Position so presumably it's more generic
        // but a bunch of alarms go off because the Vec3 one (which it selects automatically as the overload) is going away
        //@SuppressWarnings("UnstableApiUsage")
        Vec3 worldPosition = ColdSweatSableCompat.SABLE_COMPANION.projectOutOfSubLevel(instance, position);

        List<Entity> originalEntities = original.call(instance, entity, boundingBox, predicate);
        double size = boundingBox.getXsize();
        AABB projectedAABB = AABB.ofSize(worldPosition, size, size, size);
        List<Entity> entitiesFromTheOtherSide = instance.getEntities((Entity) null, projectedAABB, EntityTempManager::isTemperatureEnabled);
        originalEntities.addAll(entitiesFromTheOtherSide);
        // make it into a set first to prevent duplicates
        //csac$LOGGER.info("Entities in the area: " + originalEntities);
        return new HashSet<>(originalEntities).stream().toList();
    }

    // this second one is to make it check its projected fill stuff against the player's
    // as in, the hearth does a fill algorithm thing to figure out all the positions that it's heating up
    // then it checks if any of the player's positions are touching the fill's
    // the problem is that the hearth's position is millions of blocks away in the plotyard
    // so all this is doing is making it so that our player's positions are being transformed into plotyard ones before it does its check

    // also could use ModifyArg but i uhkljgkl;kghljldj;lk forgot. whatever id have to do an object this etc whatever hip spaloo round the rosy cast otherwise

    // this code SUCKS capital S SUCKS
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lcom/momosoftworks/coldsweat/common/blockentity/HearthBlockEntity;isAffectingPos(Ljava/util/List;)Z"))
    private boolean modifyCheckedAffectingPositions(HearthBlockEntity instance, List<BlockPos> positions, Operation<Boolean> original) {
        List<BlockPos> usedPositions;
        if (ColdSweatSableCompat.SABLE_COMPANION.isInPlotGrid(instance.getLevel(), instance.getBlockPos())) {
            //csac$LOGGER.info("we're in a plot grid, currently working with " + positions + " and we're at " + instance.getBlockPos());
            ActiveSableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;
            SubLevel subLevel = companion.getContaining(instance.getLevel(), instance.getBlockPos());
            //csac$LOGGER.info("sublevel: " + subLevel);
            if (subLevel == null) {
                //csac$LOGGER.info("sublevel is null :(");
                usedPositions = positions;
            } else {
                usedPositions = positions.stream().map(position -> {
                    return BlockPos.containing(subLevel.logicalPose().transformPositionInverse(position.getCenter()));
                }).toList();
            }
        } else usedPositions = positions;
        //csac$LOGGER.info("resulting positions: " + usedPositions);
        return original.call(instance, usedPositions);
    }
}
