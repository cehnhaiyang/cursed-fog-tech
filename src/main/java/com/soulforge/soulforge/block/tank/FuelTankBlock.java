package com.soulforge.soulforge.block.tank;

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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * 燃料储罐方块。它是炉子的燃料来源：炉体结构中放入储罐，控制器从中抽取燃料液体。
 *
 * <p>右键用流体容器（桶等）与罐内液体交换——这是把燃料装进炉子的唯一入口。
 */
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

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection(), null)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // 空手时尝试用主手（可能为空）触发一次流体交互，让桶在副手等场景也能工作
        return FluidUtil.interactWithFluidHandler(player, InteractionHand.MAIN_HAND, level, pos, hit.getDirection(), null)
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }
}
