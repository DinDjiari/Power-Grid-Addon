/*
 * Copyright 2026 Tomrox GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified from Create: Power Grid by patryk3211, Apache-2.0
 * (multiblock handling follows the removed HvTransformerBlock / HvSwitchBlock).
 */
package com.tomrox.pghv.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.tomrox.pghv.block.entity.HvCoolingFanBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.tomrox.pghv.block.entity.CoolingFanTopBlockEntity;
import com.tomrox.pghv.registry.PghvBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.patryk3211.powergrid.electricity.transformer.TransformerMediumBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.PlayerUtilities;

/*
 * Transformer cooling fan: a thin 2x2x1 panel (1/3 block thick) in front of / behind a Power Grid transformer.
 *
 * Block states: facing (front direction, away from the transformer), part (0..3), slot (0..2).
 *   part bit 0 = one block to the right (seen from the front: facing.getCounterClockWise()), bit 1 = one block up.
 *   part 0 (lower left) holds the block entity.
 *   slot = position of the panel inside the cell along the facing axis (0 front, 1 middle, 2 back).
 *
 * Local coordinates below are for facing = NORTH (front = -z), in 1/16 units; they are rotated for other facings.
 *   panel:  z0 .. z0 + 16/3 with z0 = slot * 16/3
 *   pins:   both on part 0 (lower left seen from the front): plus x 11..14, minus x 6..9; y 1..4, z (z0-p)..(z0+2);
 *           p = min(3, z0). Terminal 0 = plus (red), terminal 1 = minus (blue).
 */
public class HvCoolingFanBlock extends ElectricBlock implements IBE<HvCoolingFanBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    public static final IntegerProperty SLOT = IntegerProperty.create("slot", 0, 2);

    public static final double SLOT_SIZE = 16.0 / 3.0;

    /** [facing 2D index][slot] */
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][3];
    /** [facing 2D index][slot][0 = plus, 1 = minus], both on part 0 */
    private static final TerminalBoundingBox[][][] TERMINALS = new TerminalBoundingBox[4][3][2];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Rotation rotation = rotationFromNorth(facing);
            for (int slot = 0; slot < 3; ++slot) {
                double z0 = slot * SLOT_SIZE;
                SHAPES[facing.get2DDataValue()][slot] = rotateBox(new AABB(0, 0, z0 / 16.0, 1, 1, (z0 + SLOT_SIZE) / 16.0), rotation);

                double pin = Math.min(3.0, z0);
                TERMINALS[facing.get2DDataValue()][slot][0] =
                        new TerminalBoundingBox(IDecoratedTerminal.POSITIVE, 11, 1, z0 - pin, 14, 4, z0 + 2)
                                .withColor(IDecoratedTerminal.RED).rotateAroundY(rotation);
                TERMINALS[facing.get2DDataValue()][slot][1] =
                        new TerminalBoundingBox(IDecoratedTerminal.NEGATIVE, 6, 1, z0 - pin, 9, 4, z0 + 2)
                                .withColor(IDecoratedTerminal.BLUE).rotateAroundY(rotation);
            }
        }
    }

    /** Variant: false = andesite fan (fixed speed menu), true = brass fan (automatic, target temperature menu). */
    private final boolean brass;

    public HvCoolingFanBlock(Properties properties) {
        this(properties, false);
    }

    public HvCoolingFanBlock(Properties properties, boolean brass) {
        super(properties);
        this.brass = brass;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, 0)
                .setValue(SLOT, 2));
    }

    /** True for the brass variant (pghv:brass_cooling_fan). */
    public boolean isBrass() {
        return brass;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, PART, SLOT);
    }

    // ---- geometry helpers ----

    /** Rotation that turns a NORTH-oriented model into the given facing (clockwise seen from above). */
    public static Rotation rotationFromNorth(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Rotates a box given in block-local units (0..1) around the block centre; same maths as TerminalBoundingBox. */
    private static VoxelShape rotateBox(AABB b, Rotation rotation) {
        AABB r = switch (rotation) {
            case CLOCKWISE_90 -> new AABB(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX);
            case CLOCKWISE_180 -> new AABB(1 - b.maxX, b.minY, 1 - b.maxZ, 1 - b.minX, b.maxY, 1 - b.minZ);
            case COUNTERCLOCKWISE_90 -> new AABB(b.minZ, b.minY, 1 - b.maxX, b.maxZ, b.maxY, 1 - b.minX);
            default -> b;
        };
        return Shapes.create(r);
    }

    /** Cell of the given part, derived from the main cell. */
    public static BlockPos cellOf(BlockPos main, Direction facing, int part) {
        BlockPos p = main;
        if ((part & 1) != 0)
            p = p.relative(facing.getCounterClockWise());
        if ((part & 2) != 0)
            p = p.above();
        return p;
    }

    /** Position of the main part (part 0) for a block at pos. */
    public static BlockPos mainPos(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        int part = state.getValue(PART);
        BlockPos p = pos;
        if ((part & 1) != 0)
            p = p.relative(facing.getClockWise());
        if ((part & 2) != 0)
            p = p.below();
        return p;
    }

    private boolean isPartOfSameFan(BlockState state, BlockState other, int expectedPart) {
        return other.is(this)
                && other.getValue(FACING) == state.getValue(FACING)
                && other.getValue(PART) == expectedPart;
    }

    // ---- placement ----

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        BlockPos target = context.getClickedPos();
        BlockPos main = target;

        // Clicked onto a large face of a Power Grid medium transformer: snap the 2x2 panel onto the 2x2 face,
        // no matter which of the four transformer cells was clicked.
        BlockPos against = target.relative(clicked.getOpposite());
        BlockPos snapped = snapToTransformer(level, against, clicked);
        if (snapped != null)
            main = snapped;

        int targetPart = -1;
        for (int part = 0; part < 4; ++part) {
            BlockPos cell = cellOf(main, facing, part);
            if (cell.equals(target))
                targetPart = part;
            if (level.isOutsideBuildHeight(cell)
                    || !level.getWorldBorder().isWithinBounds(cell)
                    || !level.getBlockState(cell).canBeReplaced(context))
                return null;
        }
        if (targetPart < 0)
            return null;
        return defaultBlockState().setValue(FACING, facing).setValue(PART, targetPart).setValue(SLOT, 2);
    }

    /**
     * Main cell of a fan that covers the large 2x2 face of the medium transformer at {@code trafoPos},
     * mounted on side {@code side}; null if there is no medium transformer or the side is not a large face.
     * Medium transformer: part bit 0 = +1 along HORIZONTAL_AXIS, bit 1 = +1 up; part 0 = origin.
     */
    @Nullable
    public static BlockPos snapToTransformer(Level level, BlockPos trafoPos, Direction side) {
        if (!side.getAxis().isHorizontal())
            return null;
        BlockState trafo = level.getBlockState(trafoPos);
        if (!(trafo.getBlock() instanceof TransformerMediumBlock))
            return null;
        Direction.Axis axis = trafo.getValue(TransformerMediumBlock.HORIZONTAL_AXIS);
        if (side.getAxis() == axis)
            return null; // narrow end, not a large face
        int trafoPart = trafo.getValue(TransformerMediumBlock.PART);
        BlockPos origin = trafoPos.relative(axis, -(trafoPart & 1));
        if ((trafoPart & 2) != 0)
            origin = origin.below();
        // Fan part 1 lies at main + facing.getCounterClockWise(); both fan columns must cover the trafo columns.
        Direction right = side.getCounterClockWise();
        BlockPos main = right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? origin : origin.relative(axis, 1);
        return main.relative(side);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide)
            return;
        Direction facing = state.getValue(FACING);
        BlockPos main = mainPos(pos, state);
        for (int part = 0; part < 4; ++part) {
            BlockPos cell = cellOf(main, facing, part);
            if (!cell.equals(pos))
                level.setBlockAndUpdate(cell, state.setValue(PART, part));
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // If a face-adjacent part is missing, this part disappears as well.
        int part = state.getValue(PART);
        BlockPos main = mainPos(pos, state);
        Direction facing = state.getValue(FACING);
        for (int bit = 1; bit <= 2; ++bit) {
            int expected = part ^ bit;
            if (neighborPos.equals(cellOf(main, facing, expected)) && !isPartOfSameFan(state, neighborState, expected))
                return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Player breaks a secondary part: handle the drop here (main part only, tool check as for the main
        // part), then remove the main part without drops; the cascade removes the rest silently.
        if (!level.isClientSide && state.getValue(PART) != 0) {
            BlockPos main = mainPos(pos, state);
            BlockState mainState = level.getBlockState(main);
            if (mainState.is(this)) {
                if (!player.isCreative() && player.hasCorrectToolForDrops(mainState))
                    Block.dropResources(mainState, level, main, level.getBlockEntity(main), player, player.getMainHandItem());
                level.destroyBlock(main, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston); // IBE.onRemove (block entity cleanup)
        if (level.isClientSide || state.is(newState.getBlock()))
            return;
        // Remove the other parts. Only the main part drops (loot table: part=0), so destroy it with drops
        // and the rest without. Every removed part is already air, so the cascade terminates.
        Direction facing = state.getValue(FACING);
        BlockPos main = mainPos(pos, state);
        for (int part = 0; part < 4; ++part) {
            BlockPos cell = cellOf(main, facing, part);
            if (cell.equals(pos))
                continue;
            BlockState other = level.getBlockState(cell);
            if (isPartOfSameFan(state, other, part))
                level.destroyBlock(cell, part == 0);
        }
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(SLOT)];
    }

    // ---- slot switching ----

    /** Moves the panel to the next slot (0 -> 1 -> 2 -> 0) for all four parts. */
    public void cycleSlot(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide)
            return;
        Direction facing = state.getValue(FACING);
        BlockPos main = mainPos(pos, state);
        int next = (state.getValue(SLOT) + 1) % 3;
        for (int part = 0; part < 4; ++part) {
            BlockPos cell = cellOf(main, facing, part);
            BlockState current = level.getBlockState(cell);
            if (isPartOfSameFan(state, current, part)) {
                // UPDATE_KNOWN_SHAPE: no updateShape, the parts are changed one after the other.
                level.setBlock(cell, current.setValue(SLOT, next), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        ElectricBlock.refreshConnectionEntities(level, main); // move attached wire ends to the new pin positions
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()
                && player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
            cycleSlot(level, pos, state);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    // ---- terminals ----

    @Override
    public int terminalCount() {
        return 2;
    }

    @Override
    public ITerminalPlacement terminal(BlockState state, int index) {
        int part = state.getValue(PART);
        if (index < 0 || index > 1 || part != 0)
            return null; // both terminals sit on part 0, parts 1..3 have none
        return TERMINALS[state.getValue(FACING).get2DDataValue()][state.getValue(SLOT)][index];
    }

    @Override
    public ElectricBehaviour getBehaviour(Level level, BlockPos pos, BlockState state) {
        // The behaviour lives in the main part.
        return super.getBehaviour(level, mainPos(pos, state), state);
    }

    // ---- wrench ----

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        // Wrench: switch the panel position.
        cycleSlot(context.getLevel(), context.getClickedPos(), state);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level instanceof ServerLevel serverLevel) {
            if (player != null && PlayerUtilities.cancelBreak(level, pos, player))
                return InteractionResult.SUCCESS;
            BlockPos main = mainPos(pos, state);
            BlockState mainState = level.getBlockState(main);
            if (!mainState.is(this))
                return InteractionResult.SUCCESS;
            if (player != null && !player.isCreative()) {
                Block.getDrops(mainState, serverLevel, main, level.getBlockEntity(main), player, context.getItemInHand())
                        .forEach(stack -> player.getInventory().placeItemBackInInventory(stack));
            }
            mainState.spawnAfterBreak(serverLevel, main, ItemStack.EMPTY, true);
            level.destroyBlock(main, false); // onRemove removes the other parts
            IWrenchable.playRemoveSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // ---- IBE ----

    @Override
    public Class<HvCoolingFanBlockEntity> getBlockEntityClass() {
        return HvCoolingFanBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HvCoolingFanBlockEntity> getBlockEntityType() {
        return brass ? PghvBlockEntities.BRASS_COOLING_FAN.get() : PghvBlockEntities.COOLING_FAN.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        if (part == 0)
            return IBE.super.newBlockEntity(pos, state);
        if (part >= 2) // upper cells carry the speed value box on top of the fan
            return new CoolingFanTopBlockEntity(brass ? PghvBlockEntities.BRASS_COOLING_FAN_TOP.get()
                    : PghvBlockEntities.COOLING_FAN_TOP.get(), pos, state);
        return null;
    }

    /** Ticks both the main block entity and the value-box block entities of the upper cells. */
    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return (l, p, s, be) -> {
            if (be instanceof SmartBlockEntity smart)
                smart.tick();
        };
    }
}
