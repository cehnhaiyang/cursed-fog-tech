package com.soulforge.soulforge.capability;

import com.soulforge.soulforge.SoulForge;
import com.soulforge.soulforge.block.entity.SoulForgeBlockEntityTypes;
import com.soulforge.soulforge.item.SoulForgeItems;
import com.soulforge.soulforge.item.component.SoulForgeDataComponents;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;

/**
 * Registers NeoForge capability providers for SoulForge block entities and items.
 */
@EventBusSubscriber(modid = SoulForge.MOD_ID)
public class SoulForgeCapabilities {
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                SoulForgeBlockEntityTypes.SMELTERY_CONTROLLER.get(),
                (be, side) -> be.getFuelTank());

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                SoulForgeBlockEntityTypes.NETHERITE_FUEL_TANK.get(),
                (be, side) -> be.getTank());

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                SoulForgeBlockEntityTypes.TRINITY_ALLOY_FUEL_TANK.get(),
                (be, side) -> be.getTank());

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                SoulForgeBlockEntityTypes.MAGVAC_FUEL_TANK.get(),
                (be, side) -> be.getTank());

        // 铸造台和铸造盆的流体处理器（用于桶交互）
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                SoulForgeBlockEntityTypes.CASTING_TABLE.get(),
                (be, side) -> be.getTank());

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                SoulForgeBlockEntityTypes.CASTING_BASIN.get(),
                (be, side) -> be.getTank());

        // 铸造台和铸造盆的物品处理器（用于漏斗自动化）
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                SoulForgeBlockEntityTypes.CASTING_TABLE.get(),
                (be, side) -> new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, side != null ? side : Direction.DOWN));

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                SoulForgeBlockEntityTypes.CASTING_BASIN.get(),
                (be, side) -> new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, side != null ? side : Direction.DOWN));

        // High-temperature empty containers expose IFluidHandlerItem for safety checks and machine filling.
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, ctx) -> new FluidHandlerItemStack(() -> SoulForgeDataComponents.FLUID_CONTENT.get(), stack, 1000),
                SoulForgeItems.NETHERITE_BUCKET.get(),
                SoulForgeItems.TRINITY_ALLOY_BUCKET.get(),
                SoulForgeItems.MAGVAC_BUCKET.get());

        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, ctx) -> new FluidHandlerItemStack(() -> SoulForgeDataComponents.FLUID_CONTENT.get(), stack, 4000),
                SoulForgeItems.NETHERITE_FUEL_TANK.get(),
                SoulForgeItems.TRINITY_ALLOY_FUEL_TANK.get(),
                SoulForgeItems.MAGVAC_FUEL_TANK.get());
    }
}