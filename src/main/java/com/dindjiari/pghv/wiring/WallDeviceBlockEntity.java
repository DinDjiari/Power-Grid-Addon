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

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.tomrox.pghv.cable.CableCores;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wall device block entity: owns the in-wall cables it was started from and remembers the cables of other devices
 * ending here ("incoming", owner position + port). Each cable = two plain Power Grid wires (L-L, N-N) built with
 * GlobalElectricNetworks.makeSimpleConnection whenever both ends are loaded (not persisted by Power Grid), like the
 * cable box.
 */
public abstract class WallDeviceBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation WIRE_ID = ResourceLocation.fromNamespaceAndPath("powergrid", "insulated_copper_wire");
    private static final double MIN_RESISTANCE = 1e-4;
    private static final float WALL_CONTACT = 1e-3f;
    // Wall connector switches, set in buildWallConnector only (buildCircuit runs in the super constructor).
    private SwitchedWire wallLine;
    private SwitchedWire wallNeutral;

    /** Client side: loaded devices, for the x-ray renderer. */
    public static final Set<WallDeviceBlockEntity> CLIENT_LOADED = ConcurrentHashMap.newKeySet();

    // Persistent (no field initialisers needed by the super constructor: these are only used after it).
    private List<InWallCable> owned = new ArrayList<>();
    /** Cables of other devices ending here: owner position + port used here. */
    private List<long[]> incoming = new ArrayList<>();

    // Server runtime
    private final Map<InWallCable, ElectricWire[]> built = new HashMap<>();
    private int timer;

    protected WallDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- wall connector ----

    /** Wall connector: two switched wires from L / N of port 0 to the connector terminal (one closed). */
    protected void buildWallConnector(CircuitBuilder builder) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof WallDeviceBlock<?> block) || block.wallConnector() < 0)
            return;
        int[] t = block.portTerminals(0);
        boolean neutral = state.hasProperty(WallDeviceBlock.WALL_NEUTRAL) && state.getValue(WallDeviceBlock.WALL_NEUTRAL);
        wallLine = builder.connectSwitch(WALL_CONTACT, builder.terminalNode(t[0]), builder.terminalNode(block.wallConnector()), !neutral);
        wallNeutral = builder.connectSwitch(WALL_CONTACT, builder.terminalNode(t[1]), builder.terminalNode(block.wallConnector()), neutral);
    }

    public void setWallNeutral(boolean neutral) {
        if (wallLine != null)
            wallLine.setState(!neutral);
        if (wallNeutral != null)
            wallNeutral.setState(neutral);
        notifyUpdate();
    }

    // ---- queries ----

    public List<InWallCable> getOwned() {
        return owned;
    }

    public int incomingCount() {
        return incoming.size();
    }

    /** Cables (owned or incoming) using a port of this device. */
    public int portUse(int port) {
        int n = 0;
        for (InWallCable c : owned)
            if (c.portA() == port)
                ++n;
        for (long[] in : incoming)
            if ((int) in[1] == port)
                ++n;
        return n;
    }

    /** Least used port (switch: first cable = input, second = output). */
    public int freePort() {
        if (!(getBlockState().getBlock() instanceof WallDeviceBlock<?> block))
            return 0;
        int best = 0;
        for (int p = 1; p < block.portCount(); ++p)
            if (portUse(p) < portUse(best))
                best = p;
        return best;
    }

    /**
     * Port an in-wall cable uses when the device is clicked at localHit (block-local 0..1, null = no click,
     * e.g. auto route). Default: least used port.
     */
    public int portFor(@Nullable Vec3 localHit) {
        return freePort();
    }

    /** Terminals {L, N} of a port of the device at pos (own wall device or any Power Grid block: 0/1). */
    public static int[] terminals(Level level, BlockPos pos, int port) {
        if (level.getBlockState(pos).getBlock() instanceof WallDeviceBlock<?> block)
            return block.portTerminals(port);
        return new int[] { 0, 1 };
    }

    public static boolean endpointValid(Level level, BlockPos pos, int port) {
        int[] t = terminals(level, pos, port);
        return new BlockWireEndpoint(pos, t[0]).isValid(level) && new BlockWireEndpoint(pos, t[1]).isValid(level);
    }

    // ---- cable management (server) ----

    /** True if an equal cable (same ends, ports and route) already exists. */
    public boolean hasCable(InWallCable cable) {
        return owned.contains(cable);
    }

    public void addCable(InWallCable cable) {
        if (owned.contains(cable))
            return;
        owned.add(cable);
        if (level != null && level.isLoaded(cable.b()) && level.getBlockEntity(cable.b()) instanceof WallDeviceBlockEntity other) {
            other.incoming.add(new long[] { worldPosition.asLong(), cable.portB() });
            other.setChanged();
            other.sendData();
        }
        setChanged();
        sendData();
        tryBuild(cable);
    }

    /** Removes an owned cable; drops its wire items at dropAt (unless laid in creative or drop is false). */
    public void removeCable(InWallCable cable, BlockPos dropAt, boolean drop) {
        dropWires(cable);
        if (!owned.remove(cable))
            return;
        if (level != null && level.isLoaded(cable.b()) && level.getBlockEntity(cable.b()) instanceof WallDeviceBlockEntity other) {
            other.removeIncoming(worldPosition, cable.portB());
        }
        if (drop && !cable.creative() && level != null && !level.isClientSide && cable.wires() > 0) {
            Item item = BuiltInRegistries.ITEM.get(WIRE_ID);
            Vec3 c = Vec3.atCenterOf(dropAt);
            int left = cable.wires();
            while (left > 0) {
                int n = Math.min(left, item.getDefaultMaxStackSize());
                Containers.dropItemStack(level, c.x, c.y, c.z, new ItemStack(item, n));
                left -= n;
            }
        }
        setChanged();
        sendData();
    }

    private void removeIncoming(BlockPos owner, int port) {
        for (int i = 0; i < incoming.size(); ++i) {
            long[] in = incoming.get(i);
            if (in[0] == owner.asLong() && (int) in[1] == port) {
                incoming.remove(i);
                setChanged();
                sendData();
                return;
            }
        }
    }

    /** Called from the block when it is broken or replaced: everything connected here goes, drops here. */
    public void onDeviceRemoved() {
        if (level == null || level.isClientSide)
            return;
        for (InWallCable c : List.copyOf(owned))
            removeCable(c, worldPosition, true);
        for (long[] in : List.copyOf(incoming)) {
            BlockPos ownerPos = BlockPos.of(in[0]);
            if (level.isLoaded(ownerPos) && level.getBlockEntity(ownerPos) instanceof WallDeviceBlockEntity owner) {
                for (InWallCable c : List.copyOf(owner.owned))
                    if (c.b().equals(worldPosition) && c.portB() == (int) in[1]) {
                        owner.removeCable(c, worldPosition, true);
                        break;
                    }
            }
            // Unloaded owner: it notices the missing end itself and drops the cable there.
        }
        incoming.clear();
    }

    // ---- electrics (server) ----

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;
        ++timer;
        if (timer % 20 == 0) {
            for (InWallCable c : List.copyOf(owned)) {
                if (level.isLoaded(c.b()) && !endpointValid(level, c.b(), c.portB())) {
                    removeCable(c, worldPosition, true); // far end gone: the cable comes back to its owner
                }
            }
        }
        if (timer % 10 == 0) {
            for (InWallCable c : owned) {
                ElectricWire[] w = built.get(c);
                if (w == null)
                    tryBuild(c);
                else if (!level.isLoaded(c.b()) || !endpointValid(level, c.b(), c.portB()))
                    dropWires(c);
            }
        }
    }

    private void tryBuild(InWallCable c) {
        if (level == null || level.isClientSide || built.containsKey(c) || !level.isLoaded(c.b()))
            return;
        if (!endpointValid(level, worldPosition, c.portA()) || !endpointValid(level, c.b(), c.portB()))
            return;
        int[] ta = terminals(level, worldPosition, c.portA());
        int[] tb = terminals(level, c.b(), c.portB());
        float r = (float) Math.max(MIN_RESISTANCE, CableCores.ohmPerBlock(level.registryAccess(), WIRE_ID) * Math.max(1, c.length()));
        ElectricWire[] w = new ElectricWire[2];
        try {
            for (int i = 0; i < 2; ++i) {
                w[i] = GlobalElectricNetworks.makeSimpleConnection(level,
                        new BlockWireEndpoint(worldPosition, ta[i]), new BlockWireEndpoint(c.b(), tb[i]), r);
                if (w[i] == null)
                    throw new IllegalStateException("no connection");
            }
        } catch (RuntimeException e) {
            for (ElectricWire x : w)
                if (x != null)
                    x.remove();
            LOGGER.debug("In-wall cable at {} not built yet: {}", worldPosition, e.toString());
            return;
        }
        built.put(c, w);
    }

    private void dropWires(InWallCable c) {
        ElectricWire[] w = built.remove(c);
        if (w != null)
            for (ElectricWire x : w)
                if (x != null)
                    x.remove();
    }

    private void dropAllWires() {
        for (InWallCable c : List.copyOf(built.keySet()))
            dropWires(c);
    }

    /** Owners of cables ending here drop their wires (rebuilt once this device is back). */
    private void releaseIncoming() {
        if (level == null)
            return;
        for (long[] in : incoming) {
            BlockPos ownerPos = BlockPos.of(in[0]);
            if (level.isLoaded(ownerPos) && level.getBlockEntity(ownerPos) instanceof WallDeviceBlockEntity owner)
                for (InWallCable c : owner.owned)
                    if (c.b().equals(worldPosition))
                        owner.dropWires(c);
        }
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) {
            dropAllWires();
            releaseIncoming();
        }
        super.remove();
    }

    @Override
    public void invalidate() {
        if (level != null && !level.isClientSide) {
            dropAllWires();
            releaseIncoming();
        }
        if (level != null && level.isClientSide)
            CLIENT_LOADED.remove(this);
        super.invalidate();
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && level.isClientSide)
            CLIENT_LOADED.add(this);
    }

    /** Voltage L - N at a port (0 if not simulated). */
    public double portVoltage(int port) {
        if (getElectricBehaviour() == null || !(getBlockState().getBlock() instanceof WallDeviceBlock<?> block))
            return 0;
        int[] t = block.portTerminals(port);
        var a = getElectricBehaviour().getTerminal(t[0]);
        var b = getElectricBehaviour().getTerminal(t[1]);
        if (a == null || b == null)
            return 0;
        double u = a.getVoltage() - b.getVoltage();
        return Double.isFinite(u) ? u : 0;
    }

    @Override
    public AABB getRenderBoundingBox() {
        AABB box = new AABB(worldPosition);
        for (InWallCable c : owned)
            for (Vec3 p : c.line())
                box = box.minmax(new AABB(p, p));
        return box.inflate(1);
    }

    // ---- NBT ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        ListTag list = new ListTag();
        for (InWallCable c : owned)
            list.add(c.toTag());
        tag.put("Cables", list);
        ListTag in = new ListTag();
        for (long[] i : incoming) {
            CompoundTag t = new CompoundTag();
            t.putLong("O", i[0]);
            t.putInt("P", (int) i[1]);
            in.add(t);
        }
        tag.put("Incoming", in);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        List<InWallCable> list = new ArrayList<>();
        for (Tag t : tag.getList("Cables", Tag.TAG_COMPOUND))
            list.add(InWallCable.fromTag((CompoundTag) t));
        if (level != null && !level.isClientSide) {
            for (InWallCable c : owned)
                if (!list.contains(c))
                    dropWires(c);
        }
        owned = list;
        List<long[]> in = new ArrayList<>();
        for (Tag t : tag.getList("Incoming", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            in.add(new long[] { c.getLong("O"), c.getInt("P") });
        }
        incoming = in;
    }

    // ---- goggles ----

    protected void cableLines(List<Component> tooltip) {
        tooltip.add(Component.literal("      ").append(Component.translatable("pghv.wall.goggles.cables",
                owned.size() + incoming.size()).withStyle(ChatFormatting.GRAY)));
    }

    protected static void line(List<Component> tooltip, Component text, int indent) {
        tooltip.add(Component.literal("    " + "  ".repeat(indent)).append(text));
    }

    @Nullable
    public static WallDeviceBlockEntity at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WallDeviceBlockEntity be ? be : null;
    }
}
