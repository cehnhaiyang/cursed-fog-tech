package com.soulforge.soulforge.alloy;

import com.soulforge.soulforge.material.MaterialRegistry;
import com.soulforge.soulforge.material.SoulForgeMaterial;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Computes the final tool/armor stats for an {@link AlloyMixture}.
 *
 * <p>Core stats are weighted linear combinations of the component material
 * stats. Mining level is taken from the dominant (highest ratio) material.
 * Cobalt's tungsten-binder effect is applied to brittle materials.
 */
public class AlloyStatsEvaluator {
    public static class AlloyStats {
        public float durability;
        public float miningSpeed;
        public float attackDamage;
        public int armor;
        public int toughness;
        public int miningLevel;
        public final Map<Identifier, Float> traitStrengths = new HashMap<>();

        public AlloyStats(float durability, float miningSpeed, float attackDamage,
                          int armor, int toughness, int miningLevel) {
            this.durability = durability;
            this.miningSpeed = miningSpeed;
            this.attackDamage = attackDamage;
            this.armor = armor;
            this.toughness = toughness;
            this.miningLevel = miningLevel;
        }
    }

    public static AlloyStats evaluate(AlloyMixture mixture, AlloyTraitEvaluator.Mode mode) {
        mixture.normalize();
        float durability = 0.0f;
        float miningSpeed = 0.0f;
        float attackDamage = 0.0f;
        float armor = 0.0f;
        float toughness = 0.0f;
        int miningLevel = 0;
        float dominantRatio = -1.0f;

        float cobaltBonus = AlloyTraitEvaluator.getCobaltTungstenBinderBonus(mixture);

        for (AlloyComponent component : mixture.getComponents()) {
            SoulForgeMaterial material = MaterialRegistry.byId(component.materialId());
            if (material == null) {
                continue;
            }
            float ratio = component.ratio();

            float matDurability = material.getDurability();
            if (AlloyTraitEvaluator.BRITTLE_MATERIALS.contains(material.getId())) {
                matDurability *= 1.0f + cobaltBonus;
            }

            durability += matDurability * ratio;
            miningSpeed += material.getMiningSpeed() * ratio;
            attackDamage += material.getAttackDamage() * ratio;
            armor += material.getArmor() * ratio;
            toughness += material.getToughness() * ratio;

            if (ratio > dominantRatio) {
                dominantRatio = ratio;
                miningLevel = material.getMiningLevel();
            }
        }

        AlloyStats stats = new AlloyStats(
                durability,
                miningSpeed,
                attackDamage,
                Math.round(armor),
                Math.round(toughness),
                miningLevel
        );

        stats.traitStrengths.putAll(AlloyTraitEvaluator.evaluateTraitStrengths(mixture, mode));
        return stats;
    }
}
