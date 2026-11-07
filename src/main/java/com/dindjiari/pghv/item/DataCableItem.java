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
package com.dindjiari.pghv.item;

import com.dindjiari.pghv.block.DataSocketBoxBlock;
import com.dindjiari.pghv.block.HvCoolingFanBlock;
import com.dindjiari.pghv.block.entity.DataSocketBoxBlockEntity;
import com.dindjiari.pghv.cable.DataPlug;
import com.dindjiari.pghv.registry.PghvDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import org.patryk3211.powergrid.kinetics.servo.ServoBlock;

import java.util.ArrayList;
import java.util.List;

/*
 * Data cable (pghv:data_cable): one cable with a round plug on each end, from the socket of a brass cooling fan
 * to the front socket of a data socket box (pghv:data_socket_box).
 *   1st right-click: any part of a brass fan -> start = socket cell (part 1), stored in DATA_CABLE_START.
 *   Right-clicks on other blocks: waypoints on the clicked surface (cable lies on floor / wall), stored in
 *   DATA_CABLE_ROUTE. Without waypoints the cable hangs freely.
 *   Right-click on a data socket box: plugs in (DataSocketBoxBlockEntity.plug), consumes 1 data cable (not creative).
 *   Max. 16 blocks cable length, max. 16 waypoints. Sneak + right-click in the air cancels.
 * Cores: copper = servo +, iron = servo -, gold = control (fan socket terminals 2/3/4 <-> box terminals 0/1/2).
 * The servo is connected to the connectors on top of the box with ordinary Power Grid wires.
 */
public class DataCableItem extends Item {
    /** Waypoints sit this far above the clicked surface (cable radius). */
    public static final double SURFACE_OFFSET = 1.0 / 16.0;

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

        GlobalPos start = stack.get(PghvDataComponents.DATA_CABLE_START.get());

        if (state.getBlock() instanceof DataSocketBoxBlock) {
            if (start == null) {
                message(player, Component.translatable("pghv.message.data_cable.need_start").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            if (!start.dimension().equals(level.dimension())) {
                clear(stack);
                message(player, Component.translatable("pghv.message.data_cable.invalid_start").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            boolean creative = player != null && player.isCreative();
            Result result = plug((ServerLevel) level, start.pos(), points(stack), pos, creative);
            message(player, result.message());
            if (result.clearStart())
                clear(stack);
            if (!result.success())
                return InteractionResult.FAIL;
            if (!creative)
                stack.shrink(1);
            return InteractionResult.SUCCESS;
        }

        if (state.getBlock() instanceof ServoBlock) {
            message(player, Component.translatable("pghv.message.data_cable.use_box").withStyle(ChatFormatting.GOLD));
            return InteractionResult.FAIL;
        }

        if (start != null) {
            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            if (!start.dimension().equals(level.dimension())
                    || DataPlug.fanSocketFace(level, start.pos()) == null) {
                clear(stack);
                message(player, Component.translatable("pghv.message.data_cable.invalid_start").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            Direction face = context.getClickedFace();
            Vec3 point = context.getClickLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(SURFACE_OFFSET));
            List<Vec3> pts = new ArrayList<>(points(stack));
            if (pts.size() >= DataPlug.MAX_POINTS) {
                message(player, Component.translatable("pghv.message.data_cable.too_many_points", DataPlug.MAX_POINTS)
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            if (DataPlug.lengthTo(level, start.pos(), pts, point) > DataPlug.MAX_LENGTH) {
                message(player, Component.translatable("pghv.message.data_cable.too_long", (int) DataPlug.MAX_LENGTH)
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            pts.add(point);
            setPoints(stack, pts);
            message(player, Component.translatable("pghv.message.data_cable.point", pts.size()).withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(PghvDataComponents.DATA_CABLE_START.get())) {
            if (!level.isClientSide) {
                clear(stack);
                message(player, Component.translatable("pghv.message.data_cable.cancelled").withStyle(ChatFormatting.GRAY));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    private static void clear(ItemStack stack) {
        stack.remove(PghvDataComponents.DATA_CABLE_START.get());
        stack.remove(PghvDataComponents.DATA_CABLE_ROUTE.get());
    }

    private static void message(@Nullable Player player, Component text) {
        if (player != null && !player.level().isClientSide)
            player.displayClientMessage(text, true);
    }

    // ---- waypoints on the stack ----

    public static List<Vec3> points(ItemStack stack) {
        CompoundTag route = stack.get(PghvDataComponents.DATA_CABLE_ROUTE.get());
        if (route == null)
            return List.of();
        List<Vec3> list = new ArrayList<>();
        for (Tag t : route.getList("P", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            list.add(new Vec3(c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z")));
        }
        return list;
    }

    private static void setPoints(ItemStack stack, List<Vec3> pts) {
        ListTag list = new ListTag();
        for (Vec3 p : pts) {
            CompoundTag c = new CompoundTag();
            c.putDouble("X", p.x);
            c.putDouble("Y", p.y);
            c.putDouble("Z", p.z);
            list.add(c);
        }
        CompoundTag route = new CompoundTag();
        route.put("P", list);
        stack.set(PghvDataComponents.DATA_CABLE_ROUTE.get(), route);
    }

    // ---- plugging in ----

    /** Outcome of {@link #plug}; clearStart = the stored start is used up or unusable. */
    public record Result(boolean success, boolean clearStart, Component message) {
        static Result fail(String key, boolean clearStart, Object... args) {
            return new Result(false, clearStart, Component.translatable(key, args).withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Plugs a data cable from the fan socket cell {@code socket} over {@code waypoints} into the box at {@code box}.
     * Server side; nothing is consumed here.
     */
    public static Result plug(ServerLevel level, BlockPos socket, List<Vec3> waypoints, BlockPos box, boolean creative) {
        if (DataPlug.fanSocketFace(level, socket) == null)
            return Result.fail("pghv.message.data_cable.invalid_start", true);
        if (!(level.getBlockEntity(box) instanceof DataSocketBoxBlockEntity be))
            return Result.fail("pghv.message.data_cable.failed", false);
        if (be.isPlugged())
            return Result.fail("pghv.message.data_cable.box_occupied", false);
        if (isFanPlugged(level, socket, box))
            return Result.fail("pghv.message.data_cable.exists", true);
        List<Vec3> path = DataPlug.path(level, socket, waypoints, box);
        if (path == null)
            return Result.fail("pghv.message.data_cable.failed", false);
        if (DataPlug.length(path) > DataPlug.MAX_LENGTH)
            return Result.fail("pghv.message.data_cable.too_long", false, (int) DataPlug.MAX_LENGTH);
        be.plug(socket, waypoints, creative);
        return new Result(true, true, Component.translatable("pghv.message.data_cable.connected")
                .withStyle(ChatFormatting.GREEN));
    }

    /** True if another loaded box within cable reach already holds a plug in this fan socket. */
    private static boolean isFanPlugged(ServerLevel level, BlockPos socket, BlockPos except) {
        int r = (int) Math.ceil(DataPlug.MAX_LENGTH);
        for (BlockPos p : BlockPos.betweenClosed(socket.offset(-r, -r, -r), socket.offset(r, r, r))) {
            if (p.equals(except) || !level.isLoaded(p))
                continue;
            if (level.getBlockState(p).getBlock() instanceof DataSocketBoxBlock
                    && level.getBlockEntity(p) instanceof DataSocketBoxBlockEntity other
                    && socket.equals(other.getFanSocket()))
                return true;
        }
        return false;
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
            int n = points(stack).size();
            if (n > 0)
                tooltip.add(Component.translatable("pghv.data_cable.tooltip.points", n).withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.cores").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.usage1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.usage4").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.usage2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.usage3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
