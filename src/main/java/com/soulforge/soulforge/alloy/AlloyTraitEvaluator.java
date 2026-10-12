package com.soulforge.soulforge.alloy;

import com.soulforge.soulforge.material.MaterialRegistry;
import com.soulforge.soulforge.material.SoulForgeMaterial;
import com.soulforge.soulforge.material.trait.MaterialTrait;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Determines the effective trait strength (R) for each material in an alloy.
 *
 * <ul>
 *   <li>Hybrid furnace output: R equals the material's normalized ratio.</li>
 *   <li>Fusion furnace output: R = 1.0 for every participating material.</li>
 * </ul>
 *
 * <p>Also implements cobalt's tungsten-binder durability bonus for brittle
 * materials.
 */
public class AlloyTraitEvaluator {
    public enum Mode {
        HYBRID,
        FUSION
    }

    public static final Set<Identifier> BRITTLE_MATERIALS = Set.of(
            MaterialRegistry.CALCITE.getId(),
            MaterialRegistry.QUARTZ.getId(),
            MaterialRegistry.DIAMOND.getId(),
            MaterialRegistry.OBSIDIAN.getId()
    );

    /**
     * Returns a map from material ID to effective trait strength R.
     */
    public static Map<Identifier, Float> evaluateTraitStrengths(AlloyMixture mixture, Mode mode) {
        mixture.normalize();
        Map<Identifier, Float> strengths = new HashMap<>();
        if (mode == Mode.FUSION) {
            for (AlloyComponent component : mixture.getComponents()) {
                strengths.put(component.materialId(), 1.0f);
            }
        } else {
            for (AlloyComponent component : mixture.getComponents()) {
                strengths.put(component.materialId(), component.ratio());
            }
        }
        return strengths;
    }

    // Placeholder to keep file stable; cobalt method follows

    /**
     * Computes the total durability multiplier granted by cobalt to brittle
     * materials. Every 10% cobalt adds 15% durability, so the multiplier is
     * {@code cobaltRatio * 1.5f}.
     */
    public static float getCobaltTungstenBinderBonus(AlloyMixture mixture) {
        mixture.normalize();
        float cobaltRatio = 0.0f;
        for (AlloyComponent component : mixture.getComponents()) {
            SoulForgeMaterial material = MaterialRegistry.byId(component.materialId());
            if (material != null && material.getTrait() == MaterialTrait.COBALT) {
                cobaltRatio += component.ratio();
            }
        }
        return cobaltRatio * 1.5f;
    }
}
