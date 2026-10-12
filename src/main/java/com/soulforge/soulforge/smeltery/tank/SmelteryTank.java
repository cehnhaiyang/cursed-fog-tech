package com.soulforge.soulforge.smeltery.tank;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 冶炼炉的多流体罐：所有流体共享一个总容量，每种流体占一个"槽位"。
 *
 * <p>这是合金能够成立的前提——单槽罐只能装一种流体，而合金需要同时存在
 * 熔融下界合金、熔融钻石、熔融石英三种输入。
 *
 * <p>槽位数量动态等于"当前流体种类数 + 1"（那 +1 是留给新流体的空位），
 * 这样 {@code size()} 不会随容量变化而误导调用方以为有固定槽数。
 * 容量由结构体积决定，见 {@link #setCapacity(int)}。
 */
public class SmelteryTank extends SnapshotJournal<List<FluidStack>> implements ResourceHandler<FluidResource> {
    /** 每种流体一个条目，顺序即倒入顺序，索引 0 是最先倒入的（也是排液口优先抽出的）。 */
    private final List<FluidStack> fluids = new ArrayList<>();
    private int capacity;
    private int contained;
    private final Runnable onChanged;

    public SmelteryTank(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getContained() {
        return contained;
    }

    public int getRemainingSpace() {
        return Math.max(0, capacity - contained);
    }

    /** 由控制器在结构尺寸变化时调用。缩小容量不会丢弃已有流体，只是不再接受新流体。 */
    public void setCapacity(int capacity) {
        this.capacity = Math.max(0, capacity);
    }

    /** 罐内全部流体，按倒入顺序。用于界面显示与存档。 */
    public List<FluidStack> getFluids() {
        return fluids;
    }

    /** 索引 0 的流体，即排液口优先抽出的那种。 */
    public FluidStack getBottomFluid() {
        return fluids.isEmpty() ? FluidStack.EMPTY : fluids.get(0);
    }

    /** 把指定流体移到最前面，让排液口能改抽另一种。 */
    public void moveFluidToBottom(int index) {
        if (index > 0 && index < fluids.size()) {
            fluids.add(0, fluids.remove(index));
            onChanged.run();
        }
    }

    /**
     * 判断"插入 fluid 并抽出 removed 之后"能否放得下。
     * 合金配方在执行前需要这个判断，否则会出现抽走了输入却装不下产物的死锁。
     */
    public boolean canFit(FluidStack fluid, int removed) {
        if (fluid.isEmpty()) {
            return true;
        }
        int current = amountOf(fluid);
        int afterDrain = contained - Math.min(removed, contained);
        int delta = fluid.getAmount() - current;
        return delta <= capacity - afterDrain;
    }

    /** 罐内与给定流体同种的量。 */
    public int amountOf(FluidStack fluid) {
        for (FluidStack existing : fluids) {
            if (FluidStack.isSameFluidSameComponents(existing, fluid)) {
                return existing.getAmount();
            }
        }
        return 0;
    }

    /* 非事务的直接操作：供合金模块在已开启的事务内使用。 */

    /** 直接灌入，返回实际灌入量。 */
    public int fill(FluidStack resource) {
        if (resource.isEmpty()) {
            return 0;
        }
        int usable = Math.min(getRemainingSpace(), resource.getAmount());
        if (usable <= 0) {
            return 0;
        }
        for (FluidStack existing : fluids) {
            if (FluidStack.isSameFluidSameComponents(existing, resource)) {
                existing.grow(usable);
                contained += usable;
                onChanged.run();
                return usable;
            }
        }
        fluids.add(resource.copyWithAmount(usable));
        contained += usable;
        onChanged.run();
        return usable;
    }

    /** 直接抽出指定流体，返回实际抽出量。 */
    public FluidStack drain(FluidStack resource) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        for (int i = 0; i < fluids.size(); i++) {
            FluidStack existing = fluids.get(i);
            if (FluidStack.isSameFluidSameComponents(existing, resource)) {
                int drained = Math.min(resource.getAmount(), existing.getAmount());
                FluidStack result = existing.copyWithAmount(drained);
                existing.shrink(drained);
                contained -= drained;
                if (existing.isEmpty()) {
                    fluids.remove(i);
                }
                onChanged.run();
                return result;
            }
        }
        return FluidStack.EMPTY;
    }

    /** 直接抽出索引 0 的流体，返回实际抽出量。 */
    public FluidStack drainFirst(int maxDrain) {
        if (fluids.isEmpty() || maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack existing = fluids.get(0);
        int drained = Math.min(maxDrain, existing.getAmount());
        FluidStack result = existing.copyWithAmount(drained);
        existing.shrink(drained);
        contained -= drained;
        if (existing.isEmpty()) {
            fluids.remove(0);
        }
        onChanged.run();
        return result;
    }

    /* ResourceHandler：供 NeoForge 流体能力使用（桶、管道、排液口）。 */

    @Override
    public int size() {
        // 每种已有流体一个槽；未满时额外给一个新流体的空位
        return getRemainingSpace() > 0 ? fluids.size() + 1 : fluids.size();
    }

    @Override
    public FluidResource getResource(int index) {
        if (index < 0 || index >= fluids.size()) {
            return FluidResource.EMPTY;
        }
        return FluidResource.of(fluids.get(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        if (index < 0 || index >= fluids.size()) {
            return 0;
        }
        return fluids.get(index).getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        // 任意槽位可增长到"自身量 + 剩余空间"，与 TiC3 的语义一致
        if (index < 0 || index > fluids.size()) {
            return 0;
        }
        long own = index < fluids.size() ? fluids.get(index).getAmount() : 0;
        return own + getRemainingSpace();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return index >= 0 && index <= fluids.size();
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        FluidStack stack = resource.toStack(amount);
        int accepted = Math.min(amount, getRemainingSpace());
        if (accepted <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        fill(stack.copyWithAmount(accepted));
        return accepted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        if (index < 0 || index >= fluids.size()) {
            return 0;
        }
        FluidStack existing = fluids.get(index);
        if (!resource.matches(existing)) {
            return 0;
        }
        int drained = Math.min(amount, existing.getAmount());
        if (drained <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        drain(existing.copyWithAmount(drained));
        return drained;
    }

    /* 事务快照 */

    @Override
    protected List<FluidStack> createSnapshot() {
        List<FluidStack> copy = new ArrayList<>(fluids.size());
        for (FluidStack fluid : fluids) {
            copy.add(fluid.copy());
        }
        return copy;
    }

    @Override
    protected void revertToSnapshot(List<FluidStack> snapshot) {
        fluids.clear();
        fluids.addAll(snapshot);
        recount();
    }

    @Override
    protected void onRootCommit(List<FluidStack> originalState) {
        onChanged.run();
    }

    private void recount() {
        contained = 0;
        for (FluidStack fluid : fluids) {
            contained += fluid.getAmount();
        }
    }

    /* 存档 */

    private static final String TAG_FLUIDS = "fluids";
    private static final String TAG_CAPACITY = "capacity";

    public void serialize(ValueOutput output) {
        output.putInt(TAG_CAPACITY, capacity);
        ValueOutput.TypedOutputList<FluidStack> list = output.list(TAG_FLUIDS, FluidStack.OPTIONAL_CODEC);
        for (FluidStack fluid : fluids) {
            if (!fluid.isEmpty()) {
                list.add(fluid);
            }
        }
    }

    public void deserialize(ValueInput input) {
        fluids.clear();
        contained = 0;
        capacity = input.getIntOr(TAG_CAPACITY, 0);
        for (FluidStack fluid : input.listOrEmpty(TAG_FLUIDS, FluidStack.OPTIONAL_CODEC)) {
            if (!fluid.isEmpty()) {
                fluids.add(fluid);
                contained += fluid.getAmount();
            }
        }
    }
}
