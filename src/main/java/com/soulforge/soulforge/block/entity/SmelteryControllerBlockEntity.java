package com.soulforge.soulforge.block.entity;

import com.soulforge.soulforge.block.SmelteryControllerBlock;
import com.soulforge.soulforge.smeltery.SmelteryTier;
import com.soulforge.soulforge.smeltery.module.AlloyingModule;
import com.soulforge.soulforge.smeltery.module.FuelModule;
import com.soulforge.soulforge.smeltery.module.MeltingInventory;
import com.soulforge.soulforge.smeltery.module.MeltingSlot;
import com.soulforge.soulforge.smeltery.multiblock.MultiblockResult;
import com.soulforge.soulforge.smeltery.multiblock.MultiblockStructure;
import com.soulforge.soulforge.smeltery.multiblock.SmelteryMultiblock;
import com.soulforge.soulforge.smeltery.tank.SmelteryTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * 冶炼炉控制器方块实体。
 *
 * <p>职责边界：它只做编排——结构探测、燃料取用、把物品槽交给 {@link MeltingSlot} 升温、
 * 把流体罐交给 {@link AlloyingModule} 合金。熔炼与合金的具体规则分别在那两个模块里，
 * 控制器不重复实现。
 *
 * <p>结构校验用"定时全量重扫"而不是"组件方块反向通知主控"：后者需要给每一种炉壁、
 * 储罐方块都挂方块实体才能跨越远距离回调，而炉壁往往是成百上千块同种砖。
 * 全量重扫的代价是 O(外壳方块数)，每秒一次；收益是**任何**改动都不可能被漏掉，
 * 不会出现"拆了墙炉子还在烧"的状态残留。
 *
 * <p>升温、合金、烧燃料三件事按 {@code tick % 4} 分相执行，对齐 TiC3 的 {@code heat()}。
 * 每 tick 全做一遍并不会更快——合金每次都要重扫配方表——却会让燃料消耗与进度推进
 * 的比例随实现细节漂移。
 */
public class SmelteryControllerBlockEntity extends BlockEntity implements MenuProvider {
    private static final Component DEFAULT_NAME = Component.translatable("container.soulforge.smeltery");
    /** 每个内部方块贡献的流体容量（mB）。 */
    private static final int CAPACITY_PER_BLOCK = 1728;
    /** 每多少格外壳使燃料消耗速率 +1。 */
    private static final int BLOCKS_PER_FUEL = 15;
    /** 空结构的保底容量，避免未成形时罐容量为 0 导致界面异常。 */
    private static final int MIN_CAPACITY = 1728;
    /** 未成形时保留的熔炼槽数，让界面在炉子塌掉的瞬间还有东西可画。 */
    private static final int MIN_SLOTS = 1;
    /** 结构重扫周期（tick）。 */
    private static final int STRUCTURE_RECHECK_INTERVAL = 20;

    private static final String TAG_STRUCTURE = "multiblock";
    private static final String TAG_TANK = "tank";
    private static final String TAG_ITEMS = "items";
    private static final String TAG_FUEL_MODULE = "fuel";

    private final SmelteryTier tier;
    private final SmelteryMultiblock multiblock;
    private final SmelteryTank tank = new SmelteryTank(this::onTankChanged);
    private final FuelModule fuelModule;
    private final AlloyingModule alloyingModule;
    private final MeltingInventory inventory;

    @Nullable
    private MultiblockStructure structure;
    private boolean structureUpdateQueued = true;
    private int tick;
    private int fuelRate = 1;
    /** 有变化待同步给客户端。罐里的流体与槽内物品只有同步过去，界面才画得出来。 */
    private boolean syncDirty = true;

    /** 菜单同步字段。各熔炼槽的进度由 {@link MeltingSlot} 自己作为 ContainerData 提供。 */
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> fuelModule.getTemperature();
                case 1 -> tank.getContained();
                case 2 -> tank.getCapacity();
                case 3 -> fuelModule.hasFuel() ? 1 : 0;
                case 4 -> fuelRate;
                case 5 -> fuelModule.getFuel();
                case 6 -> fuelModule.getFuelQuality();
                case 7 -> structure != null ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // 全部由服务端同步，客户端不写回
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    public SmelteryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SoulForgeBlockEntityTypes.SMELTERY_CONTROLLER.get(), pos, state);
        this.tier = state.getBlock() instanceof SmelteryControllerBlock controller
                ? controller.getTier()
                : SmelteryTier.SMELTERY;
        this.multiblock = new SmelteryMultiblock(tier);
        this.fuelModule = new FuelModule(tier);
        this.alloyingModule = new AlloyingModule(tank, tier == SmelteryTier.FUSION);
        // 世界与出料目标都在真正用到时才取：构造阶段 level 还没赋值，罐也还没配容量
        this.inventory = new MeltingInventory(index -> new MeltingSlot(this::getLevel, this::acceptMeltedFluid),
                this::onInventoryChanged);
        this.inventory.resize(MIN_SLOTS, stack -> {
        });
        tank.setCapacity(MIN_CAPACITY);
    }

    /** 罐内容变了：存档标记 + 排队同步给客户端（界面要画多段液位）。 */
    private void onTankChanged() {
        setChanged();
        syncDirty = true;
    }

    /** 熔炼槽内容变了：存档标记 + 排队同步（界面要画物品与热进度条）。 */
    private void onInventoryChanged() {
        setChanged();
        syncDirty = true;
    }

    public SmelteryTier getTier() {
        return tier;
    }

    @Nullable
    public MultiblockStructure getStructure() {
        return structure;
    }

    public SmelteryTank getTank() {
        return tank;
    }

    public FuelModule getFuelModule() {
        return fuelModule;
    }

    public int getSlotCount() {
        return inventory.size();
    }

    /** 菜单同步字段。各熔炼槽的进度由 {@link MeltingSlot} 自己作为 ContainerData 提供。 */
    public ContainerData getDataAccess() {
        return dataAccess;
    }

    /** 物品槽的处理器，供菜单槽位与漏斗绑定。 */
    public MeltingInventory getInventory() {
        return inventory;
    }

    /** 与 {@link #getInventory()} 同一个对象；名字保留给能力注册使用。 */
    public MeltingInventory getItemHandler() {
        return inventory;
    }

    /** 结构有效时才工作。 */
    public boolean canOperate() {
        return structure != null;
    }

    /** 结构内的燃料储罐位置；未成形时为空。 */
    public List<BlockPos> getTanks() {
        return structure != null ? structure.getTanks() : Collections.emptyList();
    }

    @Nullable
    public MultiblockResult getStructureResult() {
        return multiblock.getLastResult();
    }

    /**
     * 熔炼产物入罐。放得下才接收，由 {@link MeltingSlot} 决定是清槽还是标记等空间。
     *
     * @return 产物是否已全部进罐
     */
    private boolean acceptMeltedFluid(FluidStack product) {
        if (product.isEmpty()) {
            return true;
        }
        if (tank.getRemainingSpace() < product.getAmount()) {
            return false;
        }
        tank.fill(product);
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmelteryControllerBlockEntity entity) {
        entity.tick(level, pos, state);
    }

    /** 供自检探针直接驱动一次逻辑 tick；正常游戏只由 {@link #serverTick} 调用。 */
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            return;
        }

        // 每秒无条件重扫一次：这是"拆墙后炉子仍判定有效"这类状态残留的唯一兜底
        if (tick == 0) {
            structureUpdateQueued = true;
        }
        if (structureUpdateQueued) {
            structureUpdateQueued = false;
            checkStructure(level, pos, state);
        }

        // 结构不成立，或者连燃料储罐都没有：炉子只能冷却
        if (structure == null || !structure.hasTanks()) {
            cool();
            setActive(level, pos, state, false);
        } else {
            heat(level, pos, state);
        }
        advanceTick();

        // 每 4 tick 至多同步一次：罐与槽的变化频率远高于此，逐 tick 发包纯属浪费
        if (syncDirty && tick % 4 == 0) {
            syncDirty = false;
            syncToClient(level, pos, state);
        }
    }

    /**
     * 把罐内流体与槽内物品发给附近客户端。
     *
     * <p>界面要靠这些数据画多段液位、物品图标和热进度条，而 {@code ContainerData} 只能传 int，
     * 传不了流体与物品列表，所以走方块实体同步。
     */
    private void syncToClient(Level level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // 方块实体同步包的内容：罐 + 槽 + 结构尺寸
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        saveAdditional(output);
        return output.buildResult();
    }

    /**
     * 四相循环。对齐 TiC3 的 {@code SmelteryBlockEntity.heat()}：
     * 找燃料 / 升温 / 合金 / 烧燃料，各占一 tick。
     */
    private void heat(Level level, BlockPos pos, BlockState state) {
        switch (tick % 4) {
            // 第一相：没燃料就去找。只有真有活要干（能熔炼或能合金）才真的烧掉一份
            case 0 -> {
                if (!fuelModule.hasFuel()) {
                    int possible = fuelModule.findFuel(level, getTanks(), false);
                    if (possible > 0 && (canHeatAny(possible) || alloyingModule.canAlloy(level, possible))) {
                        fuelModule.findFuel(level, getTanks(), true);
                    }
                }
            }
            // 第二相：升温；没燃料就降温
            case 1 -> {
                if (fuelModule.hasFuel()) {
                    heatItems(fuelModule.getTemperature(), fuelModule.getRate());
                } else {
                    cool();
                }
            }
            // 第三相：合金
            case 2 -> {
                if (fuelModule.hasFuel()) {
                    alloyingModule.doAlloy(level, fuelModule.getTemperature());
                }
            }
            // 第四相：烧燃料，并把"是否在烧"反映到方块状态上
            default -> {
                setActive(level, pos, state, fuelModule.hasFuel());
                fuelModule.decreaseFuel(fuelRate);
            }
        }
    }

    private void heatItems(int temperature, int rate) {
        inventory.forEachExisting(slot -> {
            if (!slot.isEmpty()) {
                slot.heatItem(temperature, rate);
            }
        });
    }

    private void cool() {
        inventory.forEachExisting(MeltingSlot::coolItem);
    }

    private boolean canHeatAny(int temperature) {
        return inventory.anyMatch(slot -> slot.canHeatItem(temperature));
    }

    private void advanceTick() {
        tick = (tick + 1) % STRUCTURE_RECHECK_INTERVAL;
    }

    private void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (state.getValue(SmelteryControllerBlock.ACTIVE) != active) {
            level.setBlockAndUpdate(pos, state.setValue(SmelteryControllerBlock.ACTIVE, active));
        }
    }

    private void checkStructure(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(SmelteryControllerBlock.FACING);
        MultiblockStructure detected = multiblock.detectMultiblock(level, pos, facing);
        boolean wasFormed = state.getValue(SmelteryControllerBlock.IN_STRUCTURE);
        boolean formed = detected != null;

        if (formed != wasFormed) {
            BlockState newState = state.setValue(SmelteryControllerBlock.IN_STRUCTURE, formed);
            if (!formed) {
                newState = newState.setValue(SmelteryControllerBlock.ACTIVE, false);
            }
            level.setBlockAndUpdate(pos, newState);
        }

        if (detected != null) {
            tank.setCapacity(Math.max(MIN_CAPACITY, detected.getInteriorVolume() * CAPACITY_PER_BLOCK));
            fuelRate = 1 + detected.getPerimeterCount() / BLOCKS_PER_FUEL;
            // 每个内部方块一个熔炼槽：把炉子建大不只是容量变大，能同时熔的物品也变多
            inventory.resize(detected.getInteriorVolume(), stack -> dropItem(level, pos, state, stack));
        } else {
            // 炉子塌了就不该继续按旧尺寸算容量，否则界面会显示一个不存在的罐
            tank.setCapacity(MIN_CAPACITY);
            fuelRate = 1;
            inventory.resize(MIN_SLOTS, stack -> dropItem(level, pos, state, stack));
        }
        structure = detected;
        setChanged();
    }

    /** 把放不下的物品丢在控制器正面，与 TiC3 的 {@code dropItem} 一致。 */
    private void dropItem(Level level, BlockPos pos, BlockState state, ItemStack stack) {
        if (stack.isEmpty() || level.isClientSide()) {
            return;
        }
        BlockPos front = pos.relative(state.getValue(SmelteryControllerBlock.FACING));
        double x = front.getX() + 0.25D + level.getRandom().nextDouble() * 0.5D;
        double y = front.getY() + 0.25D + level.getRandom().nextDouble() * 0.5D;
        double z = front.getZ() + 0.25D + level.getRandom().nextDouble() * 0.5D;
        ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }

    /** 方块邻居变化时立即排队重扫，不必等到下一秒。 */
    public void updateStructure() {
        structureUpdateQueued = true;
    }

    /** 控制器被破坏时清空结构引用，避免残留状态被后续 tick 使用。 */
    public void invalidateStructure() {
        structure = null;
    }

    @Override
    public void setRemoved() {
        invalidateStructure();
        super.setRemoved();
    }

    /* 存档 */

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child(TAG_TANK).ifPresent(tank::deserialize);
        input.child(TAG_FUEL_MODULE).ifPresent(fuelModule::deserialize);
        input.child(TAG_ITEMS).ifPresent(items -> {
            int size = items.getIntOr("size", MIN_SLOTS);
            inventory.resize(Math.max(MIN_SLOTS, size), stack -> {
            });
            for (int i = 0; i < inventory.size(); i++) {
                MeltingSlot slot = inventory.getSlot(i);
                items.child("slot" + i).ifPresent(slot::deserialize);
            }
        });
        // 只恢复尺寸用于界面显示；储罐列表等细节留给首次 tick 的完整重扫
        input.child(TAG_STRUCTURE).ifPresent(struct -> {
            int minX = struct.getIntOr("minX", 0);
            int minY = struct.getIntOr("minY", 0);
            int minZ = struct.getIntOr("minZ", 0);
            int maxX = struct.getIntOr("maxX", 0);
            int maxY = struct.getIntOr("maxY", 0);
            int maxZ = struct.getIntOr("maxZ", 0);
            if (maxX > minX && maxZ > minZ) {
                structure = new MultiblockStructure(new BlockPos(minX, minY, minZ),
                        new BlockPos(maxX, maxY, maxZ), readTanks(struct));
            }
        });
    }

    /**
     * 储罐位置必须同步到客户端：燃料表要显示"结构里那几个储罐装了多少"，
     * 而客户端不做结构扫描（那是服务端每秒一次的事），只靠这里的列表去找方块。
     */
    private static List<BlockPos> readTanks(ValueInput struct) {
        List<BlockPos> tanks = new java.util.ArrayList<>();
        struct.listOrEmpty("tanks", BlockPos.CODEC).forEach(tanks::add);
        return tanks;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child(TAG_TANK));
        fuelModule.serialize(output.child(TAG_FUEL_MODULE));
        ValueOutput items = output.child(TAG_ITEMS);
        items.putInt("size", inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            inventory.getSlot(i).serialize(items.child("slot" + i));
        }
        if (structure != null) {
            ValueOutput struct = output.child(TAG_STRUCTURE);
            BlockPos min = structure.getMin();
            BlockPos max = structure.getMax();
            struct.putInt("minX", min.getX());
            struct.putInt("minY", min.getY());
            struct.putInt("minZ", min.getZ());
            struct.putInt("maxX", max.getX());
            struct.putInt("maxY", max.getY());
            struct.putInt("maxZ", max.getZ());
            // 储罐位置同步给客户端，燃料表据此读取每个储罐的实际存量
            ValueOutput.TypedOutputList<BlockPos> tanks = struct.list("tanks", BlockPos.CODEC);
            for (BlockPos tank : structure.getTanks()) {
                tanks.add(tank);
            }
        }
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
