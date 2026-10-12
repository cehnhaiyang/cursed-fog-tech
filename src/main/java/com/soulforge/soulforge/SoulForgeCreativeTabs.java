package com.soulforge.soulforge;

import com.soulforge.soulforge.item.SoulForgeItems;
import com.soulforge.soulforge.fluid.SoulForgeFluids;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

public class SoulForgeCreativeTabs {
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SOULFORGE_TAB = SoulForgeRegistries.CREATIVE_TABS
            .register("soulforge_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.soulforge"))
                            .icon(() -> new ItemStack(SoulForgeItems.TRINITY_ALLOY_INGOT.get()))
                            .displayItems((params, output) -> {
                                // Basic items
                                output.accept(SoulForgeItems.GLOVE.get());
                                output.accept(SoulForgeItems.BLUNT_THERMAL_GLOVE.get());
                                output.accept(SoulForgeItems.MAGNETIC_COIL.get());
                                output.accept(SoulForgeItems.VACUUM_GENERATOR.get());
                                output.accept(SoulForgeItems.MAGVAC_GENERATOR.get());

                                // Special items
                                output.accept(SoulForgeItems.NETHER_HEART.get());
                                output.accept(SoulForgeItems.INGOT_CAST.get());
                                output.accept(SoulForgeItems.GEM_CAST.get());

                                // Ingots and storage blocks
                                output.accept(SoulForgeItems.TRINITY_ALLOY_INGOT.get());
                                output.accept(SoulForgeItems.TRINITY_ALLOY_BLOCK.get());
                                output.accept(SoulForgeItems.SUPER_RESISTANT_METAL_BLOCK.get());
                                output.accept(SoulForgeItems.MAGVAC_ALLOY_BLOCK.get());

                                // Bricks
                                output.accept(SoulForgeItems.SMELTERY_BRICKS.get());
                                output.accept(SoulForgeItems.NETHERITE_BRICKS.get());
                                output.accept(SoulForgeItems.TRINITY_ALLOY_BRICKS.get());
                                output.accept(SoulForgeItems.SINGULARITY_BRICKS.get());

                                // Controllers
                                output.accept(SoulForgeItems.SMELTERY_CONTROLLER.get());
                                output.accept(SoulForgeItems.INTERMEDIATE_SMELTERY_CONTROLLER.get());
                                output.accept(SoulForgeItems.ADVANCED_SMELTERY_CONTROLLER.get());
                                output.accept(SoulForgeItems.HYBRID_FURNACE_CONTROLLER.get());
                                output.accept(SoulForgeItems.FUSION_FURNACE_CONTROLLER.get());

                                // Tanks (portable)
                                output.accept(SoulForgeItems.NETHERITE_FUEL_TANK.get());
                                output.accept(SoulForgeItems.TRINITY_ALLOY_FUEL_TANK.get());
                                output.accept(SoulForgeItems.MAGVAC_FUEL_TANK.get());

                                // Tank blocks
                                output.accept(SoulForgeItems.NETHERITE_FUEL_TANK_BLOCK.get());
                                output.accept(SoulForgeItems.TRINITY_ALLOY_FUEL_TANK_BLOCK.get());
                                output.accept(SoulForgeItems.MAGVAC_FUEL_TANK_BLOCK.get());

                                // Casting devices
                                output.accept(SoulForgeItems.CASTING_TABLE.get());
                                output.accept(SoulForgeItems.CASTING_BASIN.get());

                                // Empty high-temperature buckets
                                output.accept(SoulForgeItems.NETHERITE_BUCKET.get());
                                output.accept(SoulForgeItems.TRINITY_ALLOY_BUCKET.get());
                                output.accept(SoulForgeItems.MAGVAC_BUCKET.get());

                                // Molten metal buckets
                                output.accept(SoulForgeFluids.MOLTEN_IRON.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_GOLD.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_COPPER.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_COBALT.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_NETHERITE.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_DIAMOND.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_QUARTZ.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_OBSIDIAN.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_TRINITY_ALLOY.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_SINGULARITY.bucket().get());

                                // Blazing blood buckets
                                output.accept(SoulForgeFluids.BLAZING_BLOOD.bucket().get());
                                output.accept(SoulForgeFluids.ACTIVATED_BLAZING_BLOOD.bucket().get());
                                output.accept(SoulForgeFluids.IONIZED_BLAZING_BLOOD.bucket().get());
                                output.accept(SoulForgeFluids.PLASMA_BLAZING_BLOOD.bucket().get());
                                output.accept(SoulForgeFluids.ETERNAL_BLAZING_BLOOD.bucket().get());

                                // Other fluid buckets
                                output.accept(SoulForgeFluids.MOLTEN_NETHER_HEART.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_ENDER_PEARL.bucket().get());
                                output.accept(SoulForgeFluids.MOLTEN_NETHER_STAR.bucket().get());
                            })
                            .build());

    public static void register() {
    }
}
