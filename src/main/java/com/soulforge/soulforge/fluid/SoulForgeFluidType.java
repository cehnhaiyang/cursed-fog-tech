package com.soulforge.soulforge.fluid;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 锻魂流体的公共流体类型。
 *
 * <p>必须覆写 {@link #move}：26.3 起非原版流体的实体移动**只**经由这个钩子。
 * NeoForge 在 {@code LivingEntity.travel} 里的写法是
 * <pre>
 * if (!isInFluidMatching(e, (e, type, _) -&gt; !type.isVanilla() &amp;&amp; e.moveInFluid(type, input, gravity)))
 *     if (isInWater()) travelInWater(...);
 *     else if (isInLava()) travelInLava(...);
 * </pre>
 * 也就是说：实体浸在自定义流体里、而 {@code move} 返回 false 时，
 * 水中与岩浆两个分支都不会执行，实体每 tick 都被完全冻结——玩家在里面一步都动不了。
 * 覆写并返回 true 才会接管移动。
 */
public class SoulForgeFluidType extends FluidType {
    /** 高温熔融液体的黏滞程度：明显比水慢，但仍能自由走动。 */
    private static final double MOLTEN_DRAG = 0.6;
    /** 下沉速度：熔融金属不提供浮力，玩家会缓慢下沉。 */
    private static final double MOLTEN_GRAVITY_SCALE = 0.02;
    /** 输入加速度，与水中的 0.02F 同级。 */
    private static final float MOLTEN_SPEED = 0.02F;

    private final int tintColor;

    public SoulForgeFluidType(Properties properties, int tintColor) {
        super(properties);
        this.tintColor = tintColor;
    }

    /** 获取流体染色值（ARGB 格式） */
    public int getTintColor() {
        return tintColor;
    }

    @Override
    public boolean move(LivingEntity entity, Vec3 movementVector, double gravity) {
        // 结构对齐原版水中移动：先按输入加速，再施加阻力与轻微下沉，最后位移。
        // 与水的差别只有常数——阻力更大、不提供浮力，所以"能走但很沉"。
        entity.moveRelative(MOLTEN_SPEED, movementVector);
        Vec3 delta = entity.getDeltaMovement();
        entity.setDeltaMovement(delta.x * MOLTEN_DRAG, delta.y - gravity * MOLTEN_GRAVITY_SCALE, delta.z * MOLTEN_DRAG);
        entity.move(MoverType.SELF, entity.getDeltaMovement());
        entity.setDeltaMovement(entity.getDeltaMovement().scale(MOLTEN_DRAG));
        return true;
    }
}
