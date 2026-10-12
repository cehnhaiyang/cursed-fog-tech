package com.soulforge.soulforge.recipe;

import com.soulforge.soulforge.SoulForge;
import com.soulforge.soulforge.SoulForgeRegistries;
import com.soulforge.soulforge.recipe.alloying.AlloyingRecipe;
import com.soulforge.soulforge.recipe.alloying.FusionRecipe;
import com.soulforge.soulforge.recipe.casting.CastingRecipe;
import com.soulforge.soulforge.recipe.melting.MeltingRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;

public class SoulForgeRecipeTypes {
    public static final DeferredHolder<RecipeType<?>, RecipeType<AlloyingRecipe>> ALLOYING = SoulForgeRegistries.RECIPE_TYPES.register("alloying", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, "alloying")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<FusionRecipe>> FUSION = SoulForgeRegistries.RECIPE_TYPES.register("fusion", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, "fusion")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<CastingRecipe>> CASTING = SoulForgeRegistries.RECIPE_TYPES.register("casting", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, "casting")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<MeltingRecipe>> MELTING = SoulForgeRegistries.RECIPE_TYPES.register("melting", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, "melting")));

    public static void register() {
    }
}
