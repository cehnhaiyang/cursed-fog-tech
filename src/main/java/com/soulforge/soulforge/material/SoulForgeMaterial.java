package com.soulforge.soulforge.material;

import com.soulforge.soulforge.material.trait.MaterialTrait;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class SoulForgeMaterial {
    private final Identifier id;
    private final String formula;
    private final float hardness;
    private final float density;
    private final int meltingPoint;
    private final int durability;
    private final float miningSpeed;
    private final float attackDamage;
    private final int armor;
    private final int toughness;
    private final int miningLevel;
    private final MaterialTrait trait;

    public SoulForgeMaterial(Identifier id, String formula, float hardness, float density, int meltingPoint,
                             int durability, float miningSpeed, float attackDamage, int armor, int toughness, int miningLevel,
                             MaterialTrait trait) {
        this.id = id;
        this.formula = formula;
        this.hardness = hardness;
        this.density = density;
        this.meltingPoint = meltingPoint;
        this.durability = durability;
        this.miningSpeed = miningSpeed;
        this.attackDamage = attackDamage;
        this.armor = armor;
        this.toughness = toughness;
        this.miningLevel = miningLevel;
        this.trait = trait;
    }

    public Identifier getId() { return id; }
    public String getFormula() { return formula; }
    public float getHardness() { return hardness; }
    public float getDensity() { return density; }
    public int getMeltingPoint() { return meltingPoint; }
    public int getDurability() { return durability; }
    public float getMiningSpeed() { return miningSpeed; }
    public float getAttackDamage() { return attackDamage; }
    public int getArmor() { return armor; }
    public int getToughness() { return toughness; }
    public int getMiningLevel() { return miningLevel; }
    public MaterialTrait getTrait() { return trait; }

    public static class Builder {
        private final String formula;
        private float hardness;
        private float density;
        private int meltingPoint;
        private int durability;
        private float miningSpeed;
        private float attackDamage;
        private int armor;
        private int toughness;
        private int miningLevel;
        private MaterialTrait trait = MaterialTrait.NONE;

        public Builder(String formula) {
            this.formula = formula;
        }

        public Builder hardness(float v) { this.hardness = v; return this; }
        public Builder density(float v) { this.density = v; return this; }
        public Builder meltingPoint(int v) { this.meltingPoint = v; return this; }
        public Builder durability(int v) { this.durability = v; return this; }
        public Builder miningSpeed(float v) { this.miningSpeed = v; return this; }
        public Builder attackDamage(float v) { this.attackDamage = v; return this; }
        public Builder armor(int v) { this.armor = v; return this; }
        public Builder toughness(int v) { this.toughness = v; return this; }
        public Builder miningLevel(int v) { this.miningLevel = v; return this; }

        public Builder noTrait() { this.trait = MaterialTrait.NONE; return this; }
        public Builder traitCalcite() { this.trait = MaterialTrait.CALCITE; return this; }
        public Builder traitGold() { this.trait = MaterialTrait.GOLD; return this; }
        public Builder traitCopper() { this.trait = MaterialTrait.COPPER; return this; }
        public Builder traitQuartz() { this.trait = MaterialTrait.QUARTZ; return this; }
        public Builder traitDiamond() { this.trait = MaterialTrait.DIAMOND; return this; }
        public Builder traitCobalt() { this.trait = MaterialTrait.COBALT; return this; }
        public Builder traitObsidian() { this.trait = MaterialTrait.OBSIDIAN; return this; }
        public Builder traitNetherite() { this.trait = MaterialTrait.NETHERITE; return this; }
        public Builder traitTrinityAlloy() { this.trait = MaterialTrait.TRINITY_ALLOY; return this; }

        public SoulForgeMaterial build(Identifier id) {
            return new SoulForgeMaterial(id, formula, hardness, density, meltingPoint, durability, miningSpeed, attackDamage, armor, toughness, miningLevel, trait);
        }
    }
}
