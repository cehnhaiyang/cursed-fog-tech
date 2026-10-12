package com.soulforge.soulforge.item.component;

import com.soulforge.soulforge.SoulForgeRegistries;
import com.soulforge.soulforge.alloy.AlloyMixture;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;

public class SoulForgeDataComponents {
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AlloyMixture>> ALLOY_DATA = SoulForgeRegistries.DATA_COMPONENTS.register("alloy_data",
            () -> DataComponentType.<AlloyMixture>builder()
                    .persistent(AlloyMixture.CODEC)
                    .networkSynchronized(AlloyMixture.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> FLUID_CONTENT = SoulForgeRegistries.DATA_COMPONENTS.register("fluid_content",
            () -> DataComponentType.<SimpleFluidContent>builder()
                    .persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC)
                    .build());

    public static void register() {
    }
}
