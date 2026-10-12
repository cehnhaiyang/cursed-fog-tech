package com.soulforge.soulforge.recipe.alloying;

import com.mojang.serialization.MapCodec;
import com.soulforge.soulforge.recipe.SoulForgeRecipeSerializers;
import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Recipe for the fusion furnace. Behavior is identical to
 * {@link AlloyingRecipe}
 * at the recipe-data level; the furnace block entity evaluates traits using
 * {@code Mode.FUSION} so every participating material contributes at R = 1.0.
 */
public class FusionRecipe extends AlloyingRecipe {
        public static final MapCodec<FusionRecipe> CODEC = AlloyingRecipe.CODEC.xmap(
                        recipe -> new FusionRecipe(recipe.getInputs(), recipe.getAmounts(), recipe.getOutputTemplate(),
                                        recipe.getCustomName(), recipe.getColor(), recipe.getTemperature()),
                        recipe -> recipe);

        public static final StreamCodec<RegistryFriendlyByteBuf, FusionRecipe> STREAM_CODEC = AlloyingRecipe.STREAM_CODEC
                        .map(
                                        recipe -> new FusionRecipe(recipe.getInputs(), recipe.getAmounts(),
                                                        recipe.getOutputTemplate(),
                                                        recipe.getCustomName(), recipe.getColor(),
                                                        recipe.getTemperature()),
                                        recipe -> recipe);

        public static final RecipeSerializer<FusionRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

        public FusionRecipe(java.util.List<net.neoforged.neoforge.fluids.crafting.FluidIngredient> inputs,
                        java.util.List<Integer> amounts,
                        net.neoforged.neoforge.fluids.FluidStackTemplate output,
                        String customName, int color, int temperature) {
                super(inputs, amounts, output, customName, color, temperature);
        }

        @Override
        public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
                return SoulForgeRecipeSerializers.FUSION.get();
        }

        @Override
        public RecipeType<? extends Recipe<RecipeInput>> getType() {
                return SoulForgeRecipeTypes.FUSION.get();
        }
}
