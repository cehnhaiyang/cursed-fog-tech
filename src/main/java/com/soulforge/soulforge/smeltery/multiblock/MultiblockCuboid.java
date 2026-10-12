package com.soulforge.soulforge.smeltery.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 加热结构（冶炼炉/混合炉/聚变炉）的立方体多方块扫描器。
 *
 * <p>算法对齐 TiC3 的 {@code MultiblockCuboid}：以控制器朝向的反方向那一格为中心，
 * 先在水平方向走到墙，校验中心层的空腔与四壁；再逐层向下、向上校验，
 * 最后要求底部（可选顶部）是一个完整有效的盖面。
 * 这样"敞口无顶盖"的结构才可能成立——闭盒式校验会把敞口炉判为无效。
 */
public class MultiblockCuboid {
    /** 单层内部水平最大边长。 */
    public static final int DEFAULT_INNER_LIMIT = 11;
    /** 结构最大总高度（含底板）。 */
    public static final int DEFAULT_MAX_HEIGHT = 32;

    private final boolean hasFloor;
    private final boolean hasCeiling;
    private final int maxHeight;
    private final int innerLimit;

    /** 尚未探测过时的初始结果：不是成功，而是"还没成形"，右键时应据此提示。 */
    private MultiblockResult lastResult = MultiblockResult.error(null, "multiblock.soulforge.not_formed");

    public MultiblockCuboid(boolean hasFloor, boolean hasCeiling) {
        this(hasFloor, hasCeiling, DEFAULT_MAX_HEIGHT, DEFAULT_INNER_LIMIT);
    }

    public MultiblockCuboid(boolean hasFloor, boolean hasCeiling, int maxHeight, int innerLimit) {
        this.hasFloor = hasFloor;
        this.hasCeiling = hasCeiling;
        this.maxHeight = maxHeight;
        this.innerLimit = innerLimit;
    }

    public boolean hasFloor() {
        return hasFloor;
    }

    public boolean hasCeiling() {
        return hasCeiling;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public MultiblockResult getLastResult() {
        return lastResult;
    }

    /**
     * 探测以控制器为中心的多方块结构。
     *
     * @param world     世界
     * @param master    控制器位置
     * @param facing    控制器朝向（其反方向即炉内）
     * @param validator 方块合法性判定，同时负责收集燃料储罐位置
     * @return 结构描述，无效时返回 null 并在 {@link #getLastResult()} 中留下原因
     */
    @Nullable
    public MultiblockStructure detectMultiblock(Level world, BlockPos master, Direction facing, BlockValidator validator) {
        BlockPos center = master.relative(facing.getOpposite());
        if (!world.hasChunkAt(center) || !isInnerBlock(world, center)) {
            return fail(MultiblockResult.error(center, "multiblock.soulforge.invalid_inner_block"));
        }

        // 水平方向走到墙，得到四面墙的位置
        BlockPos north = walkToWall(world, center, Direction.NORTH);
        BlockPos south = walkToWall(world, center, Direction.SOUTH);
        BlockPos west = walkToWall(world, center, Direction.WEST);
        BlockPos east = walkToWall(world, center, Direction.EAST);

        BlockPos from = new BlockPos(west.getX(), center.getY(), north.getZ());
        BlockPos to = new BlockPos(east.getX(), center.getY(), south.getZ());

        int innerX = to.getX() - from.getX() - 1;
        int innerZ = to.getZ() - from.getZ() - 1;
        if (innerX < 1 || innerZ < 1) {
            return fail(MultiblockResult.error(center, "multiblock.soulforge.too_small"));
        }
        if (innerX > innerLimit || innerZ > innerLimit) {
            return fail(MultiblockResult.error(center, "multiblock.soulforge.too_large"));
        }

        List<BlockPos> tanks = new ArrayList<>();
        MultiblockResult layer = detectLayer(world, from, to, validator, tanks);
        if (!layer.isSuccess()) {
            return fail(layer);
        }

        // 向下逐层扫描，直到某层不再是有效的炉壁层
        int down = 0;
        while (down + 1 < maxHeight) {
            BlockPos layerFrom = from.above(-down - 1);
            BlockPos layerTo = to.above(-down - 1);
            if (!detectLayer(world, layerFrom, layerTo, validator, tanks).isSuccess()) {
                break;
            }
            down++;
        }
        BlockPos belowFrom = from.above(-down - 1);
        BlockPos belowTo = to.above(-down - 1);
        int minY;
        if (hasFloor) {
            MultiblockResult floor = detectCap(world, belowFrom, belowTo, CuboidSide.FLOOR, validator, tanks);
            if (!floor.isSuccess()) {
                return fail(floor);
            }
            minY = belowFrom.getY();
        } else {
            minY = from.above(-down).getY();
        }

        // 向上逐层扫描
        int up = 0;
        while (down + up + 2 < maxHeight) {
            BlockPos layerFrom = from.above(up + 1);
            BlockPos layerTo = to.above(up + 1);
            if (!detectLayer(world, layerFrom, layerTo, validator, tanks).isSuccess()) {
                break;
            }
            up++;
        }
        BlockPos aboveFrom = from.above(up + 1);
        BlockPos aboveTo = to.above(up + 1);
        int maxY;
        if (hasCeiling) {
            MultiblockResult ceiling = detectCap(world, aboveFrom, aboveTo, CuboidSide.CEILING, validator, tanks);
            if (!ceiling.isSuccess()) {
                return fail(ceiling);
            }
            maxY = aboveFrom.getY();
        } else {
            maxY = from.above(up).getY();
        }

        // 收集到的储罐位置可能落在最终边界之外（向上扫描失败的那一层），需要剔除
        BlockPos min = new BlockPos(from.getX(), minY, from.getZ());
        BlockPos max = new BlockPos(to.getX(), maxY, to.getZ());
        tanks.removeIf(pos -> pos.getY() < minY || pos.getY() > maxY);

        lastResult = MultiblockResult.success();
        return new MultiblockStructure(min, max, tanks);
    }

    /**
     * 校验单层：内部必须为空，四壁必须是合法炉壁方块。
     * 储罐方块在墙体上同样算合法，并被记录进 {@code tanks}。
     */
    private MultiblockResult detectLayer(Level world, BlockPos from, BlockPos to,
                                         BlockValidator validator, List<BlockPos> tanks) {
        if (!world.hasChunksAt(from, to)) {
            return MultiblockResult.error(null, "multiblock.soulforge.not_loaded");
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int y = from.getY();
        for (int x = from.getX() + 1; x < to.getX(); x++) {
            for (int z = from.getZ() + 1; z < to.getZ(); z++) {
                mutable.set(x, y, z);
                if (!isInnerBlock(world, mutable)) {
                    return MultiblockResult.error(mutable.immutable(), "multiblock.soulforge.invalid_inner_block");
                }
            }
        }

        for (int x = from.getX(); x <= to.getX(); x++) {
            for (int z = from.getZ(); z <= to.getZ(); z++) {
                // 只查四壁，内部已在上面查过
                boolean onRing = x == from.getX() || x == to.getX() || z == from.getZ() || z == to.getZ();
                if (!onRing) {
                    continue;
                }
                mutable.set(x, y, z);
                if (!validator.isValid(world, mutable, CuboidSide.WALL)) {
                    return MultiblockResult.error(mutable.immutable(), "multiblock.soulforge.invalid_wall_block");
                }
                recordTank(world, mutable, validator, tanks);
            }
        }
        return MultiblockResult.success();
    }

    /** 校验底板或顶盖：整面都必须是合法方块（底板通常允许更宽的材料集合）。 */
    private MultiblockResult detectCap(Level world, BlockPos from, BlockPos to, CuboidSide side,
                                       BlockValidator validator, List<BlockPos> tanks) {
        if (!world.hasChunksAt(from, to)) {
            return MultiblockResult.error(null, "multiblock.soulforge.not_loaded");
        }
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int y = from.getY();
        for (int x = from.getX(); x <= to.getX(); x++) {
            for (int z = from.getZ(); z <= to.getZ(); z++) {
                mutable.set(x, y, z);
                if (!validator.isValid(world, mutable, side)) {
                    return MultiblockResult.error(mutable.immutable(), "multiblock.soulforge.invalid_floor_block");
                }
                recordTank(world, mutable, validator, tanks);
            }
        }
        return MultiblockResult.success();
    }

    private void recordTank(Level world, BlockPos pos, BlockValidator validator, List<BlockPos> tanks) {
        if (validator.isTank(world.getBlockState(pos)) && !tanks.contains(pos)) {
            tanks.add(pos.immutable());
        }
    }

    /** 从 pos 出发沿 direction 走到第一面墙，最多走 innerLimit + 1 格。 */
    private BlockPos walkToWall(Level world, BlockPos pos, Direction direction) {
        BlockPos current = pos;
        for (int i = 0; i <= innerLimit && world.hasChunkAt(current); i++) {
            BlockPos next = current.relative(direction);
            if (!world.hasChunkAt(next) || !isInnerBlock(world, next)) {
                return next;
            }
            current = next;
        }
        return current.relative(direction);
    }

    /**
     * 判断结构正上方一层能否补成新的炉壁层，用于免去整结构重扫就能判断"能不能往上加盖"。
     */
    public boolean canExpand(Level world, MultiblockStructure structure, BlockValidator validator) {
        BlockPos from = new BlockPos(structure.getMin().getX(), structure.getMax().getY() + 1, structure.getMin().getZ());
        BlockPos to = new BlockPos(structure.getMax().getX(), structure.getMax().getY() + 1, structure.getMax().getZ());
        return detectLayer(world, from, to, validator, new ArrayList<>()).isSuccess();
    }

    /**
     * 判断某个方块变化是否值得触发一次结构重扫。
     * 只有"结构外壳被换成了非法方块""结构内部被塞了东西""正上方出现了墙"三种情况才需要，
     * 否则远处的无关方块变动会不断触发全结构扫描。
     */
    public boolean shouldUpdate(Level world, MultiblockStructure structure, BlockPos pos, BlockState state,
                                BlockValidator validator) {
        if (structure.withinBounds(pos)) {
            if (structure.contains(pos)) {
                CuboidSide side = pos.getY() == structure.getMin().getY() && hasFloor ? CuboidSide.FLOOR : CuboidSide.WALL;
                return !validator.isValid(world, pos, side);
            }
            return structure.isInside(pos) && !state.isAir();
        }
        return structure.isDirectlyAbove(pos);
    }

    public boolean isInnerBlock(Level world, BlockPos pos) {
        return world.getBlockState(pos).isAir();
    }

    @Nullable
    private MultiblockStructure fail(MultiblockResult result) {
        this.lastResult = result;
        return null;
    }

    public enum CuboidSide {
        FLOOR,
        WALL,
        CEILING
    }

    /** 方块合法性判定。{@link #isTank} 用于让扫描过程顺带收集燃料储罐位置。 */
    public interface BlockValidator {
        boolean isValid(Level world, BlockPos pos, CuboidSide side);

        default boolean isTank(BlockState state) {
            return false;
        }
    }
}
