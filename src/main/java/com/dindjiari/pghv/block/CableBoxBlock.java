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
import com.tomrox.pghv.block.entity.CableBoxBlockEntity;
import com.tomrox.pghv.registry.PghvBlockEntities;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/*
 * Cable box: copper housing with three terminals on top (terminal i = cable core i) and a large
 * iron cable socket on the side the block faces. Geometry (north-local, pixels): body x 1..15,
 * y 0..13, z 1..15; socket flange x 3..13, y 0..9, z 0..1; terminals y 13..16, z 6.5..9.5 at
 * x 2..5, 6.5..9.5, 11..14.
 *
 * Placement: the socket points away from the player (sneak: towards the player), like Power
 * Grid's HorizontalElectricBlock. When a cable is connected the box turns its socket towards
 * the first cable cell if that step is horizontal.
 */
@MethodsReturnNonnullByDefault
public class CableBoxBlock extends HorizontalElectricBlock implements IBE<CableBoxBlockEntity> {
    /** Default terminal highlight colour (copper). */
    private static final int TERMINAL_COLOR = 0xC87533;

    private static final TerminalBoundingBox[] TERMINALS = new TerminalBoundingBox[] {
            new TerminalBoundingBox(Component.translatable("pghv.cable.terminal", 1), 2, 13, 6.5, 5, 16, 9.5)
                    .withColor(TERMINAL_COLOR),
            new TerminalBoundingBox(Component.translatable("pghv.cable.terminal", 2), 6.5, 13, 6.5, 9.5, 16, 9.5)
                    .withColor(TERMINAL_COLOR),
            new TerminalBoundingBox(Component.translatable("pghv.cable.terminal", 3), 11, 13, 6.5, 14, 16, 9.5)
                    .withColor(TERMINAL_COLOR)
    };

    private static final VoxelShape SHAPE = Shapes.or(
            box(1, 0, 1, 15, 13, 15),
            box(3, 0, 0, 13, 9, 1),
            box(2, 13, 6.5, 5, 16, 9.5),
            box(6.5, 13, 6.5, 9.5, 16, 9.5),
            box(11, 13, 6.5, 14, 16, 9.5));

    public CableBoxBlock(Properties settings) {
        super(settings);
        setTerminalCollection(horizontalNorthTerminals(this, TERMINALS, SHAPE));
    }

    /** Any Power Grid wire may be attached to the box terminals. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return true;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            // Box gone: cut the cable, drop it here (not for creative players).
            // Must run before the block entity is removed.
            boolean drop = !CableBoxBlockEntity.consumeCreativeBreak(pos);
            CableBoxBlockEntity.disconnectAt(level, pos, pos, null, drop);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        CableBoxBlockEntity be = getBlockEntity(level, pos);
        if (be != null && be.hasCable()) {
            // Wrench on a connected box: cut the cable instead of rotating.
            if (!level.isClientSide)
                CableBoxBlockEntity.disconnectAt(level, pos, pos, null,
                        context.getPlayer() == null || !context.getPlayer().isCreative());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.onWrenched(state, context);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide && context.getPlayer() != null && context.getPlayer().isCreative())
            CableBoxBlockEntity.markCreativeBreak(context.getClickedPos());
        return super.onSneakWrenched(state, context);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative())
            CableBoxBlockEntity.markCreativeBreak(pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public Class<CableBoxBlockEntity> getBlockEntityClass() {
        return CableBoxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CableBoxBlockEntity> getBlockEntityType() {
        return PghvBlockEntities.CABLE_BOX.get();
    }
}
