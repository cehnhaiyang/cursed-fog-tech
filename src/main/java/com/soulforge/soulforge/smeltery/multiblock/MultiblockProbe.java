package com.soulforge.soulforge.smeltery.multiblock;

import com.soulforge.soulforge.block.SoulForgeBlocks;
import com.soulforge.soulforge.block.entity.SmelteryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 多方块结构自检探针：在真实世界里搭一座冶炼炉，然后调用**真实**的探测与逻辑 tick 代码，
 * 把结果写进日志。
 *
 * <p>存在的理由：多方块探测是几何 + 谓词判定，"编译通过"与"启动无报错"都不能证明
 * 墙砌对了就能成形。这里直接调用 {@link SmelteryMultiblock#detectMultiblock}，
 * 结果不可能因为重写了一遍逻辑而失真。
 *
 * <p>启用方式：设置环境变量 {@code SOULFORGE_SELFTEST=true} 后运行服务端，例如
 * {@code $env:SOULFORGE_SELFTEST='true'; gradlew.bat runServer}。
 * 用环境变量而非系统属性，是因为 Gradle 的 {@code -D} 只作用于 Gradle 自身，
 * 不会传给 fork 出的服务端 JVM。不设置该变量时本探针完全不执行。
 */
public final class MultiblockProbe {
    private MultiblockProbe() {
    }

    public static boolean enabled() {
        return "true".equalsIgnoreCase(System.getenv("SOULFORGE_SELFTEST"));
    }

    /** 在给定位置搭一座 3x3 内腔、高 2 的冶炼炉，控制器嵌在北墙。 */
    public static void build(ServerLevel level, BlockPos origin) {
        BlockState bricks = SoulForgeBlocks.SMELTERY_BRICKS.get().defaultBlockState();
        // 清空一块空间
        for (int x = -2; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -2; z <= 4; z++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // 底板：x -1..3, z 0..4
        for (int x = -1; x <= 3; x++) {
            for (int z = 0; z <= 4; z++) {
                level.setBlock(origin.offset(x, -1, z), bricks, 3);
            }
        }
        // 四面墙，高 2 层
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 3; x++) {
                for (int z = 0; z <= 4; z++) {
                    boolean onRing = x == -1 || x == 3 || z == 0 || z == 4;
                    if (onRing) {
                        level.setBlock(origin.offset(x, y, z), bricks, 3);
                    }
                }
            }
        }
        // 控制器：嵌在北墙 (z=0) 中间。炉内在它南边，所以朝向必须是 NORTH
        // （约定与 getStateForPlacement 一致：FACING 是控制器正面朝外的方向）。
        BlockPos controllerPos = origin.offset(1, 0, 0);
        level.setBlock(controllerPos, SoulForgeBlocks.SMELTERY_CONTROLLER.get().defaultBlockState()
                .setValue(com.soulforge.soulforge.block.SmelteryControllerBlock.FACING, Direction.NORTH), 3);
    }

    /** 直接跑一次真实探测并返回可读结果。 */
    public static String probe(ServerLevel level, BlockPos controllerPos) {
        if (!(level.getBlockEntity(controllerPos) instanceof SmelteryControllerBlockEntity controller)) {
            return "FAIL 控制器方块实体不存在 @" + controllerPos;
        }
        BlockState state = level.getBlockState(controllerPos);
        Direction facing = state.getValue(com.soulforge.soulforge.block.SmelteryControllerBlock.FACING);
        SmelteryMultiblock multiblock = new SmelteryMultiblock(controller.getTier());
        MultiblockStructure structure = multiblock.detectMultiblock(level, controllerPos, facing);
        MultiblockResult result = multiblock.getLastResult();
        if (structure == null) {
            return "FAIL 未成形: " + result.getMessage().getString()
                    + " @ " + result.getPos();
        }
        return "OK 成形 min=" + structure.getMin() + " max=" + structure.getMax()
                + " inner=" + structure.getInnerX() + "x" + structure.getInnerY() + "x" + structure.getInnerZ()
                + " tanks=" + structure.getTanks().size()
                + " volume=" + structure.getInteriorVolume();
    }

    /**
     * 验证实体浸在自定义流体里能否移动：铺一层熔融铁，生成一个真实生物，
     * 连推若干 tick 的移动输入，检查位移是否非零。
     *
     * <p>这是"玩家在熔融液体里完全无法移动"这一缺陷的直接回归测试。
     * 26.3 起非原版流体的实体移动只经由 {@code FluidType#move}，
     * 该方法返回 false 时水中与岩浆两个分支都不会执行，实体被完全冻结。
     */
    public static String probeFluidMovement(ServerLevel level, BlockPos pos) {
        // 铺一层熔融铁
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(pos.offset(x, 0, z),
                        com.soulforge.soulforge.fluid.SoulForgeFluids.MOLTEN_IRON.block().get().defaultBlockState(), 3);
            }
        }
        net.minecraft.world.entity.Mob mob =
                new net.minecraft.world.entity.monster.Creeper(
                        net.minecraft.world.entity.EntityTypes.CREEPER, level);
        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(mob);

        double startX = mob.getX();
        double startZ = mob.getZ();
        // 连续 20 tick 给一个固定的水平输入，走的是真实的 travel 分支
        for (int i = 0; i < 20; i++) {
            mob.travel(new net.minecraft.world.phys.Vec3(0.5, 0.0, 0.5));
        }
        double moved = Math.abs(mob.getX() - startX) + Math.abs(mob.getZ() - startZ);
        boolean inFluid = !level.getFluidState(pos).isEmpty();
        String fluidType = level.getFluidState(pos).getFluidType().getClass().getSimpleName();
        mob.discard();
        if (moved <= 1.0E-4) {
            return "FAIL 位移为 0（被冻结）inFluid=" + inFluid + " fluidType=" + fluidType;
        }
        return String.format("OK 20 tick 内水平位移=%.4f inFluid=%s fluidType=%s", moved, inFluid, fluidType);
    }

    /**
     * 验证燃料储罐方块的流体能力与"灌入燃料"这条路径。
     *
     * <p>"右键储罐放不进燃料"的直接前提是方块能通过能力被写入，
     * 所以这里直接对能力做一次真实插入与抽出，确认整条链路通。
     */
    public static String probeTankCapability(ServerLevel level, BlockPos pos) {
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, pos, null);
        if (handler == null) {
            return "FAIL 储罐方块没有流体能力 @" + pos;
        }
        var fuel = com.soulforge.soulforge.fluid.SoulForgeFluids.BLAZING_BLOOD;
        var resource = net.neoforged.neoforge.transfer.fluid.FluidResource.of(fuel.still().get());
        int inserted;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            inserted = handler.insert(0, resource, 1000, tx);
            tx.commit();
        }
        int amount = handler.getAmountAsInt(0);
        if (inserted != 1000 || amount != 1000) {
            return "FAIL 插入 " + inserted + " mB，罐内 " + amount + " mB";
        }
        // 再抽出来，确认不是只写不读
        int extracted;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            extracted = handler.extract(0, resource, 1000, tx);
            tx.commit();
        }
        if (extracted != 1000) {
            return "FAIL 只能插入不能抽出，抽出 " + extracted + " mB";
        }
        return "OK 插入 1000mB 成功，抽出 1000mB 成功";
    }

    /**
     * 验证熔炼槽的完整状态机：升温 -> 出料 -> 降温。
     *
     * <p>这三步各自都容易写错，而且错了以后界面表现很相似（"进度条不动"）：
     * <ul>
     *   <li>配方时间没乘 {@code TIME_SCALE}，进度条会瞬间走满</li>
     *   <li>{@code NO_SPACE} 与进度 0 混用，罐满后物品会凭空消失或永久卡住</li>
     *   <li>降温没实现，熄火后进度冻住</li>
     * </ul>
     * 因此这里断言的是**具体数值**，而不只是"能熔"。
     */
    public static String probeMeltingSlot(ServerLevel level, BlockPos controllerPos) {
        if (!(level.getBlockEntity(controllerPos) instanceof SmelteryControllerBlockEntity controller)) {
            return "FAIL 控制器方块实体不存在 @" + controllerPos;
        }
        var inventory = controller.getInventory();
        if (inventory.size() <= 0) {
            return "FAIL 熔炼槽数为 0";
        }
        var slot = inventory.getSlot(0);
        slot.clear();

        // 铁锭：配方温度 1539、时间 120
        var iron = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        slot.setStack(iron);
        if (slot.getRequiredTime() != 120 * 10) {
            return "FAIL 所需时间=" + slot.getRequiredTime() + "，期望 1200（配方 120 × 10）";
        }
        if (slot.getRequiredTemp() != 1539) {
            return "FAIL 所需温度=" + slot.getRequiredTemp() + "，期望 1539";
        }

        // 温度不够：不该升温
        slot.heatItem(1000, 10);
        if (slot.getCurrentTime() != 0) {
            return "FAIL 温度不足却升温到 " + slot.getCurrentTime();
        }
        // 温度够：按 rate 累加
        slot.heatItem(1600, 10);
        if (slot.getCurrentTime() != 10) {
            return "FAIL 升温后进度=" + slot.getCurrentTime() + "，期望 10（rate=10 一次）";
        }
        // 降温每次 5
        slot.coolItem();
        if (slot.getCurrentTime() != 5) {
            return "FAIL 降温后进度=" + slot.getCurrentTime() + "，期望 5";
        }

        // 罐灌满，热透后应当留在槽里并标记 NO_SPACE
        var tank = controller.getTank();
        var moltenIron = com.soulforge.soulforge.fluid.SoulForgeFluids.MOLTEN_IRON;
        tank.setCapacity(144);
        tank.fill(new net.neoforged.neoforge.fluids.FluidStack(moltenIron.still().get(), 144));
        slot.heatItem(1600, 10000);
        if (slot.isEmpty()) {
            return "FAIL 罐满时物品被吞掉了";
        }
        if (slot.getCurrentTime() != com.soulforge.soulforge.smeltery.module.MeltingSlot.NO_SPACE) {
            return "FAIL 罐满后进度=" + slot.getCurrentTime() + "，期望 NO_SPACE(-1)";
        }
        // 降温不该影响"已热透等空间"的状态
        slot.coolItem();
        if (slot.getCurrentTime() != com.soulforge.soulforge.smeltery.module.MeltingSlot.NO_SPACE) {
            return "FAIL 等空间时降温把状态改成了 " + slot.getCurrentTime();
        }

        // 腾出空间，再推一次就该出料
        tank.drain(new net.neoforged.neoforge.fluids.FluidStack(moltenIron.still().get(), 144));
        tank.setCapacity(1728);
        slot.heatItem(1600, 10000);
        if (!slot.isEmpty()) {
            return "FAIL 有空间却没出料，槽内仍是 " + slot.getStack();
        }
        int produced = tank.amountOf(new net.neoforged.neoforge.fluids.FluidStack(moltenIron.still().get(), 1));
        if (produced <= 0) {
            return "FAIL 出料后罐内熔融铁为 " + produced + " mB";
        }
        slot.clear();
        tank.drain(new net.neoforged.neoforge.fluids.FluidStack(moltenIron.still().get(), produced));
        return "OK 时间=1200 温度=1539 升温10 降温5 罐满NO_SPACE 出料=" + produced + "mB";
    }

    /**
     * 验证燃料模块按燃料表的四个参数工作：抽取量、燃烧时长、温度、速率。
     *
     * <p>"温度对但抽取量或速率写死"是一种看不出来的错误：炉子能烧、界面有温度，
     * 只是不同燃料的性价比完全一样。这里逐项断言。
     */
    public static String probeFuelModule(ServerLevel level, BlockPos controllerPos) {
        if (!(level.getBlockEntity(controllerPos) instanceof SmelteryControllerBlockEntity controller)) {
            return "FAIL 控制器方块实体不存在 @" + controllerPos;
        }
        var tanks = controller.getTanks();
        if (tanks.isEmpty()) {
            return "FAIL 结构里没有燃料储罐";
        }
        var module = controller.getFuelModule();
        // 先清掉可能残留的燃烧状态
        module.decreaseFuel(Integer.MAX_VALUE);

        int temperature = module.findFuel(level, tanks, true);
        var expected = com.soulforge.soulforge.smeltery.fuel.FuelRegistry.get(
                new net.neoforged.neoforge.fluids.FluidStack(
                        com.soulforge.soulforge.fluid.SoulForgeFluids.BLAZING_BLOOD.still().get(), 1));
        if (expected == null) {
            return "FAIL 烈焰之血未登记为燃料";
        }
        if (temperature != expected.temperature()) {
            return "FAIL 温度=" + temperature + "，期望 " + expected.temperature();
        }
        if (module.getRate() != expected.rate()) {
            return "FAIL 速率=" + module.getRate() + "，期望 " + expected.rate();
        }
        if (module.getFuel() != expected.duration()) {
            return "FAIL 燃烧时长=" + module.getFuel() + "，期望 " + expected.duration();
        }
        if (module.getFuelQuality() != expected.duration()) {
            return "FAIL 火焰基准=" + module.getFuelQuality() + "，期望 " + expected.duration();
        }
        // 抽走一份后罐里应当正好少 amount
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                tanks.get(0), null);
        int left = handler == null ? -1 : handler.getAmountAsInt(0);
        if (left != 1000 - expected.amount()) {
            return "FAIL 抽走一份后罐内=" + left + "，期望 " + (1000 - expected.amount());
        }
        // 烧完温度必须归零，否则会凭空维持高温
        module.decreaseFuel(Integer.MAX_VALUE);
        if (module.getTemperature() != 0 || module.getRate() != 0) {
            return "FAIL 烧完后温度=" + module.getTemperature() + " 速率=" + module.getRate() + "，期望都是 0";
        }
        return "OK 温度=" + temperature + " 速率=" + expected.rate() + " 时长=" + expected.duration()
                + " 每份=" + expected.amount() + "mB";
    }

    /**
     * 验证熔炼槽数随炉体内部体积变化——这是"把炉子建大"的核心收益之一。
     */
    public static String probeSlotScaling(ServerLevel level, BlockPos controllerPos) {
        if (!(level.getBlockEntity(controllerPos) instanceof SmelteryControllerBlockEntity controller)) {
            return "FAIL 控制器方块实体不存在 @" + controllerPos;
        }
        int slots = controller.getSlotCount();
        int volume = controller.getStructure() == null ? -1 : controller.getStructure().getInteriorVolume();
        if (volume <= 0) {
            return "FAIL 结构未成形，拿不到内部体积";
        }
        if (slots != volume) {
            return "FAIL 槽位数=" + slots + "，内部体积=" + volume + "，两者应当相等";
        }
        return "OK 槽位数=" + slots + " = 内部体积=" + volume;
    }

    /**
     * 验证界面布局与滚动几何。
     *
     * <p>这些是纯计算，但它们决定"物品格画在不在凹槽里"和"能不能点到看不见的格子"，
     * 出错的表征是错位而非崩溃，肉眼很难在代码里看出来。因此直接断言具体数值。
     */
    public static String probeLayout() {
        // 18 格（3x2x3 炉膛）：3 列 6 行，无需滚动
        int slots18 = 18;
        int columns = com.soulforge.soulforge.menu.ControllerLayout.columns(slots18);
        if (columns != 3) {
            return "FAIL 18 槽的列数=" + columns + "，期望 3";
        }
        if (com.soulforge.soulforge.menu.ControllerLayout.needsSlider(slots18, columns)) {
            return "FAIL 18 槽（3 列 6 行）不该需要滚动条";
        }
        int sideW = com.soulforge.soulforge.menu.ControllerLayout.sideWidth(slots18);
        int expectW = 3 * 22 + 7 * 2;
        if (sideW != expectW) {
            return "FAIL 侧栏宽度=" + sideW + "，期望 " + expectW;
        }
        // 槽位元素左边缘 7（边框），物品格再内缩 5 -> 12；第二个槽位 +22
        int item0 = com.soulforge.soulforge.menu.ControllerLayout.slotItemX(0, columns);
        int item1 = com.soulforge.soulforge.menu.ControllerLayout.slotItemX(1, columns);
        if (item0 != 12 || item1 != 34) {
            return "FAIL 物品格 x = " + item0 + "," + item1 + "，期望 12,34";
        }
        // 热进度条在元素内 +1
        if (com.soulforge.soulforge.menu.ControllerLayout.heatBarX(0, columns) != 8) {
            return "FAIL 热进度条 x=" + com.soulforge.soulforge.menu.ControllerLayout.heatBarX(0, columns) + "，期望 8";
        }

        // 大炉子：4 列上限
        if (com.soulforge.soulforge.menu.ControllerLayout.columns(100) != 4) {
            return "FAIL 100 槽的列数应当被限制在 4";
        }
        // 100 槽 / 4 列 = 25 行 > 10 行可见 -> 需要滚动条，且最大滚动 15 行
        int maxScroll = com.soulforge.soulforge.menu.ControllerLayout.maxScrollRow(100);
        if (maxScroll != 15) {
            return "FAIL 100 槽最大滚动行=" + maxScroll + "，期望 15（25 行 - 10 行可见）";
        }
        // 滚到底时最后一行可见，第一行不可见
        if (com.soulforge.soulforge.menu.ControllerLayout.isSlotVisible(0, 4, maxScroll)) {
            return "FAIL 滚到底时第 0 行仍可见";
        }
        if (!com.soulforge.soulforge.menu.ControllerLayout.isSlotVisible(99, 4, maxScroll)) {
            return "FAIL 滚到底时最后一格不可见";
        }
        // 滚动后槽位元素 y 会整体上移
        int y0 = com.soulforge.soulforge.menu.ControllerLayout.slotElementY(0, 4, 0);
        int y1 = com.soulforge.soulforge.menu.ControllerLayout.slotElementY(0, 4, 1);
        if (y0 - y1 != 18) {
            return "FAIL 滚动一行应当上移 18px，实际 " + (y0 - y1);
        }
        return "OK 18槽=3列宽" + sideW + " 物品格x=12,34 热条x=8 100槽滚动15行";
    }

    /**
     * 验证"把液体装进玩家手上的容器"这条链路真的会写回物品。
     *
     * <p>界面按钮与桶槽都依赖它：拿物品的流体能力灌液体，然后指望物品本身变了。
     * 如果 {@code ItemAccess.forStack} 只操作副本，液体就会被凭空吞掉——
     * 表现是"点一下桶里的液体没了，桶还是空的"，且不报任何错。
     * 这种事必须实测，不能从 API 名字推断。
     */
    public static String probeItemFluidWriteBack() {
        var bucket = new net.minecraft.world.item.ItemStack(
                com.soulforge.soulforge.item.SoulForgeItems.NETHERITE_BUCKET.get());
        var fluid = com.soulforge.soulforge.fluid.SoulForgeFluids.MOLTEN_IRON;
        var resource = net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluid.still().get());

        var handler = bucket.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM,
                net.neoforged.neoforge.transfer.access.ItemAccess.forStack(bucket));
        if (handler == null) {
            return "FAIL 桶没有物品流体能力";
        }
        int accepted;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            accepted = handler.insert(0, resource, 1000, tx);
            tx.commit();
        }
        if (accepted != 1000) {
            return "FAIL 插入被拒绝，accepted=" + accepted;
        }
        // 关键断言：原物品必须真的带上了液体
        var written = net.neoforged.neoforge.transfer.fluid.FluidUtil.getFirstStackContained(bucket);
        if (written.isEmpty()) {
            return "FAIL 写回丢失：插入返回 1000 但物品里没有液体（ItemAccess.forStack 未回写）";
        }
        if (written.getAmount() != 1000) {
            return "FAIL 写回量=" + written.getAmount() + "，期望 1000";
        }
        // 再抽出来，确认可逆
        int extracted;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            extracted = handler.extract(0, resource, 1000, tx);
            tx.commit();
        }
        var after = net.neoforged.neoforge.transfer.fluid.FluidUtil.getFirstStackContained(bucket);
        if (extracted != 1000 || !after.isEmpty()) {
            return "FAIL 抽出 " + extracted + "，抽后残留 " + after.getAmount() + " mB";
        }
        return "OK 装入 1000mB 已回写到物品，抽出后为空";
    }
}
