package com.soulforge.soulforge.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class NetheriteFuelTankBlockEntity extends FuelTankBlockEntity {
    public NetheriteFuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(SoulForgeBlockEntityTypes.NETHERITE_FUEL_TANK.get(), pos, state);
    }
}
