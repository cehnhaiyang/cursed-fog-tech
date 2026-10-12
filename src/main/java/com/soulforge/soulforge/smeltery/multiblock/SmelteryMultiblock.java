package com.soulforge.soulforge.smeltery.multiblock;

import com.soulforge.soulforge.block.SoulForgeBlocks;
import com.soulforge.soulforge.smeltery.SmelteryTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 冶炼炉多方块：有底板、无顶盖，向上可扩建。
 *
 * <p>合法方块分三类：本等级的炉壁砖块、更高等级的炉壁砖块（允许混搭升级）、
 * 以及燃料储罐方块。储罐同时被登记为燃料来源。
 *
 * <p>本类直接实现 {@link MultiblockCuboid.BlockValidator} 而**不是**传一个只覆写
 * {@code isValid} 的方法引用：{@code isTank} 的默认实现恒为 false，用方法引用会把
 * 它悄悄丢掉，结果是储罐被当成合法炉壁、却永远不被收集为燃料来源。
 */
public class SmelteryMultiblock implements MultiblockCuboid.BlockValidator {
    private final SmelteryTier tier;
    private final MultiblockCuboid cuboid = new MultiblockCuboid(true, false);
    /** 本次探测的控制器位置，由 {@link #detectMultiblock} 设置。 */
    private BlockPos controllerPos = BlockPos.ZERO;

    public SmelteryMultiblock(SmelteryTier tier) {
        this.tier = tier;
    }

    public SmelteryTier getTier() {
        return tier;
    }

    public int getMaxHeight() {
        return cuboid.getMaxHeight();
    }

    @Nullable
    public MultiblockResult getLastResult() {
        return cuboid.getLastResult();
    }

    /**
     * 探测控制器背后的多方块结构。
     *
     * @param world      世界
     * @param controller 控制器位置
     * @param facing     控制器朝向
     * @return 结构描述，无效时返回 null
     */
    @Nullable
    public MultiblockStructure detectMultiblock(Level world, BlockPos controller, Direction facing) {
        this.controllerPos = controller;
        return cuboid.detectMultiblock(world, controller, facing, this);
    }

    /** 判断某个方块变化是否需要触发结构重扫。 */
    public boolean shouldUpdate(Level world, MultiblockStructure structure, BlockPos pos, BlockState state) {
        return cuboid.shouldUpdate(world, structure, pos, state, this);
    }

    /** 结构正上方一层能否补成新炉壁层。 */
    public boolean canExpand(Level world, MultiblockStructure structure) {
        return cuboid.canExpand(world, structure, this);
    }

    @Override
    public boolean isValid(Level world, BlockPos pos, MultiblockCuboid.CuboidSide side) {
        // 控制器自身就嵌在炉壁上，它既不是砖块也不是储罐，必须无条件放行，
        // 否则任何结构都会因为"控制器那一格非法"而永远无法成形。
        if (pos.equals(controllerPos)) {
            return true;
        }
        Block block = world.getBlockState(pos).getBlock();

        if (isFuelTank(block)) {
            return true;
        }
        // 允许用更高等级的砖块搭建：升级炉体时不必整体拆除
        for (SmelteryTier candidate : SmelteryTier.values()) {
            if (candidate.ordinal() >= tier.ordinal() && block == candidate.getRepresentativeBlock()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isTank(BlockState state) {
        return isFuelTank(state.getBlock());
    }

    private boolean isFuelTank(Block block) {
        return block == SoulForgeBlocks.NETHERITE_FUEL_TANK.get()
                || block == SoulForgeBlocks.TRINITY_ALLOY_FUEL_TANK.get()
                || block == SoulForgeBlocks.MAGVAC_FUEL_TANK.get();
    }
}