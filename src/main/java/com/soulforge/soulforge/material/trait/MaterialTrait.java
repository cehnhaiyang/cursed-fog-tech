package com.soulforge.soulforge.material.trait;

public enum MaterialTrait {
    NONE("none"),
    CALCITE("calcite"),
    GOLD("gold"),
    COPPER("copper"),
    QUARTZ("quartz"),
    DIAMOND("diamond"),
    COBALT("cobalt"),
    OBSIDIAN("obsidian"),
    NETHERITE("netherite"),
    TRINITY_ALLOY("trinity_alloy");

    private final String name;

    MaterialTrait(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
