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

import com.tomrox.pghv.registry.PghvSounds;
import com.tomrox.pghv.registry.PghvBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/*
 * House connection box (pghv:house_connection_box), surface mounted on the outer wall. North-local: box x 2..14,
 * y 3..15, z 11..16; door at the front. Click opens the door; inside a thick red main switch (lever area x 5..11,
 * y 5..13). Open: click on the lever toggles it, click elsewhere closes the door.
 * Terminals: 0 L grid, 1 N grid (visible connectors under the box for Power Grid wires), 2 L house, 3 N house (hidden);
 * in-wall port 0 = house side (2, 3). Main switch = switched wire 0 -> 2, N straight through 1 -> 3.
 */
@MethodsReturnNonnullByDefault
public class HouseConnectionBoxBlock extends WallDeviceBlock<HouseConnectionBoxBlockEntity> {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty POWERED = net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED;

    private static final TerminalBoundingBox[] TERMINALS = {
            new TerminalBoundingBox(Component.translatable("pghv.terminal.line").withStyle(ChatFormatting.RED),
                    4, 0, 12, 7, 3, 15).withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.translatable("pghv.terminal.neutral").withStyle(ChatFormatting.BLUE),
                    9, 0, 12, 12, 3, 15).withColor(IDecoratedTerminal.BLUE),
            hidden(Component.translatable("pghv.terminal.line_out"), 3, 10, 5, 12),
            hidden(Component.translatable("pghv.terminal.neutral_out"), 11, 10, 13, 12)
    };
    private static final VoxelShape SHAPE = Shapes.or(box(2, 3, 11, 14, 15, 16), box(4, 0, 12, 12, 3, 15));

    public HouseConnectionBoxBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(OPEN, false).setValue(POWERED, true));
        setTerminalCollection(horizontalNorthTerminals(this, TERMINALS, SHAPE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN, POWERED);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        if (terminalAt(state, local) != null)
            return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (!state.getValue(OPEN)) {
                level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.1f);
            } else {
                Vec3 n = BoardStructure.northLocal(local, state.getValue(HORIZONTAL_FACING));
                if (n.x >= 5 && n.x <= 11 && n.y >= 5 && n.y <= 13) {
                    boolean on = !state.getValue(POWERED);
                    level.setBlock(pos, state.setValue(POWERED, on), Block.UPDATE_ALL);
                    if (level.getBlockEntity(pos) instanceof HouseConnectionBoxBlockEntity be)
                        be.setOn(on);
                    level.playSound(null, pos, on ? PghvSounds.MAIN_SWITCH_ON.get() : PghvSounds.MAIN_SWITCH_OFF.get(),
                            SoundSource.BLOCKS, 0.9f, 1.0f);
                } else {
                    level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_ALL);
                    level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.1f);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public int portCount() {
        return 1;
    }

    @Override
    public int[] portTerminals(int port) {
        return new int[] { 2, 3 };
    }

    @Override
    public Class<HouseConnectionBoxBlockEntity> getBlockEntityClass() {
        return HouseConnectionBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HouseConnectionBoxBlockEntity> getBlockEntityType() {
        return PghvBlockEntities.HOUSE_CONNECTION_BOX.get();
    }
}
