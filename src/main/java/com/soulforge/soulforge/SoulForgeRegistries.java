package com.soulforge.soulforge;

import com.soulforge.soulforge.block.SoulForgeBlocks;
import com.soulforge.soulforge.block.entity.SoulForgeBlockEntityTypes;
import com.soulforge.soulforge.fluid.SoulForgeFluids;
import com.soulforge.soulforge.item.SoulForgeItems;
import com.soulforge.soulforge.item.component.SoulForgeDataComponents;
import com.soulforge.soulforge.menu.SoulForgeMenuTypes;
import com.soulforge.soulforge.recipe.SoulForgeRecipeSerializers;
import com.soulforge.soulforge.recipe.SoulForgeRecipeTypes;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SoulForgeRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SoulForge.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SoulForge.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, SoulForge.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS = DeferredRegister
            .create(Registries.FLUID, SoulForge.MOD_ID);
    public static final DeferredRegister<net.neoforged.neoforge.fluids.FluidType> FLUID_TYPES = DeferredRegister
            .create(net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES, SoulForge.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, SoulForge.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENU_TYPES = DeferredRegister
            .create(Registries.MENU, SoulForge.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeType<?>> RECIPE_TYPES = DeferredRegister
            .create(Registries.RECIPE_TYPE, SoulForge.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
            .create(Registries.RECIPE_SERIALIZER, SoulForge.MOD_ID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister
            .create(Registries.DATA_COMPONENT_TYPE, SoulForge.MOD_ID);

    public static void register(IEventBus eventBus) {
        SoulForgeBlocks.register();
        SoulForgeItems.register();
        SoulForgeFluids.register();
        SoulForgeBlockEntityTypes.register();
        SoulForgeMenuTypes.register();
        SoulForgeRecipeTypes.register();
        SoulForgeRecipeSerializers.register();
        SoulForgeCreativeTabs.register();
        SoulForgeDataComponents.register();

        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
        FLUIDS.register(eventBus);
        FLUID_TYPES.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENU_TYPES.register(eventBus);
        RECIPE_TYPES.register(eventBus);
        RECIPE_SERIALIZERS.register(eventBus);
        DATA_COMPONENTS.register(eventBus);
    }
}
