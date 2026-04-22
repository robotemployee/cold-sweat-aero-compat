package com.robotemployee.cold_sweat_sable_compat.mixin;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.momosoftworks.coldsweat.api.temperature.modifier.FrigidnessTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WarmthTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.util.entity.DummyPlayer;
import com.momosoftworks.coldsweat.util.math.CSMath;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import com.robotemployee.cold_sweat_sable_compat.ColdSweatSableCompat;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(WorldHelper.class)
public class WorldHelperMixin {

    @Unique
    private static final Logger csac$LOGGER = LogUtils.getLogger();
    // this code sucks
    // hsould prolly find where in aero they do raycasts
    // lazy tho just wanna see if this eve nworks
    // anyways this just checks for if there's a sublevel above your head and if it's blocking your view of the sky
    // won't check if there's a static part of the world blocking your view already
    // but if the usual method thinks it's all clear, we go to also check for a sublevel
    @Inject(method = "canSeeSky", at = @At("TAIL"), cancellable = true)
    private static void considerAirshipOverhead(LevelAccessor levelAccessor, BlockPos pos, int maxDistance, CallbackInfoReturnable<Boolean> cir) {
        // if it's figured out we can't see the sky already, then return early
        if (!cir.getReturnValue()) return;

        // but if it thinks we can see the sky, ask it to consider if an airship is above our head

        AABB aabb = new AABB(pos);
        aabb.setMaxY(aabb.getCenter().y() + maxDistance);

        if (!(levelAccessor instanceof Level level)) return;
        boolean hasSublevel = ColdSweatSableCompat.SABLE_COMPANION.getAllIntersecting(level, new BoundingBox3d(aabb)).iterator().hasNext();
        cir.setReturnValue(!hasSublevel);
    }


    /*
    @Inject(method = "getBlockTemperature", at = @At("TAIL"))
    private static void onGetBlockTemperature(Level level, BlockState block, CallbackInfoReturnable<Double> cir) {
        csac$LOGGER.info("Getting block temperature for " + block + ", equals " + cir.getReturnValue());
    }

     */

    // make it so that it considers the water temperature of the biome
    @ModifyVariable(method = "getWaterTemperatureAt", at = @At("HEAD"), index = 1, argsOnly = true)
    private static BlockPos modifyGetWaterTemperaturePos(BlockPos pos, Level level) {
        ActiveSableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;
        if (companion.isInPlotGrid(level, pos)) return BlockPos.containing(companion.projectOutOfSubLevel(level, pos.getCenter()));
        return pos;
    }

    // INSULATION
    // only feed in non plotyard positions
    @ModifyVariable(method = "getInsulationAt", at = @At("HEAD"), index = 1, argsOnly = true)
    private static BlockPos modifyInsulationPositionToBeOutOfPlotyard(BlockPos pos, Level level) {
        if (ColdSweatSableCompat.SABLE_COMPANION.isInPlotGrid(level, pos)) {
            return BlockPos.containing(ColdSweatSableCompat.SABLE_COMPANION.projectOutOfSubLevel(level, pos.getCenter()));
        }
        return pos;
    }

    // this is inefficient
    // todo test
    @SuppressWarnings("UnstableApiUsage")
    @Inject(method = "getInsulationAt", at = @At("TAIL"), cancellable = true)
    private static void getInsulationAtIncludingSubships(Level level, BlockPos pos, int chunkRadius, CallbackInfoReturnable<Pair<Integer, Integer>> cir) {
        ActiveSableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;

        ArrayList<Pair<Integer, Integer>> results = new ArrayList<>();
        for (SubLevel subLevel : companion.getAllIntersecting(level, new BoundingBox3d(pos))) {
            BlockPos plotyardPos = BlockPos.containing(subLevel.logicalPose().transformPositionInverse(pos.getCenter()));
            results.add(csac$copeGetInsulationAt(level, plotyardPos, chunkRadius));
        }
        // if there are no sublevels just put the fries in the bag
        if (results.isEmpty()) return;

        results.add(cir.getReturnValue());

        int highestMaxCoolingLevel = 0;
        int highestMaxHeatingLevel = 0;

        for (Pair<Integer, Integer> result : results) {
            highestMaxCoolingLevel = Math.max(result.getFirst(), highestMaxCoolingLevel);
            highestMaxHeatingLevel = Math.max(result.getFirst(), highestMaxHeatingLevel);
        }

        //csac$LOGGER.info("resulting max cool: {} max heat: {}", highestMaxCoolingLevel, highestMaxHeatingLevel);
        cir.setReturnValue(Pair.of(highestMaxCoolingLevel, highestMaxHeatingLevel));
    }

    // the purpose of this is to have a version that will not have @ModifyArg run on it
    // / to have a copy of the mixin that i can run that has no chance of causing recursion
    // there's definitely a better way to do this which is why i've named it cope, after my first love
    @Unique
    private static Pair<Integer, Integer> csac$copeGetInsulationAt(Level level, BlockPos pos, int chunkRadius) {
        int maxCoolingLevel = 0;
        int maxHeatingLevel = 0;
        ChunkPos chunkPos = new ChunkPos(pos);

        for(int x = -chunkRadius; x <= chunkRadius; ++x) {
            for(int z = -chunkRadius; z <= chunkRadius; ++z) {
                ChunkAccess chunk = WorldHelper.getChunk(level, chunkPos.x + x, chunkPos.z + z);
                if (chunk != null) {
                    for(BlockPos bePos : chunk.getBlockEntitiesPos()) {
                        BlockEntity be = chunk.getBlockEntity(bePos);
                        if (be instanceof HearthBlockEntity) {
                            HearthBlockEntity hearth = (HearthBlockEntity) be;
                            if (hearth.getPathLookup().contains(pos)) {
                                maxCoolingLevel = Math.max(maxCoolingLevel, hearth.getCoolingLevel());
                                maxHeatingLevel = Math.max(maxHeatingLevel, hearth.getHeatingLevel());
                            }
                        }
                    }
                }
            }
        }

        return Pair.of(maxCoolingLevel, maxHeatingLevel);
    }

    /*
    @Inject(method = "getTemperatureAt", at = @At("HEAD"))
    private static void getTemperatureAt(Level level, BlockPos pos, CallbackInfoReturnable<Double> cir) {
        csac$LOGGER.info("Getting temperature for " + pos);
    }

     */

    // TEMPERATURE

    @Inject(method = "getTemperatureAt", at = @At("HEAD"), cancellable = true)
    private static void modifyGetTemperatureAt(Level level, BlockPos rawPos, CallbackInfoReturnable<Double> cir) {
        ActiveSableCompanion companion = ColdSweatSableCompat.SABLE_COMPANION;
        // if it's in a plot grid turn it into worldly stuff
        BlockPos pos = companion.isInPlotGrid(level, rawPos) ? BlockPos.containing(companion.projectOutOfSubLevel(level, rawPos.getCenter())) : rawPos;

        ArrayList<TempModifier> modifiers = new ArrayList<>();
        ArrayList<Pair<Integer, Integer>> insulations = new ArrayList<>();
        // note that we're inflating the bounding box a bit so that you don't have to be on the sublevel, just close
        // magic number 8 wild guess honestly
        for (SubLevel subLevel : companion.getAllIntersecting(level, new BoundingBox3d((new AABB(pos)).inflate(8)))) {
            Vec3 examined = subLevel.logicalPose().transformPositionInverse(pos.getCenter());
            DummyPlayer dummyPlayer = WorldHelper.getDummyPlayer(level);
            dummyPlayer.setPos(examined);
            modifiers.addAll(csac$getDummyModifiers(dummyPlayer));
            //csac$LOGGER.info("Adding modifiers " + csac$getDummyModifiers(dummyPlayer) + " for position " + examined);

            insulations.add(csac$copeGetInsulationAt(level, BlockPos.containing(examined), 2));
        }
        // if the modifiers are empty, the position wasn't in a sublevel anyway
        // eg it's in the world and there's no sublevels nearby
        if (modifiers.isEmpty()) return;

        // there's sublevels alright. so we need to do our own temperature calculations
        DummyPlayer dummyPlayer = WorldHelper.getDummyPlayer(level);
        dummyPlayer.setPos(pos.getCenter());
        modifiers.addAll(csac$getDummyModifiers(dummyPlayer));
        insulations.add(csac$copeGetInsulationAt(level, pos, 2));

        // okay, now we need to consider insulation
        // get our max insulatons first
        // could use streams but honest to god comparators are annoying as fuck and would defeat the whole condensing / readability point of using said stream
        // can you tell im peeved
        // though the -1 0 1 thing makes a lot of sense, it's so common that you'd only want a "is it greater" true / false with ignorance towards equality
        // and it would be deadass so much better if there was a non-verbose way to just do that
        // anyways mald over
        int maxCooling = 0;
        int maxHeating = 0;
        for (Pair<Integer, Integer> insulation : insulations) {
            maxCooling = Math.max(insulation.getFirst(), maxCooling);
            maxHeating = Math.max(insulation.getFirst(), maxHeating);
        }

        // and you might be thinking, why am i re-doing the insulation stuff specifically in here?
        // well if we're already doing the bounding box check for these subships it's probably best to just use that information
        // instead of running it through another function that will then just get the same information (intersecting ships) over again
        // specifically what i need from it is the tallying up and finding the maxes
        // anyways
        if (maxCooling > 0) modifiers.add(new FrigidnessTempModifier(maxCooling));
        if (maxHeating > 0) modifiers.add(new WarmthTempModifier(maxHeating));

        // the traits should hopefully even themselves out
        cir.setReturnValue(Temperature.apply(0.0F, dummyPlayer, Temperature.Trait.WORLD, modifiers, true));
        return;
        // i
        // FUCK IT GIVES IT THE ENTITY WHICH PROBABLY HAS POSITIONAL CONSIDERATION WHICH WILL AAAAAH. AAH. A.H.AHDSJKLDSAJKLWTJK;LDJKLDK;LSAK;LDSANKL;DA KLJLK okayw
    }

    @Unique
    private static List<TempModifier> csac$getDummyModifiers(DummyPlayer dummy) {
        return Temperature.getModifiers(dummy, Temperature.Trait.WORLD);

    }

    @Unique
    private static double csac$copeGetTemperatureAt(Level level, BlockPos pos) {
        DummyPlayer dummy = WorldHelper.getDummyPlayer(level);
        dummy.setPos(CSMath.getCenterPos(pos));
        List<TempModifier> modifiers = new ArrayList(Temperature.getModifiers(dummy, Temperature.Trait.WORLD));
        Pair<Integer, Integer> maxCoolingHeating = csac$copeGetInsulationAt(level, pos, 2);
        if ((Integer)maxCoolingHeating.getFirst() > 0) {
            modifiers.add(new FrigidnessTempModifier((Integer)maxCoolingHeating.getFirst()));
        }

        if ((Integer)maxCoolingHeating.getSecond() > 0) {
            modifiers.add(new WarmthTempModifier((Integer)maxCoolingHeating.getSecond()));
        }

        return Temperature.apply((double)0.0F, dummy, Temperature.Trait.WORLD, modifiers, true);
    }



}
