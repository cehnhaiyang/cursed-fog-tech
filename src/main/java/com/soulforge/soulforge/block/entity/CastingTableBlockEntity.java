package com.soulforge.soulforge.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 铸造台方块实体。使用 table 类型配方（basin=false）。
 */
public class CastingTableBlockEntity extends AbstractCastingBlockEntity {
    public CastingTableBlockEntity(BlockPos pos, BlockState state) {
        super(SoulForgeBlockEntityTypes.CASTING_TABLE.get(), pos, state, false);
    }
}
