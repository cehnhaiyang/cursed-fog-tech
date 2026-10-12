package com.soulforge.soulforge.item;

import net.minecraft.world.item.Item;

public class SuperheatedResistantTankItem extends Item {
    private final int maxTemperature;

    public SuperheatedResistantTankItem(int maxTemperature, Properties properties) {
        super(properties);
        this.maxTemperature = maxTemperature;
    }

    public int getMaxTemperature() {
        return maxTemperature;
    }
}
