package com.soulforge.soulforge.recipe.casting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soulforge.soulforge.recipe.SoulForgeRecipeSerializers;
import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import net.minecraft.core.NonNullList;
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
 * Recipe for the casting table or basin. A fluid is poured over an optional
 * cast item to produce an item result.
 */
public class CastingRecipe implements Recipe<SingleRecipeInput> {
    public static final MapCodec<CastingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FluidStack.CODEC.fieldOf("fluid").forGetter(CastingRecipe::getFluid),
            Ingredient.CODEC.optionalFieldOf("cast", Ingredient.EMPTY).forGetter(CastingRecipe::getCast),
            ItemStack.CODEC.fieldOf("output").forGetter(CastingRecipe::getOutput),
            Codec.INT.optionalFieldOf("coolingTime", 100).forGetter(CastingRecipe::getCoolingTime),
            Codec.BOOL.optionalFieldOf("basin", false).forGetter(CastingRecipe::isBasin)
    ).apply(instance, CastingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastingRecipe> STREAM_CODEC = StreamCodec.composite(
            FluidStack.STREAM_CODEC, CastingRecipe::getFluid,
            Ingredient.CONTENTS_STREAM_CODEC, CastingRecipe::getCast,
            ItemStack.STREAM_CODEC, CastingRecipe::getOutput,
            ByteBufCodecs.INT, CastingRecipe::getCoolingTime,
            ByteBufCodecs.BOOL, CastingRecipe::isBasin,
            CastingRecipe::new
    );

    public static final RecipeSerializer<CastingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private final FluidStack fluid;
    private final Ingredient cast;
    private final ItemStack output;
    private final int coolingTime;
    private final boolean basin;

    public CastingRecipe(FluidStack fluid, Ingredient cast, ItemStack output, int coolingTime, boolean basin) {
        this.fluid = fluid != null ? fluid : FluidStack.EMPTY;
        this.cast = cast != null ? cast : Ingredient.EMPTY;
        this.output = output != null ? output.copy() : ItemStack.EMPTY;
        this.coolingTime = coolingTime;
        this.basin = basin;
    }

    public FluidStack getFluid() {
        return fluid;
    }

    public Ingredient getCast() {
        return cast;
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public int getCoolingTime() {
        return coolingTime;
    }

    public boolean isBasin() {
        return basin;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        if (!cast.isEmpty()) {
            list.add(cast);
        }
        return list;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return cast.isEmpty() || cast.test(input.getItem(0));
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return output.copy();
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
        return cast.isEmpty() ? PlacementInfo.NOT_PLACEABLE : PlacementInfo.create(cast);
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.FURNACE_MISC;
    }

    @Override
    public RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
        return SoulForgeRecipeSerializers.CASTING.get();
    }

    @Override
    public RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
        return SoulForgeRecipeTypes.CASTING.get();
    }
}
