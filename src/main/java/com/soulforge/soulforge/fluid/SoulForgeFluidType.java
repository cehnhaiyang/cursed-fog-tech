package com.soulforge.soulforge.fluid;

import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

public class SoulForgeFluidType extends FluidType {
    private final Identifier stillTexture;
    private final Identifier flowingTexture;
    private final int tintColor;

    public SoulForgeFluidType(Properties properties, Identifier stillTexture, Identifier flowingTexture, int tintColor) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.tintColor = tintColor;
    }

    /** 获取流体染色值（ARGB 格式） */
    public int getTintColor() {
        return tintColor;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public Identifier getStillTexture() {
                return stillTexture;
            }

            @Override
            public Identifier getFlowingTexture() {
                return flowingTexture;
            }

            @Override
            public int getTintColor() {
                return tintColor;
            }
        });
    }
}
