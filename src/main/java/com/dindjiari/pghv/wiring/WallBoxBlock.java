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
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/*
 * Flush junction box (pghv:wall_box): cover x 2..14, y 2..14, z 14..16; two small connectors on the cover
 * (L red at x 5.5, N blue at x 10.5, y 8) for ordinary Power Grid wires. In-wall cables: port 0 = (L, N), any number.
 */
@MethodsReturnNonnullByDefault
public class WallBoxBlock extends WallDeviceBlock<WallBoxBlockEntity> {
    private static final TerminalBoundingBox[] TERMINALS = {
            new TerminalBoundingBox(Component.translatable("pghv.terminal.line").withStyle(ChatFormatting.RED),
                    4, 6.5, 11, 7, 9.5, 14).withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.translatable("pghv.terminal.neutral").withStyle(ChatFormatting.BLUE),
                    9, 6.5, 11, 12, 9.5, 14).withColor(IDecoratedTerminal.BLUE)
    };
    private static final VoxelShape SHAPE = Shapes.or(box(2, 2, 14, 14, 14, 16), box(4, 6.5, 11, 12, 9.5, 14));

    public WallBoxBlock(Properties settings) {
        super(settings);
        setTerminalCollection(wallTerminals(TERMINALS, SHAPE));
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
    public Class<WallBoxBlockEntity> getBlockEntityClass() {
        return WallBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WallBoxBlockEntity> getBlockEntityType() {
        return PghvBlockEntities.WALL_BOX.get();
    }
}
