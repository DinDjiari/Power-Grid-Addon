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
package com.tomrox.pghv.block.entity;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.tomrox.pghv.block.CableBoxBlock;
import com.tomrox.pghv.block.CableSegmentBlock;
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.cable.CablePath;
import com.tomrox.pghv.registry.PghvBlocks;
import com.tomrox.pghv.registry.PghvDataComponents;
import com.tomrox.pghv.registry.PghvItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Cable box with three terminals (terminal i = cable core i).
 *
 * A cable always has an owner box A (the box where laying started) and a partner box B.
 * A stores partner, path points, cores and length; B stores the owner position,
 * cores and length (for colours and goggles). A segment block entity stores the owner position.
 *
 * Electrics: per intact core one plain Power Grid wire (GlobalElectricNetworks.makeSimpleConnection)
 * between terminal i of A and terminal i of B. These wires are not persisted by Power Grid, so A
 * builds them on the server whenever both boxes are loaded and removes them as soon as either box
 * unloads or is removed (same pattern as Power Grid's StringLightCordEntity parallel wires).
 */
public class CableBoxBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    private static final double MIN_RESISTANCE = 1e-6;
    private static final float CURRENT_SYNC_THRESHOLD = 0.5f;
    private static final Logger LOGGER = LogUtils.getLogger();

    // Persistent connection data. Not touched in the super constructor.
    @Nullable
    private CableCores cores;
    @Nullable
    private BlockPos partner;
    @Nullable
    private BlockPos owner;
    private List<BlockPos> points = List.of();
    private int length;
    /** Laid in creative mode (no items used): cutting it never drops items. */
    private boolean creativeLaid;

    /** Position a creative player is breaking right now (set in playerWillDestroy, read in onRemove). */
    @Nullable
    private static BlockPos creativeBreakPos;

    // Server runtime state
    private final ElectricWire[] wires = new ElectricWire[CableCores.COUNT];
    private boolean wiresBuilt;
    private int timer;

    // Synced status (also mirrored onto box B)
    private boolean linked;
    private final float[] currents = new float[CableCores.COUNT];
    // Last synced status, to send updates only on change
    private boolean sentLinked;
    private final float[] sentCurrents = new float[CableCores.COUNT];

    public CableBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(CableCores.COUNT);
    }

    // ---- state queries ----

    public boolean hasCable() {
        return cores != null && (partner != null || owner != null);
    }

    public boolean isOwner() {
        return cores != null && partner != null;
    }

    @Nullable
    public CableCores getCores() {
        return cores;
    }

    /** Colour of terminal cap i: core colour, copper when no cable is connected. */
    public int coreColor(int index) {
        CableCores c = cores;
        return c == null ? CableCores.COLOR_COPPER : c.color(index);
    }

    // ---- connection setup / teardown ----

    /** Called on box A after the segments have been placed. */
    public void setupOwner(BlockPos partnerPos, List<BlockPos> pathPoints, int cableLength, CableCores cableCores,
                           boolean creative) {
        dropWires();
        cores = cableCores;
        partner = partnerPos.immutable();
        owner = null;
        points = List.copyOf(pathPoints);
        length = cableLength;
        creativeLaid = creative;
        resetStatus();
        timer = 0;
        notifyUpdate();
    }

    /** Called on box B. */
    public void setupPartner(BlockPos ownerPos, int cableLength, CableCores cableCores) {
        dropWires();
        cores = cableCores;
        owner = ownerPos.immutable();
        partner = null;
        points = List.of();
        length = cableLength;
        resetStatus();
        notifyUpdate();
    }

    private void clearConnection() {
        dropWires();
        cores = null;
        partner = null;
        owner = null;
        points = List.of();
        length = 0;
        creativeLaid = false;
        resetStatus();
        notifyUpdate();
    }

    private void resetStatus() {
        linked = false;
        for (int i = 0; i < CableCores.COUNT; ++i) {
            currents[i] = 0;
        }
    }

    /** Marks a block broken by a creative player: the following onRemove must not drop cable. */
    public static void markCreativeBreak(BlockPos pos) {
        creativeBreakPos = pos.immutable();
    }

    /** True (once) if {@code pos} is being broken by a creative player. */
    public static boolean consumeCreativeBreak(BlockPos pos) {
        boolean creative = pos.equals(creativeBreakPos);
        creativeBreakPos = null;
        return creative;
    }

    /**
     * Cuts the cable connected to the box at {@code boxPos} (either end) and drops the cable items
     * at {@code dropPos} if {@code allowDrop}. If {@code fromSegment} is set, the call comes from a
     * removed segment: the cable is only cut if that segment lies on the owner's path (otherwise it
     * was an orphan, e.g. from a copied structure). Safe to call repeatedly.
     */
    public static void disconnectAt(Level level, BlockPos boxPos, BlockPos dropPos,
                                    @Nullable BlockPos fromSegment, boolean allowDrop) {
        if (level.isClientSide)
            return;
        if (!(level.getBlockEntity(boxPos) instanceof CableBoxBlockEntity box) || !box.hasCable())
            return;
        if (box.isOwner()) {
            if (fromSegment != null && !CablePath.cells(box.points).contains(fromSegment))
                return; // orphan segment, nothing to cut
            box.disconnect(dropPos, allowDrop);
            return;
        }
        // Box B: the owner holds the path. getBlockEntity loads the owner chunk if necessary.
        BlockPos ownerPos = box.owner;
        if (ownerPos != null && level.getBlockEntity(ownerPos) instanceof CableBoxBlockEntity ownerBox
                && ownerBox.isOwner() && box.worldPosition.equals(ownerBox.partner)) {
            ownerBox.disconnect(dropPos, allowDrop);
        } else {
            box.clearConnection(); // stale end without owner
        }
    }

    /**
     * Owner only: removes wires, segments and the partner link. Items are dropped only if allowed,
     * the cable was not laid in creative mode and the partner box really points back to this box
     * (no duplication through copied NBT / structures).
     */
    private void disconnect(BlockPos dropPos, boolean allowDrop) {
        if (level == null || !isOwner())
            return;
        BlockPos partnerPos = partner;
        List<BlockPos> path = points;
        int cableLength = length;
        CableCores cableCores = cores;
        boolean drop = allowDrop && !creativeLaid;

        // Clear first, so segment removals below (which call disconnectAt) end as no-ops.
        clearConnection();
        boolean consistent = false;
        if (partnerPos != null && level.getBlockEntity(partnerPos) instanceof CableBoxBlockEntity other
                && worldPosition.equals(other.owner)) {
            other.clearConnection();
            consistent = true;
        }

        removeSegments(path);
        if (drop && consistent)
            dropCable(level, dropPos, cableCores, cableLength);
    }

    private void removeSegments(List<BlockPos> path) {
        if (level == null || path.size() < 2)
            return;
        List<BlockPos> cells = CablePath.cells(path);
        Set<BlockPos> done = new HashSet<>();
        for (int i = 1; i < cells.size() - 1; ++i) {
            BlockPos cell = cells.get(i);
            if (!done.add(cell))
                continue;
            BlockState state = level.getBlockState(cell);
            if (!state.is(PghvBlocks.CABLE_SEGMENT.get()))
                continue;
            if (!(level.getBlockEntity(cell) instanceof CableSegmentBlockEntity segment)
                    || !worldPosition.equals(segment.getOwner()))
                continue;
            BlockState replacement = state.getValue(CableSegmentBlock.WATERLOGGED)
                    ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(cell, replacement, Block.UPDATE_ALL);
        }
    }

    public static void dropCable(Level level, BlockPos pos, @Nullable CableCores cableCores, int count) {
        if (cableCores == null)
            return;
        while (count > 0) {
            int n = Math.min(64, count);
            ItemStack stack = new ItemStack(PghvItems.UNDERGROUND_CABLE.get(), n);
            stack.set(PghvDataComponents.CABLE_CORES.get(), cableCores);
            Block.popResource(level, pos, stack);
            count -= n;
        }
    }

    // ---- electrics (server, owner only) ----

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !isOwner())
            return;
        BlockPos partnerPos = partner;
        ++timer;

        if (timer % 20 == 0 && level.isLoaded(partnerPos)
                && (!(level.getBlockState(partnerPos).getBlock() instanceof CableBoxBlock)
                || !(level.getBlockEntity(partnerPos) instanceof CableBoxBlockEntity other)
                || !worldPosition.equals(other.owner))) {
            // Partner vanished or does not point back (command, copied NBT): cut without drops.
            disconnect(worldPosition, false);
            return;
        }

        if (!wiresBuilt) {
            if (timer % 10 == 0)
                tryBuildWires();
        } else if (timer % 20 == 0 && !endpointsValid()) {
            dropWires();
        }

        if (wiresBuilt)
            checkCurrents();
        if (timer % 10 == 0)
            pushStatus();
    }

    private boolean endpointsValid() {
        if (level == null || partner == null)
            return false;
        for (int i = 0; i < CableCores.COUNT; ++i) {
            if (!new BlockWireEndpoint(worldPosition, i).isValid(level)
                    || !new BlockWireEndpoint(partner, i).isValid(level))
                return false;
        }
        return true;
    }

    private void tryBuildWires() {
        if (!endpointsValid())
            return;
        boolean complete = true;
        try {
            for (int i = 0; i < CableCores.COUNT; ++i) {
                wires[i] = GlobalElectricNetworks.makeSimpleConnection(level,
                        new BlockWireEndpoint(worldPosition, i), new BlockWireEndpoint(partner, i),
                        (float) totalResistance(level.registryAccess(), i));
                if (wires[i] == null)
                    complete = false;
            }
        } catch (RuntimeException e) {
            LOGGER.error("Failed to create cable wires at {}", worldPosition, e);
            complete = false;
        }
        if (!complete) {
            dropWires(); // retry in the next interval
            return;
        }
        wiresBuilt = true;
        linked = true;
    }

    /** Removes the simulation wires (idempotent). */
    public void dropWires() {
        for (int i = 0; i < CableCores.COUNT; ++i) {
            if (wires[i] != null) {
                wires[i].remove();
                wires[i] = null;
            }
            currents[i] = 0;
        }
        wiresBuilt = false;
        linked = false;
    }

    /** Resistance of core i over the whole cable length. */
    public double totalResistance(@Nullable HolderLookup.Provider registries, int index) {
        if (cores == null)
            return 0;
        return Math.max(MIN_RESISTANCE, CableCores.ohmPerBlock(registries, cores.core(index)) * Math.max(1, length));
    }

    /** Reads the core currents for goggles (no overload handling: cores never burn through). */
    private void checkCurrents() {
        if (level == null || cores == null)
            return;
        for (int i = 0; i < CableCores.COUNT; ++i) {
            ElectricWire wire = wires[i];
            double current = wire == null ? 0 : Math.abs(wire.current());
            currents[i] = Double.isFinite(current) ? (float) current : 0;
        }
    }

    /** Syncs status to clients and mirrors it onto box B, only when something changed. */
    private void pushStatus() {
        if (level == null || partner == null || !statusChanged())
            return;
        sentLinked = linked;
        System.arraycopy(currents, 0, sentCurrents, 0, CableCores.COUNT);
        sendData();
        if (level.isLoaded(partner) && level.getBlockEntity(partner) instanceof CableBoxBlockEntity other
                && worldPosition.equals(other.owner)) {
            other.linked = linked;
            System.arraycopy(currents, 0, other.currents, 0, CableCores.COUNT);
            other.sendData();
        }
    }

    private boolean statusChanged() {
        if (linked != sentLinked)
            return true;
        for (int i = 0; i < CableCores.COUNT; ++i) {
            if (Math.abs(currents[i] - sentCurrents[i]) > CURRENT_SYNC_THRESHOLD)
                return true;
        }
        return false;
    }

    // ---- lifecycle ----

    @Override
    public void remove() {
        releaseWires();
        super.remove();
    }

    @Override
    public void invalidate() {
        // Unload or removal: the plain wires reference our nodes, drop them before the nodes go.
        releaseWires();
        super.invalidate();
    }

    private void releaseWires() {
        if (level == null || level.isClientSide)
            return;
        dropWires();
        if (owner != null && level.isLoaded(owner) && level.getBlockEntity(owner) instanceof CableBoxBlockEntity ownerBox
                && worldPosition.equals(ownerBox.partner)) {
            ownerBox.dropWires(); // the owner rebuilds them once this box is back
            ownerBox.sendData();
        }
        if (partner != null && level.isLoaded(partner) && level.getBlockEntity(partner) instanceof CableBoxBlockEntity other
                && worldPosition.equals(other.owner)) {
            other.linked = false;
            for (int i = 0; i < CableCores.COUNT; ++i)
                other.currents[i] = 0;
            other.sendData();
        }
    }

    // ---- NBT ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (cores != null) {
            tag.put("Cores", cores.toTag());
            tag.putInt("Length", length);
            if (creativeLaid)
                tag.putBoolean("CreativeLaid", true);
            if (partner != null) {
                tag.putLong("Partner", partner.asLong());
                tag.putLongArray("Points", CablePath.pack(points));
            }
            if (owner != null)
                tag.putLong("Owner", owner.asLong());
        }
        if (clientPacket) {
            tag.putBoolean("Linked", linked);
            for (int i = 0; i < CableCores.COUNT; ++i)
                tag.putFloat("Current" + i, currents[i]);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        CableCores oldCores = cores;
        cores = tag.contains("Cores", Tag.TAG_LIST) ? CableCores.fromTag(tag.getList("Cores", CableCores.TAG_TYPE)) : null;
        length = tag.getInt("Length");
        creativeLaid = tag.getBoolean("CreativeLaid");
        partner = tag.contains("Partner") ? BlockPos.of(tag.getLong("Partner")) : null;
        points = tag.contains("Points") ? CablePath.unpack(tag.getLongArray("Points")) : List.of();
        owner = tag.contains("Owner") ? BlockPos.of(tag.getLong("Owner")) : null;
        if (cores == null) {
            partner = null;
            owner = null;
        }
        if (clientPacket) {
            linked = tag.getBoolean("Linked");
            for (int i = 0; i < CableCores.COUNT; ++i)
                currents[i] = tag.getFloat("Current" + i);
            if (!Objects.equals(oldCores, cores) && level != null && level.isClientSide) {
                // Re-render so the BlockColor picks up the new core colours.
                BlockState state = getBlockState();
                level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_IMMEDIATE);
            }
        }
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        line(tooltip, Component.translatable("pghv.cable.goggles.title").withStyle(ChatFormatting.GRAY), 0);
        CableCores c = cores;
        if (c == null || !hasCable()) {
            line(tooltip, Component.translatable("pghv.cable.goggles.not_connected").withStyle(ChatFormatting.DARK_GRAY), 1);
            return true;
        }
        HolderLookup.Provider registries = level == null ? null : level.registryAccess();
        line(tooltip, Component.translatable("pghv.cable.goggles.length", length).withStyle(ChatFormatting.GRAY), 1);
        line(tooltip, Component.translatable(linked ? "pghv.cable.goggles.linked" : "pghv.cable.goggles.unlinked")
                .withStyle(linked ? ChatFormatting.GREEN : ChatFormatting.GOLD), 1);
        for (int i = 0; i < CableCores.COUNT; ++i) {
            line(tooltip, Component.translatable("pghv.cable.goggles.core", i + 1, CableCores.name(c.core(i)))
                    .withStyle(ChatFormatting.GRAY), 1);
            line(tooltip, Component.translatable("pghv.cable.goggles.resistance",
                    CableCores.formatOhm(totalResistance(registries, i))).withStyle(ChatFormatting.DARK_AQUA), 2);
            double max = CableCores.maxCurrent(registries, c.core(i));
            ChatFormatting color = currents[i] > max ? ChatFormatting.RED
                    : currents[i] > max * 0.75 ? ChatFormatting.GOLD : ChatFormatting.AQUA;
            line(tooltip, Component.translatable("pghv.cable.goggles.current",
                    CableCores.formatAmps(currents[i]), CableCores.formatAmps(max)).withStyle(color), 2);
        }
        return true;
    }

    private static void line(List<Component> tooltip, MutableComponent text, int indent) {
        tooltip.add(Component.literal("    " + "  ".repeat(indent)).append(text));
    }
}
