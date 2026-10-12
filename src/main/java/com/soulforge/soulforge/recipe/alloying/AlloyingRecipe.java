package com.soulforge.soulforge.recipe.alloying;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.soulforge.soulforge.alloy.AlloyComponent;
import com.soulforge.soulforge.alloy.AlloyMixture;
import com.soulforge.soulforge.item.component.AlloyData;
import com.soulforge.soulforge.recipe.SoulForgeRecipeSerializers;
import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipe for the hybrid furnace. Inputs are fluid ingredients with optional
 * ratios; output is a fluid stack carrying an {@link AlloyMixture} component.
 */
public class AlloyingRecipe implements Recipe<RecipeInput> {
    public static final MapCodec<AlloyingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FluidIngredient.CODEC.listOf().fieldOf("inputs").forGetter(AlloyingRecipe::getInputs),
            Codec.INT.listOf().optionalFieldOf("ratios", List.of()).forGetter(AlloyingRecipe::getRatios),
            FluidStack.CODEC.fieldOf("output").forGetter(AlloyingRecipe::getOutput),
            Codec.STRING.optionalFieldOf("customName", "").forGetter(AlloyingRecipe::getCustomName),
            Codec.INT.optionalFieldOf("color", 0).forGetter(AlloyingRecipe::getColor)
    ).apply(instance, AlloyingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyingRecipe> STREAM_CODEC = StreamCodec.composite(
            FluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AlloyingRecipe::getInputs,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list()), AlloyingRecipe::getRatios,
            FluidStack.STREAM_CODEC, AlloyingRecipe::getOutput,
            ByteBufCodecs.STRING_UTF8, AlloyingRecipe::getCustomName,
            ByteBufCodecs.INT, AlloyingRecipe::getColor,
            AlloyingRecipe::new
    );

    public static final RecipeSerializer<AlloyingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private final List<FluidIngredient> inputs;
    private final List<Integer> ratios;
    private final FluidStack output;
    private final String customName;
    private final int color;

    public AlloyingRecipe(List<FluidIngredient> inputs, List<Integer> ratios, FluidStack output,
                          String customName, int color) {
        this.inputs = inputs != null ? inputs : List.of();
        this.ratios = ratios != null && !ratios.isEmpty() ? ratios : defaultRatios(this.inputs.size());
        this.output = output != null ? output : FluidStack.EMPTY;
        this.customName = customName != null ? customName : "";
        this.color = color;
    }

    private static List<Integer> defaultRatios(int size) {
        List<Integer> list = new ArrayList<>(Math.max(size, 0));
        for (int i = 0; i < size; i++) {
            list.add(1);
        }
        return list;
    }

    public List<FluidIngredient> getInputs() {
        return inputs;
    }

    public List<Integer> getRatios() {
        return ratios;
    }

    public FluidStack getOutput() {
        return output;
    }

    public String getCustomName() {
        return customName;
    }

    public int getColor() {
        return color;
    }

    /**
     * Builds the resulting alloy mixture from drained input fluids. If an input
     * already carries alloy data, its components are merged weighted by ratio;
     * otherwise the fluid is mapped to a material by stripping the {@code molten_}
     * prefix from its registry name.
     */
    public FluidStack assembleMixture(List<FluidStack> drainedInputs) {
        AlloyMixture mixture = new AlloyMixture();
        for (int i = 0; i < drainedInputs.size() && i < inputs.size(); i++) {
            FluidStack input = drainedInputs.get(i);
            if (input.isEmpty()) {
                continue;
            }
            int ratio = i < ratios.size() ? ratios.get(i) : 1;
            AlloyMixture inputMixture = AlloyData.getMixture(input);
            if (inputMixture != null && !inputMixture.getComponents().isEmpty()) {
                mixture.merge(inputMixture, ratio);
            } else {
                Identifier fluidId = BuiltInRegistries.FLUID.getKey(input.getFluid());
                Identifier materialId = mapFluidToMaterial(fluidId);
                mixture.addComponent(new AlloyComponent(materialId, ratio));
            }
        }
        mixture.setCustomName(customName);
        mixture.setColor(color);
        mixture.normalize();

        FluidStack result = output.copy();
        AlloyData.setMixture(result, mixture);
        return result;
    }

    protected Identifier mapFluidToMaterial(Identifier fluidId) {
        String path = fluidId.getPath();
        if (path.startsWith("molten_")) {
            return Identifier.fromNamespaceAndPath(fluidId.getNamespace(), path.substring("molten_".length()));
        }
        return fluidId;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
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
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.FURNACE_MISC;
    }

    @Override
    public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return SoulForgeRecipeSerializers.ALLOYING.get();
    }

    @Override
    public RecipeType<? extends Recipe<RecipeInput>> getType() {
        return SoulForgeRecipeTypes.ALLOYING.get();
    }
}
