package com.soulforge.soulforge.block.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 铸造流体处理器（参照 TiC3 的 CastingFluidHandler）。
 * 罐容量动态变化：当流体首次灌入时，调用方块的 initNewCasting 查找配方，
 * 如果找到配方则将容量设为配方所需流体量。
 * 只有第一个灌入的流体类型被接受（filter 机制）。
 */
public class CastingFluidHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
    private final AbstractCastingBlockEntity tile;
    private FluidStack fluid = FluidStack.EMPTY;
    private int capacity = 0;
    private Fluid filter = Fluids.EMPTY;

    public CastingFluidHandler(AbstractCastingBlockEntity tile) {
        this.tile = tile;
    }

    public FluidStack getFluid() {
        return fluid;
    }

    public void setFluid(FluidStack fluid) {
        this.fluid = fluid;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean isEmpty() {
        return fluid.isEmpty();
    }

    public int getCapacity() {
        return capacity == 0 ? fluid.getAmount() : capacity;
    }

    public boolean isFluidValid(FluidStack stack) {
        return !stack.isEmpty() && (filter == Fluids.EMPTY || stack.getFluid() == filter);
    }

    /** 重置到初始空状态 */
    public void reset() {
        capacity = 0;
        fluid = FluidStack.EMPTY;
        filter = Fluids.EMPTY;
    }

    /**
     * 尝试灌入流体。
     *
     * @param simulate 为 true 时只计算可灌入量，不改变状态
     * @return 实际（或模拟的）灌入量
     */
    public int fill(FluidStack resource, boolean simulate) {
        if (resource.isEmpty() || !isFluidValid(resource)) {
            return 0;
        }

        // 首次灌入：查找配方并设置容量
        int currentCapacity = this.capacity;
        if (filter == Fluids.EMPTY || this.capacity == 0) {
            Fluid fluidType = resource.getFluid();
            currentCapacity = tile.initNewCasting(resource, !simulate);
            if (currentCapacity <= 0) {
                return 0;
            }
            if (!simulate) {
                this.capacity = currentCapacity;
                this.filter = fluidType;
            }
        }

        // 空罐：直接灌入
        if (fluid.isEmpty()) {
            int amount = Math.min(currentCapacity, resource.getAmount());
            if (!simulate) {
                fluid = resource.copyWithAmount(amount);
                tile.onContentsChanged();
            }
            return amount;
        }

        // 类型不匹配（安全检查）
        if (!FluidStack.isSameFluid(fluid, resource)) {
            return 0;
        }

        // 已满
        int space = currentCapacity - fluid.getAmount();
        if (space <= 0) {
            return 0;
        }

        // 灌入
        int amount = resource.getAmount();
        if (amount < space) {
            if (!simulate) {
                fluid.grow(amount);
                tile.onContentsChanged();
            }
            return amount;
        } else {
            if (!simulate) {
                fluid.setAmount(currentCapacity);
                tile.onContentsChanged();
            }
            return space;
        }
    }

    /**
     * 尝试抽出流体。
     *
     * @param simulate 为 true 时只计算可抽出量，不改变状态
     * @return 实际（或模拟的）抽出的流体
     */
    public FluidStack drain(int maxDrain, boolean simulate) {
        int drained = Math.min(fluid.getAmount(), maxDrain);
        if (drained <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack stack = fluid.copyWithAmount(drained);
        if (!simulate) {
            fluid.shrink(drained);
            if (fluid.isEmpty()) {
                tile.reset();
            } else {
                tile.onContentsChanged();
            }
        }
        return stack;
    }

    /* ResourceHandler 实现：供 NeoForge 流体能力与桶交互使用。 */

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return index == 0 ? FluidResource.of(fluid) : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return index == 0 ? fluid.getAmount() : 0;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return getCapacity();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return index == 0 && (resource.isEmpty() || isFluidValid(resource.toStack(1)));
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (index != 0) {
            return 0;
        }
        int inserted = fill(resource.toStack(amount), true);
        if (inserted > 0) {
            updateSnapshots(transaction);
            fill(resource.toStack(inserted), false);
        }
        return inserted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (index != 0 || !resource.matches(fluid)) {
            return 0;
        }
        int extracted = Math.min(amount, fluid.getAmount());
        if (extracted > 0) {
            updateSnapshots(transaction);
            drain(extracted, false);
        }
        return extracted;
    }

    @Override
    protected FluidStack createSnapshot() {
        return fluid.copy();
    }

    @Override
    protected void revertToSnapshot(FluidStack snapshot) {
        this.fluid = snapshot;
    }

    @Override
    protected void onRootCommit(FluidStack originalState) {
        tile.setChanged();
    }

    /* NBT */

    private static final String TAG_FLUID = "fluid";
    private static final String TAG_FILTER = "filter";
    private static final String TAG_CAPACITY = "capacity";

    public void readFromNBT(ValueInput input) {
        capacity = input.getIntOr(TAG_CAPACITY, 0);
        input.read(TAG_FLUID, FluidStack.OPTIONAL_CODEC).ifPresent(stack -> fluid = stack);
        input.getString(TAG_FILTER).ifPresent(name -> {
            Fluid fluidType = BuiltInRegistries.FLUID.getValue(Identifier.parse(name));
            if (fluidType != null && fluidType != Fluids.EMPTY) {
                filter = fluidType;
            }
        });
    }

    public void writeToNBT(ValueOutput output) {
        output.putInt(TAG_CAPACITY, capacity);
        if (!fluid.isEmpty()) {
            output.store(TAG_FLUID, FluidStack.OPTIONAL_CODEC, fluid);
        }
        if (filter != Fluids.EMPTY) {
            output.putString(TAG_FILTER, BuiltInRegistries.FLUID.getKey(filter).toString());
        }
    }
}
