package com.soulforge.soulforge.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 铸造盆方块实体。使用 basin 类型配方（basin=true）。
 */
public class CastingBasinBlockEntity extends AbstractCastingBlockEntity {
    public CastingBasinBlockEntity(BlockPos pos, BlockState state) {
        super(SoulForgeBlockEntityTypes.CASTING_BASIN.get(), pos, state, true);
    }
}
