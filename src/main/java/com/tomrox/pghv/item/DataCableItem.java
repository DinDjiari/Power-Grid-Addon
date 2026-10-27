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
 *
 * Wire spawning follows Create: Power Grid by patryk3211, Apache-2.0
 * (IElectric.makeHangingWireConnection, ElectricInstructions.connect).
 */
package com.tomrox.pghv.item;

import com.tomrox.pghv.block.HvCoolingFanBlock;
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.registry.PghvDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.ImaginaryWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.patryk3211.powergrid.utility.BlockTrace;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.kinetics.servo.ServoBlock;

import java.util.ArrayList;
import java.util.List;

/*
 * Data cable (pghv:data_cable): connects the socket of a brass cooling fan with a Power Grid servo.
 *   1st right-click: any part of a brass fan -> start = socket cell (part 1), stored in DATA_CABLE_START.
 *   2nd right-click: servo -> three ordinary Power Grid hanging wires:
 *       socket 2 (+24 V) - servo 0 (coil +)  copper  powergrid:wire
 *       socket 3 (-)     - servo 1 (coil -)  iron    powergrid:iron_wire
 *       socket 4 (ctrl)  - servo 2 (control) gold    powergrid:golden_wire
 *   Resistance as Power Grid for the wire type and length: resistancePerItem x max(round(d x itemsPerMeter), 1)
 *   (d = straight distance between the terminals, values from the synced registry powergrid:wire_types).
 *   Each wire entity holds 1 wire item (the recipe used exactly one of each), so cutting it with the wire cutter
 *   gives back the ingredients and nothing more; the resistance is passed as override (see API_ANNAHMEN).
 *   Max. 16 blocks per wire; consumes 1 data cable (not in creative). Sneak + right-click in the air cancels.
 *
 * Ground mode (like Power Grid's block wires lying on blocks):
 *   after the start, right-click on any other block surface -> three Power Grid block wires (copper, iron, gold)
 *   are laid side by side (3/16 apart) from the socket to the clicked spot; further clicks extend them; the click
 *   on the servo finishes them at servo terminals 0/1/2. Path finding, rendering, wire cutter and electrics are
 *   Power Grid's own (WireItem.connect, BlockTrace). Item use: Power Grid's per-length count for each core,
 *   data cables consumed = the largest of the three (each data cable = 1 copper + 1 iron + 1 gold wire),
 *   so cutting the wires never returns more than was put in. Open ends are kept in DATA_CABLE_ROUTE.
 */
public class DataCableItem extends Item {
    public static final double MAX_LENGTH = 16.0;

    /** Socket terminal -> servo terminal, wire item; index = wire number. */
    public static final int[] SOCKET_TERMINALS = {
            HvCoolingFanBlock.SOCKET_POSITIVE, HvCoolingFanBlock.SOCKET_NEGATIVE, HvCoolingFanBlock.SOCKET_CONTROL };
    public static final int[] SERVO_TERMINALS = { 0, 1, 2 };
    public static final ResourceLocation[] WIRES = { CableCores.COPPER, CableCores.IRON, CableCores.GOLD };

    public DataCableItem(Properties properties) {
        super(properties);
    }

    // ---- interaction ----

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        if (state.getBlock() instanceof HvCoolingFanBlock fan) {
            if (!fan.isBrass()) {
                message(player, Component.translatable("pghv.message.data_cable.no_socket").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                BlockPos socket = HvCoolingFanBlock.socketPos(pos, state);
                stack.set(PghvDataComponents.DATA_CABLE_START.get(), GlobalPos.of(level.dimension(), socket));
                stack.remove(PghvDataComponents.DATA_CABLE_ROUTE.get());
                message(player, Component.translatable("pghv.message.data_cable.start").withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.getBlock() instanceof ServoBlock && stack.has(PghvDataComponents.DATA_CABLE_ROUTE.get())) {
            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            Result result = finishRoute((ServerLevel) level, player, stack, pos);
            message(player, result.message());
            if (result.clearStart() || result.success()) {
                stack.remove(PghvDataComponents.DATA_CABLE_ROUTE.get());
                stack.remove(PghvDataComponents.DATA_CABLE_START.get());
            }
            return result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }

        if (!(state.getBlock() instanceof ServoBlock) && stack.has(PghvDataComponents.DATA_CABLE_START.get())) {
            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            Result result = layOnGround((ServerLevel) level, player, stack, context.getClickLocation(),
                    context.getClickedFace(), pos, context.getHorizontalDirection());
            message(player, result.message());
            if (result.clearStart()) {
                stack.remove(PghvDataComponents.DATA_CABLE_ROUTE.get());
                stack.remove(PghvDataComponents.DATA_CABLE_START.get());
            }
            return result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }

        if (state.getBlock() instanceof ServoBlock) {
            GlobalPos start = stack.get(PghvDataComponents.DATA_CABLE_START.get());
            if (start == null) {
                message(player, Component.translatable("pghv.message.data_cable.need_start").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            if (!start.dimension().equals(level.dimension())) {
                stack.remove(PghvDataComponents.DATA_CABLE_START.get());
                message(player, Component.translatable("pghv.message.data_cable.invalid_start").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            Result result = connect((ServerLevel) level, start.pos(), pos);
            message(player, result.message());
            if (!result.success()) {
                if (result.clearStart())
                    stack.remove(PghvDataComponents.DATA_CABLE_START.get());
                return InteractionResult.FAIL;
            }
            stack.remove(PghvDataComponents.DATA_CABLE_START.get());
            if (player == null || !player.isCreative())
                stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(PghvDataComponents.DATA_CABLE_START.get())) {
            if (!level.isClientSide) {
                stack.remove(PghvDataComponents.DATA_CABLE_START.get());
                stack.remove(PghvDataComponents.DATA_CABLE_ROUTE.get());
                message(player, Component.translatable("pghv.message.data_cable.cancelled").withStyle(ChatFormatting.GRAY));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    private static void message(@Nullable Player player, Component text) {
        if (player != null && !player.level().isClientSide)
            player.displayClientMessage(text, true);
    }

    // ---- connecting ----

    /** Outcome of {@link #connect}; clearStart = the stored start is unusable and should be dropped. */
    public record Result(boolean success, boolean clearStart, Component message) {
        static Result fail(String key, boolean clearStart, Object... args) {
            return new Result(false, clearStart, Component.translatable(key, args).withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Spawns the three hanging wires between the socket (part 1 of a brass fan at {@code socket}) and the servo
     * at {@code servo}. Server side; nothing is consumed here.
     */
    public static Result connect(ServerLevel level, BlockPos socket, BlockPos servo) {
        BlockState socketState = level.getBlockState(socket);
        if (!(socketState.getBlock() instanceof HvCoolingFanBlock fan) || !fan.isBrass()
                || socketState.getValue(HvCoolingFanBlock.PART) != 1)
            return Result.fail("pghv.message.data_cable.invalid_start", true);
        if (!(level.getBlockState(servo).getBlock() instanceof ServoBlock))
            return Result.fail("pghv.message.data_cable.need_servo", false);

        List<BlockWireEndpoint[]> pairs = new ArrayList<>(3);
        double[] lengths = new double[3];
        WireItemEntry[] entries = new WireItemEntry[3];
        for (int i = 0; i < 3; ++i) {
            BlockWireEndpoint a = new BlockWireEndpoint(socket, SOCKET_TERMINALS[i]);
            BlockWireEndpoint b = new BlockWireEndpoint(servo, SERVO_TERMINALS[i]);
            ElectricBehaviour ba = a.getElectricBehaviour(level);
            ElectricBehaviour bb = b.getElectricBehaviour(level);
            if (ba == null || bb == null || a.getNode(level) == null || b.getNode(level) == null)
                return Result.fail("pghv.message.data_cable.failed", false);
            if (ba.hasConnection(a, b) || bb.hasConnection(b, a))
                return Result.fail("pghv.message.data_cable.exists", false);
            Vec3 pa = a.getExactPosition(level);
            Vec3 pb = b.getExactPosition(level);
            lengths[i] = pa.distanceTo(pb);
            if (lengths[i] > MAX_LENGTH)
                return Result.fail("pghv.message.data_cable.too_long", false, (int) MAX_LENGTH);
            entries[i] = CableCores.entry(level.registryAccess(), WIRES[i]);
            if (entries[i] == null || !BuiltInRegistries.ITEM.containsKey(WIRES[i]))
                return Result.fail("pghv.message.data_cable.failed", false);
            pairs.add(new BlockWireEndpoint[] { a, b });
        }

        List<HangingWireEntity> spawned = new ArrayList<>(3);
        for (int i = 0; i < 3; ++i) {
            Item wireItem = BuiltInRegistries.ITEM.get(WIRES[i]);
            float resistance = (float) wireResistance(entries[i], lengths[i]);
            HangingWireEntity entity = HangingWireEntity.create(level, pairs.get(i)[0], pairs.get(i)[1],
                    new ItemStack(wireItem, 1), resistance);
            if (!level.tryAddFreshEntityWithPassengers(entity)) {
                for (HangingWireEntity done : spawned)
                    done.discard();
                return Result.fail("pghv.message.data_cable.failed", false);
            }
            spawned.add(entity);
        }
        return new Result(true, true, Component.translatable("pghv.message.data_cable.connected")
                .withStyle(ChatFormatting.GREEN));
    }

    /** Power Grid's hanging wire resistance: resistancePerItem x max(round(length x itemsPerMeter), 1). */
    public static double wireResistance(WireItemEntry entry, double length) {
        int items = Math.max(Math.round((float) (length * entry.itemsPerMeter())), 1);
        return entry.resistancePerItem() * items;
    }

    // ---- ground mode (block wires) ----

    /** Lateral spacing of the three cores on the ground (Power Grid grid = 1/16 block). */
    public static final double GROUND_SPACING = 3.0 / 16.0;

    /** Open ends of the route, or the socket terminals if nothing is laid yet; null if unusable. */
    @Nullable
    private static IWireEndpoint[] currentEnds(ServerLevel level, ItemStack stack) {
        GlobalPos start = stack.get(PghvDataComponents.DATA_CABLE_START.get());
        if (start == null || !start.dimension().equals(level.dimension()))
            return null;
        CompoundTag route = stack.get(PghvDataComponents.DATA_CABLE_ROUTE.get());
        IWireEndpoint[] ends = new IWireEndpoint[3];
        for (int i = 0; i < 3; ++i) {
            if (route == null) {
                BlockState socketState = level.getBlockState(start.pos());
                if (!(socketState.getBlock() instanceof HvCoolingFanBlock fan) || !fan.isBrass()
                        || socketState.getValue(HvCoolingFanBlock.PART) != 1)
                    return null;
                ends[i] = new BlockWireEndpoint(start.pos(), SOCKET_TERMINALS[i]);
            } else {
                if (!route.contains("W" + i))
                    return null;
                BlockWireEntityEndpoint e = new BlockWireEntityEndpoint();
                e.read(route.getCompound("W" + i));
                if (e.getEntity(level) == null)
                    return null;
                ends[i] = e;
            }
        }
        return ends;
    }

    /** How many data cables the player can spend (creative: plenty). */
    private static int budget(@Nullable Player player, ItemStack stack) {
        if (player == null)
            return stack.getCount();
        if (player.isCreative())
            return 4096;
        return player.getInventory().countItem(stack.getItem());
    }

    private static void consume(@Nullable Player player, ItemStack stack, int count) {
        if (count <= 0)
            return;
        if (player == null) {
            stack.shrink(Math.min(count, stack.getCount()));
            return;
        }
        if (player.isCreative())
            return;
        int fromHand = Math.min(count, stack.getCount());
        stack.shrink(fromHand);
        int rest = count - fromHand;
        if (rest > 0)
            net.minecraft.world.ContainerHelper.clearOrCountMatchingItems(player.getInventory(),
                    s -> s.getItem() instanceof DataCableItem, rest, false);
    }

    /**
     * Mirrors the checks of Power Grid's WireItem.connect without changing the world: path found and the number
     * of wire items that call would use. -1 = no path.
     */
    private static int plannedItems(ServerLevel level, IWireEndpoint from, IWireEndpoint to, WireItemEntry entry) {
        Vec3 last = from.getExactPosition(level);
        if (from.type() != WireEndpointType.BLOCK_WIRE)
            last = BlockTrace.alignPosition(last);
        Vec3 target = to.getExactPosition(level);
        Direction continueDir = null;
        BlockWireEntity existing = null;
        if (from instanceof BlockWireEntityEndpoint bwe) {
            existing = bwe.getEntity(level);
            if (existing == null || existing.segments.isEmpty() || !bwe.getEnd())
                return -1;
            continueDir = existing.segments.get(existing.segments.size() - 1).direction;
        }
        var terminal = to instanceof BlockWireEndpoint b ? b.getTerminalPlacement(level) : null;
        var result = WireItem.alternateWirePlacement(null)
                ? BlockTrace.alternatePath(last, target)
                : BlockTrace.findPath(level, last, target, terminal, continueDir);
        if (result == null || !result.reachedTarget())
            return -1;
        float added = 0;
        for (var point : result.points())
            added += point.length();
        if (existing == null)
            return (int) Math.ceil(added * entry.itemsPerMeter());
        return Math.max(0, (int) Math.ceil((existing.getTotalLength() + added) * entry.itemsPerMeter()
                - existing.getWireCount()));
    }

    /**
     * Connects the three ends to the three targets with Power Grid's own block-wire placement.
     * All paths are checked first; the world is only changed if all three fit. Returns the new open ends.
     */
    @Nullable
    private static BlockWireEntity[] placeAll(ServerLevel level, @Nullable Player player, ItemStack stack,
                                              IWireEndpoint[] ends, IWireEndpoint[] targets, Result[] error) {
        int[] need = new int[3];
        int max = 0;
        for (int i = 0; i < 3; ++i) {
            WireItemEntry entry = CableCores.entry(level.registryAccess(), WIRES[i]);
            if (entry == null || !BuiltInRegistries.ITEM.containsKey(WIRES[i])) {
                error[0] = Result.fail("pghv.message.data_cable.failed", false);
                return null;
            }
            need[i] = plannedItems(level, ends[i], targets[i], entry);
            if (need[i] < 0) {
                error[0] = Result.fail("pghv.message.data_cable.no_path", false);
                return null;
            }
            max = Math.max(max, need[i]);
        }
        if (budget(player, stack) < max) {
            error[0] = Result.fail("pghv.message.data_cable.missing_items", false, max);
            return null;
        }
        BlockWireEntity[] placed = new BlockWireEntity[3];
        for (int i = 0; i < 3; ++i) {
            // Power Grid takes the wire items from this stack (player = null); its count = what this core may use.
            ItemStack wires = new ItemStack(BuiltInRegistries.ITEM.get(WIRES[i]), Math.max(need[i], 1));
            var r = WireItem.connect(level, wires, null, ends[i], targets[i]);
            if (!r.getResult().consumesAction() || r.getObject() == null) {
                error[0] = Result.fail("pghv.message.data_cable.failed", true);
                return null;
            }
            placed[i] = r.getObject();
        }
        consume(player, stack, max);
        return placed;
    }

    /** Click on a block surface while a start is set: lay or extend the three cores up to that spot. */
    public static Result layOnGround(ServerLevel level, @Nullable Player player, ItemStack stack, Vec3 hit,
                                     Direction face, BlockPos clicked, Direction horizontalLook) {
        IWireEndpoint[] ends = currentEnds(level, stack);
        if (ends == null)
            return Result.fail("pghv.message.data_cable.invalid_start", true);

        Vec3 base = hit.relative(face, 1 / 32f);
        // Side-by-side axis: along the clicked wall, or across the laying direction on floors/ceilings.
        Direction across;
        if (face.getAxis().isHorizontal()) {
            across = face.getClockWise();
        } else {
            Vec3 from = ends[1].getExactPosition(level);
            double dx = base.x - from.x, dz = base.z - from.z;
            Direction along = Math.abs(dx) < 1.0e-3 && Math.abs(dz) < 1.0e-3 ? horizontalLook
                    : Direction.getNearest(dx, 0, dz);
            across = along.getClockWise();
        }
        Vec3 acrossVec = Vec3.atLowerCornerOf(across.getNormal());
        // Keep all three on the clicked block face: centre between 4/16 and 12/16 of the block on that axis.
        Direction.Axis axis = across.getAxis();
        double min = clicked.get(axis) + 4.0 / 16.0, maxC = clicked.get(axis) + 12.0 / 16.0;
        double centre = Math.max(min, Math.min(maxC, base.get(axis)));
        base = base.with(axis, centre);

        IWireEndpoint[] targets = new IWireEndpoint[3];
        Integer[] order = { 0, 1, 2 };
        final Vec3 av = acrossVec;
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(i -> ends[i].getExactPosition(level).dot(av)));
        for (int rank = 0; rank < 3; ++rank)
            targets[order[rank]] = new ImaginaryWireEndpoint(base.add(acrossVec.scale((rank - 1) * GROUND_SPACING)));

        Result[] error = new Result[1];
        BlockWireEntity[] placed = placeAll(level, player, stack, ends, targets, error);
        if (placed == null)
            return error[0];
        CompoundTag route = new CompoundTag();
        for (int i = 0; i < 3; ++i) {
            CompoundTag t = new CompoundTag();
            new BlockWireEntityEndpoint(placed[i], true).write(t);
            route.put("W" + i, t);
        }
        stack.set(PghvDataComponents.DATA_CABLE_ROUTE.get(), route);
        return new Result(true, false, Component.translatable("pghv.message.data_cable.laid")
                .withStyle(ChatFormatting.GRAY));
    }

    /** Click on the servo while cores lie on the ground: run them into servo terminals 0/1/2. */
    public static Result finishRoute(ServerLevel level, @Nullable Player player, ItemStack stack, BlockPos servo) {
        IWireEndpoint[] ends = currentEnds(level, stack);
        if (ends == null || !stack.has(PghvDataComponents.DATA_CABLE_ROUTE.get()))
            return Result.fail("pghv.message.data_cable.invalid_start", true);
        IWireEndpoint[] targets = new IWireEndpoint[3];
        for (int i = 0; i < 3; ++i) {
            BlockWireEndpoint b = new BlockWireEndpoint(servo, SERVO_TERMINALS[i]);
            if (b.getElectricBehaviour(level) == null || b.getNode(level) == null)
                return Result.fail("pghv.message.data_cable.failed", false);
            targets[i] = b;
        }
        Result[] error = new Result[1];
        if (placeAll(level, player, stack, ends, targets, error) == null)
            return error[0];
        return new Result(true, true, Component.translatable("pghv.message.data_cable.connected")
                .withStyle(ChatFormatting.GREEN));
    }

    // ---- display ----

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(PghvDataComponents.DATA_CABLE_START.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        GlobalPos start = stack.get(PghvDataComponents.DATA_CABLE_START.get());
        if (start != null) {
            tooltip.add(Component.translatable("pghv.data_cable.tooltip.start", start.pos().toShortString())
                    .withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.cores").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.usage1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.usage2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.usage4").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.usage3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
