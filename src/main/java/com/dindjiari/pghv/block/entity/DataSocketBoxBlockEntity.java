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
package com.dindjiari.pghv.block.entity;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.dindjiari.pghv.block.HvCoolingFanBlock;
import com.dindjiari.pghv.cable.CableCores;
import com.dindjiari.pghv.cable.DataPlug;
import com.dindjiari.pghv.registry.PghvItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Data socket box: holds the plugged data cable (fan socket position + waypoints) and connects
 * box terminal i with brass-fan socket terminal 2 + i (copper +, iron -, gold control).
 *
 * The three cores are plain Power Grid wires (GlobalElectricNetworks.makeSimpleConnection). They are not
 * persisted by Power Grid, so the box builds them whenever both ends are loaded and drops them on unload,
 * like the cable box. The cable itself is drawn by DataSocketBoxRenderer from the synced path.
 */
public class DataSocketBoxBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int CORES = 3;
    /** Cores = the Power Grid wires of the recipe: copper (+), iron (-), gold (control). */
    private static final net.minecraft.resources.ResourceLocation[] CORE_WIRES = { CableCores.COPPER, CableCores.IRON, CableCores.GOLD };
    private static final double MIN_RESISTANCE = 1e-4;

    // Persistent, not touched in the super constructor.
    @Nullable
    private BlockPos fanSocket;
    private List<Vec3> points = List.of();
    private boolean creativeLaid;

    // Server runtime
    private final ElectricWire[] wires = new ElectricWire[CORES];
    private boolean wiresBuilt;
    private int timer;
    // Synced
    private boolean linked;
    private boolean sentLinked;

    public DataSocketBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(CORES);
    }

    // ---- state ----

    public boolean isPlugged() {
        return fanSocket != null;
    }

    @Nullable
    public BlockPos getFanSocket() {
        return fanSocket;
    }

    public List<Vec3> getPoints() {
        return points;
    }

    public boolean isLinked() {
        return linked;
    }

    /** Plugs a data cable in (server). Caller checked the path. */
    public void plug(BlockPos socket, List<Vec3> waypoints, boolean creative) {
        dropWires();
        fanSocket = socket.immutable();
        points = List.copyOf(waypoints);
        creativeLaid = creative;
        timer = 0;
        setChanged();
        sendData();
        tryBuildWires();
    }

    /** Pulls the plug (server); drops one data cable at the box unless it was laid in creative or drop is false. */
    public void unplug(boolean drop) {
        if (fanSocket == null)
            return;
        dropWires();
        boolean give = drop && !creativeLaid;
        fanSocket = null;
        points = List.of();
        creativeLaid = false;
        setChanged();
        sendData();
        if (give && level != null && !level.isClientSide) {
            Vec3 c = Vec3.atCenterOf(worldPosition);
            Containers.dropItemStack(level, c.x, c.y, c.z, new ItemStack(PghvItems.DATA_CABLE.get()));
        }
    }

    /** Length of the plugged cable (0 if the fan is not loaded). */
    public double cableLength() {
        if (level == null || fanSocket == null)
            return 0;
        List<Vec3> path = DataPlug.path(level, fanSocket, points, worldPosition);
        return path == null ? 0 : DataPlug.length(path);
    }

    // ---- ticking ----

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || fanSocket == null)
            return;
        ++timer;
        if (timer % 20 == 0 && level.isLoaded(fanSocket) && DataPlug.fanSocketFace(level, fanSocket) == null) {
            // Fan broken or replaced: the plug falls out.
            unplug(true);
            return;
        }
        if (!wiresBuilt) {
            if (timer % 10 == 0)
                tryBuildWires();
        } else if (timer % 20 == 0 && !endpointsValid()) {
            dropWires();
        }
        if (timer % 10 == 0 && linked != sentLinked) {
            sentLinked = linked;
            sendData();
        }
    }

    private boolean endpointsValid() {
        if (level == null || fanSocket == null || !level.isLoaded(fanSocket))
            return false;
        for (int i = 0; i < CORES; ++i) {
            if (!new BlockWireEndpoint(worldPosition, i).isValid(level)
                    || !new BlockWireEndpoint(fanSocket, HvCoolingFanBlock.SOCKET_POSITIVE + i).isValid(level))
                return false;
        }
        return true;
    }

    private void tryBuildWires() {
        if (level == null || level.isClientSide || wiresBuilt || !endpointsValid())
            return;
        double length = Math.max(1.0, cableLength());
        boolean complete = true;
        try {
            for (int i = 0; i < CORES; ++i) {
                wires[i] = GlobalElectricNetworks.makeSimpleConnection(level,
                        new BlockWireEndpoint(worldPosition, i),
                        new BlockWireEndpoint(fanSocket, HvCoolingFanBlock.SOCKET_POSITIVE + i),
                        (float) Math.max(MIN_RESISTANCE,
                                CableCores.ohmPerBlock(level.registryAccess(), CORE_WIRES[i]) * length));
                if (wires[i] == null)
                    complete = false;
            }
        } catch (RuntimeException e) {
            LOGGER.error("Failed to connect data cable at {}", worldPosition, e);
            complete = false;
        }
        if (!complete) {
            dropWires();
            return;
        }
        wiresBuilt = true;
        linked = true;
    }

    /** Removes the simulation wires (idempotent). */
    public void dropWires() {
        for (int i = 0; i < CORES; ++i) {
            if (wires[i] != null) {
                wires[i].remove();
                wires[i] = null;
            }
        }
        wiresBuilt = false;
        linked = false;
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide)
            dropWires();
        super.remove();
    }

    @Override
    public void invalidate() {
        if (level != null && !level.isClientSide)
            dropWires();
        super.invalidate();
    }

    // ---- rendering ----

    @Override
    public AABB getRenderBoundingBox() {
        AABB box = new AABB(worldPosition);
        if (fanSocket != null) {
            box = box.minmax(new AABB(fanSocket));
            for (Vec3 p : points)
                box = box.minmax(new AABB(p, p));
        }
        return box.inflate(1.0);
    }

    // ---- NBT ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (fanSocket != null) {
            tag.putLong("FanSocket", fanSocket.asLong());
            ListTag list = new ListTag();
            for (Vec3 p : points) {
                CompoundTag t = new CompoundTag();
                t.putDouble("X", p.x);
                t.putDouble("Y", p.y);
                t.putDouble("Z", p.z);
                list.add(t);
            }
            tag.put("Points", list);
            if (creativeLaid)
                tag.putBoolean("CreativeLaid", true);
        }
        if (clientPacket)
            tag.putBoolean("Linked", linked);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        fanSocket = tag.contains("FanSocket") ? BlockPos.of(tag.getLong("FanSocket")) : null;
        List<Vec3> list = new ArrayList<>();
        if (fanSocket != null) {
            for (Tag t : tag.getList("Points", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                list.add(new Vec3(c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z")));
            }
        }
        points = List.copyOf(list);
        creativeLaid = tag.getBoolean("CreativeLaid");
        if (clientPacket)
            linked = tag.getBoolean("Linked");
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.pghv.data_socket_box")
                .withStyle(ChatFormatting.GRAY)));
        if (fanSocket == null) {
            tooltip.add(Component.literal("      ").append(Component.translatable("pghv.data_socket_box.goggles.empty")
                    .withStyle(ChatFormatting.DARK_GRAY)));
            return true;
        }
        tooltip.add(Component.literal("      ").append(Component.translatable(linked
                ? "pghv.data_socket_box.goggles.linked" : "pghv.data_socket_box.goggles.unlinked")
                .withStyle(linked ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        tooltip.add(Component.literal("      ").append(Component.translatable("pghv.data_socket_box.goggles.length",
                String.format(java.util.Locale.ROOT, "%.1f", cableLength())).withStyle(ChatFormatting.GRAY)));
        return true;
    }
}
