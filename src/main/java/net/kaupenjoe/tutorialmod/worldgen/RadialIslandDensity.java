
package net.kaupenjoe.tutorialmod.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.SimpleFunction;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;

public record RadialIslandDensity() implements SimpleFunction {

    public static final MapCodec<RadialIslandDensity> CODEC =
            MapCodec.unit(new RadialIslandDensity());


    @Override
    public double compute(FunctionContext context) {
        return 1000.0;
    }

    @Override
    public double minValue() {
        return -1000.0;
    }

    @Override
    public double maxValue() {
        return 1000.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return new KeyDispatchDataCodec<>(CODEC);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(this);
    }
}
