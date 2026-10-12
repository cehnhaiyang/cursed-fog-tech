package com.soulforge.soulforge.item;

import net.minecraft.world.item.Item;

public class SuperheatedResistantBucketItem extends Item {
    private final int maxTemperature;

    public SuperheatedResistantBucketItem(int maxTemperature, Properties properties) {
        super(properties);
        this.maxTemperature = maxTemperature;
    }

    public int getMaxTemperature() {
        return maxTemperature;
    }
}
