/*
 * Copyright 2026 DinDjiari
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
package com.dindjiari.pghv.wiring;

import com.dindjiari.pghv.registry.PghvBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

import java.util.List;

/*
 * Distribution board / meter cabinet block (pghv:distribution_board). Blocks join into one board up to 8 x 8
 * (BoardStructure). Flush mounted: the block sits IN the wall (full block), door flush with the wall surface at
 * north-local z 0, recess 7 px deep (back plate z 7..8, housing z 8..16). DIN rail at z 6..7 with 4 places
 * (place s at x 16-4(s+1) .. 16-4s, y 3..13); modules stand in the recess (z about 1..6). Door: click opens the whole board, sneak + empty hand closes it.
 * Open: module item on a free place inserts it (Power Grid fuse holder: 1 place, Power Grid energy meter: 4 places,
 * RCD / main switch: 1 place); empty hand toggles a lever or takes out a blown fuse; iron wire resets a fuse;
 * wrench cycles the fuse rating; sneak + wrench takes a module out.
 * Terminals (hidden at the wall): 0 L_in, 1 N_in, 2 L bus, 3 N bus, 4..7 L_out of place 0..3.
 * In-wall ports: 0 = feed (0, 1), 1..4 = circuit of place 0..3 (L_out, N bus).
 */
@MethodsReturnNonnullByDefault
public class DistributionBoardBlock extends WallDeviceBlock<DistributionBoardBlockEntity> {
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final TerminalBoundingBox[] TERMINALS = {
            hidden(Component.translatable("pghv.terminal.line_in"), 1, 1, 2, 2),
            hidden(Component.translatable("pghv.terminal.neutral_in"), 3, 1, 4, 2),
            hidden(Component.translatable("pghv.terminal.line"), 5, 1, 6, 2),
            hidden(Component.translatable("pghv.terminal.neutral"), 7, 1, 8, 2),
            hidden(Component.translatable("pghv.terminal.circuit", 1), 13, 14, 15, 15),
            hidden(Component.translatable("pghv.terminal.circuit", 2), 9, 14, 11, 15),
            hidden(Component.translatable("pghv.terminal.circuit", 3), 5, 14, 7, 15),
            hidden(Component.translatable("pghv.terminal.circuit", 4), 1, 14, 3, 15)
    };
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public DistributionBoardBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false)
                .setValue(RIGHT, false).setValue(OPEN, false));
        setTerminalCollection(horizontalNorthTerminals(this, TERMINALS, SHAPE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(UP, DOWN, LEFT, RIGHT, OPEN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null)
            return null;
        Direction facing = state.getValue(HORIZONTAL_FACING);
        List<BlockPos> comp = BoardStructure.component(ctx.getLevel(), ctx.getClickedPos(), facing, ctx.getClickedPos(), null);
        if (!BoardStructure.fits(comp, facing)) {
            if (ctx.getPlayer() != null && !ctx.getLevel().isClientSide)
                ctx.getPlayer().displayClientMessage(Component.translatable("pghv.board.msg.too_big", BoardStructure.MAX, BoardStructure.MAX)
                        .withStyle(ChatFormatting.RED), true);
            return null;
        }
        return state;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !oldState.is(this))
            BoardStructure.refresh(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        boolean gone = !state.is(newState.getBlock());
        if (!level.isClientSide && gone && level.getBlockEntity(pos) instanceof DistributionBoardBlockEntity be)
            be.dropModules();
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && gone) {
            Direction facing = state.getValue(HORIZONTAL_FACING);
            for (Direction d : new Direction[] { Direction.UP, Direction.DOWN, BoardStructure.left(facing), BoardStructure.right(facing) })
                BoardStructure.refresh(level, pos.relative(d));
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!state.getValue(OPEN)) {
            if (!level.isClientSide) {
                BoardStructure.setOpen(level, pos, true);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.3f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (!level.isClientSide) {
                BoardStructure.setOpen(level, pos, false);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.3f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        int slot = slotAt(state, pos, hit.getLocation());
        if (slot < 0 || !(level.getBlockEntity(pos) instanceof DistributionBoardBlockEntity be))
            return InteractionResult.PASS;
        if (!level.isClientSide)
            return be.interact(slot, player, held) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    /** Wrench on an open board: fuse rating cycles; sneak + wrench takes the module out. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (state.getValue(OPEN) && level.getBlockEntity(pos) instanceof DistributionBoardBlockEntity be) {
            int slot = slotAt(state, pos, context.getClickLocation());
            if (slot >= 0 && be.module(slot) != null) {
                if (!level.isClientSide)
                    be.cycleRating(slot, context.getPlayer());
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (state.getValue(OPEN) && level.getBlockEntity(pos) instanceof DistributionBoardBlockEntity be) {
            int slot = slotAt(state, pos, context.getClickLocation());
            if (slot >= 0 && be.module(slot) != null) {
                if (!level.isClientSide)
                    be.remove(slot, context.getPlayer());
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.onSneakWrenched(state, context);
    }

    public static int slotAt(BlockState state, BlockPos pos, Vec3 hitWorld) {
        Vec3 local = hitWorld.subtract(Vec3.atLowerCornerOf(pos));
        return BoardStructure.slotAt(BoardStructure.northLocal(local, state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    public int portCount() {
        return 5;
    }

    @Override
    public int[] portTerminals(int port) {
        return port == 0 ? new int[] { 0, 1 } : new int[] { 3 + port, 3 };
    }

    @Override
    public Class<DistributionBoardBlockEntity> getBlockEntityClass() {
        return DistributionBoardBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DistributionBoardBlockEntity> getBlockEntityType() {
        return PghvBlockEntities.DISTRIBUTION_BOARD.get();
    }
}
