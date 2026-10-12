package com.soulforge.soulforge.smeltery.fuel;

import com.soulforge.soulforge.fluid.SoulForgeFluids;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 炉体燃料表：每种可燃液体一份燃烧参数。
 *
 * <p>四个字段的含义与 TiC3 的 {@code melting_fuel} 配方一一对应，缺一不可：
 * <ul>
 *   <li>{@code amount} —— 一次抽走多少 mB。抽够一份才开始烧，所以燃料罐被拆掉不会立刻熄火。</li>
 *   <li>{@code duration} —— 一份燃料折算的燃烧 tick 数。</li>
 *   <li>{@code temperature} —— 燃烧期间炉体温度，决定能熔化哪些配方。</li>
 *   <li>{@code rate} —— 每 tick 推进的熔炼进度，与 {@code duration} 一起决定实际熔炼速度。</li>
 * </ul>
 *
 * <p>只写温度、把抽取量和燃烧时间写死，会让不同燃料的性价比完全失真：
 * 一度温度一份燃料的模型下，温度越高越划算，低阶燃料变成纯亏本。
 */
public final class FuelRegistry {
    /** 一种燃料的燃烧参数。 */
    public record FuelInfo(int amount, int duration, int temperature, int rate) {
        /** 这份燃料能否供给定温度上限的炉体使用。 */
        public boolean usableAt(int maxTemperature) {
            return temperature <= maxTemperature;
        }
    }

    private static final Map<Fluid, FuelInfo> FUELS = new HashMap<>();

    static {
        // 数值梯度对齐 TiC3 的岩浆(1000/100/10) → 烈焰之血(1500/150/15)
        register(SoulForgeFluids.BLAZING_BLOOD, 50, 150, 1500, 15);
        register(SoulForgeFluids.ACTIVATED_BLAZING_BLOOD, 50, 200, 3500, 25);
        register(SoulForgeFluids.IONIZED_BLAZING_BLOOD, 50, 250, 5000, 35);
        register(SoulForgeFluids.PLASMA_BLAZING_BLOOD, 50, 300, 10000, 50);
        register(SoulForgeFluids.ETERNAL_BLAZING_BLOOD, 50, 500, 100_000_000, 100);
    }

    private FuelRegistry() {
    }

    private static void register(SoulForgeFluids.FluidSet fluid, int amount, int duration, int temperature, int rate) {
        FUELS.put(fluid.still().get(), new FuelInfo(amount, duration, temperature, rate));
    }

    /** 该流体作为燃料的参数；不是燃料时返回 null。 */
    public static FuelInfo get(Fluid fluid) {
        return FUELS.get(fluid);
    }

    /** 该流体堆作为燃料的参数；空堆或非燃料时返回 null。 */
    public static FuelInfo get(FluidStack stack) {
        return stack.isEmpty() ? null : get(stack.getFluid());
    }

    /** 该流体的燃烧温度，非燃料返回 0。 */
    public static int getTemperature(FluidStack stack) {
        FuelInfo info = get(stack);
        return info == null ? 0 : info.temperature();
    }

    /** 该流体是否登记为燃料。 */
    public static boolean isFuel(Fluid fluid) {
        return FUELS.containsKey(fluid);
    }
}
