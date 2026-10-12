package com.soulforge.soulforge;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(SoulForge.MOD_ID)
public class SoulForge {
    public static final String MOD_ID = "soulforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static Identifier prefix(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public SoulForge(IEventBus modEventBus, ModContainer modContainer) {
        SoulForgeRegistries.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.STARTUP, SoulForgeConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        SoulForge.LOGGER.info("SoulForge common setup complete");
    }
}
