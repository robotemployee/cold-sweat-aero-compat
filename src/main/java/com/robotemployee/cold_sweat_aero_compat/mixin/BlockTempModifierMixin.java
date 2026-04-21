package com.robotemployee.cold_sweat_aero_compat.mixin;

import com.mojang.logging.LogUtils;
import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import com.momosoftworks.coldsweat.api.temperature.modifier.BlockTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import com.momosoftworks.coldsweat.core.advancement.trigger.BlockAffectTempTrigger;
import com.momosoftworks.coldsweat.core.init.ModAdvancementTriggers;
import com.momosoftworks.coldsweat.util.entity.DummyPlayer;
import com.momosoftworks.coldsweat.util.math.CSMath;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import com.robotemployee.cold_sweat_aero_compat.ColdSweatAeroCompat;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import oshi.util.tuples.Triplet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Mixin(BlockTempModifier.class)
public class BlockTempModifierMixin {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    List<Triplet<BlockPos, BlockTemp, Double>> triggers;

    @Shadow
    Map<ChunkPos, ChunkAccess> chunks;

    @Shadow
    Map<BlockTemp, Double> blockTempTotals;

    @Shadow
    Map<BlockPos, BlockState> stateCache;

    @Shadow
    private boolean areAnyBlockTempsInRange(Collection<BlockTemp> blockTemps) {
        throw new IllegalStateException();
    }

    @Shadow
    private double getGroupTotal(BlockTemp blockTemp) {
        throw new IllegalStateException();
    }


    @Inject(method = "calculate", at = @At("HEAD"), cancellable = true)
    private void mutilateCalculate(LivingEntity entity, Temperature.Trait trait, CallbackInfoReturnable<Function<Double, Double>> cir) {
        BlockTempModifier self = (BlockTempModifier) (Object) this;
        int range = self.getNBT().contains("RangeOverride", 3) ? self.getNBT().getInt("RangeOverride") : (Integer) ConfigSettings.BLOCK_RANGE.get();

        ActiveSableCompanion companion = ColdSweatAeroCompat.SABLE_COMPANION;
        // first, turn into worldly coordinates
        BlockPos checked = companion.isInPlotGrid(entity) ? BlockPos.containing(companion.projectOutOfSubLevel(entity.level(), entity.position())) : entity.blockPosition();
        AABB aabb = new AABB(checked).inflate(range);
        Iterable<SubLevel> subLevels = companion.getAllIntersecting(entity.level(), new BoundingBox3d(aabb));
        // if there are no sublevels, just return
        // how the fuck would you be in the plotyard and not have a sublevel at the projected position
        if (subLevels == null || !subLevels.iterator().hasNext()) return;

        // get a list of Vector3s to check
        // plotyard positions for the sublevels that are around
        ArrayList<Vec3> placesToCheck = new ArrayList<>();
        for (SubLevel subLevel : subLevels) placesToCheck.add(subLevel.logicalPose().transformPositionInverse(entity.position()));
        placesToCheck.add(entity.position());

        blockTempTotals.clear();
        stateCache.clear();
        triggers.clear();

        boolean shouldTickAdvancements = self.getTicksExisted() % 20 == 0;
        for (Vec3 placeToCheck : placesToCheck) {
            // note that stuff is passed and modified in this
            csac$evaluateBlockPos(
                    entity,
                    placeToCheck,
                    entity.level(),
                    BlockPos.containing(placeToCheck),
                    range,
                    shouldTickAdvancements,
                    chunks,
                    stateCache,
                    blockTempTotals,
                    triggers,
                    this::areAnyBlockTempsInRange,
                    this::getGroupTotal
            );
        }

        if (entity instanceof ServerPlayer player) {
            if (shouldTickAdvancements) {
                for(Triplet<BlockPos, BlockTemp, Double> trigger : triggers) {
                    ((BlockAffectTempTrigger) ModAdvancementTriggers.BLOCK_AFFECTS_TEMP.value()).trigger(player, (BlockPos)trigger.getA(), (Double)trigger.getC(), (Double)this.blockTempTotals.get(trigger.getB()));
                }
            }
        }

        while(this.chunks.size() >= 16) {
            this.chunks.remove(this.chunks.keySet().iterator().next());
        }

        cir.setReturnValue((temp) -> {
            for(Map.Entry<BlockTemp, Double> entry : this.blockTempTotals.entrySet()) {
                BlockTemp blockTemp = (BlockTemp)entry.getKey();
                double min = blockTemp.minTemperature();
                double max = blockTemp.maxTemperature();
                if (CSMath.betweenInclusive(temp, min, max)) {
                    double effectValue = (Double)entry.getValue();
                    temp = CSMath.clamp(temp + effectValue, min, max);
                }
            }

            return temp;
        });
    }

    // this is chock full of copium
    // also made it take lambdas because uhhhh i forgor actually lmfao. something about encapsulation
    @Unique
    private void csac$evaluateBlockPos(
            LivingEntity entity,
            Vec3 positionToPlaceDummy,
            Level level,
            BlockPos blockPos,
            int range,
            boolean shouldTickAdvancements,
            Map<ChunkPos, ChunkAccess> chunks,
            Map<BlockPos, BlockState> stateCache,
            Map<BlockTemp, Double> blockTempTotals,
            List<Triplet<BlockPos, BlockTemp, Double>> triggers,
            Function<Collection<BlockTemp>, Boolean> areAnyBlockTempsInRangeFunction,
            Function<BlockTemp, Double> getGroupTotalFunction

    ) {
        // im going to touch the forbidden fruit
        DummyPlayer dummyPlayer = WorldHelper.getDummyPlayer(level);
        dummyPlayer.setPos(positionToPlaceDummy);

        // 99.9% not my code \/ \/ \/
        int entX = blockPos.getX();
        int entY = blockPos.getY();
        int entZ = blockPos.getZ();
        BlockPos.MutableBlockPos blockpos = new BlockPos.MutableBlockPos();

        for(int x = -range; x < range; ++x) {
            for(int z = -range; z < range; ++z) {
                ChunkPos chunkPos = new ChunkPos(entX + x >> 4, entZ + z >> 4);
                ChunkAccess chunk = (ChunkAccess)chunks.get(chunkPos);
                if (chunk == null) {
                    chunks.put(chunkPos, chunk = WorldHelper.getChunk(level, chunkPos));
                }

                if (chunk != null) {
                    for(int y = -range; y < range; ++y) {
                        blockpos.set(entX + x, entY + y, entZ + z);
                        BlockState state = (BlockState)stateCache.get(blockpos);
                        if (state == null) {
                            LevelChunkSection section = WorldHelper.getChunkSection(chunk, blockpos.getY());
                            state = section.getBlockState(blockpos.getX() & 15, blockpos.getY() & 15, blockpos.getZ() & 15);
                            stateCache.put(blockpos.immutable(), state);
                        }

                        if (!state.isAir()) {
                            Collection<BlockTemp> blockTemps = BlockTempRegistry.getBlockTempsFor(state);
                            if (!blockTemps.isEmpty() && (blockTemps.size() != 1 || !blockTemps.contains(BlockTempRegistry.DEFAULT_BLOCK_TEMP)) && areAnyBlockTempsInRangeFunction.apply(blockTemps)) {
                                Vec3 pos = Vec3.atCenterOf(blockpos);
                                // now this use of entity i've replaced with our dummy player. i mean look at it. it's obviously positional
                                Vec3 playerClosest = WorldHelper.getClosestPointOnEntity(dummyPlayer, pos);
                                int[] blocks = new int[1];
                                Vec3 ray = pos.subtract(playerClosest);
                                Direction direction = Direction.getNearest(ray.x, ray.y, ray.z);
                                WorldHelper.forBlocksInRay(playerClosest, pos, level, chunk, stateCache, (rayState, bpos) -> {
                                    if (!bpos.equals(blockpos) && WorldHelper.isSpreadBlocked(level, rayState, bpos, direction, direction)) {
                                        int var10002 = blocks[0]++;
                                    }

                                }, 3);
                                double distance = CSMath.getDistance(playerClosest, pos);

                                for(BlockTemp blockTemp : blockTemps) {
                                    if (blockTemp.isValid(level, blockpos, state)) {
                                        // this use of entity is being kept because i don't believe this is meant to calculate distance
                                        // it's being fed distance already, probably just an equation implementation thing
                                        double temperature = blockTemp.getTemperature(level, entity, state, blockpos, distance);
                                        if (temperature != (double)0.0F) {
                                            double tempToAdd = blockTemp.fade() ? CSMath.blend(temperature, (double)0.0F, distance, (double)0.5F, blockTemp.range()) : temperature;
                                            double blockTempTotal = (Double)blockTempTotals.getOrDefault(blockTemp, (double)0.0F);
                                            double blockGroupTotal = getGroupTotalFunction.apply(blockTemp);
                                            double blockGroupDelta = blockGroupTotal - blockTempTotal;
                                            if (blockTemp.logarithmic()) {
                                                double newTotal = Math.pow(Math.pow(blockTempTotal, 1.923076923076923) + tempToAdd, 0.52);
                                                double delta = newTotal - blockTempTotal;
                                                delta /= (double)(blocks[0] + 1);
                                                blockTempTotals.put(blockTemp, CSMath.clamp(blockTempTotal + delta, blockTemp.minEffect() + blockGroupDelta, blockTemp.maxEffect() - blockGroupDelta));
                                            } else {
                                                tempToAdd /= (double)(blocks[0] + 1);
                                                double newTotal = blockTempTotal + tempToAdd;
                                                blockTempTotals.put(blockTemp, CSMath.clamp(newTotal, blockTemp.minEffect() + blockGroupDelta, blockTemp.maxEffect() - blockGroupDelta));
                                            }

                                            if (shouldTickAdvancements) {
                                                triggers.add(new Triplet(blockpos, blockTemp, distance));
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
