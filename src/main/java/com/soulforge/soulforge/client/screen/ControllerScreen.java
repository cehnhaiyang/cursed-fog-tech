package com.soulforge.soulforge.client.screen;

import com.soulforge.soulforge.SoulForge;
import com.soulforge.soulforge.menu.ControllerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 冶炼炉控制器客户端 GUI 界面。
 * 渲染背景、物品槽、燃料罐液位条、熔融罐液位条和熔炼进度箭头。
 */
public class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, "textures/gui/smeltery_controller.png");

    // 液位条区域（相对于 GUI 左上角）
    private static final int FUEL_TANK_X = 8;
    private static final int FUEL_TANK_Y = 18;
    private static final int TANK_WIDTH = 18;
    private static final int TANK_HEIGHT = 54;

    private static final int MELT_TANK_X = 150;
    private static final int MELT_TANK_Y = 18;

    // 进度箭头区域
    private static final int ARROW_X = 80;
    private static final int ARROW_Y = 36;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    public ControllerScreen(ControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // 标题位置
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        // 渲染主背景
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);

        // 渲染熔炼进度箭头
        int progress = menu.getMeltProgress();
        int maxProgress = menu.getMeltTimeRequired();
        if (maxProgress > 0 && progress > 0) {
            int arrowProgress = (int) ((float) progress / maxProgress * ARROW_WIDTH);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + ARROW_X, topPos + ARROW_Y,
                    (float) imageWidth, 0.0F, arrowProgress, ARROW_HEIGHT, 256, 256);
        }

        // 渲染燃料罐液位
        renderTank(graphics, FUEL_TANK_X, FUEL_TANK_Y, menu.getFuelAmount(), menu.getFuelCapacity(),
                menu.getFuelFluid(), 0xFF6B0000);

        // 渲染熔融罐液位
        renderTank(graphics, MELT_TANK_X, MELT_TANK_Y, menu.getMeltAmount(), menu.getMeltCapacity(),
                menu.getMeltFluid(), 0xFFFF6600);
    }

    /**
     * 渲染流体罐液位条。
     * 从底部向上填充，颜色根据流体类型变化。
     */
    private void renderTank(GuiGraphicsExtractor graphics, int tankX, int tankY, int amount, int capacity,
                            FluidStack fluid, int defaultColor) {
        if (amount <= 0 || capacity <= 0) {
            return;
        }
        int fillHeight = (int) ((float) amount / capacity * TANK_HEIGHT);
        if (fillHeight <= 0) {
            return;
        }
        int renderY = tankY + TANK_HEIGHT - fillHeight;

        // 选择颜色：有流体时使用 SoulForgeFluidType 的染色，否则用默认色
        int color = defaultColor;
        if (!fluid.isEmpty() && fluid.getFluid().getFluidType() instanceof com.soulforge.soulforge.fluid.SoulForgeFluidType sfft) {
            color = sfft.getTintColor();
        }

        int x = leftPos + tankX;
        int y = topPos + renderY;
        // 绘制实心矩形作为液位指示
        graphics.fill(x + 1, y, x + TANK_WIDTH - 1, y + fillHeight, color);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // 悬停时显示液位信息
        renderTankTooltip(graphics, mouseX, mouseY, FUEL_TANK_X, FUEL_TANK_Y, "燃料", menu.getFuelAmount(), menu.getFuelCapacity());
        renderTankTooltip(graphics, mouseX, mouseY, MELT_TANK_X, MELT_TANK_Y, "熔融", menu.getMeltAmount(), menu.getMeltCapacity());
    }

    private void renderTankTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int tankX, int tankY,
                                   String label, int amount, int capacity) {
        int x = leftPos + tankX;
        int y = topPos + tankY;
        if (mouseX >= x && mouseX < x + TANK_WIDTH && mouseY >= y && mouseY < y + TANK_HEIGHT) {
            graphics.setTooltipForNextFrame(this.font, Component.literal(label + ": " + amount + "/" + capacity + " mB"), mouseX, mouseY);
        }
    }
}
