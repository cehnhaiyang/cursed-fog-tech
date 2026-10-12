package com.soulforge.soulforge.block.entity;

import com.soulforge.soulforge.SoulForgeConfig;
import com.soulforge.soulforge.block.SmelteryControllerBlock;
import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import com.soulforge.soulforge.recipe.melting.MeltingRecipe;
import com.soulforge.soulforge.smeltery.SmelteryTier;
import com.soulforge.soulforge.smeltery.fuel.FuelTemperatureRegistry;
import com.soulforge.soulforge.smeltery.multiblock.MultiblockStructure;
import com.soulforge.soulforge.smeltery.multiblock.SmelteryMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 冶炼炉控制器方块实体。
 * 管理多方块结构验证、燃料温度安全、物品熔炼逻辑，并暴露燃料输入和熔融流体输出的流体处理器。
 */
public class SmelteryControllerBlockEntity extends BlockEntity implements MenuProvider {
    private static final Component DEFAULT_NAME = Component.translatable("container.soulforge.smeltery");
    private static final int FUEL_TANK_CAPACITY = 4000;
    private static final int BASE_MELT_TANK_CAPACITY = 4000;
    private static final String TAG_FUEL = "fuel_tank";
    private static final String TAG_MELT = "melt_tank";
    private static final String TAG_ITEMS = "items";
    private static final String TAG_PROGRESS = "melt_progress";
    private static final int FUEL_PER_TICK = 1; // 每 tick 消耗 1mB 燃料

    private final SmelteryTier tier;
    private final SmelteryMultiblock multiblock;
    // 燃料罐：存放烈焰血等燃料液体
    private final FluidStacksResourceHandler fuelTank = new FluidStacksResourceHandler(1, FUEL_TANK_CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || FuelTemperatureRegistry.isFuel(resource.getFluid());
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    // 熔融罐：存放熔炼产物液体（容量随多方块结构动态调整）
    private FluidStacksResourceHandler meltTank = newMeltTank(BASE_MELT_TANK_CAPACITY);
    // 物品输入槽：放入待熔炼物品
    private final ItemStacksResourceHandler itemInput = new ItemStacksResourceHandler(4) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    @Nullable
    private MultiblockStructure structure;
    private boolean firstTick = true;
    private boolean structureUpdateQueued = false;
    private boolean fuelTooHot = false;
    private int tick = 0;
    // 当前熔炼进度（0~maxProgress）
    private int meltProgress = 0;
    // 当前熔炼所需总时间
    private int meltTimeRequired = 0;

    // 同步给客户端的容器数据：[熔炼进度, 熔炼总时间, 燃料量, 熔融量]
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> meltProgress;
                case 1 -> meltTimeRequired;
                case 2 -> fuelTank.getAmountAsInt(0);
                case 3 -> meltTank.getAmountAsInt(0);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> meltProgress = value;
                case 1 -> meltTimeRequired = value;
                default -> { /* 燃料和熔融量由方块实体 NBT 同步，不在此设置 */ }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public SmelteryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SoulForgeBlockEntityTypes.SMELTERY_CONTROLLER.get(), pos, state);
        this.tier = state.getBlock() instanceof SmelteryControllerBlock controller
                ? controller.getTier()
                : SmelteryTier.SMELTERY;
        this.multiblock = new SmelteryMultiblock(tier);
    }

    public SmelteryTier getTier() {
        return tier;
    }

    @Nullable
    public MultiblockStructure getStructure() {
        return structure;
    }

    public FluidStacksResourceHandler getFuelTank() {
        return fuelTank;
    }

    public FluidStacksResourceHandler getMeltTank() {
        return meltTank;
    }

    public ItemStacksResourceHandler getItemInput() {
        return itemInput;
    }

    /** 当前燃料量（mB） */
    public int getFuelAmount() {
        return fuelTank.getAmountAsInt(0);
    }

    /** 当前熔融量（mB） */
    public int getMeltAmount() {
        return meltTank.getAmountAsInt(0);
    }

    public FluidStack getFuelFluid() {
        return fuelTank.getResource(0).toStack(fuelTank.getAmountAsInt(0));
    }

    public FluidStack getMeltFluid() {
        return meltTank.getResource(0).toStack(meltTank.getAmountAsInt(0));
    }

    /** 熔融罐容量（mB），随多方块结构体积变化。 */
    public int getMeltCapacity() {
        return meltTank.getCapacityAsInt(0, FluidResource.EMPTY);
    }

    private FluidStacksResourceHandler newMeltTank(int capacity) {
        return new FluidStacksResourceHandler(1, capacity) {
            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
            }
        };
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    public boolean isFuelTooHot() {
        return fuelTooHot;
    }

    /** 结构有效且燃料温度安全时返回 true。 */
    public boolean canOperate() {
        return structure != null && !fuelTooHot;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmelteryControllerBlockEntity entity) {
        entity.tick(level, pos, state);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            return;
        }

        if (firstTick) {
            firstTick = false;
            checkStructure(level, pos, state);
        }

        if (structureUpdateQueued) {
            structureUpdateQueued = false;
            checkStructure(level, pos, state);
        }

        tick = (tick + 1) % 20;

        // 每秒检查一次燃料温度安全
        if (tick == 0) {
            updateFuelTemperatureState(level, pos, state);
        }

        // 结构有效且燃料安全时执行熔炼逻辑
        if (canOperate()) {
            processMelting(level);
        }
    }

    /**
     * 熔炼处理核心逻辑：
     * 1. 查找输入槽中第一个有匹配熔炼配方的物品
     * 2. 检查燃料温度是否足够、熔融罐是否能容纳产物
     * 3. 消耗燃料并累积进度，完成后消耗物品并产出流体
     */
    private void processMelting(Level level) {
        // 查找可熔炼的物品
        MeltingRecipe activeRecipe = findMeltingRecipe(level);
        if (activeRecipe == null) {
            // 没有可熔炼物品，重置进度
            if (meltProgress > 0) {
                meltProgress = 0;
                meltTimeRequired = 0;
                setChanged();
            }
            return;
        }

        // 检查燃料温度是否足够
        int fuelTemp = FuelTemperatureRegistry.getTemperature(getFuelFluid());
        if (fuelTemp < activeRecipe.getTemperature()) {
            // 燃料温度不够，重置进度
            meltProgress = 0;
            return;
        }

        // 检查熔融罐是否能容纳产物
        FluidResource outputResource = FluidResource.of(activeRecipe.getOutput());
        try (Transaction tx = Transaction.openRoot()) {
            int canFill = meltTank.insert(0, outputResource, activeRecipe.getOutput().getAmount(), tx);
            if (canFill < activeRecipe.getOutput().getAmount()) {
                // 罐已满或流体类型不匹配
                return;
            }
        }

        // 检查燃料是否还有余量
        if (fuelTank.getAmountAsInt(0) < FUEL_PER_TICK) {
            return;
        }

        // 设置所需时间
        if (meltTimeRequired == 0) {
            meltTimeRequired = activeRecipe.getTime();
        }

        // 消耗燃料
        try (Transaction tx = Transaction.openRoot()) {
            fuelTank.extract(0, fuelTank.getResource(0), FUEL_PER_TICK, tx);
            tx.commit();
        }

        // 累积进度
        meltProgress++;

        // 熔炼完成
        if (meltProgress >= meltTimeRequired) {
            // 消耗一个输入物品
            for (int i = 0; i < itemInput.size(); i++) {
                ItemStack stack = itemInput.getResource(i).toStack(itemInput.getAmountAsInt(i));
                if (!stack.isEmpty() && activeRecipe.getInput().test(stack)) {
                    try (Transaction tx = Transaction.openRoot()) {
                        itemInput.extract(i, ItemResource.of(stack), 1, tx);
                        tx.commit();
                    }
                    break;
                }
            }
            // 产出流体
            try (Transaction tx = Transaction.openRoot()) {
                meltTank.insert(0, outputResource, activeRecipe.getOutput().getAmount(), tx);
                tx.commit();
            }
            meltProgress = 0;
            meltTimeRequired = 0;
        }
        setChanged();
    }

    /**
     * 在输入槽中查找第一个匹配熔炼配方的物品，返回对应配方。
     */
    @Nullable
    private MeltingRecipe findMeltingRecipe(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (int i = 0; i < itemInput.size(); i++) {
            ItemStack stack = itemInput.getResource(i).toStack(itemInput.getAmountAsInt(i));
            if (stack.isEmpty()) {
                continue;
            }
            SingleRecipeInput input = new SingleRecipeInput(stack);
            List<RecipeHolder<MeltingRecipe>> recipes = serverLevel.recipeAccess().recipeMap()
                    .getRecipesFor(SoulForgeRecipeTypes.MELTING.get(), input, level)
                    .toList();
            if (!recipes.isEmpty()) {
                return recipes.get(0).value();
            }
        }
        return null;
    }

    private void checkStructure(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(SmelteryControllerBlock.FACING);
        MultiblockStructure newStructure = multiblock.detectMultiblock(level, pos, facing);
        boolean wasFormed = state.getValue(SmelteryControllerBlock.IN_STRUCTURE);
        boolean formed = newStructure != null;

        if (formed != wasFormed) {
            level.setBlockAndUpdate(pos, state
                    .setValue(SmelteryControllerBlock.IN_STRUCTURE, formed)
                    .setValue(SmelteryControllerBlock.ACTIVE, formed));
        }

        // 结构形成时根据内部体积调整熔融罐容量
        if (formed && newStructure != null) {
            int newCapacity = Math.max(BASE_MELT_TANK_CAPACITY, newStructure.getInteriorVolume() * 1000);
            if (getMeltCapacity() != newCapacity) {
                FluidStack current = getMeltFluid();
                meltTank = newMeltTank(newCapacity);
                if (!current.isEmpty()) {
                    try (Transaction tx = Transaction.openRoot()) {
                        meltTank.insert(0, FluidResource.of(current), current.getAmount(), tx);
                        tx.commit();
                    }
                }
            }
        }

        this.structure = newStructure;
        setChanged();
    }

    private void updateFuelTemperatureState(Level level, BlockPos pos, BlockState state) {
        FluidStack fuel = getFuelFluid();
        int temp = FuelTemperatureRegistry.getTemperature(fuel);
        boolean tooHot = temp > 0 && !tier.canHandleTemperature(temp);

        if (tooHot != fuelTooHot) {
            fuelTooHot = tooHot;
            setChanged();
        }

        if (tooHot && SoulForgeConfig.enableSuperheatedFluidDanger) {
            if (structure != null) {
                damageRandomStructureBlock(level);
            }
            this.structure = null;
            level.setBlockAndUpdate(pos, state
                    .setValue(SmelteryControllerBlock.IN_STRUCTURE, false)
                    .setValue(SmelteryControllerBlock.ACTIVE, false));
        }
    }

    private void damageRandomStructureBlock(Level level) {
        if (structure == null) {
            return;
        }
        BlockPos min = structure.getMin();
        BlockPos max = structure.getMax();
        int x = min.getX() + level.random.nextInt(max.getX() - min.getX() + 1);
        int y = min.getY() + level.random.nextInt(max.getY() - min.getY() + 1);
        int z = min.getZ() + level.random.nextInt(max.getZ() - min.getZ() + 1);
        BlockPos target = new BlockPos(x, y, z);
        if (!level.isEmptyBlock(target)) {
            level.destroyBlock(target, true);
        }
    }

    public void updateStructure() {
        this.structureUpdateQueued = true;
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child(TAG_FUEL).ifPresent(fuelTank::deserialize);
        input.child(TAG_MELT).ifPresent(meltTank::deserialize);
        input.child(TAG_ITEMS).ifPresent(itemInput::deserialize);
        meltProgress = input.getIntOr(TAG_PROGRESS, 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fuelTank.serialize(output.child(TAG_FUEL));
        meltTank.serialize(output.child(TAG_MELT));
        itemInput.serialize(output.child(TAG_ITEMS));
        output.putInt(TAG_PROGRESS, meltProgress);
    }

    @Override
    public Component getDisplayName() {
        return DEFAULT_NAME;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.soulforge.soulforge.menu.ControllerMenu(containerId, inventory, this);
    }
}
