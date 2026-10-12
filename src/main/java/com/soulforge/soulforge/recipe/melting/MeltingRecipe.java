package com.soulforge.soulforge.recipe.melting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soulforge.soulforge.recipe.SoulForgeRecipeSerializers;
import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Recipe for melting items into fluids in a smeltery or furnace.
 */
public class MeltingRecipe implements Recipe<SingleRecipeInput> {
    public static final MapCodec<MeltingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.fieldOf("input").forGetter(MeltingRecipe::getInput),
            FluidStack.CODEC.fieldOf("output").forGetter(MeltingRecipe::getOutput),
            Codec.INT.optionalFieldOf("temperature", 1000).forGetter(MeltingRecipe::getTemperature),
            Codec.INT.optionalFieldOf("time", 100).forGetter(MeltingRecipe::getTime)
    ).apply(instance, MeltingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MeltingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, MeltingRecipe::getInput,
            FluidStack.STREAM_CODEC, MeltingRecipe::getOutput,
            ByteBufCodecs.INT, MeltingRecipe::getTemperature,
            ByteBufCodecs.INT, MeltingRecipe::getTime,
            MeltingRecipe::new
    );

    public static final RecipeSerializer<MeltingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private final Ingredient input;
    private final FluidStack output;
    private final int temperature;
    private final int time;

    public MeltingRecipe(Ingredient input, FluidStack output, int temperature, int time) {
        this.input = input != null ? input : Ingredient.of();
        this.output = output != null ? output.copy() : FluidStack.EMPTY;
        this.temperature = temperature;
        this.time = time;
    }

    public Ingredient getInput() {
        return input;
    }

    public FluidStack getOutput() {
        return output.copy();
    }

    public int getTemperature() {
        return temperature;
    }

    public int getTime() {
        return time;
    }

    @Override
    public boolean matches(SingleRecipeInput recipeInput, Level level) {
        return input.test(recipeInput.getItem(0));
    }

    @Override
    public ItemStack assemble(SingleRecipeInput recipeInput) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(input);
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.FURNACE_MISC;
    }

    @Override
    public RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
        return SoulForgeRecipeSerializers.MELTING.get();
    }

    @Override
    public RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
        return SoulForgeRecipeTypes.MELTING.get();
    }
}
