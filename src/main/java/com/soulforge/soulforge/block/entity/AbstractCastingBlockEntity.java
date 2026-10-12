package com.soulforge.soulforge.block.entity;

import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import com.soulforge.soulforge.recipe.casting.CastingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 铸造方块实体基类（参照 TiC3 的 CastingBlockEntity）。
 * 使用 WorldlyContainer 提供 INPUT(0)/OUTPUT(1) 双槽位。
 * 右键交互：先尝试流体传输，再尝试物品交换。
 * 流体灌入时自动查找铸造配方，罐容量动态设为配方所需量。
 */
public abstract class AbstractCastingBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    private static final int SLOTS = 2;

    private static final String TAG_TANK = "tank";
    private static final String TAG_TIMER = "timer";
    private static final String TAG_COOLING_TIME = "cooling_time";
    private static final String TAG_INPUT = "input";
    private static final String TAG_OUTPUT = "output";

    // 铸造流体处理器（动态容量）
    private final CastingFluidHandler tank;
    // 当前铸造配方
    @Nullable
    private CastingRecipe currentRecipe;
    // 冷却进度
    private int timer = 0;
    // 冷却所需时间（-1 表示未开始）
    private int coolingTime = -1;
    // 是否为铸造盆
    private final boolean isBasin;
    // 物品槽
    private final ItemStack[] items = new ItemStack[SLOTS];

    protected AbstractCastingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean isBasin) {
        super(type, pos, state);
        this.isBasin = isBasin;
        this.tank = new CastingFluidHandler(this);
        items[INPUT] = ItemStack.EMPTY;
        items[OUTPUT] = ItemStack.EMPTY;
    }

    public CastingFluidHandler getTank() {
        return tank;
    }

    public ItemStack getInputStack() {
        return items[INPUT];
    }

    public ItemStack getOutputStack() {
        return items[OUTPUT];
    }

    public int getTimer() {
        return timer;
    }

    public int getCoolingTime() {
        return coolingTime;
    }

    public boolean isBasin() {
        return isBasin;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractCastingBlockEntity entity) {
        if (level.isClientSide()) {
            return;
        }
        entity.serverTick(level);
    }

    private void serverTick(Level level) {
        // 没有配方时不处理
        if (currentRecipe == null) {
            return;
        }
        // 罐已满时开始冷却
        if (coolingTime >= 0) {
            timer++;
            if (timer >= coolingTime) {
                // 冷却完成，产出
                FluidStack currentFluid = tank.getFluid();
                // 重新验证配方
                if (!currentRecipe.getFluid().getFluid().equals(currentFluid.getFluid())
                        || currentRecipe.getFluid().getAmount() > currentFluid.getAmount()) {
                    currentRecipe = findCastingRecipe(level);
                    if (currentRecipe == null) {
                        timer = 0;
                        return;
                    }
                }
                // 产出物品
                ItemStack output = currentRecipe.getOutput().copy();
                boolean consumed = true; // 简化：铸模总是被消耗
                if (!items[OUTPUT].isEmpty()) {
                    // 输出槽有东西，放到输入槽
                    items[INPUT] = output;
                } else {
                    if (consumed) {
                        items[INPUT] = ItemStack.EMPTY;
                    }
                    items[OUTPUT] = output;
                }
                // 消耗流体
                tank.drain(currentRecipe.getFluid().getAmount(), false);
                level.playSound(null, getBlockPos(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 4f);
                reset();
                setChanged();
            }
        }
    }

    /**
     * 右键交互逻辑（参照 TiC3 的 interact 方法）。
     * 1. 先尝试流体传输（桶装/桶倒）
     * 2. 如果没有流体交互且罐为空，进行物品交换
     */
    public void interact(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide() || (coolingTime >= 0 && timer > 0)) {
            return;
        }

        // 先尝试流体交互
        boolean fluidInteracted = FluidUtil.interactWithFluidHandler(player, hand, level, worldPosition, null, null);
        if (fluidInteracted || !tank.isEmpty()) {
            return;
        }

        // 流体交互失败且罐为空：进行物品交换
        ItemStack held = player.getItemInHand(hand);
        ItemStack input = items[INPUT];
        ItemStack output = items[OUTPUT];

        if (input.isEmpty() && output.isEmpty()) {
            // 两个槽都空：放入手中物品到输入槽
            if (!held.isEmpty()) {
                ItemStack stack = held.split(1);
                player.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);
                items[INPUT] = stack;
                setChanged();
            }
        } else {
            // 有物品：取出（优先输出槽）
            int slot = output.isEmpty() ? INPUT : OUTPUT;
            ItemStack stack = items[slot];
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            items[slot] = ItemStack.EMPTY;
            player.setItemInHand(hand, held);
            level.playSound(null, getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, 1.0f);
            setChanged();
        }
    }

    /**
     * 流体灌入时调用，查找匹配的铸造配方。
     * @return 配方所需流体量（用于设置罐容量），0 表示无匹配
     */
    public int initNewCasting(FluidStack fluid, boolean execute) {
        if (this.currentRecipe != null) {
            return 0;
        }
        if (level == null) {
            return 0;
        }

        boolean hasInput = !items[INPUT].isEmpty();
        boolean hasOutput = !items[OUTPUT].isEmpty();

        // 输出槽有物品时不接受新铸造
        if (hasInput && hasOutput) {
            return 0;
        }

        // 查找匹配配方
        CastingRecipe recipe = findCastingRecipe(level, fluid, hasInput ? items[INPUT] : ItemStack.EMPTY);
        if (recipe != null) {
            if (execute) {
                this.currentRecipe = recipe;
            }
            return recipe.getFluid().getAmount();
        }
        return 0;
    }

    /**
     * 查找匹配当前流体和输入物品的铸造配方。
     */
    @Nullable
    private CastingRecipe findCastingRecipe(Level level, FluidStack fluid, ItemStack cast) {
        List<RecipeHolder<CastingRecipe>> recipes = level.getRecipeManager()
                .getRecipesFor(SoulForgeRecipeTypes.CASTING.get(), new net.minecraft.world.item.crafting.SingleRecipeInput(cast), level);
        for (RecipeHolder<CastingRecipe> holder : recipes) {
            CastingRecipe recipe = holder.value();
            // 类型必须匹配（台面 vs 盆）
            if (recipe.isBasin() != isBasin) {
                continue;
            }
            // 流体必须匹配
            if (!recipe.getFluid().getFluid().equals(fluid.getFluid())) {
                continue;
            }
            // 铸模必须匹配（无铸模配方或铸模测试通过）
            if (recipe.getCast().isEmpty() || recipe.getCast().test(cast)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private CastingRecipe findCastingRecipe(Level level) {
        return findCastingRecipe(level, tank.getFluid(), items[INPUT]);
    }

    /** 罐内容变化时调用，启动冷却计时 */
    public void onContentsChanged() {
        FluidStack fluidStack = tank.getFluid();
        if (!fluidStack.isEmpty() && fluidStack.getAmount() >= tank.getCapacity() && currentRecipe != null) {
            coolingTime = Math.max(0, currentRecipe.getCoolingTime());
        } else {
            coolingTime = -1;
        }
        setChanged();
    }

    /** 重置铸造状态到初始空状态 */
    public void reset() {
        timer = 0;
        currentRecipe = null;
        coolingTime = -1;
        tank.reset();
    }

    /* WorldlyContainer 实现 */

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{INPUT, OUTPUT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        return tank.isEmpty() && index == INPUT && items[OUTPUT].isEmpty();
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return tank.isEmpty() && index == OUTPUT;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items[INPUT].isEmpty() && items[OUTPUT].isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOTS ? items[slot] : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot >= 0 && slot < SLOTS && !items[slot].isEmpty()) {
            return items[slot].split(amount);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot >= 0 && slot < SLOTS) {
            ItemStack stack = items[slot];
            items[slot] = ItemStack.EMPTY;
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < SLOTS) {
            items[slot] = stack;
            setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) {
            return false;
        }
        return level.getBlockEntity(worldPosition) == this;
    }

    @Override
    public void clearContent() {
        items[INPUT] = ItemStack.EMPTY;
        items[OUTPUT] = ItemStack.EMPTY;
    }

    /* NBT */

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.readFromNBT(input.lookup(), input.read("tank", CompoundTag.CODEC).orElseGet(CompoundTag::new));
        timer = input.getIntOr(TAG_TIMER, 0);
        coolingTime = input.getIntOr(TAG_COOLING_TIME, -1);
        input.read(TAG_INPUT, ItemStack.CODEC).ifPresent(stack -> items[INPUT] = stack);
        input.read(TAG_OUTPUT, ItemStack.CODEC).ifPresent(stack -> items[OUTPUT] = stack);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_TANK, CompoundTag.CODEC, tank.writeToNBT(output.lookup(), new CompoundTag()));
        output.putInt(TAG_TIMER, timer);
        output.putInt(TAG_COOLING_TIME, coolingTime);
        if (!items[INPUT].isEmpty()) {
            output.store(TAG_INPUT, ItemStack.CODEC, items[INPUT]);
        }
        if (!items[OUTPUT].isEmpty()) {
            output.store(TAG_OUTPUT, ItemStack.CODEC, items[OUTPUT]);
        }
    }
}
