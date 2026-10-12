package com.soulforge.soulforge.client.screen;

import com.soulforge.soulforge.SoulForge;
import com.soulforge.soulforge.menu.ControllerLayout;
import com.soulforge.soulforge.menu.ControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 冶炼炉控制器界面。布局、绘制顺序与交互都照搬 TiC3 的 {@code HeatingStructureScreen}。
 *
 * <p>由两块面板拼成：
 * <ul>
 *   <li>侧栏（熔炼槽）贴在主面板左侧，与之重叠一小段，看起来是一块整体</li>
 *   <li>主面板 176×220：熔融罐、燃料罐、两个桶槽、传输方向按钮、玩家物品栏</li>
 * </ul>
 *
 * <p>贴图里的子元素坐标是硬约定，必须与 {@link ControllerLayout} 的常量一致：
 * 刻度尺 176,0 / 火焰 176,136 / 热进度条 176,150 / 传输方向图标 176,186 /
 * 按钮高亮 176,202 / 侧栏槽位 0,238。改动其一必须同时改另一个。
 */
public class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, "textures/gui/smeltery_controller.png");
    private static final Identifier GENERIC =
            Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, "textures/gui/generic.png");

    /* 贴图内的子元素坐标 */
    private static final int SCALE_U = 176;
    private static final int SCALE_V = 0;
    private static final int SCALE_HEIGHT = 106;
    private static final int FLAME_U = 176;
    private static final int FLAME_V = 136;
    private static final int HEAT_BAR_U = 176;
    private static final int HEAT_BAR_V = 150;
    /** 四条热进度条在贴图里横向排列，每条宽 3。 */
    private static final int HEAT_BAR_STRIDE = 3;
    private static final int MODE_ICON_U = 176;
    private static final int MODE_ICON_V = 186;
    private static final int MODE_ICON_SIZE = 16;
    private static final int MODE_ICON_STRIDE = 16;
    private static final int BUTTON_HOVER_U = 176;
    private static final int BUTTON_HOVER_V = 202;
    private static final int BUCKET_ICON_U = 224;
    private static final int BUCKET_ICON_V = 186;
    private static final int BUCKET_ICON_SIZE = 16;
    private static final int SIDE_SLOT_U = 0;
    private static final int SIDE_SLOT_V = 238;

    /* generic.png 里的边框元素，全部以 (0,0) 的 7x7 角为基准偏移 */
    private static final int BORDER_CORNER = 7;
    private static final int BORDER_SPAN = 64 - 7 - 7;
    private static final int OVERLAP_U = 21;
    private static final int OVERLAP_V = 45;

    /** 主面板左上角在整个界面里的位置。 */
    private final int mainX;
    private final int mainY;

    public ControllerScreen(ControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, ControllerLayout.realWidth(menu.getSlotCount()),
                ControllerLayout.realHeight(menu.getSlotCount()));
        this.mainX = menu.mainPanelX();
        this.mainY = ControllerLayout.SIDE_Y_OFFSET;
        this.inventoryLabelX = mainX + ControllerLayout.INVENTORY_X;
        this.inventoryLabelY = ControllerLayout.INVENTORY_Y - 11;
        this.titleLabelX = mainX + ControllerLayout.INVENTORY_X;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        // 界面在屏幕正中，leftPos/topPos 是整块面板的原点；主面板相对它再偏移
        this.titleLabelX = mainX + ControllerLayout.INVENTORY_X;
    }

    /* 背景与前景 */

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawSidePanel(graphics);
        drawMainPanel(graphics);
        renderFuel(graphics);
        renderTank(graphics);
        renderHeatBars(graphics);
    }

    /** 侧栏：边框 + 槽位元素。 */
    private void drawSidePanel(GuiGraphicsExtractor graphics) {
        int columns = ControllerLayout.columns(menu.getSlotCount());
        int width = ControllerLayout.sideWidth(menu.getSlotCount());
        int height = ControllerLayout.sideHeight(menu.getSlotCount());
        int scroll = menu.getScrollRow();
        int x = leftPos;
        int y = topPos + ControllerLayout.SIDE_Y_OFFSET;

        drawBorder(graphics, x, y, width, height);

        for (int i = 0; i < menu.getSlotCount(); i++) {
            if (!ControllerLayout.isSlotVisible(i, columns, scroll)) {
                continue;
            }
            int sx = x + ControllerLayout.slotElementX(i, columns);
            int sy = y + ControllerLayout.slotElementY(i, columns, scroll);
            // 槽位元素是 22x18：左侧 4px 热进度条凹槽 + 16x16 物品格
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, sx, sy, SIDE_SLOT_U, SIDE_SLOT_V,
                    ControllerLayout.SLOT_WIDTH, ControllerLayout.SLOT_HEIGHT, 256, 256);
        }
        if (ControllerLayout.needsSlider(menu.getSlotCount(), columns)) {
            drawSlider(graphics, x + width - ControllerLayout.BORDER - ControllerLayout.SLIDER_WIDTH,
                    y + ControllerLayout.BORDER, height - ControllerLayout.BORDER * 2);
        }
    }

    /**
     * 侧栏滚动条。只是位置指示，拖动由滚轮完成——
     * 槽位坐标归菜单所有，让滚动条直接改坐标会让两侧各持一份状态。
     */
    private void drawSlider(GuiGraphicsExtractor graphics, int x, int y, int height) {
        int columns = ControllerLayout.columns(menu.getSlotCount());
        int total = ControllerLayout.totalRows(menu.getSlotCount(), columns);
        int visible = ControllerLayout.visibleRows(menu.getSlotCount(), columns);
        int max = ControllerLayout.maxScrollRow(menu.getSlotCount());

        // 轨道
        graphics.fill(x + 2, y, x + ControllerLayout.SLIDER_WIDTH - 2, y + height, 0xFF000000);
        graphics.fill(x + 3, y + 1, x + ControllerLayout.SLIDER_WIDTH - 3, y + height - 1, 0xFF555555);
        // 滑块：按"可见行 / 总行数"占比决定长度
        int thumbHeight = Math.max(8, height * visible / total);
        int travel = height - thumbHeight;
        int thumbY = max == 0 ? y : y + travel * menu.getScrollRow() / max;
        graphics.fill(x + 2, thumbY, x + ControllerLayout.SLIDER_WIDTH - 2, thumbY + thumbHeight, 0xFFC6C6C6);
        graphics.fill(x + 2, thumbY, x + ControllerLayout.SLIDER_WIDTH - 2, thumbY + 1, 0xFFFFFFFF);
    }

    /**
     * generic.png 的九宫格边框。与主面板相连的那一侧用"重叠"元素，
     * 让两块面板看起来是一体的。
     */
    private void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int midW = width - BORDER_CORNER * 2;
        int midH = height - BORDER_CORNER * 2;
        // 上边：左上角 + 可伸缩中段 + 右上角
        blitGeneric(graphics, x, y, 0, 0, BORDER_CORNER, BORDER_CORNER);
        blitGenericStretched(graphics, x + BORDER_CORNER, y, BORDER_CORNER, 0, BORDER_SPAN, BORDER_CORNER, midW, BORDER_CORNER);
        blitGeneric(graphics, x + width - BORDER_CORNER, y, 64 - BORDER_CORNER, 0, BORDER_CORNER, BORDER_CORNER);
        // 下边
        blitGeneric(graphics, x, y + height - BORDER_CORNER, 0, 64 - BORDER_CORNER, BORDER_CORNER, BORDER_CORNER);
        blitGenericStretched(graphics, x + BORDER_CORNER, y + height - BORDER_CORNER, BORDER_CORNER, 64 - BORDER_CORNER, BORDER_SPAN, BORDER_CORNER, midW, BORDER_CORNER);
        blitGeneric(graphics, x + width - BORDER_CORNER, y + height - BORDER_CORNER, 64 - BORDER_CORNER, 64 - BORDER_CORNER, BORDER_CORNER, BORDER_CORNER);
        // 左右两侧：与主面板相接的一侧（右边）用重叠元素
        blitGenericStretched(graphics, x, y + BORDER_CORNER, 0, BORDER_CORNER, BORDER_CORNER, BORDER_SPAN, BORDER_CORNER, midH);
        blitGenericStretched(graphics, x + width - BORDER_CORNER, y + BORDER_CORNER, OVERLAP_U, OVERLAP_V, BORDER_CORNER, 14, BORDER_CORNER, midH);
    }

    /** 主面板：整张 176×220 贴图。 */
    private void drawMainPanel(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + mainX, topPos + mainY,
                0.0F, 0.0F, ControllerLayout.PANEL_WIDTH, ControllerLayout.PANEL_HEIGHT, 256, 256);
    }

    /** 熔融罐的多流体液位，从底部向上堆叠。 */
    private void renderTank(GuiGraphicsExtractor graphics) {
        List<FluidStack> fluids = menu.getMeltFluids();
        int capacity = menu.getMeltCapacity();
        if (fluids.isEmpty() || capacity <= 0) {
            return;
        }
        int[] heights = calcLiquidHeights(fluids, capacity, ControllerLayout.TANK_HEIGHT,
                ControllerLayout.MIN_FLUID_HEIGHT);
        int x = leftPos + mainX + ControllerLayout.TANK_X;
        int bottom = topPos + mainY + ControllerLayout.TANK_Y + ControllerLayout.TANK_HEIGHT;
        for (int i = 0; i < heights.length && i < fluids.size(); i++) {
            int fluidHeight = heights[i];
            if (fluidHeight <= 0) {
                continue;
            }
            bottom -= fluidHeight;
            // 用流体自身贴图的静帧颜色铺满，比纯色更接近原版观感
            graphics.fill(x, bottom, x + ControllerLayout.TANK_WIDTH, bottom + fluidHeight,
                    fluidColor(fluids.get(i)));
        }
    }

    /** 燃料罐液位与火焰图标。 */
    private void renderFuel(GuiGraphicsExtractor graphics) {
        ControllerMenu.FuelModuleView fuel = menu.getFuelView();
        int x = leftPos + mainX;
        int y = topPos + mainY;
        // 火焰按剩余燃烧时间缩放，与 TiC3 的 drawScaledYUp 一致
        int quality = menu.getFuelQuality();
        int remaining = menu.getFuel();
        if (remaining > 0 && quality > 0) {
            int height = Math.min(ControllerLayout.FIRE_HEIGHT,
                    ControllerLayout.FIRE_HEIGHT * remaining / quality);
            if (height > 0) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                        x + ControllerLayout.FIRE_X, y + ControllerLayout.FIRE_Y + ControllerLayout.FIRE_HEIGHT - height,
                        FLAME_U, FLAME_V + ControllerLayout.FIRE_HEIGHT - height,
                        ControllerLayout.FIRE_WIDTH, height, 256, 256);
            }
        }
        if (!fuel.isEmpty()) {
            int fluidHeight = Math.min(ControllerLayout.FUEL_HEIGHT,
                    ControllerLayout.FUEL_HEIGHT * fuel.amount() / fuel.capacity());
            if (fluidHeight > 0) {
                graphics.fill(x + ControllerLayout.FUEL_X,
                        y + ControllerLayout.FUEL_Y + ControllerLayout.FUEL_HEIGHT - fluidHeight,
                        x + ControllerLayout.FUEL_X + ControllerLayout.FUEL_WIDTH,
                        y + ControllerLayout.FUEL_Y + ControllerLayout.FUEL_HEIGHT,
                        fluidColor(fuel.fluid()));
            }
        }
    }

    /** 每个熔炼槽左侧的热进度条，四种状态四种颜色。 */
    private void renderHeatBars(GuiGraphicsExtractor graphics) {
        int columns = ControllerLayout.columns(menu.getSlotCount());
        int scroll = menu.getScrollRow();
        int temperature = menu.getFuelTemperature();
        int x = leftPos;
        int y = topPos + ControllerLayout.SIDE_Y_OFFSET;
        for (int i = 0; i < menu.getSlotCount(); i++) {
            if (menu.getMeltingSlot(i).isEmpty() || !ControllerLayout.isSlotVisible(i, columns, scroll)) {
                continue;
            }
            int current = menu.getSlotCurrentTime(i);
            int required = menu.getSlotRequiredTime(i);
            int requiredTemp = menu.getSlotRequiredTemp(i);

            // 状态与颜色一一对应：无配方 / 温度不够 / 罐满 / 正常进度
            int bar;
            float progress = 1.0F;
            if (required <= 0) {
                bar = 3;
            } else if (requiredTemp > temperature) {
                bar = 1;
            } else if (current < 0) {
                bar = 2;
            } else {
                bar = 0;
                progress = current >= required ? 1.0F : current / (float) required;
            }
            int height = Math.max(0, Math.min(ControllerLayout.HEAT_BAR_HEIGHT,
                    (int) (progress * ControllerLayout.HEAT_BAR_HEIGHT + 0.5F)));
            if (height <= 0) {
                continue;
            }
            int bx = x + ControllerLayout.heatBarX(i, columns);
            int by = y + ControllerLayout.heatBarY(i, columns, scroll);
            int offset = ControllerLayout.HEAT_BAR_HEIGHT - height;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    bx, by + offset,
                    HEAT_BAR_U + bar * HEAT_BAR_STRIDE, HEAT_BAR_V + offset,
                    ControllerLayout.HEAT_BAR_WIDTH, height, 256, 256);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int x = leftPos + mainX;
        int y = topPos + mainY;

        // 刻度尺叠在熔融罐上
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + ControllerLayout.TANK_X, y + ControllerLayout.TANK_Y,
                SCALE_U, SCALE_V, ControllerLayout.TANK_WIDTH, SCALE_HEIGHT, 256, 256);

        // 上桶槽为空时画一个桶形占位，提示这里放流体容器
        if (menu.getBucketContainer().getItem(ControllerMenu.BUCKET_INPUT).isEmpty()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + ControllerLayout.BUCKET_INPUT_X + 1, y + ControllerLayout.BUCKET_INPUT_Y + 1,
                    BUCKET_ICON_U, BUCKET_ICON_V, BUCKET_ICON_SIZE, BUCKET_ICON_SIZE, 256, 256);
        }

        renderTransferButton(graphics, mouseX, mouseY);
        renderTankTooltip(graphics, mouseX, mouseY);
        renderFuelTooltip(graphics, mouseX, mouseY);
        renderButtonTooltip(graphics, mouseX, mouseY);
        renderHeatBarTooltips(graphics, mouseX, mouseY);
    }

    /** 传输方向按钮：悬停时叠一层高亮，然后画当前方向的图标。 */
    private void renderTransferButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = leftPos + mainX;
        int y = topPos + mainY;
        if (isHovered(mouseX, mouseY, ControllerLayout.BUTTON_X, ControllerLayout.BUTTON_Y,
                ControllerLayout.BUTTON_SIZE, ControllerLayout.BUTTON_SIZE)) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + ControllerLayout.BUTTON_X, y + ControllerLayout.BUTTON_Y,
                    BUTTON_HOVER_U, BUTTON_HOVER_V,
                    ControllerLayout.BUTTON_SIZE, ControllerLayout.BUTTON_SIZE, 256, 256);
        }
        int icon = switch (menu.getTransferDirection()) {
            case AUTO -> 0;
            case EMPTY_ITEM -> 1;
            case FILL_ITEM -> 2;
        };
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x + ControllerLayout.BUTTON_X + 1, y + ControllerLayout.BUTTON_Y + 1,
                MODE_ICON_U + icon * MODE_ICON_STRIDE, MODE_ICON_V,
                MODE_ICON_SIZE, MODE_ICON_SIZE, 256, 256);
    }

    /* 提示 */

    private void renderTankTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isHovered(mouseX, mouseY, ControllerLayout.TANK_X, ControllerLayout.TANK_Y,
                ControllerLayout.TANK_WIDTH, ControllerLayout.TANK_HEIGHT)) {
            return;
        }
        List<FluidStack> fluids = menu.getMeltFluids();
        List<Component> tooltip = new ArrayList<>();
        int hovered = getFluidUnderMouse(mouseX, mouseY, fluids);
        if (hovered >= 0) {
            FluidStack fluid = fluids.get(hovered);
            tooltip.add(fluid.getHoverName());
            tooltip.add(Component.literal(fluid.getAmount() + " mB").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.soulforge.melting.tank.click").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("gui.soulforge.melting.capacity"));
            tooltip.add(Component.literal(menu.getMeltCapacity() + " mB").withStyle(ChatFormatting.GRAY));
            int remaining = Math.max(0, menu.getMeltCapacity() - menu.getMeltAmount());
            if (remaining > 0) {
                tooltip.add(Component.translatable("gui.soulforge.melting.available"));
                tooltip.add(Component.literal(remaining + " mB").withStyle(ChatFormatting.GRAY));
            }
            if (menu.getMeltAmount() > 0) {
                tooltip.add(Component.translatable("gui.soulforge.melting.used"));
                tooltip.add(Component.literal(menu.getMeltAmount() + " mB").withStyle(ChatFormatting.GRAY));
            }
        }
        graphics.setTooltipForNextFrame(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
    }

    private void renderFuelTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isHovered(mouseX, mouseY, ControllerLayout.FUEL_X - 1, ControllerLayout.FUEL_Y - 1,
                ControllerLayout.FUEL_WIDTH + 2, ControllerLayout.FUEL_HEIGHT + 2)) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        ControllerMenu.FuelModuleView fuel = menu.getFuelView();
        if (!fuel.isEmpty()) {
            tooltip.add(fuel.fluid().getHoverName());
            tooltip.add(Component.literal(fuel.amount() + " / " + fuel.capacity() + " mB")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (menu.hasFuel()) {
            tooltip.add(Component.translatable("gui.soulforge.melting.fuel.temperature",
                    menu.getFuelTemperature()));
            tooltip.add(Component.translatable("gui.soulforge.melting.fuel.rate", menu.getFuelRate())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else if (fuel.isEmpty()) {
            tooltip.add(Component.translatable("gui.soulforge.melting.fuel.no_tank"));
        } else {
            tooltip.add(Component.translatable("gui.soulforge.melting.fuel.empty"));
        }
        graphics.setTooltipForNextFrame(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
    }

    private void renderButtonTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isHovered(mouseX, mouseY, ControllerLayout.BUTTON_X, ControllerLayout.BUTTON_Y,
                ControllerLayout.BUTTON_SIZE, ControllerLayout.BUTTON_SIZE)) {
            return;
        }
        String key = switch (menu.getTransferDirection()) {
            case AUTO -> "gui.soulforge.tank.bucket.auto";
            case EMPTY_ITEM -> "gui.soulforge.tank.bucket.empty_item";
            case FILL_ITEM -> "gui.soulforge.tank.bucket.fill_item";
        };
        graphics.setTooltipForNextFrame(this.font,
                List.of(Component.translatable(key),
                        Component.translatable("gui.soulforge.tank.bucket.clickable")
                                .withStyle(ChatFormatting.GRAY)),
                java.util.Optional.empty(), mouseX, mouseY);
    }

    /** 热进度条上的状态说明，只在异常状态（无配方 / 温度不够 / 罐满）时显示。 */
    private void renderHeatBarTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int columns = ControllerLayout.columns(menu.getSlotCount());
        int scroll = menu.getScrollRow();
        int temperature = menu.getFuelTemperature();
        for (int i = 0; i < menu.getSlotCount(); i++) {
            if (menu.getMeltingSlot(i).isEmpty() || !ControllerLayout.isSlotVisible(i, columns, scroll)) {
                continue;
            }
            int bx = leftPos + ControllerLayout.heatBarX(i, columns);
            int by = topPos + ControllerLayout.SIDE_Y_OFFSET + ControllerLayout.heatBarY(i, columns, scroll);
            if (!isHovered(mouseX, mouseY, bx - leftPos, by - topPos,
                    ControllerLayout.HEAT_BAR_WIDTH, ControllerLayout.HEAT_BAR_HEIGHT)) {
                continue;
            }
            int required = menu.getSlotRequiredTime(i);
            Component message = null;
            if (required <= 0) {
                message = Component.translatable("gui.soulforge.melting.no_recipe");
            } else if (menu.getSlotRequiredTemp(i) > temperature) {
                message = Component.translatable("gui.soulforge.melting.no_heat");
            } else if (menu.getSlotCurrentTime(i) < 0) {
                message = Component.translatable("gui.soulforge.melting.no_space");
            }
            if (message != null) {
                graphics.setTooltipForNextFrame(this.font, message, mouseX, mouseY);
            }
            // 一次只能悬停一个槽位
            return;
        }
    }

    /* 交互 */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null
                || minecraft.player.isSpectator()) {
            return super.mouseClicked(event, doubleClick);
        }
        int checkX = (int) event.x() - leftPos - mainX;
        int checkY = (int) event.y() - topPos - mainY;
        int button = event.button();

        // 按钮 0：循环传输方向
        if (button == 0 && isHovered(checkX, checkY, ControllerLayout.BUTTON_X, ControllerLayout.BUTTON_Y,
                ControllerLayout.BUTTON_SIZE, ControllerLayout.BUTTON_SIZE)
                && this.menu.clickMenuButton(minecraft.player, 0)) {
            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            return true;
        }
        // 按钮 1~2：与燃料罐交换手上物品的液体
        if ((button == 0 || button == 1) && isHovered(checkX, checkY,
                ControllerLayout.FUEL_X - 1, ControllerLayout.FUEL_Y - 1,
                ControllerLayout.FUEL_WIDTH + 2, ControllerLayout.FUEL_HEIGHT + 2)) {
            int id = button == 0 ? 1 : 2;
            if (this.menu.clickMenuButton(minecraft.player, id)) {
                minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
                return true;
            }
        }
        // 按钮 3：点熔融罐空白处，把手上的液体倒进去
        // 按钮 4+：点熔融罐里的某种流体，装桶或把它挪到最前面
        if (button == 0 || button == 1) {
            int tankX = (int) event.x() - leftPos - mainX;
            int tankY = (int) event.y() - topPos - mainY;
            if (isHovered(tankX, tankY, ControllerLayout.TANK_X, ControllerLayout.TANK_Y,
                    ControllerLayout.TANK_WIDTH, ControllerLayout.TANK_HEIGHT)) {
                int id;
                if (button == 1) {
                    // 右键总是"倒进去"
                    id = 3;
                } else {
                    int index = getFluidUnderMouse(event.x(), event.y(), menu.getMeltFluids());
                    id = index + 4;
                }
                if (id > 2 && this.menu.clickMenuButton(minecraft.player, id)) {
                    minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** 侧栏上滚轮翻页。槽位坐标归菜单所有，这里只报告"滚到第几行"。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int columns = ControllerLayout.columns(menu.getSlotCount());
        if (ControllerLayout.needsSlider(menu.getSlotCount(), columns)
                && mouseX >= leftPos && mouseX < leftPos + ControllerLayout.sideWidth(menu.getSlotCount())
                && mouseY >= topPos + ControllerLayout.SIDE_Y_OFFSET
                && mouseY < topPos + ControllerLayout.SIDE_Y_OFFSET + ControllerLayout.sideHeight(menu.getSlotCount())) {
            menu.setScrollRow(menu.getScrollRow() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /* 几何工具 */

    /** 鼠标是否落在主面板内的某个区域上。 */
    private boolean isHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    /** 鼠标下方的流体索引，-1 表示空白处。 */
    private int getFluidUnderMouse(double mouseX, double mouseY, List<FluidStack> fluids) {
        if (fluids.isEmpty()) {
            return -1;
        }
        int checkY = (int) mouseY - topPos - mainY - ControllerLayout.TANK_Y;
        int[] heights = calcLiquidHeights(fluids, menu.getMeltCapacity(),
                ControllerLayout.TANK_HEIGHT, ControllerLayout.MIN_FLUID_HEIGHT);
        // 从罐底往上累加，与渲染顺序一致
        int y = ControllerLayout.TANK_HEIGHT - 1;
        for (int i = 0; i < heights.length; i++) {
            y -= heights[i];
            if (checkY > y) {
                return i;
            }
        }
        return -1;
    }

    private void blitGeneric(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int w, int h) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, GENERIC, x, y, u, v, w, h, 64, 64);
    }

    /**
     * 把 generic.png 里的一段拉伸到目标尺寸。
     *
     * <p>参数顺序是 {@code x, y, u, v, 目标宽, 目标高, 源宽, 源高, 贴图宽, 贴图高}：
     * 源区域与目标区域分开给，才能把 50px 的中段拉到任意长度。
     */
    private void blitGenericStretched(GuiGraphicsExtractor graphics, int x, int y,
                                      int u, int v, int sourceW, int sourceH, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, GENERIC, x, y, u, v, width, height,
                sourceW, sourceH, 64, 64);
    }

    private static int fluidColor(FluidStack fluid) {
        if (fluid.getFluid().getFluidType() instanceof com.soulforge.soulforge.fluid.SoulForgeFluidType type) {
            return type.getTintColor();
        }
        return 0xFF808080;
    }

    /**
     * 计算每种流体的像素高度。算法与 TiC3 的 {@code GuiSmelteryTank.calcLiquidHeights} 相同：
     * 先按占比取整（不低于最小值），再逐个从最高的流体上削像素直到总和放得下。
     */
    static int[] calcLiquidHeights(List<FluidStack> fluids, int capacity, int height, int min) {
        int[] fluidHeights = new int[fluids.size()];
        if (fluids.isEmpty() || capacity <= 0) {
            return fluidHeights;
        }
        int totalAmount = 0;
        for (int i = 0; i < fluids.size(); i++) {
            FluidStack fluid = fluids.get(i);
            totalAmount += fluid.getAmount();
            float ratio = (float) fluid.getAmount() / (float) capacity;
            fluidHeights[i] = Math.max(min, (int) Math.ceil(ratio * (float) height));
        }
        int available = height;
        if (totalAmount < capacity) {
            // 未满时留出一点空间，让"空罐"看得出还有余量
            available -= min;
        }
        int sum;
        do {
            sum = 0;
            int biggest = -1;
            int index = 0;
            for (int i = 0; i < fluidHeights.length; i++) {
                sum += fluidHeights[i];
                if (fluidHeights[i] > biggest) {
                    biggest = fluidHeights[i];
                    index = i;
                }
            }
            if (fluidHeights[index] == 0) {
                break;
            }
            if (sum > available) {
                fluidHeights[index]--;
            }
        } while (sum > available);
        return fluidHeights;
    }
}
