package com.soulforge.soulforge.block;

import com.soulforge.soulforge.block.entity.SmelteryControllerBlockEntity;
import com.soulforge.soulforge.block.entity.SoulForgeBlockEntityTypes;
import com.soulforge.soulforge.smeltery.SmelteryTier;
import com.soulforge.soulforge.smeltery.multiblock.MultiblockResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

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

    /**
     * 空手右键：结构成形才开界面，否则把失败原因作为动作栏提示给玩家。
     * 未成形时开界面毫无意义——界面里的槽位与罐体都还不存在。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        if (!state.getValue(IN_STRUCTURE)) {
            displayStructureError(serverPlayer, controller);
            return InteractionResult.SUCCESS;
        }
        // 槽位数随炉体大小变化，放进打开包让客户端与服务端用同一个数建槽位
        serverPlayer.openMenu(controller,
                buf -> com.soulforge.soulforge.menu.ControllerMenu.writeOpenData(
                        buf, pos, controller.getSlotCount()));
        return InteractionResult.SUCCESS;
    }

    /** 把最近一次结构探测的失败原因显示在动作栏上。 */
    private void displayStructureError(ServerPlayer player, SmelteryControllerBlockEntity controller) {
        MultiblockResult result = controller.getStructureResult();
        Component message = result != null && !result.isSuccess()
                ? result.getMessage()
                : Component.translatable("multiblock.soulforge.not_formed");
        player.sendSystemMessage(message, true);
    }

    /**
     * 手持流体容器右键控制器时优先做流体交换：把炉内熔融金属装进桶，或把桶里的燃料倒进去。
     * 只有流体交互没发生时（例如空手或拿着非容器）才落到 {@link #useWithoutItem} 开界面。
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof SmelteryControllerBlockEntity controller
                && FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection(), null)) {
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
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
            controller.invalidateStructure();
        }
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving);
    }
}
