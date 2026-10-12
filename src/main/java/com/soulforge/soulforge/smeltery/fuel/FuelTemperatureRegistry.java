package com.soulforge.soulforge.smeltery.fuel;

import com.soulforge.soulforge.fluid.SoulForgeFluids;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps fuel fluids to their combustion temperatures (Celsius).
 */
public class FuelTemperatureRegistry {
    private static final Map<Fluid, Integer> TEMPERATURES = new HashMap<>();

    static {
        register(SoulForgeFluids.BLAZING_BLOOD.still().get(), 1500);
        register(SoulForgeFluids.ACTIVATED_BLAZING_BLOOD.still().get(), 3500);
        register(SoulForgeFluids.IONIZED_BLAZING_BLOOD.still().get(), 5000);
        register(SoulForgeFluids.PLASMA_BLAZING_BLOOD.still().get(), 10000);
        register(SoulForgeFluids.ETERNAL_BLAZING_BLOOD.still().get(), 100_000_000);
    }

    private static void register(Fluid fluid, int temperature) {
        TEMPERATURES.put(fluid, temperature);
    }

    /** Gets the temperature for a fluid, or 0 if the fluid is not a registered fuel. */
    public static int getTemperature(Fluid fluid) {
        return TEMPERATURES.getOrDefault(fluid, 0);
    }

    /** Gets the temperature for a FluidStack, or 0 if it is not a registered fuel. */
    public static int getTemperature(FluidStack stack) {
        return stack.isEmpty() ? 0 : getTemperature(stack.getFluid());
    }

    /** True if the fluid is registered as a fuel. */
    public static boolean isFuel(Fluid fluid) {
        return TEMPERATURES.containsKey(fluid);
    }
}
