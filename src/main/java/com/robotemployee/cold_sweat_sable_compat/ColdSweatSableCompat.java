package com.robotemployee.cold_sweat_sable_compat;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.companion.SableCompanion;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ColdSweatSableCompat.MODID)
public class ColdSweatSableCompat {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = "cold_sweat_sable_compat";

    public static final ActiveSableCompanion SABLE_COMPANION = (ActiveSableCompanion) SableCompanion.INSTANCE;

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public ColdSweatSableCompat(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }
}
