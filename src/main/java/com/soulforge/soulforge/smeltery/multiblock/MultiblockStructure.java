package com.soulforge.soulforge.smeltery.multiblock;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Consumer;

/**
 * Immutable description of a detected cuboid multiblock.
 *
 * <p>{@link #getMin()}/{@link #getMax()} 是外壳（墙体与底板最外圈）的边界；
 * {@link #getMinInside()}/{@link #getMaxInside()} 是内部空腔边界。两者相差一圈，
 * 判定"这个位置属不属于结构"时必须区分：外壳算结构，空腔不算。
 */
public class MultiblockStructure {
    private final BlockPos min;
    private final BlockPos max;
    private final BlockPos minInside;
    private final BlockPos maxInside;
    private final List<BlockPos> tanks;

    public MultiblockStructure(BlockPos min, BlockPos max, List<BlockPos> tanks) {
        this.min = min;
        this.max = max;
        // 有底板、无顶盖：内部从底板上方一层起，到最上层墙体所在高度为止（水平方向各内缩一格）。
        this.minInside = min.offset(1, 1, 1);
        this.maxInside = max.offset(-1, 0, -1);
        this.tanks = List.copyOf(tanks);
    }

    public BlockPos getMin() {
        return min;
    }

    public BlockPos getMax() {
        return max;
    }

    public BlockPos getMinInside() {
        return minInside;
    }

    public BlockPos getMaxInside() {
        return maxInside;
    }

    /** 结构中所有燃料储罐方块的位置，用于燃料模块定位。 */
    public List<BlockPos> getTanks() {
        return tanks;
    }

    public boolean hasTanks() {
        return !tanks.isEmpty();
    }

    public int getInnerX() {
        return maxInside.getX() - minInside.getX() + 1;
    }

    public int getInnerY() {
        return maxInside.getY() - minInside.getY() + 1;
    }

    public int getInnerZ() {
        return maxInside.getZ() - minInside.getZ() + 1;
    }

    /** 内部空腔体积，用于按体积推算流体容量。 */
    public int getInteriorVolume() {
        return Math.max(0, getInnerX() * getInnerY() * getInnerZ());
    }

    /** 外壳周长（墙 + 底板）的方块数，用于按规模推算燃料消耗速率。 */
    public int getPerimeterCount() {
        int dx = getInnerX();
        int dy = getInnerY();
        int dz = getInnerZ();
        return 2 * (dx * dy) + 2 * (dy * dz) + (dx * dz);
    }

    public boolean withinBounds(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean isInside(BlockPos pos) {
        return pos.getX() >= minInside.getX() && pos.getX() <= maxInside.getX()
                && pos.getY() >= minInside.getY() && pos.getY() <= maxInside.getY()
                && pos.getZ() >= minInside.getZ() && pos.getZ() <= maxInside.getZ();
    }

    /** 该位置是否属于结构本体（外壳的一部分）。 */
    public boolean contains(BlockPos pos) {
        return withinBounds(pos) && !isInside(pos);
    }

    /** 该位置是否正好在结构正上方一层，用于判断能否向上扩建。 */
    public boolean isDirectlyAbove(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()
                && pos.getY() == max.getY() + 1;
    }

    /**
     * 遍历结构外壳的每个位置。
     * 传入的位置是可变的，需要保存时自行调用 {@link BlockPos#immutable()}。
     */
    public void forEachContained(Consumer<BlockPos.MutableBlockPos> consumer) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    mutable.set(x, y, z);
                    if (!isInside(mutable)) {
                        consumer.accept(mutable);
                    }
                }
            }
        }
    }
}
