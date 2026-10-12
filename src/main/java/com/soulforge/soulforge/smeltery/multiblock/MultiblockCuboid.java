package com.soulforge.soulforge.smeltery.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Simple cuboid multiblock scanner. Given a controller position and a facing,
 * scans outward to find a rectangular structure formed by valid wall/floor blocks
 * with an air interior. Tracks interior volume and whether the structure is valid.
 */
public class MultiblockCuboid {
    private static final int DEFAULT_MAX_WIDTH = 11;
    private static final int DEFAULT_MAX_HEIGHT = 32;

    private final boolean hasFloor;
    private final boolean hasCeiling;
    private final int maxWidth;
    private final int maxHeight;

    @Nullable
    private MultiblockResult lastResult;

    public MultiblockCuboid(boolean hasFloor, boolean hasCeiling, int maxWidth, int maxHeight) {
        this.hasFloor = hasFloor;
        this.hasCeiling = hasCeiling;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public MultiblockCuboid(boolean hasFloor, boolean hasCeiling) {
        this(hasFloor, hasCeiling, DEFAULT_MAX_WIDTH, DEFAULT_MAX_HEIGHT);
    }

    /**
     * Detects a cuboid multiblock centered behind the controller.
     *
     * @param world  Level instance
     * @param center Center position of the interior (one block behind the controller face)
     * @return Multiblock structure data, or null if invalid
     */
    @Nullable
    public MultiblockStructure detectMultiblock(Level world, BlockPos center) {
        return detectMultiblock(world, center, (w, p, side) -> !isAirOrInnerBlock(w, p));
    }

    @Nullable
    public MultiblockStructure detectMultiblock(Level world, BlockPos center, BlockValidator validator) {
        // Find horizontal extents from the center
        int west = measureToEdge(world, center, Direction.WEST, maxWidth);

        int east = measureToEdge(world, center, Direction.EAST, maxWidth);
        int north = measureToEdge(world, center, Direction.NORTH, maxWidth);
        int south = measureToEdge(world, center, Direction.SOUTH, maxWidth);

        int innerX = west + east + 1;
        int innerZ = north + south + 1;
        if (innerX > maxWidth || innerZ > maxWidth) {
            lastResult = MultiblockResult.error(center, "multiblock.soulforge.too_large");
            return null;
        }

        BlockPos innerMin = center.offset(-west, 0, -north);
        BlockPos innerMax = center.offset(east, 0, south);

        // Determine vertical extents. If the structure has a floor/ceiling, the shell
        // extends one block below/above the interior. Otherwise we scan for more air.
        int down = hasFloor ? 1 : measureVerticalToEdge(world, innerMin, innerMax, Direction.DOWN, maxHeight / 2) + 1;
        int up = hasCeiling ? 1 : measureVerticalToEdge(world, innerMin, innerMax, Direction.UP, maxHeight / 2) + 1;

        BlockPos min = innerMin.offset(0, -down, 0);
        BlockPos max = innerMax.offset(0, up, 0);

        // Validate the full cuboid shell
        MultiblockResult result = validateShell(world, min, max, validator);
        if (!result.isSuccess()) {
            lastResult = result;
            return null;
        }


        lastResult = MultiblockResult.success();
        int innerY = Math.max(1, max.getY() - min.getY() - 1);
        return new MultiblockStructure(min, max, innerX, innerZ, innerY);

    }

    /**
     * Measures how many valid interior (air) blocks exist from pos in direction,
     * stopping at the first non-air block. Returns the distance to the wall.
     */
    private int measureToEdge(Level world, BlockPos pos, Direction direction, int limit) {
        int distance = 0;
        BlockPos.MutableBlockPos mutable = pos.mutable();
        for (int i = 0; i < limit; i++) {
            mutable.move(direction);
            if (!isAirOrInnerBlock(world, mutable)) {
                break;
            }
            distance++;
        }
        return distance;
    }

    private int measureVerticalToEdge(Level world, BlockPos innerMin, BlockPos innerMax, Direction direction, int limit) {
        int distance = 0;
        for (int i = 0; i < limit; i++) {
            BlockPos layerMin = innerMin.relative(direction, i + 1);
            BlockPos layerMax = innerMax.relative(direction, i + 1);
            if (!isLayerAir(world, layerMin, layerMax)) {
                break;
            }
            distance++;
        }
        return distance;
    }

    private boolean isLayerAir(Level world, BlockPos from, BlockPos to) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = from.getX(); x <= to.getX(); x++) {
            for (int z = from.getZ(); z <= to.getZ(); z++) {
                mutable.set(x, from.getY(), z);
                if (!isAirOrInnerBlock(world, mutable)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Validates that the outer shell of the cuboid is made of valid blocks.
     * Delegates block validity to the supplied validator.
     */
    public MultiblockResult validateShell(Level world, BlockPos min, BlockPos max, BlockValidator validator) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    boolean isEdgeX = x == min.getX() || x == max.getX();
                    boolean isEdgeY = y == min.getY() || y == max.getY();
                    boolean isEdgeZ = z == min.getZ() || z == max.getZ();
                    if (!isEdgeX && !isEdgeY && !isEdgeZ) {
                        continue; // interior
                    }

                    mutable.set(x, y, z);
                    CuboidSide side = getSide(mutable, min, max);
                    if (!validator.isValid(world, mutable, side)) {
                        return MultiblockResult.error(mutable.immutable(), "multiblock.soulforge.invalid_block");
                    }
                }
            }
        }
        return MultiblockResult.success();
    }

    /**
     * Overload that only checks for non-air shell blocks. Used when no tier-specific validator is supplied.
     */
    public MultiblockResult validateShell(Level world, BlockPos min, BlockPos max) {
        return validateShell(world, min, max, (w, p, side) -> !isAirOrInnerBlock(w, p));
    }

    private CuboidSide getSide(BlockPos pos, BlockPos min, BlockPos max) {
        if (pos.getY() == min.getY() && hasFloor) {
            return CuboidSide.FLOOR;
        }
        if (pos.getY() == max.getY() && hasCeiling) {
            return CuboidSide.CEILING;
        }
        return CuboidSide.WALL;
    }

    /** True if the position is air or otherwise allowed inside the multiblock. */
    public boolean isAirOrInnerBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir();
    }

    @Nullable
    public MultiblockResult getLastResult() {
        return lastResult;
    }

    public enum CuboidSide {
        FLOOR, WALL, CEILING
    }

    @FunctionalInterface
    public interface BlockValidator {
        boolean isValid(Level world, BlockPos pos, CuboidSide side);
    }
}
