package com.soulforge.soulforge.smeltery.module;

import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import com.soulforge.soulforge.recipe.melting.MeltingRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 单个熔炼槽：记录槽内物品的升温进度与配方要求，并把这三点同步给界面。
 *
 * <p>对齐 TiC3 的 {@code MeltingModule}。物品必须先在炉内被加热到配方温度才开始熔化，
 * 进度按燃料速率累加；燃料断了就按更快的速度降温。没有这个过程，炉子就退化成
 * "放进去就凭空出液体"。
 *
 * <p>三条容易做错、但直接决定手感的状态：
 * <ul>
 *   <li>进度按 {@code rate} 累加，而 {@code rate} 来自燃料（岩浆 10、烈焰之血 15），
 *       所以换更好的燃料不只是温度上限更高，熔炼本身也更快。</li>
 *   <li>{@link #NO_SPACE}（-1）表示"已热透但罐装不下"。它与进度 0 完全不同：
 *       0 是没热，-1 是热透了在等空间。混用会导致物品要么被反复重算、要么凭空消失。</li>
 *   <li>降温每 tick 扣 5，远快于升温，否则熄火后进度会冻在原地。</li>
 * </ul>
 *
 * <p>"出料"这一步由构造时传入的 {@code output} 回调独占：它返回 true 表示产物已进罐，
 * 槽随即清空；返回 false 表示罐满，槽被标记为 {@link #NO_SPACE} 并留在炉内。
 * 这个判断只有一处，调用方不可能与槽的状态判断失配。
 *
 * <p>本类同时是 {@link ContainerData}，由菜单注册后自动同步到客户端——
 * 界面画热进度条需要的正是这三项，不必另开同步通道。
 */
public class MeltingSlot implements ContainerData {
    /** 进度达到此值表示"已热透但罐装不下"，物品留在炉内继续等待空间。 */
    public static final int NO_SPACE = -1;

    private static final String TAG_STACK = "stack";
    private static final String TAG_TIME = "time";
    private static final String TAG_REQUIRED_TIME = "required_time";
    private static final String TAG_REQUIRED_TEMP = "required_temp";

    private static final int CURRENT_TIME = 0;
    private static final int REQUIRED_TIME = 1;
    private static final int REQUIRED_TEMP = 2;

    /**
     * 配方时间要乘上这个系数才是真正的进度总量。
     * 与 TiC3 一致：JSON 里的 {@code time} 是"基准速率下的 tick 数"，乘 10 之后再按
     * 燃料速率推进，于是更好的燃料能让同一配方更快完成。
     */
    private static final int TIME_SCALE = 10;

    private final Supplier<Level> level;
    private final Predicate<FluidStack> output;

    private ItemStack stack = ItemStack.EMPTY;
    private int currentTime;
    private int requiredTime;
    private int requiredTemp;

    /** 上次命中的配方，避免每次升温都重扫配方表。 */
    @Nullable
    private MeltingRecipe lastRecipe;

    /**
     * @param level  当前世界。方块实体构造阶段 {@code level} 尚未赋值，因此用 {@link Supplier} 延后取值。
     * @param output 出料回调，返回 true 表示产物已被罐接收
     */
    public MeltingSlot(Supplier<Level> level, Predicate<FluidStack> output) {
        this.level = level;
        this.output = output;
    }

    public ItemStack getStack() {
        return stack;
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int getCurrentTime() {
        return currentTime;
    }

    public int getRequiredTime() {
        return requiredTime;
    }

    public int getRequiredTemp() {
        return requiredTemp;
    }

    /**
     * 换入新物品。物品不同则进度清零，并立即解析配方要求。
     *
     * <p>配方在写入时就解析（而不是等到升温），这样界面能立刻显示"这个物品熔化需要多少度"，
     * 也让 {@link #canHeatItem} 不必每 tick 做一次配方检索。
     *
     * @return 内容是否真的变了
     */
    public boolean setStack(ItemStack newStack) {
        if (ItemStack.matches(stack, newStack)) {
            return false;
        }
        // 换成空槽或换了物品，旧进度一律作废
        if (newStack.isEmpty()) {
            resetRecipe();
        } else if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, newStack)) {
            currentTime = 0;
        }
        this.stack = newStack;

        int time = 0;
        int temp = 0;
        if (!stack.isEmpty()) {
            MeltingRecipe recipe = findRecipe();
            if (recipe != null) {
                time = recipe.getTime() * TIME_SCALE;
                temp = recipe.getTemperature();
            }
        }
        requiredTime = time;
        requiredTemp = temp;
        return true;
    }

    /** 清空槽位与进度。 */
    public void clear() {
        stack = ItemStack.EMPTY;
        resetRecipe();
    }

    private void resetRecipe() {
        currentTime = 0;
        requiredTime = 0;
        requiredTemp = 0;
        lastRecipe = null;
    }

    /**
     * 该槽是否有待熔炼、且当前温度已达标的物品。
     * 已热透等空间的槽也算"能继续处理"，否则罐一满物品就会被判成不可熔炼。
     */
    public boolean canHeatItem(int temperature) {
        if (requiredTime <= 0) {
            return false;
        }
        if (stack.isEmpty()) {
            resetRecipe();
            return false;
        }
        return currentTime != NO_SPACE && temperature >= requiredTemp;
    }

    /** 按燃料速率推进一格；热透后尝试出料。 */
    public void heatItem(int temperature, int rate) {
        if (currentTime != NO_SPACE && !canHeatItem(temperature)) {
            return;
        }
        if (currentTime == NO_SPACE || currentTime >= requiredTime) {
            finish();
            return;
        }
        currentTime += rate;
        if (currentTime >= requiredTime) {
            finish();
        }
    }

    /**
     * 断燃料时降温。
     *
     * <p>已热透等空间的槽不降温：它没有"进度"可退，降温只会让它在罐腾出空间后重新热一遍。
     */
    public void coolItem() {
        if (currentTime == NO_SPACE) {
            return;
        }
        if (currentTime > 0 && requiredTime > 0) {
            currentTime = Math.max(0, currentTime - 5);
        }
    }

    /** 热透后尝试把产物送进罐；装不下就留在槽里等下一次。 */
    private void finish() {
        MeltingRecipe recipe = findRecipe();
        if (recipe == null) {
            clear();
            return;
        }
        FluidStack product = recipe.getOutput();
        if (product.isEmpty() || output.test(product)) {
            clear();
        } else {
            currentTime = NO_SPACE;
        }
    }

    /** 解析配方：先试上次命中的，再查配方表。 */
    @Nullable
    private MeltingRecipe findRecipe() {
        Level world = level.get();
        if (stack.isEmpty() || !(world instanceof ServerLevel serverLevel)) {
            return null;
        }
        MeltingRecipe last = lastRecipe;
        if (last != null && last.getInput().test(stack)) {
            return last;
        }
        SingleRecipeInput input = new SingleRecipeInput(stack);
        Optional<RecipeHolder<MeltingRecipe>> found = serverLevel.recipeAccess().recipeMap()
                .getRecipesFor(SoulForgeRecipeTypes.MELTING.get(), input, world)
                .findFirst();
        if (found.isPresent()) {
            lastRecipe = found.get().value();
            return lastRecipe;
        }
        return null;
    }

    /* 菜单同步：进度 / 所需时间 / 所需温度 */

    @Override
    public int getCount() {
        return 3;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case CURRENT_TIME -> currentTime;
            case REQUIRED_TIME -> requiredTime;
            case REQUIRED_TEMP -> requiredTemp;
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        switch (index) {
            case CURRENT_TIME -> currentTime = value;
            case REQUIRED_TIME -> requiredTime = value;
            case REQUIRED_TEMP -> requiredTemp = value;
            default -> {
            }
        }
    }

    /* 存档 */

    public void serialize(ValueOutput output) {
        if (stack.isEmpty()) {
            return;
        }
        output.store(TAG_STACK, ItemStack.CODEC, stack);
        output.putInt(TAG_TIME, currentTime);
        output.putInt(TAG_REQUIRED_TIME, requiredTime);
        output.putInt(TAG_REQUIRED_TEMP, requiredTemp);
    }

    public void deserialize(ValueInput input) {
        resetRecipe();
        input.read(TAG_STACK, ItemStack.CODEC).ifPresent(loaded -> stack = loaded);
        if (!stack.isEmpty()) {
            currentTime = input.getIntOr(TAG_TIME, 0);
            requiredTime = input.getIntOr(TAG_REQUIRED_TIME, 0);
            requiredTemp = input.getIntOr(TAG_REQUIRED_TEMP, 0);
        }
    }
}
