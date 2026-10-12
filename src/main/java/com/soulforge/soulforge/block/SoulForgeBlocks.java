package com.soulforge.soulforge.block;

import com.soulforge.soulforge.SoulForgeRegistries;
import com.soulforge.soulforge.block.casting.CastingBasinBlock;
import com.soulforge.soulforge.block.casting.CastingTableBlock;
import com.soulforge.soulforge.block.entity.SoulForgeBlockEntityTypes;
import com.soulforge.soulforge.block.tank.FuelTankBlock;
import com.soulforge.soulforge.smeltery.SmelteryTier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;

public class SoulForgeBlocks {
        public static final DeferredBlock<Block> SMELTERY_BRICKS = SoulForgeRegistries.BLOCKS.registerSimpleBlock(
                        "smeltery_bricks",
                        p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0F, 12.0F).sound(SoundType.STONE)
                                        .requiresCorrectToolForDrops());
        public static final DeferredBlock<Block> NETHERITE_BRICKS = SoulForgeRegistries.BLOCKS
                        .registerSimpleBlock("netherite_bricks",
                                        p -> p.mapColor(MapColor.COLOR_BLACK).strength(5.0F, 1200.0F)
                                                        .sound(SoundType.NETHERITE_BLOCK)
                                                        .requiresCorrectToolForDrops());
        public static final DeferredBlock<Block> TRINITY_ALLOY_BRICKS = SoulForgeRegistries.BLOCKS.registerSimpleBlock(
                        "trinity_alloy_bricks",
                        p -> p.mapColor(MapColor.COLOR_PURPLE).strength(10.0F, 1200.0F).sound(SoundType.METAL)
                                        .requiresCorrectToolForDrops());
        public static final DeferredBlock<Block> SINGULARITY_BRICKS = SoulForgeRegistries.BLOCKS.registerSimpleBlock(
                        "singularity_bricks",
                        p -> p.mapColor(MapColor.COLOR_BLACK).strength(20.0F, 3600000.0F).sound(SoundType.METAL)
                                        .requiresCorrectToolForDrops());

        public static final DeferredBlock<Block> TRINITY_ALLOY_BLOCK = SoulForgeRegistries.BLOCKS.registerSimpleBlock(
                        "trinity_alloy_block",
                        p -> p.mapColor(MapColor.COLOR_PURPLE).strength(10.0F, 1200.0F).sound(SoundType.METAL)
                                        .requiresCorrectToolForDrops());
        public static final DeferredBlock<SuperResistantMetalBlock> SUPER_RESISTANT_METAL_BLOCK = SoulForgeRegistries.BLOCKS
                        .registerBlock("super_resistant_metal_block",
                                        SuperResistantMetalBlock::new,
                                        p -> p.mapColor(MapColor.COLOR_BLACK).strength(80.0F, 1200.0F)
                                                        .sound(SoundType.METAL).requiresCorrectToolForDrops());
        public static final DeferredBlock<MagvacAlloyBlock> MAGVAC_ALLOY_BLOCK = SoulForgeRegistries.BLOCKS
                        .registerBlock("magvac_alloy_block",
                                        MagvacAlloyBlock::new,
                                        p -> p.mapColor(MapColor.COLOR_BLUE).strength(15.0F, 1200.0F)
                                                        .sound(SoundType.METAL).requiresCorrectToolForDrops());

        public static final DeferredBlock<FuelTankBlock> NETHERITE_FUEL_TANK = SoulForgeRegistries.BLOCKS.registerBlock(
                        "netherite_fuel_tank",
                        props -> new FuelTankBlock(props, () -> SoulForgeBlockEntityTypes.NETHERITE_FUEL_TANK.get()),
                        p -> p.mapColor(MapColor.COLOR_BLACK).strength(5.0F, 1200.0F).sound(SoundType.METAL)
                                        .requiresCorrectToolForDrops());
        public static final DeferredBlock<FuelTankBlock> TRINITY_ALLOY_FUEL_TANK = SoulForgeRegistries.BLOCKS
                        .registerBlock("trinity_alloy_fuel_tank",
                                        props -> new FuelTankBlock(props,
                                                        () -> SoulForgeBlockEntityTypes.TRINITY_ALLOY_FUEL_TANK.get()),
                                        p -> p.mapColor(MapColor.COLOR_PURPLE).strength(10.0F, 1200.0F)
                                                        .sound(SoundType.METAL).requiresCorrectToolForDrops());
        public static final DeferredBlock<FuelTankBlock> MAGVAC_FUEL_TANK = SoulForgeRegistries.BLOCKS.registerBlock(
                        "magvac_fuel_tank",
                        props -> new FuelTankBlock(props, () -> SoulForgeBlockEntityTypes.MAGVAC_FUEL_TANK.get()),
                        p -> p.mapColor(MapColor.COLOR_BLUE).strength(15.0F, 1200.0F).sound(SoundType.METAL)
                                        .requiresCorrectToolForDrops());

        public static final DeferredBlock<SmelteryControllerBlock> SMELTERY_CONTROLLER = SoulForgeRegistries.BLOCKS
                        .registerBlock("smeltery_controller",
                                        props -> new SmelteryControllerBlock(props, SmelteryTier.SMELTERY),
                                        p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0F, 12.0F)
                                                        .sound(SoundType.METAL).requiresCorrectToolForDrops());
        public static final DeferredBlock<SmelteryControllerBlock> INTERMEDIATE_SMELTERY_CONTROLLER = SoulForgeRegistries.BLOCKS
                        .registerBlock("intermediate_smeltery_controller",
                                        props -> new SmelteryControllerBlock(props, SmelteryTier.INTERMEDIATE),
                                        p -> p.mapColor(MapColor.COLOR_BLACK).strength(5.0F, 1200.0F)
                                                        .sound(SoundType.METAL).requiresCorrectToolForDrops());
        public static final DeferredBlock<SmelteryControllerBlock> ADVANCED_SMELTERY_CONTROLLER = SoulForgeRegistries.BLOCKS
                        .registerBlock("advanced_smeltery_controller",
                                        props -> new SmelteryControllerBlock(props, SmelteryTier.ADVANCED),
                                        p -> p.mapColor(MapColor.COLOR_PURPLE).strength(10.0F, 1200.0F)
                                                        .sound(SoundType.METAL).requiresCorrectToolForDrops());
        public static final DeferredBlock<SmelteryControllerBlock> HYBRID_FURNACE_CONTROLLER = SoulForgeRegistries.BLOCKS
                        .registerBlock("hybrid_furnace_controller",
                                        props -> new SmelteryControllerBlock(props, SmelteryTier.HYBRID),
                                        p -> p.mapColor(MapColor.COLOR_BLUE).strength(15.0F, 1200.0F)
                                                        .sound(SoundType.METAL).requiresCorrectToolForDrops());
        public static final DeferredBlock<SmelteryControllerBlock> FUSION_FURNACE_CONTROLLER = SoulForgeRegistries.BLOCKS
                        .registerBlock("fusion_furnace_controller",
                                        props -> new SmelteryControllerBlock(props, SmelteryTier.FUSION),
                                        p -> p.mapColor(MapColor.COLOR_BLACK).strength(20.0F, 3600000.0F)
                                                        .sound(SoundType.METAL).requiresCorrectToolForDrops());

        public static final DeferredBlock<CastingTableBlock> CASTING_TABLE = SoulForgeRegistries.BLOCKS.registerBlock(
                        "casting_table",
                        props -> new CastingTableBlock(props, () -> SoulForgeBlockEntityTypes.CASTING_TABLE.get()),
                        p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0F, 12.0F).sound(SoundType.STONE)
                                        .requiresCorrectToolForDrops());
        public static final DeferredBlock<CastingBasinBlock> CASTING_BASIN = SoulForgeRegistries.BLOCKS.registerBlock(
                        "casting_basin",
                        props -> new CastingBasinBlock(props, () -> SoulForgeBlockEntityTypes.CASTING_BASIN.get()),
                        p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0F, 12.0F).sound(SoundType.STONE)
                                        .requiresCorrectToolForDrops());

        public static void register() {
        }
}
