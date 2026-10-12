package com.soulforge.soulforge.smeltery.module;

import com.soulforge.soulforge.smeltery.SmelteryTier;
import com.soulforge.soulforge.smeltery.fuel.FuelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 燃料模块：从结构内的燃料储罐方块抽取燃料液体。
 *
 * <p>设计依据 README 第 20 行"置入燃料储罐的燃料温度过高……"——燃料来源是
 * 炉体结构里的储罐方块，控制器本体**不**存燃料。控制器对外暴露的流体能力是熔融罐。
 *
 * <p>温度是"取用即烧"模型：从储罐抽走完整的一份燃料后折算成若干 tick 的燃烧时间，
 * 燃烧期间温度与速率保持不变；烧完再抽下一份。这样燃料罐被拆掉不会立刻熄火，
 * 也不会每 tick 都去扫一遍所有储罐。
 *
 * <p>世界实例按调用传入而非构造时注入：方块实体构造阶段 {@code level} 尚未赋值。
 * 上次取到燃料的储罐位置会被记住（{@link #lastPos}），下个 tick 优先从同一格抽，
 * 避免每次都在整圈炉壁里重新找。
 */
public class FuelModule {
    private static final String TAG_FUEL = "fuel";
    private static final String TAG_FUEL_QUALITY = "fuel_quality";
    private static final String TAG_TEMPERATURE = "temperature";
    private static final String TAG_RATE = "rate";
    private static final String TAG_LAST_POS = "last_pos";

    /** 尚未找到燃料储罐时的哨兵位置，Y 取一个世界里不可能出现的值。 */
    private static final BlockPos NULL_POS = new BlockPos(0, Short.MIN_VALUE, 0);

    private final SmelteryTier tier;

    /** 剩余燃烧 tick 数。 */
    private int fuel;
    /** 当前这一份燃料的总燃烧时长，界面用它把火焰图标按比例画高。 */
    private int fuelQuality;
    private int temperature;
    private int rate;
    private BlockPos lastPos = NULL_POS;

    public FuelModule(SmelteryTier tier) {
        this.tier = tier;
    }

    public boolean hasFuel() {
        return fuel > 0;
    }

    /** 剩余燃烧 tick 数。 */
    public int getFuel() {
        return fuel;
    }

    /** 当前这份燃料的总燃烧时长，0 表示没在烧。 */
    public int getFuelQuality() {
        return fuelQuality;
    }

    public int getTemperature() {
        return temperature;
    }

    /** 每 tick 推进的熔炼进度，由燃料品质决定。 */
    public int getRate() {
        return rate;
    }

    public void decreaseFuel(int amount) {
        fuel = Math.max(0, fuel - amount);
        if (fuel == 0) {
            // 烧完立刻熄火，温度与速率一并归零，否则会凭空维持高温
            temperature = 0;
            rate = 0;
        }
    }

    /**
     * 从结构内的储罐寻找并消耗一份燃料。
     *
     * @param level   世界
     * @param tanks   结构内的储罐位置
     * @param consume true 时真正抽取，false 时只探测温度
     * @return 找到的燃料温度，0 表示没有可用燃料
     */
    public int findFuel(Level level, List<BlockPos> tanks, boolean consume) {
        // 优先复用上次取到燃料的那一格，它最可能还有存货
        if (lastPos.getY() != NULL_POS.getY() && tanks.contains(lastPos)) {
            int cached = tryTank(level, lastPos, consume);
            if (cached > 0) {
                return cached;
            }
        }
        for (BlockPos pos : tanks) {
            if (pos.equals(lastPos)) {
                continue;
            }
            int found = tryTank(level, pos, consume);
            if (found > 0) {
                lastPos = pos;
                return found;
            }
        }
        return 0;
    }

    /** 尝试从单个储罐抽一份燃料。 */
    private int tryTank(Level level, BlockPos pos, boolean consume) {
        if (!level.hasChunkAt(pos)) {
            return 0;
        }
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
        if (handler == null) {
            return 0;
        }
        for (int i = 0; i < handler.size(); i++) {
            FluidStack fluid = handler.getResource(i).toStack(handler.getAmountAsInt(i));
            FuelRegistry.FuelInfo info = FuelRegistry.get(fluid);
            if (info == null) {
                continue;
            }
            // 炉体材质扛不住的温度不能作为本炉燃料，否则等于绕过了炉体等级限制
            if (!info.usableAt(tier.getMaxTemperature())) {
                continue;
            }
            if (!consume) {
                return info.temperature();
            }
            // 抽不满一整份就跳过这一格：半份燃料烧不出对应的时长
            if (fluid.getAmount() < info.amount()) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                int drained = handler.extract(i, handler.getResource(i), info.amount(), tx);
                if (drained < info.amount()) {
                    continue;
                }
                tx.commit();
            }
            fuel += info.duration();
            fuelQuality = info.duration();
            temperature = info.temperature();
            rate = info.rate();
            return info.temperature();
        }
        return 0;
    }

    /* 界面与桶交互用的储罐访问 */

    /**
     * 燃料表上要显示的流体与总量。
     *
     * <p>优先显示上次取用的那一格；它空了就退回第一格有液体的储罐，否则玩家刚灌满
     * 另一个罐子时表面会一直空着。把所有同种燃料的储罐加在一起，显示的才是真实储备。
     *
     * @return 长度为 3 的数组：流体、总量、总容量；没有燃料时流体为空
     */
    public FuelDisplay getDisplay(Level level, List<BlockPos> tanks) {
        FluidStack shown = FluidStack.EMPTY;
        int total = 0;
        int capacity = 0;
        BlockPos primary = lastPos;
        for (BlockPos pos : tanks) {
            ResourceHandler<FluidResource> handler = handlerAt(level, pos);
            if (handler == null || handler.size() == 0) {
                continue;
            }
            FluidStack fluid = handler.getResource(0).toStack(handler.getAmountAsInt(0));
            int tankCapacity = handler.getCapacityAsInt(0, handler.getResource(0));
            if (fluid.isEmpty()) {
                // 空罐只贡献容量，用来把液位画得更低
                capacity += tankCapacity;
                continue;
            }
            if (shown.isEmpty()) {
                if (!pos.equals(primary) && !primary.equals(NULL_POS)) {
                    // 上次用的那格还有货就等它，先记下这一格作为兜底
                    continue;
                }
                shown = fluid;
            }
            if (FluidStack.isSameFluidSameComponents(shown, fluid)) {
                total += fluid.getAmount();
                capacity += tankCapacity;
            }
        }
        return new FuelDisplay(shown, total, capacity);
    }

    /** 界面显示用的燃料摘要。 */
    public record FuelDisplay(FluidStack fluid, int amount, int capacity) {
        public boolean isEmpty() {
            return fluid.isEmpty() || amount <= 0;
        }
    }

    /**
     * 往结构内的燃料储罐灌液体，返回实际接收量。
     * 供桶交互使用；灌的是不是"合法燃料"不做限制，与 TiC3 一致。
     *
     * @param simulate true 时只算能灌多少，不改动储罐
     */
    public int fill(Level level, List<BlockPos> tanks, FluidStack resource, boolean simulate) {
        if (resource.isEmpty()) {
            return 0;
        }
        int totalFilled = 0;
        FluidStack remaining = resource.copy();
        for (BlockPos pos : tanks) {
            if (remaining.isEmpty()) {
                break;
            }
            ResourceHandler<FluidResource> handler = handlerAt(level, pos);
            if (handler == null) {
                continue;
            }
            int accepted = insertInto(handler, remaining, simulate);
            if (accepted > 0) {
                totalFilled += accepted;
                remaining.shrink(accepted);
            }
        }
        return totalFilled;
    }

    /** 从结构内的燃料储罐抽液体，返回实际抽出量。 */
    public FluidStack drain(Level level, List<BlockPos> tanks, int maxDrain, boolean simulate) {
        FluidStack drained = FluidStack.EMPTY;
        int wanted = maxDrain;
        for (BlockPos pos : tanks) {
            if (wanted <= 0) {
                break;
            }
            ResourceHandler<FluidResource> handler = handlerAt(level, pos);
            if (handler == null || handler.size() == 0) {
                continue;
            }
            FluidStack fluid = handler.getResource(0).toStack(handler.getAmountAsInt(0));
            if (fluid.isEmpty()) {
                continue;
            }
            int amount = Math.min(wanted, fluid.getAmount());
            if (amount <= 0) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                int extracted = handler.extract(0, handler.getResource(0), amount, tx);
                if (extracted <= 0) {
                    continue;
                }
                if (!simulate) {
                    tx.commit();
                }
                amount = extracted;
            }
            if (drained.isEmpty()) {
                drained = fluid.copyWithAmount(amount);
            } else {
                drained.grow(amount);
            }
            wanted -= amount;
        }
        return drained;
    }

    @Nullable
    private ResourceHandler<FluidResource> handlerAt(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        return level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
    }

    /**
     * 往单个储罐里灌，返回接收量。
     *
     * <p>不提交事务就等于回滚，因此模拟与真正灌入共用同一条路径，
     * 唯一的区别是要不要 {@code commit()}。
     */
    private int insertInto(ResourceHandler<FluidResource> handler, FluidStack resource, boolean simulate) {
        if (handler.size() <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            // 多槽储罐（燃料罐只有一个槽）逐个试，取第一个能接收的
            for (int i = 0; i < handler.size(); i++) {
                int accepted = handler.insert(i, FluidResource.of(resource), resource.getAmount(), tx);
                if (accepted > 0) {
                    if (!simulate) {
                        tx.commit();
                    }
                    return accepted;
                }
            }
        }
        return 0;
    }

    /* 存档 */

    public void serialize(ValueOutput output) {
        output.putInt(TAG_FUEL, fuel);
        output.putInt(TAG_FUEL_QUALITY, fuelQuality);
        output.putInt(TAG_TEMPERATURE, temperature);
        output.putInt(TAG_RATE, rate);
        if (lastPos.getY() != NULL_POS.getY()) {
            output.putIntArray(TAG_LAST_POS, new int[]{lastPos.getX(), lastPos.getY(), lastPos.getZ()});
        }
    }

    public void deserialize(ValueInput input) {
        fuel = input.getIntOr(TAG_FUEL, 0);
        fuelQuality = input.getIntOr(TAG_FUEL_QUALITY, 0);
        temperature = input.getIntOr(TAG_TEMPERATURE, 0);
        rate = input.getIntOr(TAG_RATE, 0);
        input.getIntArray(TAG_LAST_POS).ifPresent(arr -> {
            if (arr.length == 3) {
                lastPos = new BlockPos(arr[0], arr[1], arr[2]);
            }
        });
    }
}
