package com.soulforge.soulforge.block.casting;

import com.soulforge.soulforge.block.entity.AbstractCastingBlockEntity;
import com.soulforge.soulforge.block.entity.SoulForgeBlockEntityTypes;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 铸造盆方块（参照 TiC3 的 AbstractCastingBlock）。
 * 无 GUI，右键直接交互：放物品、倒流体、取产物。
 */
public class CastingBasinBlock extends BaseEntityBlock {
    private final Supplier<BlockEntityType<? extends BlockEntity>> blockEntityType;

    public CastingBasinBlock(Properties properties, Supplier<BlockEntityType<? extends BlockEntity>> blockEntityType) {
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, SoulForgeBlockEntityTypes.CASTING_BASIN.get(),
                (lvl, pos, st, be) -> AbstractCastingBlockEntity.serverTick(lvl, pos, st, (AbstractCastingBlockEntity) be));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof AbstractCastingBlockEntity casting) {
            casting.interact(player, InteractionHand.MAIN_HAND);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof AbstractCastingBlockEntity casting) {
            casting.interact(player, hand);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
