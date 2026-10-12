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
 * 注册菜单对应的 Screen 界面，以及流体的渲染模型。
 */
@EventBusSubscriber(modid = SoulForge.MOD_ID, value = Dist.CLIENT)
public class SoulForgeClient {
    /**
     * 每种流体有自己的静流/流动贴图，颜色已经画进贴图里，所以染色用纯白（乘 1 等于不染色）。
     * 若这里再按 tintColor 染色，贴图会被二次上色。
     */
    private static final int NO_TINT = 0xFFFFFFFF;

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
            String path = "block/fluid/" + fluid.name();
            FluidModel.Unbaked model = new FluidModel.Unbaked(
                    new Material(Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, path + "_still")),
                    new Material(Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, path + "_flowing")),
                    null,
                    FluidTintSources.constant(NO_TINT));
            event.register(model, fluid.still(), fluid.flowing());
        }
    }
}
