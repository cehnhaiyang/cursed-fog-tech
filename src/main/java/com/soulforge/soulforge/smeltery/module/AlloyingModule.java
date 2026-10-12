package com.soulforge.soulforge.smeltery.module;

import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import com.soulforge.soulforge.recipe.alloying.AlloyingRecipe;
import com.soulforge.soulforge.smeltery.tank.SmelteryTank;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 合金模块：在炉内多流体罐上匹配并执行合金/聚变配方。
 *
 * <p>匹配规则对齐 TiC3 的 {@code AlloyRecipe}：每个输入组分必须各占用一种**不同**的
 * 罐内流体（同一种流体不能同时顶两个组分），且温度达标、产物装得下。
 * 执行时先算出每种流体要抽多少，确认空间足够后再一并抽出并灌入产物——
 * 中途失败会留下"输入没了产物也没出"的坏状态。
 *
 * <p>配方检索用 {@code byType} 而非 {@code getRecipesFor}：后者的语义是
 * "按输入匹配"，而 {@link AlloyingRecipe#matches} 恒为 false（合金的输入是流体
 * 不是物品），用它筛会把所有合金配方全部滤掉。
 *
 * <p>"能不能做"与"做"共用同一个 {@link #plan} 计算出的抽液方案。分成两处各算一次
 * 会让两个判断漂移：某条配方在检查时成立、执行时却不成立，于是抽走输入却没产出。
 *
 * <p>配方表按类型整体缓存。它不随罐内流体变化——流体变了只会让某条配方成立与否改变，
 * 那是匹配阶段的事，与配方表本身无关。因此缓存不会过期，只有数据包重载才需要刷新，
 * 而"一条都没匹配上就丢弃缓存"恰好覆盖了这种情况。
 */
public class AlloyingModule {
    private final SmelteryTank tank;
    private final boolean fusion;

    /** 该类型的全部配方。与罐内流体无关，因此不随流体变化作废。 */
    @Nullable
    private List<AlloyingRecipe> cachedRecipes;

    /** 一次可执行的合金：要抽走的每种流体，以及要灌入的产物。 */
    private record Plan(List<FluidStack> drains, FluidStack mixture) {
    }

    public AlloyingModule(SmelteryTank tank, boolean fusion) {
        this.tank = tank;
        this.fusion = fusion;
    }

    /** 是否有配方当前可执行。控制器用它决定要不要为"只差合金"的情况去找燃料。 */
    public boolean canAlloy(Level level, int temperature) {
        return findPlan(level, temperature) != null;
    }

    /**
     * 尝试执行一次合金。
     *
     * @param level       服务端世界
     * @param temperature 当前燃料温度
     * @return 是否真的产出了合金
     */
    public boolean doAlloy(Level level, int temperature) {
        Plan plan = findPlan(level, temperature);
        if (plan == null) {
            return false;
        }
        for (FluidStack toDrain : plan.drains()) {
            tank.drain(toDrain);
        }
        tank.fill(plan.mixture());
        return true;
    }

    /**
     * 找出第一条可执行的配方并算出它的抽液方案。
     *
     * <p>顺序会被打乱：两条互斥的配方（例如"铁+铜"与"铁+锡"）若总按注册顺序取，
     * 排在后面的那条永远轮不到。
     */
    @Nullable
    private Plan findPlan(Level level, int temperature) {
        List<AlloyingRecipe> recipes = getRecipes(level);
        if (recipes.isEmpty() || temperature <= 0 || tank.getRemainingSpace() <= 0) {
            return null;
        }
        List<AlloyingRecipe> shuffled = new ArrayList<>(recipes);
        Collections.shuffle(shuffled);
        for (AlloyingRecipe recipe : shuffled) {
            Plan plan = plan(recipe, temperature);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    /**
     * 尝试为一条配方算出抽液方案。
     *
     * @return 可执行时返回方案；温度不够、组分凑不齐或产物放不下时返回 null
     */
    @Nullable
    private Plan plan(AlloyingRecipe recipe, int temperature) {
        if (recipe.getTemperature() > temperature) {
            return null;
        }
        List<FluidIngredient> inputs = recipe.getInputs();
        List<Integer> amounts = recipe.getAmounts();
        List<FluidStack> fluids = tank.getFluids();
        List<FluidStack> drains = new ArrayList<>(inputs.size());
        boolean[] used = new boolean[fluids.size()];
        int drainTotal = 0;

        for (int i = 0; i < inputs.size(); i++) {
            int amount = i < amounts.size() ? amounts.get(i) : 0;
            if (amount <= 0) {
                return null;
            }
            int match = -1;
            for (int t = 0; t < fluids.size(); t++) {
                // 同一种流体不能同时顶两个组分
                if (used[t] || fluids.get(t).getAmount() < amount) {
                    continue;
                }
                if (inputs.get(i).test(fluids.get(t))) {
                    match = t;
                    used[t] = true;
                    break;
                }
            }
            if (match < 0) {
                return null;
            }
            drains.add(fluids.get(match).copyWithAmount(amount));
            drainTotal += amount;
        }

        FluidStack mixture = recipe.assembleMixture(drains);
        // 产物与输入同种流体时，净增量才是真正的空间需求
        if (mixture.getAmount() - drainTotal > tank.getRemainingSpace()) {
            return null;
        }
        return new Plan(drains, mixture);
    }

    private List<AlloyingRecipe> getRecipes(Level level) {
        // 客户端没有服务端配方表；不要把空结果缓存下来，否则同一次会话里再也读不到配方
        if (!(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        if (cachedRecipes == null) {
            var type = fusion ? SoulForgeRecipeTypes.FUSION.get() : SoulForgeRecipeTypes.ALLOYING.get();
            List<AlloyingRecipe> result = new ArrayList<>();
            for (RecipeHolder<? extends AlloyingRecipe> holder : serverLevel.recipeAccess().recipeMap().byType(type)) {
                result.add(holder.value());
            }
            cachedRecipes = result;
        }
        return cachedRecipes;
    }
}
