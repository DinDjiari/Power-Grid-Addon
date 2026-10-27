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

import com.simibubi.create.foundation.block.IBE;
import com.tomrox.pghv.block.entity.DataSocketBoxBlockEntity;
import com.tomrox.pghv.registry.PghvBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/*
 * Data socket box (pghv:data_socket_box): small wall box with a round data socket in front and three
 * small Power Grid style connectors on top.
 *   - The data cable from a brass fan is plugged into the front socket (DataCableItem).
 *   - Terminal 0 = servo +, 1 = servo -, 2 = control signal; ordinary Power Grid wires go from here to the servo.
 * HORIZONTAL_FACING = direction the socket points to. Placed on a wall: socket points out of the wall;
 * otherwise towards the player. Geometry (north-local, pixels): body x 4..12, y 1..11, z 10..16;
 * socket collar x 5..11, y 4..10, z 9..10; connectors on top at x 5 / 8 / 11, z 13, y 11.5..14.
 */
@MethodsReturnNonnullByDefault
public class DataSocketBoxBlock extends HorizontalElectricBlock implements IBE<DataSocketBoxBlockEntity> {
    public static final int TERMINAL_POSITIVE = 0;
    public static final int TERMINAL_NEGATIVE = 1;
    public static final int TERMINAL_CONTROL = 2;

    private static final TerminalBoundingBox[] TERMINALS = new TerminalBoundingBox[] {
            new TerminalBoundingBox(Component.translatable("pghv.terminal.servo_positive").withStyle(ChatFormatting.RED),
                    3.75, 11, 11.75, 6.25, 14, 14.25).withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.translatable("pghv.terminal.servo_negative").withStyle(ChatFormatting.BLUE),
                    6.75, 11, 11.75, 9.25, 14, 14.25).withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.translatable("pghv.terminal.servo_control").withStyle(ChatFormatting.DARK_GREEN),
                    9.75, 11, 11.75, 12.25, 14, 14.25).withColor(IDecoratedTerminal.GREEN)
    };

    private static final VoxelShape SHAPE = Shapes.or(
            box(4, 1, 10, 12, 11, 16),
            box(5, 4, 9, 11, 10, 10),
            box(4, 11, 11.5, 12, 14, 14.5));

    public DataSocketBoxBlock(Properties settings) {
        super(settings);
        setTerminalCollection(horizontalNorthTerminals(this, TERMINALS, SHAPE));
    }

    /** Socket points out of a clicked wall, otherwise towards the player. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    /** Any Power Grid wire may be attached to the connectors on top. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return true;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof DataSocketBoxBlockEntity box)
            box.unplug(true); // the data cable drops here
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Wrench on a plugged box pulls the plug (cable item drops), otherwise rotates as usual. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.getBlockEntity(context.getClickedPos()) instanceof DataSocketBoxBlockEntity box && box.isPlugged()) {
            if (!level.isClientSide)
                box.unplug(context.getPlayer() == null || !context.getPlayer().isCreative());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.onWrenched(state, context);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public Class<DataSocketBoxBlockEntity> getBlockEntityClass() {
        return DataSocketBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DataSocketBoxBlockEntity> getBlockEntityType() {
        return PghvBlockEntities.DATA_SOCKET_BOX.get();
    }
}
