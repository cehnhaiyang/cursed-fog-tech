package com.soulforge.soulforge.item.component;

import com.soulforge.soulforge.alloy.AlloyMixture;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.fluids.FluidStack;

public class AlloyData {
    public static DataComponentType<AlloyMixture> getType() {
        return SoulForgeDataComponents.ALLOY_DATA.get();
    }

    public static AlloyMixture getMixture(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new AlloyMixture();
        }
        AlloyMixture mixture = stack.get(getType());
        return mixture != null ? mixture : new AlloyMixture();
    }

    public static void setMixture(FluidStack stack, AlloyMixture mixture) {
        if (stack != null && !stack.isEmpty()) {
            stack.set(getType(), mixture);
        }
    }
}
