package com.elecho.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.World;
import net.minecraft.util.math.random.Random;
import net.minecraft.particle.ParticleTypes;

public class ExtinguishedWallTorchBlock extends ExtinguishedTorchBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public ExtinguishedWallTorchBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FLICKER, false).with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return switch (state.get(FACING)) {
            case NORTH -> Block.createCuboidShape(5.5, 3.0, 11.0, 10.5, 13.0, 16.0);
            case SOUTH -> Block.createCuboidShape(5.5, 3.0, 0.0, 10.5, 13.0, 5.0);
            case WEST -> Block.createCuboidShape(11.0, 3.0, 5.5, 16.0, 13.0, 10.5);
            default -> Block.createCuboidShape(0.0, 3.0, 5.5, 5.0, 13.0, 10.5);
        };
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction face = state.get(FACING);
        BlockPos support = pos.offset(face.getOpposite());
        return world.getBlockState(support).isSideSolidFullSquare(world, support, face);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState base = getDefaultState();
        WorldView world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        for (Direction d : ctx.getPlacementDirections()) {
            if (d.getAxis().isHorizontal()) {
                BlockState s = base.with(FACING, d.getOpposite());
                if (s.canPlaceAt(world, pos)) return s;
            }
        }
        return null;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return direction.getOpposite() == state.get(FACING) && !state.canPlaceAt(world, pos)
                ? Blocks.AIR.getDefaultState() : state;
    }

    @Override
    protected BlockState litState(BlockState state) {
        return Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, state.get(FACING));
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(6) == 0) {
            Direction d = state.get(FACING);
            world.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + 0.27 * d.getOffsetX(),
                    pos.getY() + 0.9, pos.getZ() + 0.5 + 0.27 * d.getOffsetZ(), 0.0, 0.03, 0.0);
        }
    }
}
