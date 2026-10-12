package com.soulforge.soulforge.capability;

import com.soulforge.soulforge.SoulForge;
import com.soulforge.soulforge.block.entity.SoulForgeBlockEntityTypes;
import com.soulforge.soulforge.item.SoulForgeItems;
import com.soulforge.soulforge.item.component.SoulForgeDataComponents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.fluid.ItemAccessFluidHandler;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * Registers NeoForge capability providers for SoulForge block entities and
 * items.
 */
@EventBusSubscriber(modid = SoulForge.MOD_ID)
public class SoulForgeCapabilities {
        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
                // 控制器对外暴露的是熔融罐：这是炉内金属唯一的出口，桶、管道、铸造台都从这里取液。
                // 燃料不经过控制器——它存在结构内的燃料储罐方块里。
                event.registerBlockEntity(Capabilities.Fluid.BLOCK,
                                SoulForgeBlockEntityTypes.SMELTERY_CONTROLLER.get(),
                                (be, side) -> be.getTank());

                // 控制器同时对外暴露物品槽，供漏斗自动化投料
                event.registerBlockEntity(Capabilities.Item.BLOCK,
                                SoulForgeBlockEntityTypes.SMELTERY_CONTROLLER.get(),
                                (be, side) -> be.getItemHandler());

                event.registerBlockEntity(Capabilities.Fluid.BLOCK,
                                SoulForgeBlockEntityTypes.NETHERITE_FUEL_TANK.get(),
                                (be, side) -> be.getTank());

                event.registerBlockEntity(Capabilities.Fluid.BLOCK,
                                SoulForgeBlockEntityTypes.TRINITY_ALLOY_FUEL_TANK.get(),
                                (be, side) -> be.getTank());

                event.registerBlockEntity(Capabilities.Fluid.BLOCK,
                                SoulForgeBlockEntityTypes.MAGVAC_FUEL_TANK.get(),
                                (be, side) -> be.getTank());

                // 铸造台和铸造盆的流体处理器（用于桶交互）
                event.registerBlockEntity(Capabilities.Fluid.BLOCK,
                                SoulForgeBlockEntityTypes.CASTING_TABLE.get(),
                                (be, side) -> be.getTank());

                event.registerBlockEntity(Capabilities.Fluid.BLOCK,
                                SoulForgeBlockEntityTypes.CASTING_BASIN.get(),
                                (be, side) -> be.getTank());

                // 铸造台和铸造盆的物品处理器（用于漏斗自动化）
                event.registerBlockEntity(Capabilities.Item.BLOCK,
                                SoulForgeBlockEntityTypes.CASTING_TABLE.get(),
                                (be, side) -> new WorldlyContainerWrapper(be, side));

                event.registerBlockEntity(Capabilities.Item.BLOCK,
                                SoulForgeBlockEntityTypes.CASTING_BASIN.get(),
                                (be, side) -> new WorldlyContainerWrapper(be, side));

                // High-temperature empty containers expose a fluid handler for safety checks
                // and machine filling.
                event.registerItem(Capabilities.Fluid.ITEM,
                                (stack, access) -> new ItemAccessFluidHandler(access,
                                                SoulForgeDataComponents.FLUID_CONTENT.get(), 1000),
                                SoulForgeItems.NETHERITE_BUCKET.get(),
                                SoulForgeItems.TRINITY_ALLOY_BUCKET.get(),
                                SoulForgeItems.MAGVAC_BUCKET.get());

                event.registerItem(Capabilities.Fluid.ITEM,
                                (stack, access) -> new ItemAccessFluidHandler(access,
                                                SoulForgeDataComponents.FLUID_CONTENT.get(), 4000),
                                SoulForgeItems.NETHERITE_FUEL_TANK.get(),
                                SoulForgeItems.TRINITY_ALLOY_FUEL_TANK.get(),
                                SoulForgeItems.MAGVAC_FUEL_TANK.get());
        }
}
