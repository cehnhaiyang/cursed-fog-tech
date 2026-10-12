package com.soulforge.soulforge.block.entity;

import com.soulforge.soulforge.SoulForgeRegistries;
import com.soulforge.soulforge.block.SoulForgeBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

public class SoulForgeBlockEntityTypes {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelteryControllerBlockEntity>> SMELTERY_CONTROLLER = SoulForgeRegistries.BLOCK_ENTITIES.register("smeltery_controller",
            () -> new BlockEntityType<>(SmelteryControllerBlockEntity::new,
                    SoulForgeBlocks.SMELTERY_CONTROLLER.get(),
                    SoulForgeBlocks.INTERMEDIATE_SMELTERY_CONTROLLER.get(),
                    SoulForgeBlocks.ADVANCED_SMELTERY_CONTROLLER.get(),
                    SoulForgeBlocks.HYBRID_FURNACE_CONTROLLER.get(),
                    SoulForgeBlocks.FUSION_FURNACE_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetheriteFuelTankBlockEntity>> NETHERITE_FUEL_TANK = SoulForgeRegistries.BLOCK_ENTITIES.register("netherite_fuel_tank",
            () -> new BlockEntityType<>(NetheriteFuelTankBlockEntity::new, SoulForgeBlocks.NETHERITE_FUEL_TANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrinityAlloyFuelTankBlockEntity>> TRINITY_ALLOY_FUEL_TANK = SoulForgeRegistries.BLOCK_ENTITIES.register("trinity_alloy_fuel_tank",
            () -> new BlockEntityType<>(TrinityAlloyFuelTankBlockEntity::new, SoulForgeBlocks.TRINITY_ALLOY_FUEL_TANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagvacFuelTankBlockEntity>> MAGVAC_FUEL_TANK = SoulForgeRegistries.BLOCK_ENTITIES.register("magvac_fuel_tank",
            () -> new BlockEntityType<>(MagvacFuelTankBlockEntity::new, SoulForgeBlocks.MAGVAC_FUEL_TANK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CastingTableBlockEntity>> CASTING_TABLE = SoulForgeRegistries.BLOCK_ENTITIES.register("casting_table",
            () -> new BlockEntityType<>(CastingTableBlockEntity::new, SoulForgeBlocks.CASTING_TABLE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CastingBasinBlockEntity>> CASTING_BASIN = SoulForgeRegistries.BLOCK_ENTITIES.register("casting_basin",
            () -> new BlockEntityType<>(CastingBasinBlockEntity::new, SoulForgeBlocks.CASTING_BASIN.get()));

    public static void register() {
    }
}
