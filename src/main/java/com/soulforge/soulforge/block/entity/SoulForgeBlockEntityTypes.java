package com.soulforge.soulforge.block.entity;

import com.soulforge.soulforge.SoulForgeRegistries;
import com.soulforge.soulforge.block.SoulForgeBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

public class SoulForgeBlockEntityTypes {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelteryControllerBlockEntity>> SMELTERY_CONTROLLER = SoulForgeRegistries.BLOCK_ENTITIES.register("smeltery_controller",
            () -> BlockEntityType.Builder.of(SmelteryControllerBlockEntity::new,
                    SoulForgeBlocks.SMELTERY_CONTROLLER.get(),
                    SoulForgeBlocks.INTERMEDIATE_SMELTERY_CONTROLLER.get(),
                    SoulForgeBlocks.ADVANCED_SMELTERY_CONTROLLER.get(),
                    SoulForgeBlocks.HYBRID_FURNACE_CONTROLLER.get(),
                    SoulForgeBlocks.FUSION_FURNACE_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetheriteFuelTankBlockEntity>> NETHERITE_FUEL_TANK = SoulForgeRegistries.BLOCK_ENTITIES.register("netherite_fuel_tank",
            () -> BlockEntityType.Builder.of(NetheriteFuelTankBlockEntity::new, SoulForgeBlocks.NETHERITE_FUEL_TANK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrinityAlloyFuelTankBlockEntity>> TRINITY_ALLOY_FUEL_TANK = SoulForgeRegistries.BLOCK_ENTITIES.register("trinity_alloy_fuel_tank",
            () -> BlockEntityType.Builder.of(TrinityAlloyFuelTankBlockEntity::new, SoulForgeBlocks.TRINITY_ALLOY_FUEL_TANK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagvacFuelTankBlockEntity>> MAGVAC_FUEL_TANK = SoulForgeRegistries.BLOCK_ENTITIES.register("magvac_fuel_tank",
            () -> BlockEntityType.Builder.of(MagvacFuelTankBlockEntity::new, SoulForgeBlocks.MAGVAC_FUEL_TANK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CastingTableBlockEntity>> CASTING_TABLE = SoulForgeRegistries.BLOCK_ENTITIES.register("casting_table",
            () -> BlockEntityType.Builder.of(CastingTableBlockEntity::new, SoulForgeBlocks.CASTING_TABLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CastingBasinBlockEntity>> CASTING_BASIN = SoulForgeRegistries.BLOCK_ENTITIES.register("casting_basin",
            () -> BlockEntityType.Builder.of(CastingBasinBlockEntity::new, SoulForgeBlocks.CASTING_BASIN.get()).build(null));

    public static void register() {
    }
}
