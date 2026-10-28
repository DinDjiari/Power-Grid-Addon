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
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/*
 * EU wall switch (pghv:wall_switch, rocker) and EU push button (pghv:wall_button, closes for 20 ticks).
 * Plate x 3..13, y 3..13, z 14..16, rocker x 5..11, y 5..11, z 13..14. POWERED = on.
 * Terminals (hidden at the wall): 0 = L in, 1 = N in, 2 = L out, 3 = N out; ports 0 = (0, 1), 1 = (2, 3).
 * L is switched between 0 and 2, N is always connected (1 - 3).
 */
@MethodsReturnNonnullByDefault
public class WallSwitchBlock extends WallDeviceBlock<WallSwitchBlockEntity> {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final int BUTTON_TICKS = 20;

    private static final TerminalBoundingBox[] TERMINALS = {
            hidden(Component.translatable("pghv.terminal.line_in"), 3, 9, 5, 11),
            hidden(Component.translatable("pghv.terminal.neutral_in"), 5, 9, 7, 11),
            hidden(Component.translatable("pghv.terminal.line_out"), 9, 9, 11, 11),
            hidden(Component.translatable("pghv.terminal.neutral_out"), 11, 9, 13, 11)
    };
    private static final VoxelShape SHAPE = box(3, 3, 13, 13, 13, 16);

    private final boolean button;

    public WallSwitchBlock(Properties settings, boolean button) {
        super(settings);
        this.button = button;
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
        setTerminalCollection(horizontalNorthTerminals(this, TERMINALS, SHAPE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown())
            return InteractionResult.PASS;
        boolean on = button || !state.getValue(POWERED);
        if (button && state.getValue(POWERED))
            return InteractionResult.SUCCESS;
        if (!level.isClientSide) {
            setOn(level, pos, state, on);
            if (button)
                level.scheduleTick(pos, this, BUTTON_TICKS);
        }
        level.playSound(player, pos, button ? PghvSounds.WALL_BUTTON_PRESS.get()
                : on ? PghvSounds.WALL_SWITCH_ON.get() : PghvSounds.WALL_SWITCH_OFF.get(),
                SoundSource.BLOCKS, 0.6f, 0.95f + level.random.nextFloat() * 0.1f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void setOn(Level level, BlockPos pos, BlockState state, boolean on) {
        level.setBlock(pos, state.setValue(POWERED, on), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof WallSwitchBlockEntity be)
            be.setOn(on);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (button && state.getValue(POWERED)) {
            setOn(level, pos, state, false);
            level.playSound(null, pos, PghvSounds.WALL_BUTTON_RELEASE.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
        }
    }

    @Override
    public int portCount() {
        return 2;
    }

    @Override
    public int[] portTerminals(int port) {
        return port == 0 ? new int[] { 0, 1 } : new int[] { 2, 3 };
    }

    @Override
    public Class<WallSwitchBlockEntity> getBlockEntityClass() {
        return WallSwitchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WallSwitchBlockEntity> getBlockEntityType() {
        return button ? PghvBlockEntities.WALL_BUTTON.get() : PghvBlockEntities.WALL_SWITCH.get();
    }
}
