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

import com.tomrox.pghv.registry.PghvBlockEntities;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.ISocketElectric;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/*
 * Wall outlet, EU (Schuko, pghv:outlet_eu, 230 V) or US (pghv:outlet_us, 120 V). Power Grid plugs fit (ISocketElectric,
 * socket area in the middle of the face). Terminals (hidden): 0 = L, 1 = N; port 0 = (0, 1).
 * EU plate x 3..13, y 3..13, z 14..16; US plate x 4..12, y 2..14, z 14..16.
 */
@MethodsReturnNonnullByDefault
public class WallOutletBlock extends WallDeviceBlock<WallOutletBlockEntity> implements ISocketElectric {
    private static final TerminalBoundingBox[] TERMINALS = {
            hidden(Component.translatable("pghv.terminal.line"), 5, 7, 7, 9),
            hidden(Component.translatable("pghv.terminal.neutral"), 9, 7, 11, 9)
    };
    private static final TerminalBoundingBox SOCKET_NORTH =
            new TerminalBoundingBox(IDecoratedTerminal.SOCKET, 5, 5, 12.5, 11, 11, 14.5);
    private static final VoxelShape SHAPE_EU = box(3, 3, 14, 13, 13, 16);
    private static final VoxelShape SHAPE_US = box(4, 2, 14, 12, 14, 16);

    private final boolean us;

    public WallOutletBlock(Properties settings, boolean us) {
        super(settings);
        this.us = us;
        setTerminalCollection(wallTerminals(TERMINALS, us ? SHAPE_US : SHAPE_EU));
    }

    /** Nominal voltage: 230 V EU, 120 V US. */
    public double nominalVoltage() {
        return us ? 120.0 : 230.0;
    }

    @Override
    public ITerminalPlacement socket(BlockState state) {
        return switch (state.getValue(HORIZONTAL_FACING)) {
            case SOUTH -> SOCKET_NORTH.rotateAroundY(180);
            case EAST -> SOCKET_NORTH.rotateAroundY(90);
            case WEST -> SOCKET_NORTH.rotateAroundY(-90);
            default -> SOCKET_NORTH;
        };
    }

    @Override
    public int portCount() {
        return 1;
    }

    @Override
    public int[] portTerminals(int port) {
        return new int[] { 0, 1 };
    }

    @Override
    public Class<WallOutletBlockEntity> getBlockEntityClass() {
        return WallOutletBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WallOutletBlockEntity> getBlockEntityType() {
        return us ? PghvBlockEntities.OUTLET_US.get() : PghvBlockEntities.OUTLET_EU.get();
    }
}
