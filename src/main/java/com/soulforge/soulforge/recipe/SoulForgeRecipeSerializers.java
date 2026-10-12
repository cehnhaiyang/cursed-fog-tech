package com.soulforge.soulforge.recipe;

import com.soulforge.soulforge.SoulForgeRegistries;
import com.soulforge.soulforge.recipe.alloying.AlloyingRecipe;
import com.soulforge.soulforge.recipe.alloying.FusionRecipe;
import com.soulforge.soulforge.recipe.casting.CastingRecipe;
import com.soulforge.soulforge.recipe.melting.MeltingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;

public class SoulForgeRecipeSerializers {
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyingRecipe>> ALLOYING = SoulForgeRegistries.RECIPE_SERIALIZERS.register("alloying", () -> AlloyingRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FusionRecipe>> FUSION = SoulForgeRegistries.RECIPE_SERIALIZERS.register("fusion", () -> FusionRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CastingRecipe>> CASTING = SoulForgeRegistries.RECIPE_SERIALIZERS.register("casting", () -> CastingRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MeltingRecipe>> MELTING = SoulForgeRegistries.RECIPE_SERIALIZERS.register("melting", () -> MeltingRecipe.SERIALIZER);

    public static void register() {
    }
}
