package com.soulforge.soulforge.smeltery.multiblock;

import com.soulforge.soulforge.SoulForge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * 自检入口：仅当环境变量 {@code SOULFORGE_SELFTEST=true} 时，在服务器启动后于主世界搭一座冶炼炉，
 * 依次验证结构探测、结构失效、燃料储罐能力与流体移动，把结果写进日志。
 * 不设置该变量时完全不执行，正常游戏与正常构建都不受影响。
 */
@EventBusSubscriber(modid = SoulForge.MOD_ID)
public class MultiblockProbeRunner {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!MultiblockProbe.enabled()) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        if (level == null) {
            SoulForge.LOGGER.error("[selftest] 主世界不存在");
            return;
        }
        // 选一块高空空地，避免破坏地形
        BlockPos origin = new BlockPos(0, 200, 0);
        SoulForge.LOGGER.info("[selftest] 开始搭建测试冶炼炉 @ {}", origin);
        MultiblockProbe.build(level, origin);

        BlockPos controllerPos = origin.offset(1, 0, 0);
        SoulForge.LOGGER.info("[selftest] 完整结构 -> {}", MultiblockProbe.probe(level, controllerPos));

        // 把一块炉壁换成燃料储罐，验证储罐会被收集为燃料来源
        level.setBlock(origin.offset(3, 0, 2),
                com.soulforge.soulforge.block.SoulForgeBlocks.NETHERITE_FUEL_TANK.get().defaultBlockState(), 3);
        SoulForge.LOGGER.info("[selftest] 换上一块燃料储罐 -> {}", MultiblockProbe.probe(level, controllerPos));

        // 在内部空腔里放一块实体方块，结构应当立即失效
        level.setBlock(origin.offset(1, 0, 2), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        SoulForge.LOGGER.info("[selftest] 内部塞入方块 -> {}", MultiblockProbe.probe(level, controllerPos));
        level.setBlock(origin.offset(1, 0, 2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);

        // 换掉顶层一块炉壁为泥土：该层失效，炉体应缩短但仍合法
        level.setBlock(origin.offset(3, 1, 4), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
        SoulForge.LOGGER.info("[selftest] 顶层炉壁换成泥土 -> {}", MultiblockProbe.probe(level, controllerPos));

        // 换回砖块，应当恢复原高度
        level.setBlock(origin.offset(3, 1, 4),
                com.soulforge.soulforge.block.SoulForgeBlocks.SMELTERY_BRICKS.get().defaultBlockState(), 3);
        SoulForge.LOGGER.info("[selftest] 换回炉壁 -> {}", MultiblockProbe.probe(level, controllerPos));

        // 验证控制器方块状态的置位：tick 一次让 checkStructure 跑起来
        if (level.getBlockEntity(controllerPos) instanceof com.soulforge.soulforge.block.entity.SmelteryControllerBlockEntity be) {
            be.tick(level, controllerPos, level.getBlockState(controllerPos));
        }
        SoulForge.LOGGER.info("[selftest] 成形后 tick 一次 -> IN_STRUCTURE = {} (期望 true)",
                level.getBlockState(controllerPos)
                        .getValue(com.soulforge.soulforge.block.SmelteryControllerBlock.IN_STRUCTURE));

        // 把底层一块砖换成泥土：整层失效，结构应当直接崩塌
        level.setBlock(origin.offset(-1, 0, 2), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
        if (level.getBlockEntity(controllerPos) instanceof com.soulforge.soulforge.block.entity.SmelteryControllerBlockEntity be) {
            be.updateStructure();
            be.tick(level, controllerPos, level.getBlockState(controllerPos));
        }
        SoulForge.LOGGER.info("[selftest] 底层炉壁换成泥土后 -> IN_STRUCTURE = {} (期望 false)",
                level.getBlockState(controllerPos)
                        .getValue(com.soulforge.soulforge.block.SmelteryControllerBlock.IN_STRUCTURE));

        // 换回砖块，应当重新成形
        level.setBlock(origin.offset(-1, 0, 2),
                com.soulforge.soulforge.block.SoulForgeBlocks.SMELTERY_BRICKS.get().defaultBlockState(), 3);
        if (level.getBlockEntity(controllerPos) instanceof com.soulforge.soulforge.block.entity.SmelteryControllerBlockEntity be) {
            be.updateStructure();
            be.tick(level, controllerPos, level.getBlockState(controllerPos));
        }
        SoulForge.LOGGER.info("[selftest] 换回砖块后 -> IN_STRUCTURE = {} (期望 true)",
                level.getBlockState(controllerPos)
                        .getValue(com.soulforge.soulforge.block.SmelteryControllerBlock.IN_STRUCTURE));

        // 验证燃料储罐的流体能力（"右键储罐放不进燃料"的底层链路）
        SoulForge.LOGGER.info("[selftest] 燃料储罐流体能力 -> {}",
                MultiblockProbe.probeTankCapability(level, origin.offset(3, 0, 2)));

        // 验证流体移动：把生物放进熔融铁里，看每 tick 的位移是否非零
        SoulForge.LOGGER.info("[selftest] 流体移动 -> {}", MultiblockProbe.probeFluidMovement(level, origin.offset(1, 0, 2)));

        // 炉体内部功能的三个关键契约
        SoulForge.LOGGER.info("[selftest] 熔炼槽数随体积 -> {}", MultiblockProbe.probeSlotScaling(level, controllerPos));
        SoulForge.LOGGER.info("[selftest] 熔炼槽状态机 -> {}", MultiblockProbe.probeMeltingSlot(level, controllerPos));

        // 燃料模块：往储罐灌 1000mB 烈焰之血，验证四个参数都被用上
        if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                origin.offset(3, 0, 2), null) instanceof net.neoforged.neoforge.transfer.ResourceHandler<
                        net.neoforged.neoforge.transfer.fluid.FluidResource> handler) {
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                handler.insert(0, net.neoforged.neoforge.transfer.fluid.FluidResource.of(
                        com.soulforge.soulforge.fluid.SoulForgeFluids.BLAZING_BLOOD.still().get()), 1000, tx);
                tx.commit();
            }
        }
        SoulForge.LOGGER.info("[selftest] 燃料模块 -> {}", MultiblockProbe.probeFuelModule(level, controllerPos));
        SoulForge.LOGGER.info("[selftest] 界面布局与滚动 -> {}", MultiblockProbe.probeLayout());
        SoulForge.LOGGER.info("[selftest] 物品流体写回 -> {}", MultiblockProbe.probeItemFluidWriteBack());
        SoulForge.LOGGER.info("[selftest] 结束");
    }
}
