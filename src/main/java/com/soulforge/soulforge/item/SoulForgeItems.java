package com.soulforge.soulforge.item;

import com.soulforge.soulforge.SoulForgeRegistries;
import com.soulforge.soulforge.block.SoulForgeBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public class SoulForgeItems {
    // Basic items
    public static final DeferredItem<Item> GLOVE = SoulForgeRegistries.ITEMS.registerSimpleItem("glove", p -> p.stacksTo(1));
    public static final DeferredItem<Item> BLUNT_THERMAL_GLOVE = SoulForgeRegistries.ITEMS.registerSimpleItem("blunt_thermal_glove", p -> p.stacksTo(1));
    public static final DeferredItem<Item> MAGNETIC_COIL = SoulForgeRegistries.ITEMS.registerSimpleItem("magnetic_coil");
    public static final DeferredItem<Item> VACUUM_GENERATOR = SoulForgeRegistries.ITEMS.registerSimpleItem("vacuum_generator");
    public static final DeferredItem<Item> MAGVAC_GENERATOR = SoulForgeRegistries.ITEMS.registerSimpleItem("magvac_generator");

    // High-temperature empty containers
    public static final DeferredItem<SuperheatedResistantBucketItem> NETHERITE_BUCKET = SoulForgeRegistries.ITEMS.registerItem("netherite_bucket",
            props -> new SuperheatedResistantBucketItem(3500, props),
            p -> p.stacksTo(1).fireResistant());
    public static final DeferredItem<SuperheatedResistantBucketItem> TRINITY_ALLOY_BUCKET = SoulForgeRegistries.ITEMS.registerItem("trinity_alloy_bucket",
            props -> new SuperheatedResistantBucketItem(5000, props),
            p -> p.stacksTo(1).fireResistant());
    public static final DeferredItem<SuperheatedResistantBucketItem> MAGVAC_BUCKET = SoulForgeRegistries.ITEMS.registerItem("magvac_bucket",
            props -> new SuperheatedResistantBucketItem(100000000, props),
            p -> p.stacksTo(1).fireResistant());

    public static final DeferredItem<SuperheatedResistantTankItem> NETHERITE_FUEL_TANK = SoulForgeRegistries.ITEMS.registerItem("netherite_fuel_tank",
            props -> new SuperheatedResistantTankItem(3500, props),
            p -> p.stacksTo(1).fireResistant());
    public static final DeferredItem<SuperheatedResistantTankItem> TRINITY_ALLOY_FUEL_TANK = SoulForgeRegistries.ITEMS.registerItem("trinity_alloy_fuel_tank",
            props -> new SuperheatedResistantTankItem(5000, props),
            p -> p.stacksTo(1).fireResistant());
    public static final DeferredItem<SuperheatedResistantTankItem> MAGVAC_FUEL_TANK = SoulForgeRegistries.ITEMS.registerItem("magvac_fuel_tank",
            props -> new SuperheatedResistantTankItem(100000000, props),
            p -> p.stacksTo(1).fireResistant());

    // Controller block items
    public static final DeferredItem<BlockItem> SMELTERY_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.SMELTERY_CONTROLLER);
    public static final DeferredItem<BlockItem> INTERMEDIATE_SMELTERY_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.INTERMEDIATE_SMELTERY_CONTROLLER);
    public static final DeferredItem<BlockItem> ADVANCED_SMELTERY_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.ADVANCED_SMELTERY_CONTROLLER);
    public static final DeferredItem<BlockItem> HYBRID_FURNACE_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.HYBRID_FURNACE_CONTROLLER);
    public static final DeferredItem<BlockItem> FUSION_FURNACE_CONTROLLER = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.FUSION_FURNACE_CONTROLLER);

    // Ingot and storage block items
    public static final DeferredItem<Item> TRINITY_ALLOY_INGOT = SoulForgeRegistries.ITEMS.registerSimpleItem("trinity_alloy_ingot", p -> p.fireResistant());
    public static final DeferredItem<BlockItem> TRINITY_ALLOY_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.TRINITY_ALLOY_BLOCK, p -> p.fireResistant());
    public static final DeferredItem<BlockItem> SUPER_RESISTANT_METAL_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.SUPER_RESISTANT_METAL_BLOCK, p -> p.fireResistant());
    public static final DeferredItem<BlockItem> MAGVAC_ALLOY_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.MAGVAC_ALLOY_BLOCK, p -> p.fireResistant());

    // Tank block items
    // 注册名必须显式指定：方块与可装流体的物品同名（netherite_fuel_tank），
    // 走 Holder 重载会按方块名注册而撞名。
    public static final DeferredItem<BlockItem> NETHERITE_FUEL_TANK_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("netherite_fuel_tank_block", SoulForgeBlocks.NETHERITE_FUEL_TANK, p -> p.fireResistant());
    public static final DeferredItem<BlockItem> TRINITY_ALLOY_FUEL_TANK_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("trinity_alloy_fuel_tank_block", SoulForgeBlocks.TRINITY_ALLOY_FUEL_TANK, p -> p.fireResistant());
    public static final DeferredItem<BlockItem> MAGVAC_FUEL_TANK_BLOCK = SoulForgeRegistries.ITEMS.registerSimpleBlockItem("magvac_fuel_tank_block", SoulForgeBlocks.MAGVAC_FUEL_TANK, p -> p.fireResistant());

    // Brick block items
    public static final DeferredItem<BlockItem> SMELTERY_BRICKS = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.SMELTERY_BRICKS);
    public static final DeferredItem<BlockItem> NETHERITE_BRICKS = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.NETHERITE_BRICKS, p -> p.fireResistant());
    public static final DeferredItem<BlockItem> TRINITY_ALLOY_BRICKS = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.TRINITY_ALLOY_BRICKS, p -> p.fireResistant());
    public static final DeferredItem<BlockItem> SINGULARITY_BRICKS = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.SINGULARITY_BRICKS, p -> p.fireResistant());

    // Special items
    public static final DeferredItem<Item> NETHER_HEART = SoulForgeRegistries.ITEMS.registerSimpleItem("nether_heart", p -> p.durability(100).fireResistant());

    // Casting block items
    public static final DeferredItem<BlockItem> CASTING_TABLE = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.CASTING_TABLE);
    public static final DeferredItem<BlockItem> CASTING_BASIN = SoulForgeRegistries.ITEMS.registerSimpleBlockItem(SoulForgeBlocks.CASTING_BASIN);

    public static void register() {
    }
}
