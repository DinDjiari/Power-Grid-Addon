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
import com.tomrox.pghv.registry.PghvBlockEntities;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

/*
 * 230 V ceiling lamp (pghv:ceiling_lamp): round-ish white canopy x 3..13, z 3..13, y 14..16 and opal glass dome
 * x 4..12, z 4..12, y 11..14, hangs under the ceiling. Terminals hidden at the ceiling (y 15.5..16): 0 = L, 1 = N.
 * Target for in-wall cables (port 0). Block light from LampBlockEntity.LIGHT.
 */
@MethodsReturnNonnullByDefault
public class CeilingLampBlock extends ElectricBlock implements IBE<LampBlockEntity> {
    private static final TerminalBoundingBox[] TERMINALS = {
            new TerminalBoundingBox(Component.translatable("pghv.terminal.line"), 5, 15.5, 7, 7, 16, 9),
            new TerminalBoundingBox(Component.translatable("pghv.terminal.neutral"), 9, 15.5, 7, 11, 16, 9)
    };
    private static final VoxelShape SHAPE = Shapes.or(box(3, 14, 3, 13, 16, 13), box(4, 11, 4, 12, 14, 12));

    public CeilingLampBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(LampBlockEntity.LIGHT, 0).setValue(LampBlockEntity.LIT, false));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> TERMINALS)
                .withShapeMapper(state -> SHAPE)
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LampBlockEntity.LIGHT, LampBlockEntity.LIT);
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

    @Override
    public Class<LampBlockEntity> getBlockEntityClass() {
        return LampBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LampBlockEntity> getBlockEntityType() {
        return PghvBlockEntities.CEILING_LAMP.get();
    }
}
