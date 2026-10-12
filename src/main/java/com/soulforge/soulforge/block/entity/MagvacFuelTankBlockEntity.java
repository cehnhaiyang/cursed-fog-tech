package com.soulforge.soulforge.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MagvacFuelTankBlockEntity extends FuelTankBlockEntity {
    public MagvacFuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(SoulForgeBlockEntityTypes.MAGVAC_FUEL_TANK.get(), pos, state);
    }
}
