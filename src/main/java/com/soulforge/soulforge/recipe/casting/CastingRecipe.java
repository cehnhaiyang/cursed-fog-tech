package com.soulforge.soulforge.recipe.casting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soulforge.soulforge.recipe.SoulForgeRecipeSerializers;
import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStackTemplate;

import java.util.Optional;

/**
 * Recipe for the casting table or basin. A fluid is poured over an optional
 * cast item to produce an item result.
 *
 * <p>输入输出用 {@link FluidStackTemplate}/{@link ItemStackTemplate} 而非
 * {@code FluidStack}/{@code ItemStack}：后两者的 codec 要求数据组件已绑定，
 * 而配方在注册表加载阶段解析，那时所有物品与流体都还没绑定组件。
 */
public class CastingRecipe implements Recipe<SingleRecipeInput> {
    public static final MapCodec<CastingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FluidStackTemplate.CODEC.fieldOf("fluid").forGetter(CastingRecipe::getFluidTemplate),
            Ingredient.CODEC.optionalFieldOf("cast").forGetter(CastingRecipe::getCast),
            ItemStackTemplate.CODEC.fieldOf("output").forGetter(CastingRecipe::getOutputTemplate),
            Codec.INT.optionalFieldOf("coolingTime", 100).forGetter(CastingRecipe::getCoolingTime),
            Codec.BOOL.optionalFieldOf("basin", false).forGetter(CastingRecipe::isBasin)
    ).apply(instance, CastingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastingRecipe> STREAM_CODEC = StreamCodec.composite(
            FluidStackTemplate.STREAM_CODEC, CastingRecipe::getFluidTemplate,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, CastingRecipe::getCast,
            ItemStackTemplate.STREAM_CODEC, CastingRecipe::getOutputTemplate,
            ByteBufCodecs.INT, CastingRecipe::getCoolingTime,
            ByteBufCodecs.BOOL, CastingRecipe::isBasin,
            CastingRecipe::new
    );

    public static final RecipeSerializer<CastingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private final FluidStackTemplate fluid;
    private final Optional<Ingredient> cast;
    private final ItemStackTemplate output;
    private final int coolingTime;
    private final boolean basin;

    public CastingRecipe(FluidStackTemplate fluid, Optional<Ingredient> cast, ItemStackTemplate output,
                         int coolingTime, boolean basin) {
        this.fluid = fluid;
        this.cast = cast != null ? cast : Optional.empty();
        this.output = output;
        this.coolingTime = coolingTime;
        this.basin = basin;
    }

    public FluidStackTemplate getFluidTemplate() {
        return fluid;
    }

    public ItemStackTemplate getOutputTemplate() {
        return output;
    }

    /** 配方所需流体量（mB）。 */
    public int getFluidAmount() {
        return fluid.amount();
    }

    public Optional<Ingredient> getCast() {
        return cast;
    }

    /** 铸造产物。每次调用返回新的堆叠实例。 */
    public ItemStack getOutput() {
        return output.create();
    }

    public int getCoolingTime() {
        return coolingTime;
    }

    public boolean isBasin() {
        return basin;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return Ingredient.testOptionalIngredient(cast, input.getItem(0));
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return output.create();
    }

    /**
     * 铸造配方没有物品输入（流体才是输入），无法放进配方书网格。
     * 标记为 special 以免 {@code RecipeManager} 对空 ingredients 报警告。
     */
    @Override
    public boolean isSpecial() {
        return true;
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
        return cast.map(PlacementInfo::create).orElse(PlacementInfo.NOT_PLACEABLE);
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
