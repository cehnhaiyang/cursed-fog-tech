package com.soulforge.soulforge.menu;

import com.soulforge.soulforge.block.entity.SmelteryControllerBlockEntity;
import com.soulforge.soulforge.smeltery.module.MeltingInventory;
import com.soulforge.soulforge.smeltery.module.MeltingSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 冶炼炉控制器菜单，布局与交互协议照搬 TiC3 的 {@code HeatingStructureContainerMenu}。
 *
 * <p>结构：
 * <ul>
 *   <li>侧栏里的 N 个熔炼输入槽，N 等于炉体内部体积，随结构大小变化</li>
 *   <li>右侧上下两个桶槽：上方放待处理的流体容器，下方放处理结果</li>
 *   <li>一个"传输方向"按钮，循环切换自动 / 只把物品抽空 / 只把物品装满</li>
 *   <li>底部玩家物品栏</li>
 * </ul>
 *
 * <p>按钮 id 的语义与 TiC3 完全一致，界面按下时直接发送这些 id：
 * <ul>
 *   <li>{@code 0} —— 循环传输方向</li>
 *   <li>{@code 1} —— 把燃料罐里的液体装进手上物品</li>
 *   <li>{@code 2} —— 把手上的液体倒进燃料罐</li>
 *   <li>{@code 3} —— 点在熔融罐空白处，把手上的液体倒进熔融罐</li>
 *   <li>{@code 4+} —— 点在熔融罐第 {@code id - 4} 种流体上：手上有物品就装它，
 *       空手就把这种流体挪到最前面（决定排液口先抽哪种）</li>
 * </ul>
 *
 * <p>桶槽是"放入即结算"：把桶放进上槽就立刻交换一次流体，结果落在下槽。
 */
public class ControllerMenu extends AbstractContainerMenu {
    /** 桶槽在菜单中的索引：0=待处理输入，1=结果。 */
    public static final int BUCKET_INPUT = 0;
    public static final int BUCKET_RESULT = 1;

    /** 传输方向。 */
    public enum TransferDirection {
        /** 能抽空就抽空，否则装满。 */
        AUTO,
        /** 把物品里的液体倒进炉内。 */
        EMPTY_ITEM,
        /** 把炉内液体装进物品。 */
        FILL_ITEM;

        public TransferDirection next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final SmelteryControllerBlockEntity blockEntity;
    private final ContainerData data;
    private final SimpleContainer bucketContainer = new SimpleContainer(2);
    private final boolean serverSide;
    /** 熔炼槽在 slots 列表中的起始下标，桶槽紧随其后。 */
    private final int slotCount;
    /** 侧栏已经向上滚过的行数；槽位多于可见行数时才非零。 */
    private int scrollRow;
    private TransferDirection transferDirection = TransferDirection.AUTO;

    /**
     * 服务端构造：由 MenuProvider.createMenu 调用。
     *
     * @param slotCount 熔炼槽数量。服务端取方块实体的真实值，客户端取菜单包里带来的值——
     *                  两侧必须用同一个数，否则槽位会整体错位。
     */
    private ControllerMenu(int containerId, Inventory playerInventory,
                           SmelteryControllerBlockEntity blockEntity, int slotCount, boolean serverSide) {
        super(getMenuType(), containerId);
        this.blockEntity = blockEntity;
        this.data = blockEntity.getDataAccess();
        this.serverSide = serverSide;
        this.slotCount = slotCount;
        // 客户端以菜单包为准调整本地槽位数，避免方块实体同步尚未到达时两侧不一致
        blockEntity.getInventory().resize(slotCount, stack -> {
        });

        addDataSlots(this.data);
        MeltingInventory inventory = blockEntity.getInventory();
        int columns = ControllerLayout.columns(slotCount);
        for (int i = 0; i < slotCount; i++) {
            MeltingSlot slot = inventory.getSlot(i);
            // 每个熔炼槽自己就是 ContainerData（进度 / 所需时间 / 所需温度），注册后自动同步，
            // 界面据此画热进度条；客户端写入会落回同步过来的槽位对象
            addDataSlots(slot);
            addSlot(new ResourceHandlerSlot(inventory, inventory, i,
                    ControllerLayout.slotItemX(i, columns),
                    ControllerLayout.SIDE_Y_OFFSET + ControllerLayout.slotItemY(i, columns, 0)));
        }
        // 槽位多于可见行数时，超出部分要移出面板，否则玩家能往看不见的格子里放东西
        updateSlotPositions();
        int mainX = mainPanelX();
        addSlot(new BucketInputSlot(bucketContainer, 0,
                mainX + ControllerLayout.BUCKET_INPUT_X, ControllerLayout.BUCKET_INPUT_Y));
        addSlot(new BucketResultSlot(bucketContainer, 1,
                mainX + ControllerLayout.BUCKET_RESULT_X, ControllerLayout.BUCKET_RESULT_Y));
        layoutPlayerInventory(playerInventory);
    }

    /** 服务端构造：由 MenuProvider.createMenu 调用。 */
    public ControllerMenu(int containerId, Inventory playerInventory, SmelteryControllerBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity, blockEntity.getSlotCount(), true);
    }

    /**
     * 客户端构造：从缓冲区读取方块实体与槽位数。
     *
     * <p>槽位数随炉体大小变化，而方块实体同步不保证先于菜单包到达，
     * 因此把权威值放进菜单包，客户端据此对齐——这是避免槽位错位的唯一可靠做法。
     */
    public ControllerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, readBlockEntity(playerInventory, buf), buf.readVarInt(), false);
    }

    /** 写进菜单打开包的内容：方块位置 + 槽位数。 */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, int slotCount) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(slotCount);
    }

    private static SmelteryControllerBlockEntity readBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof SmelteryControllerBlockEntity controller) {
            return controller;
        }
        throw new IllegalStateException("控制器方块实体不存在 @ " + pos);
    }

    private static MenuType<ControllerMenu> getMenuType() {
        return SoulForgeMenuTypes.SMELTERY_CONTROLLER.get();
    }

    /**
     * 按当前滚动位置摆放熔炼槽。
     *
     * <p>26.3 起 {@code Slot.x}/{@code Slot.y} 是 final，槽位不能像旧版本那样被移动，
     * 只能换成新坐标的新对象。因此这里重建熔炼槽并按原下标放回——
     * 下标不变，客户端与服务端的槽位对应关系不受影响。
     *
     * <p>不可见的槽位必须移出面板，否则玩家能往看不见的格子里放东西。
     */
    public void updateSlotPositions() {
        int columns = ControllerLayout.columns(slotCount);
        MeltingInventory inventory = blockEntity.getInventory();
        for (int i = 0; i < slotCount; i++) {
            boolean visible = ControllerLayout.isSlotVisible(i, columns, scrollRow);
            int x = visible ? ControllerLayout.slotItemX(i, columns) : OFF_PANEL;
            int y = visible
                    ? ControllerLayout.SIDE_Y_OFFSET + ControllerLayout.slotItemY(i, columns, scrollRow)
                    : OFF_PANEL;
            slots.set(i, new ResourceHandlerSlot(inventory, inventory, i, x, y));
        }
    }

    /** 面板外坐标。用一个大负数而不是 0，避免和左上角的真实槽位重合。 */
    private static final int OFF_PANEL = -1000;

    public int getScrollRow() {
        return scrollRow;
    }

    /** 滚到指定行，越界会被夹到合法范围。 */
    public void setScrollRow(int row) {
        int clamped = Math.max(0, Math.min(row, ControllerLayout.maxScrollRow(slotCount)));
        if (clamped == scrollRow) {
            return;
        }
        scrollRow = clamped;
        updateSlotPositions();
    }

    /** 玩家物品栏：界面总高 220，与 TiC3 一致。 */
    private void layoutPlayerInventory(Inventory playerInventory) {
        int mainX = mainPanelX();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col,
                        mainX + ControllerLayout.INVENTORY_X + col * 18,
                        ControllerLayout.INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    mainX + ControllerLayout.INVENTORY_X + col * 18,
                    ControllerLayout.HOTBAR_Y));
        }
    }

    /** 主面板左边缘在整个界面里的 x。侧栏与主面板有一小段重叠，看起来是一块整体。 */
    public int mainPanelX() {
        return ControllerLayout.sideWidth(slotCount) - ControllerLayout.SIDE_OVERLAP;
    }

    public SmelteryControllerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public TransferDirection getTransferDirection() {
        return transferDirection;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public Container getBucketContainer() {
        return bucketContainer;
    }

    public int getFuelTemperature() { return data.get(0); }
    public int getMeltAmount() { return data.get(1); }
    public int getMeltCapacity() { return data.get(2); }
    public boolean hasFuel() { return data.get(3) != 0; }
    public int getFuelRate() { return data.get(4); }
    public int getFuel() { return data.get(5); }
    public int getFuelQuality() { return data.get(6); }
    public boolean isFormed() { return data.get(7) != 0; }

    /*
     * 熔炼槽的三项进度。客户端读的是菜单数据槽里同步过来的副本，
     * 因此这几个方法在两侧都能用，界面不必自己去碰方块实体。
     * 每个槽占 3 个连续数据槽，下标为 index*3 + 字段偏移。
     */

    /** 第 index 个熔炼槽的升温进度；-1 表示已热透但罐满。 */
    public int getSlotCurrentTime(int index) {
        return getMeltingSlot(index).getCurrentTime();
    }

    public int getSlotRequiredTime(int index) {
        return getMeltingSlot(index).getRequiredTime();
    }

    public int getSlotRequiredTemp(int index) {
        return getMeltingSlot(index).getRequiredTemp();
    }

    /**
     * 取槽位对象。客户端与服务端都从方块实体的熔炼清单取——
     * 数据槽同步只更新整数进度，物品本身走方块实体同步，两者指向同一个槽位。
     */
    public MeltingSlot getMeltingSlot(int index) {
        return blockEntity != null ? blockEntity.getInventory().getSlot(index) : null;
    }

    /** 罐内全部流体，按倒入顺序，用于多段液位渲染。 */
    public List<FluidStack> getMeltFluids() {
        return blockEntity != null ? blockEntity.getTank().getFluids() : List.of();
    }

    /** 燃料罐里要显示的流体。客户端由方块实体同步的流体能力读取。 */
    public FuelModuleView getFuelView() {
        if (blockEntity == null || blockEntity.getLevel() == null) {
            return FuelModuleView.EMPTY;
        }
        var display = blockEntity.getFuelModule().getDisplay(blockEntity.getLevel(), blockEntity.getTanks());
        return new FuelModuleView(display.fluid(), display.amount(), display.capacity());
    }

    /** 界面显示用的燃料摘要。 */
    public record FuelModuleView(FluidStack fluid, int amount, int capacity) {
        public static final FuelModuleView EMPTY = new FuelModuleView(FluidStack.EMPTY, 0, 0);

        public boolean isEmpty() {
            return fluid.isEmpty() || amount <= 0 || capacity <= 0;
        }
    }

    /* 按钮协议 */

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.isSpectator()) {
            return false;
        }
        // 0：循环传输方向，并用新方向重试一次上槽里的物品
        if (id == 0) {
            transferDirection = transferDirection.next();
            updateBucket(player);
            return true;
        }
        if (blockEntity == null) {
            return false;
        }
        // 1~3：与燃料罐或熔融罐交换手上物品的液体
        if (id >= 1 && id <= 3) {
            return clickFluidButton(player, id);
        }
        // 4+：点在熔融罐的某种流体上
        if (id >= 4) {
            return clickTankFluid(player, id - 4);
        }
        return false;
    }

    private boolean clickFluidButton(Player player, int id) {
        ItemStack held = getCarried();
        if (held.isEmpty()) {
            return false;
        }
        if (!serverSide) {
            return true;
        }
        var level = player.level();
        var fuelModule = blockEntity.getFuelModule();
        var tanks = blockEntity.getTanks();
        if (id == 1) {
            // 把燃料罐里的液体装进手上物品
            FluidStack fuel = fuelModule.drain(level, tanks, Integer.MAX_VALUE, true);
            if (fuel.isEmpty()) {
                return false;
            }
            setCarried(fillHeldFromTank(player, held, fuel));
            return true;
        }
        if (id == 2) {
            // 把手上的液体倒进燃料罐
            setCarried(emptyHeldIntoTank(player, held, fuelModule, tanks));
            return true;
        }
        // id == 3：倒在熔融罐的空白处，即倒进熔融罐
        setCarried(emptyHeldIntoTank(player, held, null, null));
        return true;
    }

    private boolean clickTankFluid(Player player, int index) {
        if (!serverSide) {
            return true;
        }
        var tank = blockEntity.getTank();
        List<FluidStack> fluids = tank.getFluids();
        if (index < 0 || index >= fluids.size()) {
            return false;
        }
        FluidStack fluid = fluids.get(index);
        if (fluid.isEmpty()) {
            return false;
        }
        ItemStack held = getCarried();
        if (!held.isEmpty()) {
            setCarried(fillHeldFromTank(player, held, fluid));
        } else {
            // 空手点流体：把它挪到最前面，决定排液口先抽哪种
            tank.moveFluidToBottom(index);
            updateBucket(player);
        }
        return true;
    }

    /* 流体交换 */

    /** 把 {@code fluid} 装进玩家手上的容器，返回处理后的物品。 */
    private ItemStack fillHeldFromTank(Player player, ItemStack held, FluidStack fluid) {
        ResourceHandler<FluidResource> itemHandler = fluidHandlerOf(held);
        if (itemHandler == null) {
            return held;
        }
        var tank = blockEntity.getTank();
        try (Transaction tx = Transaction.openRoot()) {
            int accepted = itemHandler.insert(0, FluidResource.of(fluid), fluid.getAmount(), tx);
            if (accepted <= 0) {
                return held;
            }
            tank.extract(FluidResource.of(fluid), accepted, tx);
            tx.commit();
        }
        return held;
    }

    /**
     * 把手上的液体倒进燃料罐（{@code fuelModule} 非空）或熔融罐（{@code fuelModule} 为空）。
     */
    private ItemStack emptyHeldIntoTank(Player player, ItemStack held,
                                        @Nullable com.soulforge.soulforge.smeltery.module.FuelModule fuelModule,
                                        @Nullable List<BlockPos> tanks) {
        ResourceHandler<FluidResource> itemHandler = fluidHandlerOf(held);
        if (itemHandler == null) {
            return held;
        }
        for (int i = 0; i < itemHandler.size(); i++) {
            FluidStack inItem = itemHandler.getResource(i).toStack(itemHandler.getAmountAsInt(i));
            if (inItem.isEmpty()) {
                continue;
            }
            int accepted;
            if (fuelModule != null && tanks != null) {
                accepted = fuelModule.fill(player.level(), tanks, inItem, true);
            } else {
                accepted = simulateIntoMeltTank(inItem);
            }
            if (accepted <= 0) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                int moved = itemHandler.extract(i, itemHandler.getResource(i), accepted, tx);
                if (moved <= 0) {
                    continue;
                }
                if (fuelModule != null && tanks != null) {
                    fuelModule.fill(player.level(), tanks, inItem.copyWithAmount(moved), false);
                } else {
                    blockEntity.getTank().insert(FluidResource.of(inItem), moved, tx);
                }
                tx.commit();
            }
            return held;
        }
        return held;
    }

    private int simulateIntoMeltTank(FluidStack fluid) {
        var tank = blockEntity.getTank();
        try (Transaction tx = Transaction.openRoot()) {
            return tank.insert(FluidResource.of(fluid), fluid.getAmount(), tx);
        }
    }

    @Nullable
    private static ResourceHandler<FluidResource> fluidHandlerOf(ItemStack stack) {
        return stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(stack));
    }

    /* 桶槽结算 */

    /** 把上槽里的流体容器与炉内交换一次。 */
    private void updateBucket(Player player) {
        if (!serverSide || blockEntity == null) {
            return;
        }
        ItemStack input = bucketContainer.getItem(BUCKET_INPUT);
        if (input.isEmpty() || !bucketContainer.getItem(BUCKET_RESULT).isEmpty()) {
            return;
        }
        ItemStack result = transferFluid(input);
        if (result != null) {
            bucketContainer.setItem(BUCKET_INPUT, input);
            bucketContainer.setItem(BUCKET_RESULT, result);
            bucketContainer.setChanged();
        }
    }

    /**
     * 让容器物品与炉内熔融罐交换一次流体。
     *
     * @return 处理后的物品；无法处理时返回 null
     */
    @Nullable
    private ItemStack transferFluid(ItemStack stack) {
        ResourceHandler<FluidResource> itemHandler = fluidHandlerOf(stack);
        if (itemHandler == null) {
            return null;
        }
        var tank = blockEntity.getTank();
        boolean canEmpty = transferDirection != TransferDirection.FILL_ITEM;
        boolean canFill = transferDirection != TransferDirection.EMPTY_ITEM;

        try (Transaction tx = Transaction.openRoot()) {
            boolean moved = false;
            if (canEmpty) {
                // 把物品里的液体倒进炉内
                for (int i = 0; i < itemHandler.size() && !moved; i++) {
                    FluidResource resource = itemHandler.getResource(i);
                    int amount = itemHandler.getAmountAsInt(i);
                    if (resource.isEmpty() || amount <= 0) {
                        continue;
                    }
                    int accepted = tank.insert(resource, amount, tx);
                    if (accepted > 0) {
                        itemHandler.extract(i, resource, accepted, tx);
                        moved = true;
                    }
                }
            }
            if (!moved && canFill) {
                // 把炉内液体装进物品
                for (int t = 0; t < tank.size() && !moved; t++) {
                    FluidResource resource = tank.getResource(t);
                    int amount = tank.getAmountAsInt(t);
                    if (resource.isEmpty() || amount <= 0) {
                        continue;
                    }
                    int accepted = itemHandler.insert(0, resource, amount, tx);
                    if (accepted > 0) {
                        tank.extract(t, resource, accepted, tx);
                        moved = true;
                    }
                }
            }
            if (!moved) {
                return null;
            }
            tx.commit();
        }
        return stack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (serverSide) {
            // 关闭界面时把桶槽里剩下的东西还给玩家，避免物品卡在已销毁的菜单里
            clearContainer(player, bucketContainer);
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index != slotCount + BUCKET_RESULT && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack originalStack = stackInSlot.copy();
        int bucketStart = slotCount;
        int inventoryStart = slotCount + 2;

        if (index < bucketStart) {
            // 熔炼槽 -> 玩家物品栏
            if (!moveItemStackTo(stackInSlot, inventoryStart, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < inventoryStart) {
            // 桶槽 -> 玩家物品栏
            if (!moveItemStackTo(stackInSlot, inventoryStart, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家物品栏 -> 先试熔炼槽，放不下再试桶槽
            if (!moveItemStackTo(stackInSlot, 0, bucketStart, false)
                    && !moveItemStackTo(stackInSlot, bucketStart, inventoryStart, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return originalStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity == null || blockEntity.getLevel() == null
                || blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity;
    }

    /** 只接受流体容器的槽位。 */
    public static class BucketSlot extends Slot {
        public BucketSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return fluidHandlerOf(stack) != null;
        }
    }

    /** 结果槽：不允许放入，只由服务端结算写入。 */
    public static class ResultSlot extends Slot {
        public ResultSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** 上槽：放入物品时立刻结算一次流体交换。 */
    private class BucketInputSlot extends BucketSlot {
        BucketInputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public void set(ItemStack stack) {
            super.set(stack);
            updateBucket(null);
        }
    }

    /** 下槽：被取走后自动用上槽再结算一次，方便连续装桶。 */
    private class BucketResultSlot extends ResultSlot {
        BucketResultSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public void set(ItemStack stack) {
            super.set(stack);
            if (stack.isEmpty()) {
                updateBucket(null);
            }
        }
    }
}
