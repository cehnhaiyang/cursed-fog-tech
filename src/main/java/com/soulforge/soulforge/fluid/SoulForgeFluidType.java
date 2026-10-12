package com.soulforge.soulforge.fluid;

import net.neoforged.neoforge.fluids.FluidType;

public class SoulForgeFluidType extends FluidType {
    private final int tintColor;

    public SoulForgeFluidType(Properties properties, int tintColor) {
        super(properties);
        this.tintColor = tintColor;
    }

    /** 获取流体染色值（ARGB 格式） */
    public int getTintColor() {
        return tintColor;
    }
}
