package com.soulforge.soulforge.smeltery.container;

import com.soulforge.soulforge.item.SoulForgeItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum FluidContainerTier {
    VANILLA_BUCKET(1000),
    NETHERITE_BUCKET(3500),
    TRINITY_ALLOY_BUCKET(5000),
    MAGVAC_BUCKET(Integer.MAX_VALUE),
    NETHERITE_FUEL_TANK(3500),
    TRINITY_ALLOY_FUEL_TANK(5000),
    MAGVAC_FUEL_TANK(Integer.MAX_VALUE);

    private final int maxTemperature;

    FluidContainerTier(int maxTemperature) {
        this.maxTemperature = maxTemperature;
    }

    public int getMaxTemperature() {
        return maxTemperature;
    }

    public static int getMaxTemperatureFor(ItemStack stack) {
        Item item = stack.getItem();
        if (item == SoulForgeItems.NETHERITE_BUCKET.get() || item == SoulForgeItems.NETHERITE_FUEL_TANK.get()) {
            return NETHERITE_BUCKET.maxTemperature;
        }
        if (item == SoulForgeItems.TRINITY_ALLOY_BUCKET.get() || item == SoulForgeItems.TRINITY_ALLOY_FUEL_TANK.get()) {
            return TRINITY_ALLOY_BUCKET.maxTemperature;
        }
        if (item == SoulForgeItems.MAGVAC_BUCKET.get() || item == SoulForgeItems.MAGVAC_FUEL_TANK.get()) {
            return MAGVAC_BUCKET.maxTemperature;
        }
        if (item == Items.BUCKET || item == Items.GLASS_BOTTLE) {
            return VANILLA_BUCKET.maxTemperature;
        }
        // Any other fluid container (tanks from other mods) - assume steel-ish 2000
        return 2000;
    }
}
