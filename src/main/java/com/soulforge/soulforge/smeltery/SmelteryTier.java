package com.soulforge.soulforge.smeltery;

import com.mojang.serialization.Codec;
import com.soulforge.soulforge.block.SoulForgeBlocks;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;

public enum SmelteryTier implements StringRepresentable {
    SMELTERY("smeltery", 1500),
    INTERMEDIATE("intermediate", 3500),
    ADVANCED("advanced", 5000),
    HYBRID("hybrid", 10000),
    FUSION("fusion", 100_000_000);

    public static final Codec<SmelteryTier> CODEC = StringRepresentable.fromEnum(SmelteryTier::values);

    private final String name;
    private final int maxTemperature;

    SmelteryTier(String name, int maxTemperature) {
        this.name = name;
        this.maxTemperature = maxTemperature;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String getName() {
        return name;
    }

    public int getMaxTemperature() {
        return maxTemperature;
    }

    public boolean canHandleTemperature(int temperature) {
        return temperature <= maxTemperature;
    }

    public Block getRepresentativeBlock() {
        return switch (this) {
            case SMELTERY -> SoulForgeBlocks.SMELTERY_BRICKS.get();
            case INTERMEDIATE -> SoulForgeBlocks.NETHERITE_BRICKS.get();
            case ADVANCED -> SoulForgeBlocks.TRINITY_ALLOY_BRICKS.get();
            case HYBRID -> SoulForgeBlocks.SUPER_RESISTANT_METAL_BLOCK.get();
            case FUSION -> SoulForgeBlocks.SINGULARITY_BRICKS.get();
        };
    }
}
