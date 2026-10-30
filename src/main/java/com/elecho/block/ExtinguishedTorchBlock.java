package com.elecho.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.block.ShapeContext;

/**
 * Antorcha apagada (de pie). FLICKER=true significa "parpadeo": se vuelve a encender sola
 * a los pocos ticks. Se vuelve a encender con pedernal y acero (clic derecho).
 */
public class ExtinguishedTorchBlock extends Block {
    public static final MapCodec<ExtinguishedTorchBlock> CODEC = createCodec(ExtinguishedTorchBlock::new);
    public static final BooleanProperty FLICKER = BooleanProperty.of("flicker");
    protected static final VoxelShape SHAPE = Block.createCuboidShape(6.0, 0.0, 6.0, 10.0, 10.0, 10.0);

    public ExtinguishedTorchBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FLICKER, false));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FLICKER); }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) { return SHAPE; }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return Block.sideCoversSmallSquare(world, pos.down(), Direction.UP);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canPlaceAt(state, world, pos) ? Blocks.AIR.getDefaultState() : state;
    }

    /** Estado de antorcha encendida equivalente (la pared lo sobrescribe). */
    protected BlockState litState(BlockState state) { return Blocks.TORCH.getDefaultState(); }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(FLICKER)) {
            world.setBlockState(pos, litState(state));
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 0.4f, 1.6f);
        }
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isOf(Items.FLINT_AND_STEEL) || stack.isOf(Items.FIRE_CHARGE) || stack.isOf(com.elecho.ModItems.LIGHTER)) {
            if (!world.isClient) {
                world.setBlockState(pos, litState(state));
                world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                if (stack.isOf(Items.FLINT_AND_STEEL) || stack.isOf(com.elecho.ModItems.LIGHTER)) stack.damage(1, player, net.minecraft.entity.LivingEntity.getSlotForHand(hand));
                else if (!player.isCreative()) stack.decrement(1);
            }
            return ItemActionResult.success(world.isClient);
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(6) == 0) {
            world.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
        }
    }
}
