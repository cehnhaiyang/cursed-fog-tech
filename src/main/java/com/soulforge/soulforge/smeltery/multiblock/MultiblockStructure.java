package com.soulforge.soulforge.smeltery.multiblock;

import net.minecraft.core.BlockPos;

/**
 * Immutable description of a detected cuboid multiblock.
 */
public class MultiblockStructure {
    private final BlockPos min;
    private final BlockPos max;
    private final int innerX;
    private final int innerY;
    private final int innerZ;

    public MultiblockStructure(BlockPos min, BlockPos max, int innerX, int innerZ, int innerY) {
        this.min = min;
        this.max = max;
        this.innerX = innerX;
        this.innerY = innerY;
        this.innerZ = innerZ;
    }

    public BlockPos getMin() {
        return min;
    }

    public BlockPos getMax() {
        return max;
    }

    public int getInnerX() {
        return innerX;
    }

    public int getInnerY() {
        return innerY;
    }

    public int getInnerZ() {
        return innerZ;
    }

    /** Number of interior blocks (rough capacity for fluids / items). */
    public int getInteriorVolume() {
        return innerX * innerY * innerZ;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }
}
