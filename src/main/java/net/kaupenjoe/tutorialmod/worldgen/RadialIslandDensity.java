package net.kaupenjoe.tutorialmod.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.FunctionContext;
import net.minecraft.world.level.levelgen.DensityFunction.SimpleFunction;
import net.minecraft.world.level.levelgen.DensityFunction.Visitor;
import net.minecraft.util.KeyDispatchDataCodec;
import com.mojang.serialization.MapCodec;

public record RadialIslandDensity() implements SimpleFunction {

    public static final MapCodec<RadialIslandDensity> CODEC =
            MapCodec.unit(new RadialIslandDensity());

    @Override
    public double compute(FunctionContext context) {
        double x = context.blockX();
        double y = context.blockY();
        double z = context.blockZ();

        double distance = Math.sqrt(x * x + z * z);


        double hills =
                3.0 * Math.sin(x * 0.012) * Math.cos(z * 0.014)
                        + 1.5 * Math.sin((x + z) * 0.018);



        double surface;

        if (distance < 105.0) {
            surface = 78.0 + hills;
        } else if (distance < 145.0) {
            double t = (distance - 105.0) / 40.0;
            t = t * t * (3.0 - 2.0 * t);
            surface = (78.0 + hills) * (1.0 - t) + 62.0 * t;
        } else if (distance < 260.0) {
            double t = (distance - 145.0) / 115.0;
            t = t * t * (3.0 - 2.0 * t);
            surface = 62.0 * (1.0 - t) + 34.0 * t;
        } else {
            surface = 34.0;
        }

        return (surface - y) / 8.0;
    }

    @Override
    public double minValue() {
        return -50.0;
    }

    @Override
    public double maxValue() {
        return 20.0;
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