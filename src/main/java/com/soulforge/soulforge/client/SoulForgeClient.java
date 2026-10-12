package com.soulforge.soulforge.client;

import com.soulforge.soulforge.SoulForge;
import com.soulforge.soulforge.client.screen.ControllerScreen;
import com.soulforge.soulforge.fluid.SoulForgeFluids;
import com.soulforge.soulforge.menu.SoulForgeMenuTypes;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/**
 * 客户端事件处理器。
 * 注册菜单对应的 Screen 界面，以及流体的渲染模型与染色。
 */
@EventBusSubscriber(modid = SoulForge.MOD_ID, value = Dist.CLIENT)
public class SoulForgeClient {
    /** 所有流体共用水的静止/流动贴图，靠染色区分。 */
    private static final Material WATER_STILL = new Material(Identifier.withDefaultNamespace("block/water_still"));
    private static final Material WATER_FLOWING = new Material(Identifier.withDefaultNamespace("block/water_flow"));

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        // 注册所有控制器菜单的 Screen
        event.register(SoulForgeMenuTypes.SMELTERY_CONTROLLER.get(), ControllerScreen::new);
        event.register(SoulForgeMenuTypes.INTERMEDIATE_SMELTERY_CONTROLLER.get(), ControllerScreen::new);
        event.register(SoulForgeMenuTypes.ADVANCED_SMELTERY_CONTROLLER.get(), ControllerScreen::new);
        event.register(SoulForgeMenuTypes.HYBRID_FURNACE_CONTROLLER.get(), ControllerScreen::new);
        event.register(SoulForgeMenuTypes.FUSION_FURNACE_CONTROLLER.get(), ControllerScreen::new);
    }

    /**
     * 26.3 起流体模型必须显式注册，否则客户端会报
     * "Missing FluidModel for fluid ..." 并渲染成错误方块。
     */
    @SubscribeEvent
    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        for (SoulForgeFluids.FluidSet fluid : SoulForgeFluids.ALL) {
            FluidModel.Unbaked model = new FluidModel.Unbaked(
                    WATER_STILL, WATER_FLOWING, null,
                    FluidTintSources.constant(fluid.tintColor()));
            event.register(model, fluid.still(), fluid.flowing());
        }
    }
}
