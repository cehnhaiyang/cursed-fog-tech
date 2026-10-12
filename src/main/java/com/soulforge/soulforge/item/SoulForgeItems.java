package com.soulforge.soulforge.item;

import com.soulforge.soulforge.SoulForgeRegistries;
import com.soulforge.soulforge.block.SoulForgeBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public class SoulForgeItems {
    // Basic items
    public static final DeferredItem<Item> GLOVE = SoulForgeRegistries.ITEMS.registerSimpleItem("glove", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> BLUNT_THERMAL_GLOVE = SoulForgeRegistries.ITEMS.registerSimpleItem("blunt_thermal_glove", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> MAGNETIC_COIL = SoulForgeRegistries.ITEMS.registerSimpleItem("magnetic_coil", new Item.Properties());
    public static final DeferredItem<Item> VACUUM_GENERATOR = SoulForgeRegistries.ITEMS.registerSimpleItem("vacuum_generator", new Item.Properties());
    public static final DeferredItem<Item> MAGVAC_GENERATOR = SoulForgeRegistries.ITEMS.registerSimpleItem("magvac_generator", new Item.Properties());

    // High-temperature empty containers
    public static final DeferredItem<SuperheatedResistantBucketItem> NETHERITE_BUCKET = SoulForgeRegistries.ITEMS.registerItem("netherite_bucket",
            props -> new SuperheatedResistantBucketItem(3500, props),
            new Item.Properties().stacksTo(1).fireResistant());
    public static final DeferredItem<SuperheatedResistantBucketItem> TRINITY_ALLOY_BUCKET = SoulForgeRegistries.ITEMS.registerItem("trinity_alloy_bucket",
            props -> new SuperheatedResistantBucketItem(5000, props),
            new Item.Properties().stacksTo(1).fireResistant());
    public static final DeferredItem<SuperheatedResistantBucketItem> MAGVAC_BUCKET = SoulForgeRegistries.ITEMS.registerItem("magvac_bucket",
            props -> new SuperheatedResistantBucketItem(100000000, props),
            new Item.Properties().stacksTo(1).fireResistant());

    public static final DeferredItem<SuperheatedResistantTankItem> NETHERITE_FUEL_TANK = SoulForgeRegistries.ITEMS.registerItem("netherite_fuel_tank",
            props -> new SuperheatedResistantTankItem(3500, props),
            new Item.Properties().stacksTo(1).fireResistant());
    public static final DeferredItem<SuperheatedResistantTankItem> TRINITY_ALLOY_FUEL_TANK = SoulForgeRegistries.ITEMS.registerItem("trinity_alloy_fuel_tank",
            props -> new SuperheatedResistantTankItem(5000, props),
            new Item.Properties().stacksTo(1).fireResistant());
    public static final DeferredItem<SuperheatedResistantTankItem> MAGVAC_FUEL_TANK = SoulForgeRegistries.ITEMS.registerItem("magvac_fuel_tank",
            props -> new SuperheatedResistantTankItem(100000000, props),
            new Item.Properties().stacksTo(1).fireResistant());

    // Controller block items
    public static final DeferredItem<BlockItem> SMELTERY_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("smeltery_controller",
            () -> SoulForgeBlocks.SMELTERY_CONTROLLER.get(), new Item.Properties());
    public static final DeferredItem<BlockItem> INTERMEDIATE_SMELTERY_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("intermediate_smeltery_controller",
            () -> SoulForgeBlocks.INTERMEDIATE_SMELTERY_CONTROLLER.get(), new Item.Properties());
    public static final DeferredItem<BlockItem> ADVANCED_SMELTERY_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("advanced_smeltery_controller",
            () -> SoulForgeBlocks.ADVANCED_SMELTERY_CONTROLLER.get(), new Item.Properties());
    public static final DeferredItem<BlockItem> HYBRID_FURNACE_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("hybrid_furnace_controller",
            () -> SoulForgeBlocks.HYBRID_FURNACE_CONTROLLER.get(), new Item.Properties());
    public static final DeferredItem<BlockItem> FUSION_FURNACE_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("fusion_furnace_controller",
            () -> SoulForgeBlocks.FUSION_FURNACE_CONTROLLER.get(), new Item.Properties());

    // Ingot and storage block items
    public static final DeferredItem<Item> TRINITY_ALLOY_INGOT = SoulForgeRegistries.ITEMS.registerSimpleItem("trinity_alloy_ingot", new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> TRINITY_ALLOY_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("trinity_alloy_block",
            () -> SoulForgeBlocks.TRINITY_ALLOY_BLOCK.get(), new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> SUPER_RESISTANT_METAL_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("super_resistant_metal_block",
            () -> SoulForgeBlocks.SUPER_RESISTANT_METAL_BLOCK.get(), new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> MAGVAC_ALLOY_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("magvac_alloy_block",
            () -> SoulForgeBlocks.MAGVAC_ALLOY_BLOCK.get(), new Item.Properties().fireResistant());

    // Tank block items
    public static final DeferredItem<BlockItem> NETHERITE_FUEL_TANK_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("netherite_fuel_tank_block",
            () -> SoulForgeBlocks.NETHERITE_FUEL_TANK.get(), new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> TRINITY_ALLOY_FUEL_TANK_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("trinity_alloy_fuel_tank_block",
            () -> SoulForgeBlocks.TRINITY_ALLOY_FUEL_TANK.get(), new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> MAGVAC_FUEL_TANK_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("magvac_fuel_tank_block",
            () -> SoulForgeBlocks.MAGVAC_FUEL_TANK.get(), new Item.Properties().fireResistant());

    // Brick block items
    public static final DeferredItem<BlockItem> SMELTERY_BRICKS = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("smeltery_bricks",
            () -> SoulForgeBlocks.SMELTERY_BRICKS.get(), new Item.Properties());
    public static final DeferredItem<BlockItem> NETHERITE_BRICKS = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("netherite_bricks",
            () -> SoulForgeBlocks.NETHERITE_BRICKS.get(), new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> TRINITY_ALLOY_BRICKS = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("trinity_alloy_bricks",
            () -> SoulForgeBlocks.TRINITY_ALLOY_BRICKS.get(), new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> SINGULARITY_BRICKS = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("singularity_bricks",
            () -> SoulForgeBlocks.SINGULARITY_BRICKS.get(), new Item.Properties().fireResistant());

    // Special items
    public static final DeferredItem<Item> NETHER_HEART = SoulForgeRegistries.ITEMS.registerSimpleItem("nether_heart", new Item.Properties().durability(100).fireResistant());

    // Casting block items
    public static final DeferredItem<BlockItem> CASTING_TABLE = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("casting_table",
            () -> SoulForgeBlocks.CASTING_TABLE.get(), new Item.Properties());
    public static final DeferredItem<BlockItem> CASTING_BASIN = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("casting_basin",
            () -> SoulForgeBlocks.CASTING_BASIN.get(), new Item.Properties());

    public static void register() {
    }
}
