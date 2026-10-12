package com.soulforge.soulforge.client;

import com.soulforge.soulforge.SoulForge;
import com.soulforge.soulforge.client.screen.ControllerScreen;
import com.soulforge.soulforge.menu.SoulForgeMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端事件处理器。
 * 通过 RegisterMenuScreensEvent 注册菜单类型对应的 Screen 界面。
 */
@EventBusSubscriber(modid = SoulForge.MOD_ID, value = Dist.CLIENT)
public class SoulForgeClient {
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        // 注册所有控制器菜单的 Screen
        event.register(SoulForgeMenuTypes.SMELTERY_CONTROLLER.get(), ControllerScreen::new);
        event.register(SoulForgeMenuTypes.INTERMEDIATE_SMELTERY_CONTROLLER.get(), ControllerScreen::new);
        event.register(SoulForgeMenuTypes.ADVANCED_SMELTERY_CONTROLLER.get(), ControllerScreen::new);
        event.register(SoulForgeMenuTypes.HYBRID_FURNACE_CONTROLLER.get(), ControllerScreen::new);
        event.register(SoulForgeMenuTypes.FUSION_FURNACE_CONTROLLER.get(), ControllerScreen::new);
    }
}
