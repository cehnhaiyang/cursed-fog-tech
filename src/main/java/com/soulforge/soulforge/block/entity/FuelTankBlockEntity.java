package com.soulforge.soulforge.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * Simple fuel tank block entity. Holds a fluid tank and exposes it via capability.
 * The items-blocks-fluids teammate may replace or extend this class.
 */
public class FuelTankBlockEntity extends BlockEntity {
    private static final int TANK_CAPACITY = 4000;
    private static final String TAG_TANK = "tank";

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, TANK_CAPACITY) {
        @Override
        protected void onContentsChanged(int index, net.neoforged.neoforge.fluids.FluidStack previousContents) {
            setChanged();
        }
    };

    public FuelTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public FuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(SoulForgeBlockEntityTypes.NETHERITE_FUEL_TANK.get(), pos, state);
    }

    public FluidStacksResourceHandler getTank() {
        return tank;
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child(TAG_TANK).ifPresent(tank::deserialize);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child(TAG_TANK));
    }
}
