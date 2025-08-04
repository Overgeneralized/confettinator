package dev.overgeneralized.confettinator;

import com.mojang.serialization.MapCodec;
import dev.doublekekse.confetti.Confetti;
import dev.doublekekse.confetti.math.Vec3Dist;
import dev.doublekekse.confetti.packet.ExtendedParticlePacket;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.*;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfettinatorBlock extends BlockWithEntity {
    public static final BooleanProperty TRIGGERED = Properties.TRIGGERED;
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    private static final Map<Integer, Integer> INT_TO_FIREWORK_COLOR = new HashMap<>(Arrays.stream(DyeColor.values()).collect(Collectors.toMap(DyeColor::getIndex, DyeColor::getFireworkColor)));

    protected ConfettinatorBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(TRIGGERED, false).with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends ConfettinatorBlock> getCodec() {
        return createCodec(ConfettinatorBlock::new);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ConfettinatorEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);

            if (screenHandlerFactory != null) {
                player.openHandledScreen(screenHandlerFactory);
            }
        }
        return ActionResult.SUCCESS;
    }

    protected void dispense(ServerWorld world, BlockState state, BlockPos pos) {
        ConfettinatorEntity confettinatorBlockEntity = world.getBlockEntity(pos, ConfettinatorMod.CONFETTINATOR_ENTITY).orElse(null);
        if (confettinatorBlockEntity == null) return;

        DefaultedList<ItemStack> inventory = confettinatorBlockEntity.getItems();
        int index = -1;
        for (int i = 0; i < 9; i++) {
            if (!inventory.get(i).isEmpty()) {
                index = i;
                break;
            }
        }

        world.emitGameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Emitter.of(confettinatorBlockEntity.getCachedState()));
        if (index < 0) {
            world.syncWorldEvent(1001, pos, 0);
            return;
        }

        ItemStack itemStack = inventory.get(index);
        Item item = itemStack.getItem();
        if (item == Items.FIREWORK_ROCKET) {
            int calibrationPower = getCalibrationPower(world, pos, state);
            ItemStack projectileItemStack = itemStack;
            ProjectileItem projectileItem = (ProjectileItem) item;
            if (calibrationPower != 0) {
                FireworksComponent fireworksComponent = itemStack.get(DataComponentTypes.FIREWORKS);
                if (fireworksComponent == null) return;
                List<FireworkExplosionComponent> explosions = fireworksComponent.explosions();
                List<FireworkExplosionComponent> newExplosionList = List.of();
                if (!explosions.isEmpty()) {
                    FireworkExplosionComponent explosion = explosions.getFirst();
                    newExplosionList = List.of(new FireworkExplosionComponent(explosion.shape(), IntList.of(INT_TO_FIREWORK_COLOR.get(calibrationPower)), IntList.of(), explosion.hasTrail(), explosion.hasTwinkle()));
                }
                ComponentMap components = ComponentMap.builder().add(DataComponentTypes.FIREWORKS,
                        new FireworksComponent(fireworksComponent.flightDuration(), newExplosionList)
                ).build();
                projectileItemStack = new ItemStack(Items.FIREWORK_ROCKET, 1);
                projectileItemStack.applyComponentsFrom(components);
                projectileItem = (ProjectileItem) projectileItemStack.getItem();
            }
            ProjectileEntity.spawnWithVelocity(projectileItem.createEntity(world, projectileItem.getProjectileSettings().positionFunction().getDispensePosition(new BlockPointer(world, pos, null, null), Direction.UP), projectileItemStack, Direction.UP), world, projectileItemStack, 0, 1, 0, projectileItem.getProjectileSettings().power(), projectileItem.getProjectileSettings().uncertainty());
        } else if (item == Items.FIREWORK_STAR) {
            int calibrationPower = getCalibrationPower(world, pos, state);
            FireworkExplosionComponent explosion = itemStack.get(DataComponentTypes.FIREWORK_EXPLOSION);
            assert explosion != null;
            if (calibrationPower != 0) {
                explosion = new FireworkExplosionComponent(explosion.shape(), IntList.of(INT_TO_FIREWORK_COLOR.get(calibrationPower)), IntList.of(), explosion.hasTrail(), explosion.hasTwinkle());
            }
            ComponentMap components = ComponentMap.builder().add(DataComponentTypes.FIREWORKS, new FireworksComponent(1, List.of(explosion))).build();
            ItemStack projectileItemStack = new ItemStack(Items.FIREWORK_ROCKET, 1);
            projectileItemStack.applyComponentsFrom(components);
            ProjectileItem projectileItem = (ProjectileItem) projectileItemStack.getItem();
            ProjectileEntity entity = ProjectileEntity.spawnWithVelocity(projectileItem.createEntity(world, projectileItem.getProjectileSettings().positionFunction().getDispensePosition(new BlockPointer(world, pos, null, null), Direction.UP), projectileItemStack, Direction.UP), world, projectileItemStack, 0, 1, 0, projectileItem.getProjectileSettings().power(), projectileItem.getProjectileSettings().uncertainty());
            ((FireworkRocketEntity) entity).explodeAndRemove(world);
        } else if (item instanceof DyeItem) {
            DyeColor color = ((DyeItem) item).getColor();
            for (ServerPlayerEntity player : world.getPlayers()) {
                ServerPlayNetworking.send(player, new ExtendedParticlePacket(new Vec3Dist(pos.up().toCenterPos(), new Vec3d(0, 0.2, 0)), new Vec3Dist(new Vec3d(0, 0.8, 0), new Vec3d(0.1, 0.1, 0.1)), 500, false, ConfettinatorMod.COLOR_CONFETTI_PAR_LIST[color.getIndex()]));
            }
        } else if (item == Items.PAPER) {
            int calibrationPower = getCalibrationPower(world, pos, state);
            for (ServerPlayerEntity player : world.getPlayers()) {
                ServerPlayNetworking.send(player, new ExtendedParticlePacket(new Vec3Dist(pos.up().toCenterPos(), new Vec3d(0, 0.2, 0)), new Vec3Dist(new Vec3d(0, 0.8, 0), new Vec3d(0.1, 0.1, 0.1)), 500, false, ConfettinatorMod.COLOR_CONFETTI_PAR_LIST[calibrationPower]));
            }
        } else if (item == Items.GLOWSTONE_DUST) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                ServerPlayNetworking.send(player, new ExtendedParticlePacket(new Vec3Dist(pos.up().toCenterPos(), new Vec3d(0, 0.2, 0)), new Vec3Dist(new Vec3d(0, 0.8, 0), new Vec3d(0.1, 0.1, 0.1)), 500, false, Confetti.CONFETTI));
            }
        } else if (item == Items.SPLASH_POTION) {

        } else {
            world.syncWorldEvent(1001, pos, 0);
            return;
        }
        world.syncWorldEvent(1000, pos, 0);
        itemStack.decrement(1);
        inventory.set(index, itemStack);
    }

    private int getCalibrationPower(World world, BlockPos pos, BlockState state) {
        Direction direction = state.get(FACING);
        return world.getEmittedRedstonePower(pos.offset(direction), direction);
    }

    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        boolean receivingPower = isReceivingRedstonePower(world, pos, state);
        boolean currentlyPowered = state.get(TRIGGERED);
        if (receivingPower && !currentlyPowered) {
            world.scheduleBlockTick(pos, this, 4);
            world.setBlockState(pos, state.with(TRIGGERED, true), 2);
        } else if (!receivingPower && currentlyPowered) {
            world.setBlockState(pos, state.with(TRIGGERED, false), 2);
        }
    }

    private boolean isReceivingRedstonePower(World world, BlockPos pos, BlockState state) {
        for (Direction direction : DIRECTIONS) {
            if (direction != state.get(FACING) && world.getEmittedRedstonePower(pos.offset(direction), direction) > 0) {
                return true;
            }
        }
        return false;
    }

    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        this.dispense(world, state, pos);
    }

    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        ItemScatterer.onStateReplaced(state, world, pos);
        ConfettinatorEntity blockEntity = world.getBlockEntity(pos, ConfettinatorMod.CONFETTINATOR_ENTITY).orElse(null);
        if (blockEntity == null) return;
        ItemScatterer.spawn(world, pos, blockEntity.getItems());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TRIGGERED, FACING);
    }

    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    // I have no idea what these two are for, but I added them
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected boolean emitsRedstonePower(BlockState state) {
        return true; // so that redstone wires connect to the place where it gets calibrated
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
    }
}
