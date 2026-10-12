package com.soulforge.soulforge.smeltery.module;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * 把控制器的一组 {@link MeltingSlot} 暴露成物品资源处理器，供菜单槽位与漏斗使用。
 *
 * <p>槽位数随炉体内部体积变化，对齐 TiC3 的 {@code MeltingModuleInventory}：
 * 3x2x3 的炉膛有 18 个熔炼槽。这是"把炉子建大"最直接的收益——不只是容量变大，
 * 能同时熔炼的物品也变多。
 *
 * <p>槽位是惰性创建的：空槽不占内存，也不必在结构刚成形时一次性建满。
 *
 * <p>每槽只接受 1 个物品：熔炼是按个计时的，允许堆叠会让"消耗一个"的语义变模糊。
 */
public class MeltingInventory implements ResourceHandler<ItemResource>, IndexModifier<ItemResource> {
    /**
     * 槽位数上限。菜单同步走 {@code ContainerData}（每项一个 short），
     * 每个熔炼槽占 3 项，加上炉体自身若干项，因此不能无限扩张。
     * 内部空腔最大 11x32x11 = 3872，远低于此上限，正常建造不会触顶。
     */
    public static final int MAX_SLOTS = (Short.MAX_VALUE - 16) / 3;

    private final IntFunction<MeltingSlot> factory;
    private final Runnable onChanged;
    private MeltingSlot[] slots;

    public MeltingInventory(IntFunction<MeltingSlot> factory, Runnable onChanged) {
        this.factory = factory;
        this.onChanged = onChanged;
        this.slots = new MeltingSlot[0];
    }

    @Override
    public int size() {
        return slots.length;
    }

    /** 取槽位，必要时创建。读路径不应调用它，否则空炉也会建满对象。 */
    public MeltingSlot getSlot(int index) {
        MeltingSlot slot = slots[index];
        if (slot == null) {
            slot = factory.apply(index);
            slots[index] = slot;
        }
        return slot;
    }

    /**
     * 调整槽位数。
     *
     * <p>缩小时多出来的物品交给 {@code dropConsumer}，不能直接丢弃——
     * 玩家把炉子改小不该损失已经放进去的材料。
     */
    public void resize(int newSize, Consumer<ItemStack> dropConsumer) {
        newSize = Math.min(newSize, MAX_SLOTS);
        if (newSize == slots.length) {
            return;
        }
        if (newSize < slots.length) {
            for (int i = newSize; i < slots.length; i++) {
                MeltingSlot slot = slots[i];
                if (slot != null && !slot.isEmpty()) {
                    dropConsumer.accept(slot.getStack());
                }
            }
        }
        MeltingSlot[] resized = new MeltingSlot[newSize];
        System.arraycopy(slots, 0, resized, 0, Math.min(slots.length, newSize));
        slots = resized;
        onChanged.run();
    }

    /** 直接遍历已有槽位，不触发创建。 */
    public void forEachExisting(Consumer<MeltingSlot> consumer) {
        for (MeltingSlot slot : slots) {
            if (slot != null) {
                consumer.accept(slot);
            }
        }
    }

    /** 任一槽位满足条件即为真。 */
    public boolean anyMatch(Predicate<MeltingSlot> predicate) {
        for (MeltingSlot slot : slots) {
            if (slot != null && predicate.test(slot)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemResource getResource(int index) {
        MeltingSlot slot = slots[index];
        if (slot == null) {
            return ItemResource.EMPTY;
        }
        ItemStack stack = slot.getStack();
        return stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack);
    }

    @Override
    public long getAmountAsLong(int index) {
        MeltingSlot slot = slots[index];
        return slot == null ? 0 : slot.getStack().getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return 1;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index >= 0 && index < slots.length;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        MeltingSlot slot = getSlot(index);
        if (!slot.isEmpty()) {
            return 0;
        }
        slot.setStack(resource.toStack(1));
        onChanged.run();
        return 1;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        MeltingSlot slot = slots[index];
        if (slot == null || slot.isEmpty()) {
            return 0;
        }
        ItemStack current = slot.getStack();
        if (!resource.matches(current)) {
            return 0;
        }
        int extracted = Math.min(amount, current.getCount());
        slot.clear();
        onChanged.run();
        return extracted;
    }

    /** 供菜单槽位写入。amount 为 0 表示清空。 */
    @Override
    public void set(int index, ItemResource resource, int amount) {
        MeltingSlot slot = getSlot(index);
        ItemStack stack = amount <= 0 || resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(amount);
        slot.setStack(stack);
        onChanged.run();
    }
}
