package com.soulforge.soulforge.block.entity;

import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import com.soulforge.soulforge.recipe.casting.CastingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
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
 *
 * <p>流程：先由玩家把铸模（台）或什么都不放（盆）准备好，再灌入流体；
 * 流体一旦达到配方所需量就开始冷却计时，计时结束产出物品并抽走流体。
 *
 * <p>铸模**不消耗**——它留在台上重复使用。这是铸造台与"一次性模具"的分野，
 * 也是玩家能用同一个锭模浇出无数锭的前提。
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

    private final CastingFluidHandler tank;
    @Nullable
    private CastingRecipe currentRecipe;
    private int timer;
    /** 冷却所需时间；-1 表示尚未开始冷却。 */
    private int coolingTime = -1;
    private final boolean isBasin;
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

    /** 冷却进度 0..1，供渲染与界面使用。 */
    public float getCoolingProgress() {
        if (coolingTime <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, (float) timer / coolingTime);
    }

    public boolean isBasin() {
        return isBasin;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractCastingBlockEntity entity) {
        if (!level.isClientSide()) {
            entity.serverTick(level);
        }
    }

    private void serverTick(Level level) {
        if (currentRecipe == null || coolingTime < 0) {
            return;
        }
        timer++;
        if (timer < coolingTime) {
            return;
        }

        // 冷却期间玩家可能把流体抽走，产出前必须重新确认配方仍然成立
        FluidStack currentFluid = tank.getFluid();
        if (!FluidStack.isSameFluid(currentFluid, currentRecipe.getFluidTemplate())
                || currentRecipe.getFluidAmount() > currentFluid.getAmount()) {
            currentRecipe = findCastingRecipe(level);
            if (currentRecipe == null) {
                timer = 0;
                coolingTime = -1;
                return;
            }
        }

        // 输出槽被占时不能产出，否则会覆盖玩家已放好的物品
        if (!items[OUTPUT].isEmpty()) {
            timer = Math.max(0, coolingTime - 1);
            return;
        }

        items[OUTPUT] = currentRecipe.getOutput();
        // 只抽走配方所需的流体，铸模与剩余流体都留在原处
        tank.drain(currentRecipe.getFluidAmount(), false);
        level.playSound(null, getBlockPos(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 4f);
        timer = 0;
        coolingTime = -1;
        currentRecipe = null;
        setChanged();
    }

    /**
     * 右键交互：先尝试流体传输（桶装/桶倒），没有流体交互且罐为空时再交换物品。
     */
    public void interact(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide() || (coolingTime >= 0 && timer > 0)) {
            return;
        }

        boolean fluidInteracted = FluidUtil.interactWithFluidHandler(player, hand, level, worldPosition, null, null);
        if (fluidInteracted || !tank.isEmpty()) {
            return;
        }

        ItemStack held = player.getItemInHand(hand);
        ItemStack input = items[INPUT];
        ItemStack output = items[OUTPUT];

        if (input.isEmpty() && output.isEmpty()) {
            if (!held.isEmpty()) {
                ItemStack stack = held.split(1);
                player.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);
                items[INPUT] = stack;
                setChanged();
            }
            return;
        }

        // 先取产物；产物空了再取模具
        int slot = output.isEmpty() ? INPUT : OUTPUT;
        ItemStack stack = items[slot];
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        items[slot] = ItemStack.EMPTY;
        level.playSound(null, getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, 1.0f);
        setChanged();
    }

    /**
     * 流体灌入时查找匹配的铸造配方。
     *
     * @return 配方所需流体量（用于设定罐容量），0 表示无匹配
     */
    public int initNewCasting(FluidStack fluid, boolean execute) {
        if (this.currentRecipe != null || level == null) {
            return 0;
        }
        // 输出槽被占时不再接受新的浇筑，否则产物无处安放
        if (!items[OUTPUT].isEmpty()) {
            return 0;
        }

        CastingRecipe recipe = findCastingRecipe(level, fluid, items[INPUT]);
        if (recipe != null) {
            if (execute) {
                this.currentRecipe = recipe;
            }
            return recipe.getFluidAmount();
        }
        return 0;
    }

    @Nullable
    private CastingRecipe findCastingRecipe(Level level, FluidStack fluid, ItemStack cast) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        List<RecipeHolder<CastingRecipe>> recipes = serverLevel.recipeAccess().recipeMap()
                .getRecipesFor(SoulForgeRecipeTypes.CASTING.get(), new SingleRecipeInput(cast), level)
                .toList();
        for (RecipeHolder<CastingRecipe> holder : recipes) {
            CastingRecipe recipe = holder.value();
            if (recipe.isBasin() != isBasin) {
                continue;
            }
            if (!FluidStack.isSameFluid(fluid, recipe.getFluidTemplate())) {
                continue;
            }
            if (Ingredient.testOptionalIngredient(recipe.getCast(), cast)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private CastingRecipe findCastingRecipe(Level level) {
        return findCastingRecipe(level, tank.getFluid(), items[INPUT]);
    }

    /** 罐内容变化时调用，罐满则启动冷却计时。 */
    public void onContentsChanged() {
        FluidStack fluidStack = tank.getFluid();
        if (!fluidStack.isEmpty() && fluidStack.getAmount() >= tank.getCapacity() && currentRecipe != null) {
            coolingTime = Math.max(0, currentRecipe.getCoolingTime());
        } else {
            coolingTime = -1;
        }
        setChanged();
    }

    /** 重置铸造状态到初始空状态。 */
    public void reset() {
        timer = 0;
        currentRecipe = null;
        coolingTime = -1;
        tank.reset();
    }

    /* 客户端同步：渲染液位与冷却进度都需要这些数据 */

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("fluid_amount", tank.getFluid().getAmount());
        tag.putInt("fluid_capacity", tank.getCapacity());
        if (!tank.getFluid().isEmpty()) {
            tag.putString("fluid_id", net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .getKey(tank.getFluid().getFluid()).toString());
        }
        tag.putInt("timer", timer);
        tag.putInt("cooling_time", coolingTime);
        tag.putBoolean("basin", isBasin);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
        return level != null && level.getBlockEntity(worldPosition) == this;
    }

    @Override
    public void clearContent() {
        items[INPUT] = ItemStack.EMPTY;
        items[OUTPUT] = ItemStack.EMPTY;
    }

    /* 存档 */

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.readFromNBT(input.childOrEmpty(TAG_TANK));
        timer = input.getIntOr(TAG_TIMER, 0);
        coolingTime = input.getIntOr(TAG_COOLING_TIME, -1);
        input.read(TAG_INPUT, ItemStack.CODEC).ifPresent(stack -> items[INPUT] = stack);
        input.read(TAG_OUTPUT, ItemStack.CODEC).ifPresent(stack -> items[OUTPUT] = stack);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.writeToNBT(output.child(TAG_TANK));
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
