package com.soulforge.soulforge.smeltery.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Result of a multiblock validation attempt.
 */
public class MultiblockResult {
    private final boolean success;
    @Nullable
    private final BlockPos pos;
    private final Component message;

    private MultiblockResult(boolean success, @Nullable BlockPos pos, Component message) {
        this.success = success;
        this.pos = pos;
        this.message = message;
    }

    public static MultiblockResult success() {
        return new MultiblockResult(true, null, Component.empty());
    }

    public static MultiblockResult error(@Nullable BlockPos pos, String translationKey) {
        return new MultiblockResult(false, pos, Component.translatable(translationKey));
    }

    public boolean isSuccess() {
        return success;
    }

    @Nullable
    public BlockPos getPos() {
        return pos;
    }

    public Component getMessage() {
        return message;
    }
}
