package com.soulforge.soulforge.block.tank;

import com.soulforge.soulforge.block.entity.SoulForgeBlockEntityTypes;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FuelTankBlock extends BaseEntityBlock {
    private final Supplier<BlockEntityType<? extends BlockEntity>> blockEntityType;

    public FuelTankBlock(Properties properties, Supplier<BlockEntityType<? extends BlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }
}
