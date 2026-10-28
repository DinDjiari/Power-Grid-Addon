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

import com.tomrox.pghv.Pghv;
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.registry.PghvDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.ArrayList;
import java.util.List;

/*
 * Laying in-wall cables with Power Grid's insulated copper wire (takes priority over Power Grid's own wire handling
 * while it applies):
 *   right-click a wall device            -> start (laying state on the held stack, x-ray view on the client)
 *   right-click other blocks              -> waypoint (block centre)
 *   right-click a target                  -> finish; target = own wall device or any Power Grid electric block
 *   key R while looking at a target       -> automatic route from the last point (AutoRoutePayload)
 *   sneak + right-click into the air      -> cancel
 */
@EventBusSubscriber(modid = Pghv.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class InWallLaying {
    public static final double MAX_LENGTH = 64.0;
    public static final int MAX_POINTS = 32;

    private InWallLaying() {
    }

    public static boolean isWire(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(WallDeviceBlockEntity.WIRE_ID);
    }

    @Nullable
    public static CompoundTag laying(ItemStack stack) {
        return isWire(stack) ? stack.get(PghvDataComponents.INWALL_LAYING.get()) : null;
    }

    public static BlockPos start(CompoundTag laying) {
        return BlockPos.of(laying.getLong("S"));
    }

    public static List<Vec3> points(CompoundTag laying) {
        return InWallCable.points(laying.getList("P", Tag.TAG_COMPOUND));
    }

    private static void msg(@Nullable Player player, String key, ChatFormatting color, Object... args) {
        if (player != null && !player.level().isClientSide)
            player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }

    /** True if a cable can end at pos: own wall device or a Power Grid electric block with terminals 0 and 1. */
    public static boolean isTarget(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof WallDeviceBlock<?>)
            return true;
        return new BlockWireEndpoint(pos, 0).isValid(level) && new BlockWireEndpoint(pos, 1).isValid(level);
    }

    // ---- events ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!isWire(stack))
            return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        CompoundTag laying = stack.get(PghvDataComponents.INWALL_LAYING.get());
        var state = level.getBlockState(pos);
        boolean device = state.getBlock() instanceof WallDeviceBlock<?>;
        if (laying == null && (!device || ((WallDeviceBlock<?>) state.getBlock()).terminalAt(state,
                event.getHitVec().getLocation().subtract(Vec3.atLowerCornerOf(pos))) != null))
            return; // Power Grid's normal wire handling (also on the visible terminals of the wall box)
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide)
            return;
        Vec3 local = event.getHitVec().getLocation().subtract(Vec3.atLowerCornerOf(pos));
        if (laying == null) {
            begin((ServerLevel) level, player, stack, pos, local);
            return;
        }
        if (!laying.getString("D").equals(level.dimension().location().toString())) {
            stack.remove(PghvDataComponents.INWALL_LAYING.get());
            msg(player, "pghv.wall.msg.invalid_start", ChatFormatting.RED);
            return;
        }
        if (pos.equals(start(laying))) {
            msg(player, "pghv.wall.msg.same_device", ChatFormatting.RED);
            return;
        }
        if (isTarget(level, pos)) {
            finish((ServerLevel) level, player, stack, pos, List.of(), local);
            return;
        }
        addPoint(level, player, stack, Vec3.atCenterOf(pos));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (laying(stack) == null || !event.getEntity().isShiftKeyDown())
            return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!event.getLevel().isClientSide) {
            stack.remove(PghvDataComponents.INWALL_LAYING.get());
            msg(event.getEntity(), "pghv.wall.msg.cancelled", ChatFormatting.GRAY);
        }
    }

    // ---- steps (server) ----

    public static void begin(ServerLevel level, @Nullable Player player, ItemStack stack, BlockPos device) {
        begin(level, player, stack, device, null);
    }

    public static void begin(ServerLevel level, @Nullable Player player, ItemStack stack, BlockPos device, @Nullable Vec3 localHit) {
        WallDeviceBlockEntity be = WallDeviceBlockEntity.at(level, device);
        if (be == null)
            return;
        CompoundTag t = new CompoundTag();
        t.putLong("S", device.asLong());
        t.putInt("SP", be.portFor(localHit));
        t.putString("D", level.dimension().location().toString());
        t.put("P", InWallCable.pointsTag(List.of()));
        stack.set(PghvDataComponents.INWALL_LAYING.get(), t);
        msg(player, "pghv.wall.msg.start", ChatFormatting.GRAY);
    }

    public static boolean addPoint(Level level, @Nullable Player player, ItemStack stack, Vec3 point) {
        CompoundTag t = stack.get(PghvDataComponents.INWALL_LAYING.get());
        if (t == null)
            return false;
        List<Vec3> pts = new ArrayList<>(points(t));
        if (pts.size() >= MAX_POINTS) {
            msg(player, "pghv.wall.msg.too_many_points", ChatFormatting.RED, MAX_POINTS);
            return false;
        }
        pts.add(point);
        List<Vec3> line = new ArrayList<>();
        line.add(Vec3.atCenterOf(start(t)));
        line.addAll(pts);
        if (InWallCable.length(line) > MAX_LENGTH) {
            msg(player, "pghv.wall.msg.too_long", ChatFormatting.RED, (int) MAX_LENGTH);
            return false;
        }
        CompoundTag n = t.copy();
        n.put("P", InWallCable.pointsTag(pts));
        stack.set(PghvDataComponents.INWALL_LAYING.get(), n);
        msg(player, "pghv.wall.msg.point", ChatFormatting.GRAY, pts.size());
        return true;
    }

    /** Wire items for a cable length (Power Grid's itemsPerMeter of the insulated copper wire, at least 1). */
    public static int wiresFor(Level level, double length) {
        var entry = CableCores.entry(level.registryAccess(), WallDeviceBlockEntity.WIRE_ID);
        double perMeter = entry == null ? 0.5 : entry.itemsPerMeter();
        return Math.max(1, (int) Math.ceil(length * perMeter));
    }

    /** Finishes the cable at target, with extra waypoints appended (auto route). Returns true on success. */
    public static boolean finish(ServerLevel level, @Nullable Player player, ItemStack stack, BlockPos target, List<Vec3> extra) {
        return finish(level, player, stack, target, extra, null);
    }

    public static boolean finish(ServerLevel level, @Nullable Player player, ItemStack stack, BlockPos target, List<Vec3> extra,
                                 @Nullable Vec3 targetHit) {
        CompoundTag t = stack.get(PghvDataComponents.INWALL_LAYING.get());
        if (t == null)
            return false;
        BlockPos startPos = start(t);
        WallDeviceBlockEntity owner = WallDeviceBlockEntity.at(level, startPos);
        if (owner == null) {
            stack.remove(PghvDataComponents.INWALL_LAYING.get());
            msg(player, "pghv.wall.msg.invalid_start", ChatFormatting.RED);
            return false;
        }
        if (!isTarget(level, target) || target.equals(startPos)) {
            msg(player, "pghv.wall.msg.no_target", ChatFormatting.RED);
            return false;
        }
        List<Vec3> pts = new ArrayList<>(points(t));
        pts.addAll(extra);
        if (pts.size() > MAX_POINTS * 4) {
            msg(player, "pghv.wall.msg.too_many_points", ChatFormatting.RED, MAX_POINTS);
            return false;
        }
        int portA = t.getInt("SP");
        WallDeviceBlockEntity targetBe = WallDeviceBlockEntity.at(level, target);
        int portB = targetBe != null ? targetBe.portFor(targetHit) : 0;
        boolean creative = player != null && player.isCreative();
        InWallCable probe = new InWallCable(startPos, portA, target, portB, pts, 0, creative);
        double length = probe.length();
        if (length > MAX_LENGTH) {
            msg(player, "pghv.wall.msg.too_long", ChatFormatting.RED, (int) MAX_LENGTH);
            return false;
        }
        int wires = wiresFor(level, length);
        if (!creative && player != null && player.getInventory().countItem(stack.getItem()) < wires) {
            msg(player, "pghv.wall.msg.missing_items", ChatFormatting.RED, wires);
            return false;
        }
        InWallCable cable = new InWallCable(startPos, portA, target, portB, pts, creative ? 0 : wires, creative);
        if (owner.hasCable(cable)) {
            msg(player, "pghv.wall.msg.exists", ChatFormatting.RED);
            return false;
        }
        stack.remove(PghvDataComponents.INWALL_LAYING.get());
        if (!creative) {
            if (player != null)
                ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(stack.getItem()), wires, false);
            else
                stack.shrink(wires);
        }
        owner.addCable(cable);
        msg(player, "pghv.wall.msg.connected", ChatFormatting.GREEN, String.format(java.util.Locale.ROOT, "%.1f", length), wires);
        return true;
    }

    /** Key R: route automatically from the last point (or the start device) to the target. */
    public static void autoRoute(ServerPlayer player, BlockPos target) {
        ItemStack stack = player.getMainHandItem();
        CompoundTag t = laying(stack);
        if (t == null) {
            stack = player.getOffhandItem();
            t = laying(stack);
        }
        if (t == null)
            return;
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(target) || player.distanceToSqr(Vec3.atCenterOf(target)) > 64 * 64)
            return;
        if (!isTarget(level, target)) {
            msg(player, "pghv.wall.msg.no_target", ChatFormatting.RED);
            return;
        }
        BlockPos startPos = start(t);
        List<Vec3> pts = points(t);
        BlockPos from = pts.isEmpty() ? InWallRouting.anchor(level, startPos) : BlockPos.containing(pts.get(pts.size() - 1));
        BlockPos to = InWallRouting.anchor(level, target);
        List<BlockPos> path = InWallRouting.route(level, from, to);
        if (path == null) {
            msg(player, "pghv.wall.msg.no_route", ChatFormatting.RED);
            return;
        }
        List<Vec3> extra = InWallRouting.waypoints(path, startPos, target);
        if (!pts.isEmpty() && !extra.isEmpty() && extra.get(0).equals(pts.get(pts.size() - 1)))
            extra = extra.subList(1, extra.size());
        finish(level, player, stack, target, extra);
    }
}
