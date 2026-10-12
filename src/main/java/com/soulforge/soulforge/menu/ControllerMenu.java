package com.soulforge.soulforge.menu;

import com.soulforge.soulforge.block.entity.SmelteryControllerBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * 冶炼炉控制器菜单（参照 TiC3 的 HeatingStructureContainerMenu）。
 * 服务端构造使用真实方块实体；客户端构造从 RegistryFriendlyByteBuf 读取 BlockPos 查找方块实体。
 */
public class ControllerMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOTS = 4;
    public static final int DATA_SLOTS = 4;

    private final SmelteryControllerBlockEntity blockEntity;
    private final ContainerData data;

    // 服务端构造：由 MenuProvider.createMenu 调用
    public ControllerMenu(int containerId, Inventory inventory, SmelteryControllerBlockEntity blockEntity) {
        super(getMenuType(blockEntity), containerId);
        this.blockEntity = blockEntity;
        this.data = blockEntity.getDataAccess();
        addDataSlots(this.data);

        // 4 个物品输入槽（绑定方块实体的 ItemStacksResourceHandler）
        for (int i = 0; i < INPUT_SLOTS; i++) {
            addSlot(new ResourceHandlerSlot(blockEntity.getItemInput(), blockEntity.getItemInput()::set, i, 44 + i * 18, 35));
        }
        layoutPlayerInventory(inventory);
    }

    // 客户端构造：由 IMenuTypeExtension 调用，从缓冲区读取 BlockPos 查找方块实体
    public ControllerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory,
                (SmelteryControllerBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    // 客户端回退构造（方块实体不存在时）
    public ControllerMenu(int containerId, Inventory inventory) {
        super(SoulForgeMenuTypes.SMELTERY_CONTROLLER.get(), containerId);
        this.blockEntity = null;
        this.data = new SimpleContainerData(DATA_SLOTS);
        addDataSlots(this.data);
        SimpleContainer dummy = new SimpleContainer(INPUT_SLOTS);
        for (int i = 0; i < INPUT_SLOTS; i++) {
            addSlot(new Slot(dummy, i, 44 + i * 18, 35));
        }
        layoutPlayerInventory(inventory);
    }

    /** 根据方块实体的方块推断对应的菜单类型 */
    private static MenuType<ControllerMenu> getMenuType(SmelteryControllerBlockEntity be) {
        return SoulForgeMenuTypes.SMELTERY_CONTROLLER.get();
    }

    private void layoutPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    public SmelteryControllerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getMeltProgress() { return data.get(0); }
    public int getMeltTimeRequired() { return data.get(1); }
    public int getFuelAmount() { return data.get(2); }
    public int getMeltAmount() { return data.get(3); }

    public int getFuelCapacity() {
        return blockEntity != null ? blockEntity.getFuelTank().getCapacityAsInt(0, FluidResource.EMPTY) : 4000;
    }

    public int getMeltCapacity() {
        return blockEntity != null ? blockEntity.getMeltCapacity() : 4000;
    }

    public net.neoforged.neoforge.fluids.FluidStack getFuelFluid() {
        return blockEntity != null ? blockEntity.getFuelFluid() : net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    public net.neoforged.neoforge.fluids.FluidStack getMeltFluid() {
        return blockEntity != null ? blockEntity.getMeltFluid() : net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack originalStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            originalStack = stackInSlot.copy();
            if (index < INPUT_SLOTS) {
                if (!moveItemStackTo(stackInSlot, INPUT_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(stackInSlot, 0, INPUT_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return originalStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
