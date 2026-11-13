
package net.kaupenjoe.tutorialmod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RiftMarkerBlock extends Block {

    public RiftMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // Убираем форму, по которой игрок может навести прицел.
    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return Shapes.empty();
    }

    // Убираем форму взаимодействия.
    @Override
    public VoxelShape getInteractionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos
    ) {
        return Shapes.empty();
    }

    // Убираем визуальную форму.
    @Override
    public VoxelShape getVisualShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return Shapes.empty();
    }
}
