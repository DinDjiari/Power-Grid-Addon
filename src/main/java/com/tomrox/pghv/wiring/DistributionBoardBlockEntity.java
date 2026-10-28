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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedTags;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One block of a distribution board: 4 rail places.
 *
 * Circuit (built once, never rebuilt): terminals 0 L_in, 1 N_in, 2 L bus, 3 N bus, 4..7 L_out of place 0..3.
 *  - feed link L_in -> L and N_in -> N (switched wires): closed only in the controller block and only while all main
 *    switches are on and no RCD of the board is off/tripped;
 *  - place s: switched wire L -> L_out(s), closed while a fuse sits there and is CLOSED.
 * Neighbouring blocks of the board connect their L_in, N_in, L and N with plain wires (owned by the block below / to the
 * left, rebuilt after loading). The controller measures the feed link: energy for the meter(s), residual current for
 * the RCD (|I_L| - |I_N| > 30 mA trips all RCDs). Fuses blow above their rating like Power Grid's fuse holder.
 * Fields have no initialisers: buildCircuit runs in the super constructor.
 */
public class DistributionBoardBlockEntity extends WallDeviceBlockEntity {
    public static final int[] RATINGS = { 6, 10, 13, 16, 20, 25, 32, 63 };
    public static final int DEFAULT_RATING = 16;
    private static final float CONTACT = 1e-3f;
    private static final double RCD_TRIP = 0.03;
    public static final long MONTH_TICKS = 24000L * 30;

    // persistent
    private BoardModule[] modules;
    /** Fuse: 0 open, 1 closed, 2 blown. Lever modules: 1 on, 0 off; RCD tripped = 3. */
    private int[] states;
    private int[] ratings;
    private double energyWh;
    private double monthWh;
    private long monthIndex;
    @Nullable
    private BlockPos controller;
    private boolean isController;

    // runtime
    private SwitchedWire linkL, linkN;
    private SwitchedWire[] placeWires;
    private ElectricWire[] busUp, busRight;
    private int busTimer;
    private float powerW;
    private int syncTimer;

    public DistributionBoardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        ensureArrays();
    }

    private void ensureArrays() {
        if (modules == null) {
            modules = new BoardModule[4];
            states = new int[4];
            ratings = new int[] { DEFAULT_RATING, DEFAULT_RATING, DEFAULT_RATING, DEFAULT_RATING };
        }
        if (placeWires == null)
            placeWires = new SwitchedWire[4];
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        ensureArrays();
        builder.setTerminalCount(8);
        linkL = builder.connectSwitch(CONTACT, builder.terminalNode(0), builder.terminalNode(2), false);
        linkN = builder.connectSwitch(CONTACT, builder.terminalNode(1), builder.terminalNode(3), false);
        for (int s = 0; s < 4; ++s)
            placeWires[s] = builder.connectSwitch(CONTACT, builder.terminalNode(2), builder.terminalNode(4 + s), placeClosed(s));
    }

    // ---- queries ----

    @Nullable
    public BoardModule module(int slot) {
        return modules[slot];
    }

    public int state(int slot) {
        return states[slot];
    }

    public int rating(int slot) {
        return ratings[slot];
    }

    public boolean isController() {
        return isController;
    }

    @Nullable
    public BlockPos getController() {
        return controller;
    }

    public double energyWh() {
        return energyWh;
    }

    public double monthWh() {
        return monthWh;
    }

    public float powerW() {
        return powerW;
    }

    private boolean placeClosed(int s) {
        return modules != null && modules[s] == BoardModule.FUSE && states[s] == 1;
    }

    /** Current through the fuse at a place (A), 0 if none. */
    public double placeCurrent(int s) {
        if (placeWires == null || placeWires[s] == null || !placeClosed(s))
            return 0;
        double i = placeWires[s].current();
        return Double.isFinite(i) ? Math.abs(i) : 0;
    }

    // ---- structure ----

    public void setController(BlockPos ctrl, boolean self) {
        boolean changed = !ctrl.equals(controller) || self != isController;
        controller = ctrl.immutable();
        isController = self;
        if (changed) {
            dropBus();
            updateLink();
            setChanged();
            sendData();
        }
    }

    /** All board blocks (from the controller's point of view). */
    private List<BlockPos> board() {
        if (level == null)
            return List.of(worldPosition);
        Direction facing = getBlockState().getValue(DistributionBoardBlock.HORIZONTAL_FACING);
        return BoardStructure.component(level, worldPosition, facing, null, null);
    }

    /** Recomputes the feed link of the controller: closed if all main switches are on and no RCD is off/tripped. */
    public void updateLink() {
        if (level == null || level.isClientSide || linkL == null)
            return;
        boolean closed = isController;
        if (closed) {
            for (BlockPos p : board()) {
                if (!(level.getBlockEntity(p) instanceof DistributionBoardBlockEntity be))
                    continue;
                for (int s = 0; s < 4; ++s) {
                    if ((be.modules[s] == BoardModule.MAIN_SWITCH || be.modules[s] == BoardModule.RCD) && be.states[s] != 1)
                        closed = false;
                }
            }
        }
        linkL.setState(closed);
        linkN.setState(closed);
    }

    private void notifyController() {
        if (level == null || controller == null)
            return;
        if (level.getBlockEntity(controller) instanceof DistributionBoardBlockEntity c)
            c.updateLink();
    }

    // ---- interaction (server) ----

    private void msg(@Nullable Player player, String key, Object... args) {
        if (player != null)
            player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    /** Right-click on place slot of the open board. Returns true if something happened. */
    public boolean interact(int slot, Player player, ItemStack held) {
        BoardModule here = modules[slot];
        BoardModule inserting = BoardModule.of(held);
        if (here == null && inserting != null)
            return insert(slot, inserting, player, held);
        if (here == BoardModule.FUSE && held.is(ModdedTags.Item.FUSE_RESETTING.tag) && states[slot] != 1) {
            states[slot] = 1;
            placeWires[slot].setState(true);
            if (!player.isCreative())
                held.shrink(1);
            changed(true);
            level.playSound(null, worldPosition, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.5f, 1.4f);
            return true;
        }
        if (held.isEmpty() && here == BoardModule.FUSE && states[slot] == 2) {
            states[slot] = 0; // blown wire taken out
            changed(true);
            return true;
        }
        if (held.isEmpty() && here != null && here.hasLever()) {
            toggleLever(slot);
            return true;
        }
        return false;
    }

    public boolean insert(int slot, BoardModule module, @Nullable Player player, ItemStack held) {
        if (module.width == 4) {
            for (int s = 0; s < 4; ++s)
                if (modules[s] != null) {
                    msg(player, "pghv.board.msg.meter_space");
                    return false;
                }
            for (int s = 0; s < 4; ++s) {
                modules[s] = module;
                states[s] = 0;
            }
        } else {
            if (modules[slot] != null)
                return false;
            modules[slot] = module;
            states[slot] = module == BoardModule.FUSE ? 1 : 0;
            ratings[slot] = DEFAULT_RATING;
        }
        if (player == null || !player.isCreative())
            held.shrink(1);
        placeWires[slot].setState(placeClosed(slot));
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4f, 1.8f);
        changed(true);
        return true;
    }

    public void remove(int slot, @Nullable Player player) {
        BoardModule m = modules[slot];
        if (m == null)
            return;
        ItemStack drop = new ItemStack(m.item());
        if (m.width == 4) {
            for (int s = 0; s < 4; ++s)
                modules[s] = null;
        } else {
            modules[slot] = null;
        }
        for (int s = 0; s < 4; ++s)
            placeWires[s].setState(placeClosed(s));
        if (player != null && !player.isCreative()) {
            if (!player.getInventory().add(drop))
                player.drop(drop, false);
        }
        changed(true);
    }

    public void toggleLever(int slot) {
        BoardModule m = modules[slot];
        if (m == null || !m.hasLever())
            return;
        states[slot] = states[slot] == 1 ? 0 : 1; // also resets a tripped RCD (3 -> 1)
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, states[slot] == 1 ? 1.2f : 0.9f);
        changed(true);
    }

    public void cycleRating(int slot, @Nullable Player player) {
        if (modules[slot] != BoardModule.FUSE)
            return;
        int i = 0;
        while (i < RATINGS.length && RATINGS[i] <= ratings[slot])
            ++i;
        ratings[slot] = RATINGS[i % RATINGS.length];
        msg(player, "pghv.board.msg.rating", ratings[slot]);
        changed(false);
    }

    private void changed(boolean link) {
        if (link)
            notifyController();
        setChanged();
        sendData();
    }

    /** Block broken: modules drop here. */
    public void dropModules() {
        if (level == null)
            return;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        for (int s = 0; s < 4; ++s) {
            BoardModule m = modules[s];
            if (m == null)
                continue;
            Containers.dropItemStack(level, c.x, c.y, c.z, new ItemStack(m.item()));
            if (m.width == 4) {
                for (int k = 0; k < 4; ++k)
                    modules[k] = null;
            }
            modules[s] = null;
        }
        dropBus();
    }

    // ---- ticking (server) ----

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;
        // fuses
        for (int s = 0; s < 4; ++s) {
            if (placeClosed(s) && placeCurrent(s) > ratings[s]) {
                states[s] = 2;
                placeWires[s].setState(false);
                level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.8f);
                changed(false);
            }
        }
        if (isController && linkL != null) {
            double il = finite(linkL.current()), in = finite(linkN.current());
            // RCD
            if (linkL.getState() && Math.abs(Math.abs(il) - Math.abs(in)) > RCD_TRIP && hasRcd()) {
                for (BlockPos p : board())
                    if (level.getBlockEntity(p) instanceof DistributionBoardBlockEntity be)
                        for (int s = 0; s < 4; ++s)
                            if (be.modules[s] == BoardModule.RCD && be.states[s] == 1) {
                                be.states[s] = 3;
                                be.changed(false);
                            }
                level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 0.6f);
                updateLink();
            }
            // energy
            double u = Math.abs(voltage(2) - voltage(3));
            powerW = (float) (Math.abs(il) * u);
            long month = level.getGameTime() / MONTH_TICKS;
            if (month != monthIndex) {
                monthIndex = month;
                monthWh = 0;
            }
            double wh = powerW / 20.0 / 3600.0;
            energyWh += wh;
            monthWh += wh;
            if (++syncTimer >= 20) {
                syncTimer = 0;
                setChanged();
                sendData();
            }
        }
        if (++busTimer >= 10) {
            busTimer = 0;
            maintainBus();
            if (isController)
                updateLink(); // neighbours may have loaded after us
        }
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0;
    }

    private double voltage(int terminal) {
        if (getElectricBehaviour() == null)
            return 0;
        var n = getElectricBehaviour().getTerminal(terminal);
        return n == null ? 0 : finite(n.getVoltage());
    }

    private boolean hasRcd() {
        for (BlockPos p : board())
            if (level.getBlockEntity(p) instanceof DistributionBoardBlockEntity be)
                for (BoardModule m : be.modules)
                    if (m == BoardModule.RCD)
                        return true;
        return false;
    }

    /** Values of the board's controller (for meters in other blocks). */
    @Nullable
    public DistributionBoardBlockEntity controllerBe() {
        if (level == null || controller == null)
            return null;
        return level.getBlockEntity(controller) instanceof DistributionBoardBlockEntity c ? c : null;
    }

    // ---- bus between neighbouring blocks ----

    private void maintainBus() {
        Direction facing = getBlockState().getValue(DistributionBoardBlock.HORIZONTAL_FACING);
        busUp = maintain(busUp, getBlockState().getValue(DistributionBoardBlock.UP) ? worldPosition.above() : null);
        busRight = maintain(busRight, getBlockState().getValue(DistributionBoardBlock.RIGHT)
                ? worldPosition.relative(BoardStructure.right(facing)) : null);
    }

    @Nullable
    private ElectricWire[] maintain(@Nullable ElectricWire[] wires, @Nullable BlockPos other) {
        if (other == null || !level.isLoaded(other) || !(level.getBlockEntity(other) instanceof DistributionBoardBlockEntity)) {
            drop(wires);
            return null;
        }
        if (wires != null)
            return wires;
        ElectricWire[] w = new ElectricWire[4];
        try {
            for (int t = 0; t < 4; ++t) {
                if (!new BlockWireEndpoint(worldPosition, t).isValid(level) || !new BlockWireEndpoint(other, t).isValid(level))
                    throw new IllegalStateException("not ready");
                w[t] = GlobalElectricNetworks.makeSimpleConnection(level, new BlockWireEndpoint(worldPosition, t),
                        new BlockWireEndpoint(other, t), CONTACT);
                if (w[t] == null)
                    throw new IllegalStateException("no connection");
            }
        } catch (RuntimeException e) {
            drop(w);
            return null;
        }
        return w;
    }

    private static void drop(@Nullable ElectricWire[] w) {
        if (w == null)
            return;
        for (ElectricWire x : w)
            if (x != null)
                x.remove();
    }

    private void dropBus() {
        drop(busUp);
        drop(busRight);
        busUp = null;
        busRight = null;
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide)
            dropBus();
        super.remove();
    }

    @Override
    public void invalidate() {
        if (level != null && !level.isClientSide)
            dropBus();
        super.invalidate();
    }

    // ---- in-wall ports ----

    /** Clicked on a fuse of the open board: that circuit; otherwise the feed (or a free circuit for auto route). */
    @Override
    public int portFor(@Nullable Vec3 localHit) {
        BlockState state = getBlockState();
        if (localHit != null) {
            if (state.getValue(DistributionBoardBlock.OPEN)) {
                int s = BoardStructure.slotAt(BoardStructure.northLocal(localHit, state.getValue(DistributionBoardBlock.HORIZONTAL_FACING)));
                if (s >= 0 && modules[s] == BoardModule.FUSE)
                    return 1 + s;
            }
            return 0;
        }
        if (portUse(0) == 0)
            return 0;
        for (int s = 0; s < 4; ++s)
            if (modules[s] == BoardModule.FUSE && portUse(1 + s) == 0)
                return 1 + s;
        return 0;
    }

    // ---- NBT ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        ensureArrays();
        for (int s = 0; s < 4; ++s) {
            CompoundTag t = new CompoundTag();
            if (modules[s] != null)
                t.putString("M", modules[s].name());
            t.putInt("S", states[s]);
            t.putInt("R", ratings[s]);
            tag.put("Place" + s, t);
        }
        tag.putDouble("EnergyWh", energyWh);
        tag.putDouble("MonthWh", monthWh);
        tag.putLong("Month", monthIndex);
        if (controller != null)
            tag.putLong("Controller", controller.asLong());
        tag.putBoolean("IsController", isController);
        if (clientPacket)
            tag.putFloat("Power", powerW);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        ensureArrays();
        for (int s = 0; s < 4; ++s) {
            CompoundTag t = tag.getCompound("Place" + s);
            modules[s] = t.contains("M") ? BoardModule.byName(t.getString("M")) : null;
            states[s] = t.getInt("S");
            ratings[s] = t.contains("R") ? t.getInt("R") : DEFAULT_RATING;
            if (placeWires[s] != null)
                placeWires[s].setState(placeClosed(s));
        }
        energyWh = tag.getDouble("EnergyWh");
        monthWh = tag.getDouble("MonthWh");
        monthIndex = tag.getLong("Month");
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        isController = tag.getBoolean("IsController");
        if (clientPacket)
            powerW = tag.getFloat("Power");
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide)
            updateLink();
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        line(tooltip, Component.translatable("block.pghv.distribution_board").withStyle(ChatFormatting.GRAY), 0);
        boolean meter = false;
        for (int s = 0; s < 4; ++s) {
            BoardModule m = modules[s];
            if (m == null)
                continue;
            if (m == BoardModule.METER) {
                meter = true;
                continue;
            }
            Component text = switch (m) {
                case FUSE -> Component.translatable("pghv.board.goggles.fuse", s + 1, ratings[s],
                        Component.translatable("pghv.board.goggles.fuse_state." + states[s]),
                        String.format(Locale.ROOT, "%.1f", placeCurrent(s)));
                case RCD -> Component.translatable("pghv.board.goggles.rcd", s + 1,
                        Component.translatable(states[s] == 1 ? "pghv.wall.goggles.on" : states[s] == 3
                                ? "pghv.board.goggles.tripped" : "pghv.wall.goggles.off"));
                default -> Component.translatable("pghv.board.goggles.main", s + 1,
                        Component.translatable(states[s] == 1 ? "pghv.wall.goggles.on" : "pghv.wall.goggles.off"));
            };
            ChatFormatting color = (m == BoardModule.FUSE ? states[s] == 1 : states[s] == 1) ? ChatFormatting.GREEN : ChatFormatting.GOLD;
            line(tooltip, text.copy().withStyle(color), 1);
        }
        if (meter) {
            DistributionBoardBlockEntity c = controllerBe();
            if (c != null) {
                line(tooltip, Component.translatable("pghv.board.goggles.power",
                        String.format(Locale.ROOT, "%.2f", c.powerW / 1000.0)).withStyle(ChatFormatting.AQUA), 1);
                line(tooltip, Component.translatable("pghv.board.goggles.total",
                        String.format(Locale.ROOT, "%.2f", c.energyWh / 1000.0)).withStyle(ChatFormatting.AQUA), 1);
                line(tooltip, Component.translatable("pghv.board.goggles.month",
                        String.format(Locale.ROOT, "%.2f", c.monthWh / 1000.0)).withStyle(ChatFormatting.AQUA), 1);
            }
        }
        cableLines(tooltip);
        return true;
    }
}
