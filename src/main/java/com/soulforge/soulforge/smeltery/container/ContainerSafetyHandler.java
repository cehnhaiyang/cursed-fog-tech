package com.soulforge.soulforge.smeltery.container;

import com.soulforge.soulforge.SoulForge;
import com.soulforge.soulforge.SoulForgeConfig;
import com.soulforge.soulforge.item.SoulForgeItems;
import com.soulforge.soulforge.item.SuperheatedResistantBucketItem;
import com.soulforge.soulforge.item.SuperheatedResistantTankItem;
import com.soulforge.soulforge.smeltery.fuel.FuelTemperatureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Handles container overheat safety: incompatible containers holding superheated fluids
 * are destroyed and their fluid spilled, and bare hands holding hot containers take damage.
 */
@EventBusSubscriber(modid = SoulForge.MOD_ID)
public class ContainerSafetyHandler {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }

            // Per-tick hand heat damage
            if (SoulForgeConfig.enableHandHeatDamage) {
                applyHandHeatDamage(player);
            }

            // Inventory scan once per second
            if (tickCounter % 20 == 0 && SoulForgeConfig.enableSuperheatedFluidDanger) {
                scanInventoryForOverheating(player);
            }
        }
    }

    private static void applyHandHeatDamage(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        boolean hasHotHand = isSuperheatedInHand(main) || isSuperheatedInHand(off);
        boolean hasGlove = off.is(SoulForgeItems.BLUNT_THERMAL_GLOVE.get());

        if (hasHotHand && !hasGlove) {
            player.hurt(player.damageSources().onFire(), 1.0f);
        }
    }

    private static boolean isSuperheatedInHand(ItemStack stack) {
        int maxTemp = getContainerMaxTemperature(stack);
        if (maxTemp < 0) {
            return false;
        }
        int fluidTemp = getHeldFluidTemperature(stack);
        return fluidTemp > 0 && fluidTemp > maxTemp;
    }

    private static void scanInventoryForOverheating(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            int maxTemp = getContainerMaxTemperature(stack);
            if (maxTemp < 0) {
                continue;
            }

            int fluidTemp = getHeldFluidTemperature(stack);
            if (fluidTemp > 0 && fluidTemp > maxTemp) {
                FluidStack fluid = getHeldFluid(stack);
                if (!player.isCreative()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    spillFluidAtFeet(player, fluid);
                    player.setRemainingFireTicks(Math.min(200, Math.max(60, fluidTemp / 50)));
                }
            }
        }
    }

    /** Returns the fluid in the first non-empty tank of the item, or empty. */
    private static FluidStack getHeldFluid(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler != null) {
            for (int t = 0; t < handler.getTanks(); t++) {
                FluidStack inTank = handler.getFluidInTank(t);
                if (!inTank.isEmpty()) {
                    return inTank.copy();
                }
            }
        }
        return FluidStack.EMPTY;
    }

    private static void spillFluidAtFeet(ServerPlayer player, FluidStack fluid) {
        if (fluid.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos feet = player.blockPosition();
        FluidState state = fluid.getFluid().defaultFluidState();
        if (!state.isEmpty()) {
            BlockState blockState = state.createLegacyBlock();
            if (blockState.canSurvive(level, feet)) {
                level.setBlock(feet, blockState, 3);
            }
        }
    }

    /** Gets the temperature of the fluid held in the item's first tank. */
    private static int getHeldFluidTemperature(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler != null) {
            for (int t = 0; t < handler.getTanks(); t++) {
                FluidStack inTank = handler.getFluidInTank(t);
                if (!inTank.isEmpty()) {
                    return FuelTemperatureRegistry.getTemperature(inTank);
                }
            }
        }
        return 0;
    }

    /**
     * Gets the maximum temperature this container can safely hold.
     * Returns -1 if the stack is not a recognized high-temperature fluid container.
     */
    private static int getContainerMaxTemperature(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof SuperheatedResistantBucketItem bucket) {
            return bucket.getMaxTemperature();
        }
        if (item instanceof SuperheatedResistantTankItem tank) {
            return tank.getMaxTemperature();
        }
        if (item == Items.BUCKET || item instanceof BucketItem) {
            return FluidContainerTier.VANILLA_BUCKET.getMaxTemperature();
        }
        return -1;
    }
}
