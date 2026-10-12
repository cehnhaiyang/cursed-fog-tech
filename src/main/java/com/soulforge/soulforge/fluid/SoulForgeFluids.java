package com.soulforge.soulforge.fluid;

import com.soulforge.soulforge.SoulForge;
import com.soulforge.soulforge.SoulForgeRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public class SoulForgeFluids {
    public static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    public static final Identifier WATER_FLOWING = Identifier.withDefaultNamespace("block/water_flow");

    public static final FluidSet MOLTEN_IRON = registerMolten("molten_iron", 1539, 0xFFB87333, 10);
    public static final FluidSet MOLTEN_GOLD = registerMolten("molten_gold", 1067, 0xFFFFD700, 10);
    public static final FluidSet MOLTEN_COPPER = registerMolten("molten_copper", 1083, 0xFFB87333, 10);
    public static final FluidSet MOLTEN_COBALT = registerMolten("molten_cobalt", 1500, 0xFF0047AB, 10);
    public static final FluidSet MOLTEN_NETHERITE = registerMolten("molten_netherite", 3500, 0xFF4A4A4A, 12);
    public static final FluidSet MOLTEN_DIAMOND = registerMolten("molten_diamond", 3500, 0xFFB9F2FF, 12);
    public static final FluidSet MOLTEN_QUARTZ = registerMolten("molten_quartz", 1339, 0xFFEAEAEA, 10);
    public static final FluidSet MOLTEN_OBSIDIAN = registerMolten("molten_obsidian", 1700, 0xFF2D052D, 10);
    public static final FluidSet MOLTEN_TRINITY_ALLOY = registerMolten("molten_trinity_alloy", 5000, 0xFF8A2BE2, 13);
    public static final FluidSet MOLTEN_SINGULARITY = registerMolten("molten_singularity", 10000, 0xFF111111, 15);

    public static final FluidSet BLAZING_BLOOD = registerBlazingBlood("blazing_blood", 1500, 0xFFFF3300, 10);
    public static final FluidSet ACTIVATED_BLAZING_BLOOD = registerBlazingBlood("activated_blazing_blood", 3500, 0xFFFF6600, 12);
    public static final FluidSet IONIZED_BLAZING_BLOOD = registerBlazingBlood("ionized_blazing_blood", 5000, 0xFF99CCFF, 13);
    public static final FluidSet PLASMA_BLAZING_BLOOD = registerBlazingBlood("plasma_blazing_blood", 10000, 0xFF00FFFF, 15);
    public static final FluidSet ETERNAL_BLAZING_BLOOD = registerBlazingBlood("eternal_blazing_blood", 100000000, 0xFFFFFFFF, 15);

    public static final FluidSet MOLTEN_NETHER_HEART = registerMolten("molten_nether_heart", 10000, 0xFF800000, 15);
    public static final FluidSet MOLTEN_ENDER_PEARL = registerMolten("molten_ender_pearl", 3500, 0xFF2E004F, 12);
    public static final FluidSet MOLTEN_NETHER_STAR = registerMolten("molten_nether_star", 2500, 0xFFF0F0F0, 15);

    public static void register() {
    }

    private static FluidSet registerMolten(String name, int temperature, int tintColor, int lightLevel) {
        return registerFluid(name, temperature, tintColor, lightLevel, 3500, 6000);
    }

    private static FluidSet registerBlazingBlood(String name, int temperature, int tintColor, int lightLevel) {
        return registerFluid(name, temperature, tintColor, lightLevel, 2000, 4000);
    }

    private static FluidSet registerFluid(String name, int temperature, int tintColor, int lightLevel, int density, int viscosity) {
        DeferredHolder<FluidType, FluidType> type = SoulForgeRegistries.FLUID_TYPES.register(name,
                () -> new SoulForgeFluidType(FluidType.Properties.create()
                        .descriptionId("fluid." + SoulForge.MOD_ID + "." + name)
                        .temperature(temperature)
                        .density(density)
                        .viscosity(viscosity)
                        .lightLevel(lightLevel)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY),
                        WATER_STILL, WATER_FLOWING, tintColor));

        FluidReg reg = new FluidReg();

        reg.still = SoulForgeRegistries.FLUIDS.register(name,
                () -> new BaseFlowingFluid.Source(makeProperties(type, reg)));
        reg.flowing = SoulForgeRegistries.FLUIDS.register(name + "_flowing",
                () -> new BaseFlowingFluid.Flowing(makeProperties(type, reg)));
        reg.bucket = SoulForgeRegistries.ITEMS.register(name + "_bucket",
                () -> new BucketItem(reg.still.get(), new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET).fireResistant()));
        reg.block = SoulForgeRegistries.BLOCKS.register(name,
                () -> new LiquidBlock(reg.still.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA)
                        .mapColor(MapColor.COLOR_RED)
                        .noLootTable()));

        return new FluidSet(name, type, reg.still, reg.flowing, reg.bucket, reg.block);
    }

    private static BaseFlowingFluid.Properties makeProperties(DeferredHolder<FluidType, FluidType> type, FluidReg reg) {
        return new BaseFlowingFluid.Properties(type::get, reg.still::get, reg.flowing::get)
                .block(reg.block)
                .bucket(reg.bucket);
    }

    private static class FluidReg {
        DeferredHolder<Fluid, BaseFlowingFluid.Source> still;
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        DeferredItem<BucketItem> bucket;
        DeferredBlock<LiquidBlock> block;
    }

    public record FluidSet(String name,
                           DeferredHolder<FluidType, FluidType> type,
                           DeferredHolder<Fluid, BaseFlowingFluid.Source> still,
                           DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing,
                           DeferredItem<BucketItem> bucket,
                           DeferredBlock<LiquidBlock> block) {
    }
}
