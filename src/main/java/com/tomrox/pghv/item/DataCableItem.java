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
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
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
                message(player, Component.translatable("pghv.message.data_cable.start").withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
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
        tooltip.add(Component.translatable("pghv.data_cable.tooltip.usage3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
