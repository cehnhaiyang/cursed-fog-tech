package com.soulforge.soulforge.menu;

/**
 * 控制器界面的全部几何常量与布局计算。
 *
 * <p>菜单负责把槽位放在这些坐标上，界面负责按同样的坐标画贴图。两处各写一份常量是
 * 这类界面最典型的漂移来源：改了槽位没改底图，物品就会画在凹槽外面。因此坐标只在这里定义。
 *
 * <p>数值全部照搬 TiC3 的 {@code HeatingStructureScreen} 与 {@code HeatingStructureContainerMenu}。
 *
 * <p>整体由两块面板拼成，与 TiC3 的多模块结构一致：
 * <ul>
 *   <li>**主面板** 176×220：熔融罐、燃料罐、两个桶槽、传输方向按钮、玩家物品栏</li>
 *   <li>**侧栏**：熔炼槽。贴在主面板左侧并与之相连，宽度与高度随槽位数变化</li>
 * </ul>
 * 熔炼槽不在主面板里——这是照搬时最容易搞错的一点。
 */
public final class ControllerLayout {
    /** 主面板尺寸。220 比标准箱子界面高，为侧栏与桶槽留位置。 */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 220;

    /* 熔融罐：GuiSmelteryTank(this, tank, 8, 16, 106, 106, ...) */
    public static final int TANK_X = 8;
    public static final int TANK_Y = 16;
    public static final int TANK_WIDTH = 106;
    public static final int TANK_HEIGHT = 106;
    /** 每种流体的最小像素高度，与 TiC3 的 calcLiquidHeights 一致。 */
    public static final int MIN_FLUID_HEIGHT = 3;

    /* 燃料罐：GuiFuelModule(..., 152, 32, 16, 90, 153, 15, false, ...) */
    public static final int FUEL_X = 152;
    public static final int FUEL_Y = 32;
    public static final int FUEL_WIDTH = 16;
    public static final int FUEL_HEIGHT = 90;
    public static final int FIRE_X = 153;
    public static final int FIRE_Y = 15;
    public static final int FIRE_WIDTH = 14;
    public static final int FIRE_HEIGHT = 14;

    /* 桶槽与传输方向按钮 */
    public static final int BUCKET_INPUT_X = 125;
    public static final int BUCKET_INPUT_Y = 46;
    public static final int BUCKET_RESULT_X = 125;
    public static final int BUCKET_RESULT_Y = 104;
    public static final int BUTTON_X = 124;
    public static final int BUTTON_Y = 69;
    public static final int BUTTON_SIZE = 18;

    /* 玩家物品栏：getInventoryYOffset() = 138，快捷栏在 +58 */
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 138;
    public static final int HOTBAR_Y = INVENTORY_Y + 58;

    /* 侧栏（熔炼槽）。槽位元素 22x18，左侧 4px 留给热进度条。 */
    /** 槽位元素宽度：左侧 4px 热进度条 + 1px 边 + 16px 物品格 + 1px 边。 */
    public static final int SLOT_WIDTH = 22;
    /** 槽位元素高度：1px 边 + 16px 物品格 + 1px 边。 */
    public static final int SLOT_HEIGHT = 18;
    /** 物品格在槽位元素内的偏移。 */
    public static final int SLOT_ITEM_INSET_X = 5;
    public static final int SLOT_ITEM_INSET_Y = 1;
    /** generic.png 边框宽度。 */
    public static final int BORDER = 7;
    /** 滚动条宽度，仅在行数超出可见范围时占用。 */
    public static final int SLIDER_WIDTH = 12;
    /** 侧栏最多显示的行数。 */
    public static final int MAX_VISIBLE_ROWS = 10;
    /** 侧栏相对主面板顶部的下移量，与 TiC3 的 {@code yOffset = 4} 一致。 */
    public static final int SIDE_Y_OFFSET = 4;
    /**
     * 侧栏与主面板的重叠宽度。
     * TiC3 的 {@code connected} 模式把侧栏右边缘压在主面板下面，看起来是一块整体。
     */
    public static final int SIDE_OVERLAP = BORDER - 1;
    /** 热进度条：宽 3、高 16，画在物品格左侧。 */
    public static final int HEAT_BAR_WIDTH = 3;
    public static final int HEAT_BAR_HEIGHT = 16;

    private ControllerLayout() {
    }

    /**
     * 侧栏列数：每 7 格一列，最多 4 列。
     * 与 TiC3 的 {@code HeatingStructureContainerMenu.calcColumns} 一致。
     */
    public static int columns(int slotCount) {
        return Math.max(1, Math.min(4, (slotCount + 6) / 7));
    }

    /** 侧栏总行数。 */
    public static int totalRows(int slotCount, int columns) {
        if (columns <= 0) {
            return 0;
        }
        return (slotCount + columns - 1) / columns;
    }

    /** 实际能显示的行数，超出部分靠滚动查看。 */
    public static int visibleRows(int slotCount, int columns) {
        return Math.min(totalRows(slotCount, columns), MAX_VISIBLE_ROWS);
    }

    /** 是否需要滚动条。 */
    public static boolean needsSlider(int slotCount, int columns) {
        return totalRows(slotCount, columns) > MAX_VISIBLE_ROWS;
    }

    /** 侧栏宽度。 */
    public static int sideWidth(int slotCount) {
        int columns = columns(slotCount);
        int width = columns * SLOT_WIDTH + BORDER * 2;
        if (needsSlider(slotCount, columns)) {
            width += SLIDER_WIDTH;
        }
        return width;
    }

    /** 侧栏高度。 */
    public static int sideHeight(int slotCount) {
        int columns = columns(slotCount);
        return visibleRows(slotCount, columns) * SLOT_HEIGHT + BORDER * 2;
    }

    /** 加上侧栏之后整个界面的宽度。 */
    public static int realWidth(int slotCount) {
        return sideWidth(slotCount) - SIDE_OVERLAP + PANEL_WIDTH;
    }

    /** 加上侧栏之后整个界面的高度。 */
    public static int realHeight(int slotCount) {
        return Math.max(PANEL_HEIGHT, sideHeight(slotCount) + SIDE_Y_OFFSET);
    }

    /**
     * 侧栏里第 index 个槽位元素的左上角，坐标以侧栏外框左上角为原点。
     * 这是元素原点（22x18 的整块），物品格与热进度条都相对它定位。
     *
     * @param scrollRow 已经向上滚过的行数
     */
    public static int slotElementX(int index, int columns) {
        return BORDER + (index % columns) * SLOT_WIDTH;
    }

    public static int slotElementY(int index, int columns, int scrollRow) {
        return BORDER + (index / columns - scrollRow) * SLOT_HEIGHT;
    }

    /** 物品格在侧栏内的 x。 */
    public static int slotItemX(int index, int columns) {
        return slotElementX(index, columns) + SLOT_ITEM_INSET_X;
    }

    /** 物品格在侧栏内的 y。 */
    public static int slotItemY(int index, int columns, int scrollRow) {
        return slotElementY(index, columns, scrollRow) + SLOT_ITEM_INSET_Y;
    }

    /** 热进度条在侧栏内的 x。 */
    public static int heatBarX(int index, int columns) {
        return slotElementX(index, columns) + 1;
    }

    /** 热进度条在侧栏内的 y。 */
    public static int heatBarY(int index, int columns, int scrollRow) {
        return slotItemY(index, columns, scrollRow);
    }

    /** 最大可滚动行数。 */
    public static int maxScrollRow(int slotCount) {
        int columns = columns(slotCount);
        return Math.max(0, totalRows(slotCount, columns) - visibleRows(slotCount, columns));
    }

    /**
     * 该槽位当前是否落在可见区域内。
     * 不可见的槽位必须移出面板（而不是只画不到），否则玩家能把物品放进看不见的格子里。
     */
    public static boolean isSlotVisible(int index, int columns, int scrollRow) {
        int row = index / columns;
        return row >= scrollRow && row < scrollRow + MAX_VISIBLE_ROWS;
    }
}
