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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/*
 * 230 V wall light (pghv:wall_light): white base x 4..12, y 4..12, z 14..16, frosted shade x 5..11, y 5..11, z 11..14.
 * Terminals hidden at the wall: 0 = L, 1 = N; port 0. Block light from LampBlockEntity.LIGHT.
 */
@MethodsReturnNonnullByDefault
public class WallLightBlock extends WallDeviceBlock<LampBlockEntity> {
    private static final TerminalBoundingBox[] TERMINALS = {
            hidden(Component.translatable("pghv.terminal.line"), 5, 7, 7, 9),
            hidden(Component.translatable("pghv.terminal.neutral"), 9, 7, 11, 9)
    };
    private static final VoxelShape SHAPE = box(4, 4, 11, 12, 12, 16);

    public WallLightBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(LampBlockEntity.LIGHT, 0).setValue(LampBlockEntity.LIT, false));
        setTerminalCollection(horizontalNorthTerminals(this, TERMINALS, SHAPE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LampBlockEntity.LIGHT, LampBlockEntity.LIT);
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
    public Class<LampBlockEntity> getBlockEntityClass() {
        return LampBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LampBlockEntity> getBlockEntityType() {
        return PghvBlockEntities.WALL_LIGHT.get();
    }
}
