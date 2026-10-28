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
import net.minecraft.network.chat.Component;
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
    protected WallDeviceBlock(Properties settings) {
        super(settings);
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
