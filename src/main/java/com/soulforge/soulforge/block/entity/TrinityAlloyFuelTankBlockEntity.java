package com.soulforge.soulforge.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class TrinityAlloyFuelTankBlockEntity extends FuelTankBlockEntity {
    public TrinityAlloyFuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(SoulForgeBlockEntityTypes.TRINITY_ALLOY_FUEL_TANK.get(), pos, state);
    }
}
