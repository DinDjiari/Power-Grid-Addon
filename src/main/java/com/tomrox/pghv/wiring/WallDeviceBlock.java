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
package com.tomrox.pghv.wiring;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/*
 * Base of all wall-mounted devices of the in-wall installation (box, switch, button, outlets).
 * HORIZONTAL_FACING = direction pointing out of the wall. North-local model: front to the north, wall behind (z = 16).
 * Ports group two terminals (L, N) that an in-wall cable connects to.
 */
@MethodsReturnNonnullByDefault
public abstract class WallDeviceBlock<T extends WallDeviceBlockEntity> extends HorizontalElectricBlock implements IBE<T> {
    /** Wall connector mode: false = L (red), true = N (blue). */
    public static final BooleanProperty WALL_NEUTRAL = BooleanProperty.create("wall_neutral");
    /** Default wall connector position, north-local pixels: in the wall below the device (x 7..9, y 0..2, z 14..16). */
    protected static final double WALL_CONNECTOR_X = 7;
    protected static final double WALL_CONNECTOR_Y = 0;

    /** Terminal index of the wall connector, -1 if the device has none. */
    protected int wallConnector = -1;

    protected WallDeviceBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(WALL_NEUTRAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WALL_NEUTRAL);
    }

    /**
     * Terminals of a wall device plus the wall connector (an ordinary Power Grid terminal in the wall, only seen when
     * hovering; red = connected to L, blue = connected to N of port 0, right-click with an empty hand switches).
     */
    protected BlockStateTerminalCollection wallTerminals(TerminalBoundingBox[] north, VoxelShape northShape) {
        return wallTerminals(north, northShape, WALL_CONNECTOR_X, WALL_CONNECTOR_Y);
    }

    /** As above with the connector (2 x 2 px, at the wall) at north-local x0/y0. */
    protected BlockStateTerminalCollection wallTerminals(TerminalBoundingBox[] north, VoxelShape northShape, double x0, double y0) {
        double[] c = { x0, y0, 14, x0 + 2, y0 + 2, 16 };
        TerminalBoundingBox line = new TerminalBoundingBox(Component.translatable("pghv.terminal.wall_line").withStyle(ChatFormatting.RED),
                c[0], c[1], c[2], c[3], c[4], c[5]).withColor(IDecoratedTerminal.RED);
        TerminalBoundingBox neutral = new TerminalBoundingBox(Component.translatable("pghv.terminal.wall_neutral").withStyle(ChatFormatting.BLUE),
                c[0], c[1], c[2], c[3], c[4], c[5]).withColor(IDecoratedTerminal.BLUE);
        TerminalBoundingBox[] withLine = java.util.Arrays.copyOf(north, north.length + 1);
        withLine[north.length] = line;
        TerminalBoundingBox[] withNeutral = java.util.Arrays.copyOf(north, north.length + 1);
        withNeutral[north.length] = neutral;
        wallConnector = north.length;
        VoxelShaper shaper = VoxelShaper.forHorizontal(Shapes.or(northShape, box(c[0], c[1], c[2], c[3], c[4], c[5])), Direction.NORTH);
        return BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> {
                    TerminalBoundingBox[] src = state.getValue(WALL_NEUTRAL) ? withNeutral : withLine;
                    return BlockStateTerminalCollection.each(src, t -> rotateNorth(t, state.getValue(HORIZONTAL_FACING)));
                })
                .withShapeMapper(state -> shaper.get(state.getValue(HORIZONTAL_FACING)))
                .build();
    }

    private static TerminalBoundingBox rotateNorth(TerminalBoundingBox t, Direction facing) {
        return switch (facing) {
            case SOUTH -> t.rotateAroundY(180);
            case EAST -> t.rotateAroundY(90);
            case WEST -> t.rotateAroundY(-90);
            default -> t;
        };
    }

    public int wallConnector() {
        return wallConnector;
    }

    /** True if local (block-relative) hit position lies on the wall connector. */
    public boolean isOnWallConnector(BlockState state, Vec3 local) {
        return wallConnector >= 0 && terminal(state, wallConnector) instanceof TerminalBoundingBox box && box.check(local);
    }

    /** Number of in-wall ports. */
    public abstract int portCount();

    /** Terminals {L, N} of a port. */
    public abstract int[] portTerminals(int port);

    /** Hidden terminal behind the device (north-local pixels, at the wall: not clickable). */
    protected static TerminalBoundingBox hidden(Component name, double x0, double y0, double x1, double y1) {
        return new TerminalBoundingBox(name, x0, y0, 15.5, x1, y1, 16);
    }

    /** Facing out of a clicked wall, otherwise towards the player. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    /** Block in the wall behind the device. */
    public static BlockPos mountPos(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(HORIZONTAL_FACING).getOpposite());
    }

    @Override
    public boolean accepts(ItemStack wireStack) {
        return true;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WallDeviceBlockEntity be)
            be.onDeviceRemoved();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }
}
