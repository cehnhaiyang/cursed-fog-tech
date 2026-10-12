package com.soulforge.soulforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = SoulForge.MOD_ID)
public class SoulForgeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_SUPERHEATED_FLUID_DANGER = BUILDER
            .comment("If true, superheated fluids in incompatible containers will destroy the container and spill.")
            .define("enableSuperheatedFluidDanger", true);

    public static final ModConfigSpec.BooleanValue ENABLE_HAND_HEAT_DAMAGE = BUILDER
            .comment("If true, holding superheated containers without insulated gloves deals damage.")
            .define("enableHandHeatDamage", true);

    public static final ModConfigSpec.BooleanValue ETERNAL_BLAZING_BLOOD_DESTROYS_BLOCKS = BUILDER
            .comment("If true, eternal blazing blood will destroy surrounding blocks and fluids (except bedrock).")
            .define("eternalBlazingBloodDestroysBlocks", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enableSuperheatedFluidDanger;
    public static boolean enableHandHeatDamage;
    public static boolean eternalBlazingBloodDestroysBlocks;

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        enableSuperheatedFluidDanger = ENABLE_SUPERHEATED_FLUID_DANGER.get();
        enableHandHeatDamage = ENABLE_HAND_HEAT_DAMAGE.get();
        eternalBlazingBloodDestroysBlocks = ETERNAL_BLAZING_BLOOD_DESTROYS_BLOCKS.get();
    }
}
