package com.soulforge.soulforge.block;

import com.soulforge.soulforge.block.entity.SmelteryControllerBlockEntity;
import com.soulforge.soulforge.block.entity.SoulForgeBlockEntityTypes;
import com.soulforge.soulforge.smeltery.SmelteryTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Base controller block for SoulForge smelteries. Implements EntityBlock and
 * forwards block updates to its block entity for structure re-validation.
 */
public class SmelteryControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty IN_STRUCTURE = BooleanProperty.create("in_structure");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final SmelteryTier tier;

    public SmelteryControllerBlock(Properties properties, SmelteryTier tier) {
        super(properties);
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(IN_STRUCTURE, false)
                .setValue(ACTIVE, false));
    }

    public SmelteryTier getTier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, IN_STRUCTURE, ACTIVE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SoulForgeBlockEntityTypes.SMELTERY_CONTROLLER.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, SoulForgeBlockEntityTypes.SMELTERY_CONTROLLER.get(),
                SmelteryControllerBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> actualType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker) {
        return expectedType == actualType ? (BlockEntityTicker<A>) ticker : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity controller) {
                // 打开冶炼炉控制器 GUI，传递 BlockPos 给客户端
                serverPlayer.openMenu(controller, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, orientation, isMoving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity controller) {
            controller.updateStructure();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean isMoving) {
        if (level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity controller) {
            controller.setRemoved();
        }
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving);
    }
}
