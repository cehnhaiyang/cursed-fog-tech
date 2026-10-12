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
import net.neoforged.neoforge.fluids.FluidStackTemplate;
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
            Codec.INT.listOf().optionalFieldOf("amounts", List.of()).forGetter(AlloyingRecipe::getAmounts),
            FluidStackTemplate.CODEC.fieldOf("output").forGetter(AlloyingRecipe::getOutputTemplate),
            Codec.STRING.optionalFieldOf("customName", "").forGetter(AlloyingRecipe::getCustomName),
            Codec.INT.optionalFieldOf("color", 0).forGetter(AlloyingRecipe::getColor),
            Codec.INT.optionalFieldOf("temperature", 1000).forGetter(AlloyingRecipe::getTemperature))
            .apply(instance, AlloyingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyingRecipe> STREAM_CODEC = StreamCodec.composite(
            FluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AlloyingRecipe::getInputs,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list()), AlloyingRecipe::getAmounts,
            FluidStackTemplate.STREAM_CODEC, AlloyingRecipe::getOutputTemplate,
            ByteBufCodecs.STRING_UTF8, AlloyingRecipe::getCustomName,
            ByteBufCodecs.INT, AlloyingRecipe::getColor,
            ByteBufCodecs.INT, AlloyingRecipe::getTemperature,
            AlloyingRecipe::new);

    public static final RecipeSerializer<AlloyingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private final List<FluidIngredient> inputs;
    private final List<Integer> amounts;
    private final FluidStackTemplate output;
    private final String customName;
    private final int color;
    private final int temperature;

    public AlloyingRecipe(List<FluidIngredient> inputs, List<Integer> amounts, FluidStackTemplate output,
            String customName, int color, int temperature) {
        this.inputs = inputs != null ? inputs : List.of();
        this.amounts = amounts != null && !amounts.isEmpty() ? amounts : defaultAmounts(this.inputs.size(), output);
        this.output = output;
        this.customName = customName != null ? customName : "";
        this.color = color;
        this.temperature = temperature;
    }

    /**
     * 未显式给出用量时，把产物总量在输入之间均分。
     * 以产物量为基准是因为它是配方里唯一必然存在、且与用量同量纲的数值。
     */
    private static List<Integer> defaultAmounts(int inputCount, FluidStackTemplate output) {
        if (inputCount <= 0 || output == null) {
            return List.of();
        }
        int each = Math.max(1, output.amount() / inputCount);
        List<Integer> list = new ArrayList<>(inputCount);
        for (int i = 0; i < inputCount; i++) {
            list.add(each);
        }
        return list;
    }

    public List<FluidIngredient> getInputs() {
        return inputs;
    }

    /** 各输入组分的用量（mB），与 {@link #getInputs()} 一一对应。 */
    public List<Integer> getAmounts() {
        return amounts;
    }

    public FluidStackTemplate getOutputTemplate() {
        return output;
    }

    /** 合金产物（尚未附带 {@link AlloyMixture} 组件）。 */
    public FluidStack getOutput() {
        return output.create();
    }

    public String getCustomName() {
        return customName;
    }

    public int getColor() {
        return color;
    }

    /** 执行本配方所需的燃料温度（摄氏度）。 */
    public int getTemperature() {
        return temperature;
    }

    /**
     * 根据实际抽出的输入流体构建合金成分表。
     *
     * <p>
     * 成分占比按**实际抽出的 mB 量**计算，而不是配方里写的配比数字——
     * 只有用量才是守恒量。若输入本身已带合金成分（例如用合金锭回炉），
     * 则按用量把它的成分表整体缩放后合并，这样多级合金的成分能正确传递。
     */
    public FluidStack assembleMixture(List<FluidStack> drainedInputs) {
        AlloyMixture mixture = new AlloyMixture();
        for (FluidStack input : drainedInputs) {
            if (input.isEmpty()) {
                continue;
            }
            float weight = input.getAmount();
            AlloyMixture inputMixture = AlloyData.getMixture(input);
            if (inputMixture != null && !inputMixture.getComponents().isEmpty()) {
                // 输入自带成分表：其内部占比已归一，直接按用量加权合并
                mixture.merge(inputMixture, weight);
            } else {
                Identifier fluidId = BuiltInRegistries.FLUID.getKey(input.getFluid());
                mixture.addComponent(new AlloyComponent(mapFluidToMaterial(fluidId), weight));
            }
        }
        mixture.setCustomName(customName);
        mixture.setColor(color);
        mixture.normalize();

        FluidStack result = output.create();
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

    /**
     * 合金配方没有物品输入（流体才是输入），无法放进配方书网格。
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
