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
 */
package com.tomrox.pghv.block;

import com.tomrox.pghv.block.entity.CableBoxBlockEntity;
import com.tomrox.pghv.block.entity.CableSegmentBlockEntity;
import com.tomrox.pghv.registry.PghvBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/*
 * Visible piece of an underground cable, lying on the floor of its cell (cross-section 5x4 px:
 * x/z 5.5..10.5, y 0..4). Connection properties point to the neighbouring path cells; "up" adds a
 * riser to the top of the cell. No collision (players walk over it), outline = geometry.
 * Breaking a segment cuts the whole cable; the items are dropped by code (loot table is empty).
 */
public class CableSegmentBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = new EnumMap<>(Direction.class);

    private static final VoxelShape CENTER = box(5.5, 0, 5.5, 10.5, 4, 10.5);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        PROPERTY_BY_DIRECTION.put(Direction.NORTH, NORTH);
        PROPERTY_BY_DIRECTION.put(Direction.EAST, EAST);
        PROPERTY_BY_DIRECTION.put(Direction.SOUTH, SOUTH);
        PROPERTY_BY_DIRECTION.put(Direction.WEST, WEST);
        PROPERTY_BY_DIRECTION.put(Direction.UP, UP);
        PROPERTY_BY_DIRECTION.put(Direction.DOWN, DOWN);

        ARMS.put(Direction.NORTH, box(5.5, 0, 0, 10.5, 4, 5.5));
        ARMS.put(Direction.SOUTH, box(5.5, 0, 10.5, 10.5, 4, 16));
        ARMS.put(Direction.WEST, box(0, 0, 5.5, 5.5, 4, 10.5));
        ARMS.put(Direction.EAST, box(10.5, 0, 5.5, 16, 4, 10.5));
        ARMS.put(Direction.UP, box(5.5, 4, 5.5, 10.5, 16, 10.5));
        ARMS.put(Direction.DOWN, Shapes.empty());

        for (int mask = 0; mask < 64; ++mask) {
            VoxelShape shape = CENTER;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.get3DDataValue())) != 0)
                    shape = Shapes.or(shape, ARMS.get(d));
            }
            SHAPES[mask] = shape;
        }
    }

    public CableSegmentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED);
    }

    /** Segment state with the given connections. */
    public BlockState withConnections(Iterable<Direction> directions, boolean waterlogged) {
        BlockState state = defaultBlockState().setValue(WATERLOGGED, waterlogged);
        for (Direction d : directions)
            state = state.setValue(PROPERTY_BY_DIRECTION.get(d), true);
        return state;
    }

    private static int mask(BlockState state) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(d)))
                mask |= 1 << d.get3DDataValue();
        }
        return mask;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[mask(state)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED))
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        BlockPos owner = null;
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof CableSegmentBlockEntity segment)
            owner = segment.getOwner();
        super.onRemove(state, level, pos, newState, moved);
        if (owner != null) {
            // Cable cut here: remove the whole cable, drop it at this segment (not for creative
            // players). No-op if the owner already disconnected (that is how segment removal by
            // the owner ends) or if this segment is not on the owner's path (orphan).
            boolean drop = !CableBoxBlockEntity.consumeCreativeBreak(pos);
            CableBoxBlockEntity.disconnectAt(level, owner, pos, pos, drop);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative())
            CableBoxBlockEntity.markCreativeBreak(pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableSegmentBlockEntity(PghvBlockEntities.CABLE_SEGMENT.get(), pos, state);
    }
}
