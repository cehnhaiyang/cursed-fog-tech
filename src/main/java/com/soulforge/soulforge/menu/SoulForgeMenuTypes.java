package com.soulforge.soulforge.menu;

import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.soulforge.soulforge.SoulForgeRegistries;

/**
 * 冶炼炉控制器菜单类型注册。
 * 使用 IMenuTypeExtension.create() 支持向客户端传递 BlockPos 等额外数据。
 */
public class SoulForgeMenuTypes {
        public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> SMELTERY_CONTROLLER = SoulForgeRegistries.MENU_TYPES
                        .register("smeltery_controller",
                                        () -> IMenuTypeExtension.create(ControllerMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> INTERMEDIATE_SMELTERY_CONTROLLER = SoulForgeRegistries.MENU_TYPES
                        .register("intermediate_smeltery_controller",
                                        () -> IMenuTypeExtension.create(ControllerMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> ADVANCED_SMELTERY_CONTROLLER = SoulForgeRegistries.MENU_TYPES
                        .register("advanced_smeltery_controller",
                                        () -> IMenuTypeExtension.create(ControllerMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> HYBRID_FURNACE_CONTROLLER = SoulForgeRegistries.MENU_TYPES
                        .register("hybrid_furnace_controller",
                                        () -> IMenuTypeExtension.create(ControllerMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> FUSION_FURNACE_CONTROLLER = SoulForgeRegistries.MENU_TYPES
                        .register("fusion_furnace_controller",
                                        () -> IMenuTypeExtension.create(ControllerMenu::new));

        public static void register() {
        }
}
