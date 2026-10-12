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
 * Validates a SoulForge smeltery multiblock. The controller's tier decides which
 * wall/floor blocks are valid; tank blocks of equal or higher tier may also serve as walls.
 */
public class SmelteryMultiblock {
    private final SmelteryTier tier;
    private final MultiblockCuboid cuboid;

    public SmelteryMultiblock(SmelteryTier tier) {
        this.tier = tier;
        // SoulForge smelteries have a floor, no ceiling, and may expand upward
        this.cuboid = new MultiblockCuboid(true, false);
    }

    public SmelteryTier getTier() {
        return tier;
    }

    /**
     * Attempts to detect a valid smeltery multiblock.
     *
     * @param world      Level instance
     * @param controller Controller block position
     * @param facing     Direction the controller is facing (interior is opposite)
     * @return Detected structure, or null if invalid
     */
    @Nullable
    public MultiblockStructure detectMultiblock(Level world, BlockPos controller, Direction facing) {
        BlockPos center = controller.relative(facing.getOpposite());
        if (!cuboid.isAirOrInnerBlock(world, center)) {
            return null;
        }

        return cuboid.detectMultiblock(world, center, this::isValidBlock);
    }


    /**
     * Validates the shell of a candidate cuboid using this tier's rules.
     */
    public MultiblockResult validateShell(Level world, BlockPos min, BlockPos max) {
        return cuboid.validateShell(world, min, max, this::isValidBlock);
    }

    private boolean isValidBlock(Level world, BlockPos pos, MultiblockCuboid.CuboidSide side) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // Fuel tank blocks of equal or higher tier are valid as walls
        if (isValidTankBlock(block)) {
            return true;
        }

        // Primary wall/floor block for this tier
        return block == tier.getRepresentativeBlock();
    }

    private boolean isValidTankBlock(Block block) {
        return block == SoulForgeBlocks.NETHERITE_FUEL_TANK.get()
                || block == SoulForgeBlocks.TRINITY_ALLOY_FUEL_TANK.get()
                || block == SoulForgeBlocks.MAGVAC_FUEL_TANK.get();
    }


    /**
     * Determines the smeltery tier implied by the block at the given position.
     * Used by the controller block entity when it needs to infer its tier from neighbors.
     */
    public static SmelteryTier getTierFromBlock(Block block) {
        if (block == SoulForgeBlocks.NETHERITE_BRICKS.get()) {
            return SmelteryTier.INTERMEDIATE;
        }
        if (block == SoulForgeBlocks.TRINITY_ALLOY_BRICKS.get()) {
            return SmelteryTier.ADVANCED;
        }
        if (block == SoulForgeBlocks.SUPER_RESISTANT_METAL_BLOCK.get()
                || block == SoulForgeBlocks.MAGVAC_ALLOY_BLOCK.get()) {
            return SmelteryTier.HYBRID;
        }
        if (block == SoulForgeBlocks.SINGULARITY_BRICKS.get()) {
            return SmelteryTier.FUSION;
        }
        return SmelteryTier.SMELTERY;
    }

    @Nullable
    public MultiblockResult getLastResult() {
        return cuboid.getLastResult();
    }
}
