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
package com.tomrox.pghv.cable;

import com.tomrox.pghv.block.CableBoxBlock;
import com.tomrox.pghv.block.CableSegmentBlock;
import com.tomrox.pghv.block.entity.CableBoxBlockEntity;
import com.tomrox.pghv.block.entity.CableSegmentBlockEntity;
import com.tomrox.pghv.registry.PghvBlocks;
import com.tomrox.pghv.registry.PghvDataComponents;
import com.tomrox.pghv.registry.PghvItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Laying an underground cable like rails:
 *  - right-click a free cable box: start (state stored on the held stack, component cable_laying)
 *  - right-click any other block face: anchor = the cell in front of the face
 *  - right-click a second free cable box: connect (segments placed, items consumed)
 *  - right-click the start box again or sneak-right-click into the air: cancel
 */
public final class CableLayingHandler {
    private CableLayingHandler() {
    }

    public static InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        boolean isBox = level.getBlockState(pos).getBlock() instanceof CableBoxBlock;
        CableLaying laying = stack.get(PghvDataComponents.CABLE_LAYING.get());

        if (level.isClientSide)
            return isBox || laying != null ? InteractionResult.SUCCESS : InteractionResult.PASS;

        if (laying != null && !laying.dimension().equals(level.dimension())) {
            stack.remove(PghvDataComponents.CABLE_LAYING.get());
            message(player, Component.translatable("pghv.cable.msg.wrong_dimension").withStyle(ChatFormatting.RED));
            laying = null;
            if (!isBox)
                return InteractionResult.FAIL;
        }

        if (isBox) {
            if (laying == null)
                return start(level, player, stack, pos);
            if (laying.start().equals(pos)) {
                stack.remove(PghvDataComponents.CABLE_LAYING.get());
                message(player, Component.translatable("pghv.cable.msg.cancelled").withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            return finish(level, player, stack, laying, pos);
        }
        if (laying == null)
            return InteractionResult.PASS;
        return anchor(player, stack, laying, pos.relative(context.getClickedFace()));
    }

    /** Sneak-right-click into the air. */
    public static boolean cancel(Level level, Player player, ItemStack stack) {
        if (!player.isShiftKeyDown() || !stack.has(PghvDataComponents.CABLE_LAYING.get()))
            return false;
        if (!level.isClientSide) {
            stack.remove(PghvDataComponents.CABLE_LAYING.get());
            message(player, Component.translatable("pghv.cable.msg.cancelled").withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    private static InteractionResult start(Level level, @Nullable Player player, ItemStack stack, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CableBoxBlockEntity box))
            return InteractionResult.FAIL;
        if (box.hasCable()) {
            message(player, Component.translatable("pghv.cable.msg.box_occupied").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        stack.set(PghvDataComponents.CABLE_LAYING.get(), new CableLaying(level.dimension(), pos.immutable(), List.of()));
        message(player, Component.translatable("pghv.cable.msg.start").withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult anchor(@Nullable Player player, ItemStack stack, CableLaying laying, BlockPos cell) {
        if (cell.equals(laying.lastPoint()))
            return InteractionResult.SUCCESS;
        if (laying.anchors().size() >= CablePath.MAX_ANCHORS) {
            message(player, Component.translatable("pghv.cable.msg.too_many_anchors", CablePath.MAX_ANCHORS)
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        CableLaying next = laying.withAnchor(cell);
        // Length so far: cells after the start box (the target box is not known yet).
        int soFar = CablePath.cells(next.points(null), false).size() - 1;
        if (soFar > CablePath.MAX_LENGTH) {
            message(player, Component.translatable("pghv.cable.msg.too_long", CablePath.MAX_LENGTH)
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        stack.set(PghvDataComponents.CABLE_LAYING.get(), next);
        message(player, Component.translatable("pghv.cable.msg.anchor", next.anchors().size(), soFar)
                .withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult finish(Level level, @Nullable Player player, ItemStack stack,
                                            CableLaying laying, BlockPos target) {
        BlockPos start = laying.start();
        if (!(level.getBlockEntity(start) instanceof CableBoxBlockEntity startBox) || startBox.hasCable()) {
            stack.remove(PghvDataComponents.CABLE_LAYING.get());
            message(player, Component.translatable("pghv.cable.msg.start_missing").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockEntity(target) instanceof CableBoxBlockEntity targetBox))
            return InteractionResult.FAIL;
        if (targetBox.hasCable()) {
            message(player, Component.translatable("pghv.cable.msg.box_occupied").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        List<BlockPos> points = laying.points(target);
        List<BlockPos> cells = CablePath.cells(points);
        int length = CablePath.length(cells);
        if (length > CablePath.MAX_LENGTH) {
            message(player, Component.translatable("pghv.cable.msg.too_long", CablePath.MAX_LENGTH)
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        CableCores cores = CableCores.get(stack);
        boolean creative = player != null && player.getAbilities().instabuild;
        if (!creative) {
            int available = player == null ? stack.getCount() : countCable(player.getInventory(), stack, cores);
            if (available < length) {
                message(player, Component.translatable("pghv.cable.msg.not_enough", length)
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
        }

        stack.remove(PghvDataComponents.CABLE_LAYING.get());
        if (!creative) {
            if (player == null)
                stack.shrink(length);
            else
                consumeCable(player.getInventory(), stack, cores, length);
        }

        placeSegments(level, cells, start);
        orientBox(level, start, cells.get(0), cells.get(1));
        orientBox(level, target, cells.get(cells.size() - 1), cells.get(cells.size() - 2));

        // Re-fetch: rotating keeps the block entity, but stay on the safe side.
        if (level.getBlockEntity(start) instanceof CableBoxBlockEntity a)
            a.setupOwner(target, points, length, cores, creative);
        if (level.getBlockEntity(target) instanceof CableBoxBlockEntity b)
            b.setupPartner(start, length, cores);

        level.playSound(null, target, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
        message(player, Component.translatable("pghv.cable.msg.connected", length).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    /** Places segment blocks in all replaceable path cells; solid cells are skipped (cable runs underground). */
    private static void placeSegments(Level level, List<BlockPos> cells, BlockPos owner) {
        CableSegmentBlock block = PghvBlocks.CABLE_SEGMENT.get();
        Map<BlockPos, EnumSet<Direction>> connections = new LinkedHashMap<>();
        for (int i = 1; i < cells.size() - 1; ++i) {
            EnumSet<Direction> dirs = connections.computeIfAbsent(cells.get(i), p -> EnumSet.noneOf(Direction.class));
            Direction prev = CablePath.direction(cells.get(i), cells.get(i - 1));
            Direction next = CablePath.direction(cells.get(i), cells.get(i + 1));
            if (prev != null)
                dirs.add(prev);
            if (next != null)
                dirs.add(next);
        }
        for (Map.Entry<BlockPos, EnumSet<Direction>> entry : connections.entrySet()) {
            BlockPos cell = entry.getKey();
            if (level.isOutsideBuildHeight(cell))
                continue;
            BlockState existing = level.getBlockState(cell);
            if (!existing.canBeReplaced())
                continue;
            FluidState fluid = level.getFluidState(cell);
            if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER))
                continue; // lava etc.: skip like a solid cell
            boolean water = fluid.is(FluidTags.WATER) && fluid.isSource();
            level.setBlock(cell, block.withConnections(entry.getValue(), water), Block.UPDATE_ALL);
            if (level.getBlockEntity(cell) instanceof CableSegmentBlockEntity segment)
                segment.setOwner(owner);
        }
    }

    /** Turns the box socket towards the first cable cell if that step is horizontal. */
    private static void orientBox(Level level, BlockPos boxPos, BlockPos boxCell, BlockPos firstCell) {
        Direction dir = CablePath.direction(boxCell, firstCell);
        if (dir == null || !dir.getAxis().isHorizontal())
            return;
        BlockState state = level.getBlockState(boxPos);
        if (!(state.getBlock() instanceof CableBoxBlock) || state.getValue(HorizontalElectricBlock.HORIZONTAL_FACING) == dir)
            return;
        level.setBlock(boxPos, state.setValue(HorizontalElectricBlock.HORIZONTAL_FACING, dir), Block.UPDATE_ALL);
        ElectricBlock.refreshConnectionEntities(level, boxPos); // move attached hanging wires to the new terminal positions
    }

    // ---- inventory ----

    private static boolean isSameCable(ItemStack stack, CableCores cores) {
        return stack.is(PghvItems.UNDERGROUND_CABLE.get()) && Objects.equals(CableCores.get(stack), cores);
    }

    private static int countCable(Inventory inventory, ItemStack held, CableCores cores) {
        int count = 0;
        boolean heldCounted = false;
        for (int i = 0; i < inventory.getContainerSize(); ++i) {
            ItemStack s = inventory.getItem(i);
            if (s == held)
                heldCounted = true;
            if (isSameCable(s, cores))
                count += s.getCount();
        }
        if (!heldCounted)
            count += held.getCount();
        return count;
    }

    /** Takes from the held stack first, then from other stacks with the same cores. */
    private static void consumeCable(Inventory inventory, ItemStack held, CableCores cores, int amount) {
        int take = Math.min(amount, held.getCount());
        held.shrink(take);
        amount -= take;
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; ++i) {
            ItemStack s = inventory.getItem(i);
            if (s == held || !isSameCable(s, cores))
                continue;
            take = Math.min(amount, s.getCount());
            s.shrink(take);
            amount -= take;
        }
        inventory.setChanged();
    }

    private static void message(@Nullable Player player, Component text) {
        if (player != null)
            player.displayClientMessage(text, true);
    }
}
