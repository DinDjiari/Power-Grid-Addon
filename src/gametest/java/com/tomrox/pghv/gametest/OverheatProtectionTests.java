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
package com.tomrox.pghv.gametest;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.tomrox.pghv.Pghv;
import com.tomrox.pghv.block.HvCoolingFanBlock;
import com.tomrox.pghv.block.entity.HvCoolingFanBlockEntity;
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.item.DataCableItem;
import com.tomrox.pghv.registry.PghvDataComponents;
import com.tomrox.pghv.registry.PghvItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.electricswitch.HvSwitchBlock;
import org.patryk3211.powergrid.electricity.electricswitch.HvSwitchBlockEntity;
import org.patryk3211.powergrid.electricity.transformer.TransformerBlockEntity;
import org.patryk3211.powergrid.electricity.transformer.TransformerMediumBlock;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.kinetics.servo.ServoBlock;
import org.patryk3211.powergrid.kinetics.servo.ServoBlockEntity;

import java.util.List;

/** Brass fan overheat protection: socket, hysteresis, control voltage, wrench direction, data cable, servo + HV switch. */
@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class OverheatProtectionTests {
    private static final Direction FACING = Direction.SOUTH;

    // ---- helpers ----

    /** Medium transformer with origin (part 0) at {@code origin}, parts along +x and +y. */
    private static void placeTransformer(GameTestHelper helper, BlockPos origin) {
        BlockState base = ModdedBlocks.TRANSFORMER_MEDIUM.getDefaultState()
                .setValue(TransformerMediumBlock.HORIZONTAL_AXIS, Direction.Axis.X);
        for (int part = 0; part < 4; ++part) {
            BlockPos cell = origin.relative(Direction.Axis.X, part & 1).above((part & 2) >> 1);
            helper.setBlock(cell, base.setValue(TransformerMediumBlock.PART, part));
        }
    }

    /** Places a fan item like a player: onto the south face of {@code against} (transformer or floor block). */
    private static void placeFan(GameTestHelper helper, BlockPos against, Direction face, Item fanItem, BlockPos playerPos) {
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, playerPos);
        player.setYRot(FACING.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fanItem));
        PghvTestUtil.use(helper, player, against, face);
    }

    /** Brass fan snapped onto the transformer at {@code trafo}; returns the main cell. */
    private static BlockPos brassFanOnTransformer(GameTestHelper helper, BlockPos trafo) {
        BlockPos main = trafo.relative(FACING);
        placeFan(helper, trafo, FACING, PghvItems.BRASS_COOLING_FAN.get(), main.relative(FACING, 3));
        return main;
    }

    /** Free-standing fan on the floor facing south; returns the main cell. */
    private static BlockPos fanOnFloor(GameTestHelper helper, BlockPos main, Item fanItem) {
        placeFan(helper, main.below(), Direction.UP, fanItem, main.relative(FACING, 3));
        return main;
    }

    private static ThermalBehaviour thermal(GameTestHelper helper, BlockPos trafoOrigin) {
        TransformerBlockEntity be = helper.getBlockEntity(trafoOrigin);
        ThermalBehaviour thermal = be.getBehaviour(ThermalBehaviour.TYPE);
        if (thermal == null)
            throw new IllegalStateException("transformer without thermal behaviour");
        return thermal;
    }

    /** Servo with its shaft pointing to {@code shaft}. */
    private static void servo(GameTestHelper helper, BlockPos pos, Direction shaft) {
        helper.setBlock(pos, ModdedBlocks.SERVO.getDefaultState().setValue(ServoBlock.FACING, shaft));
    }

    /** Right-click with the main-hand item at an exact block-local hit position on face {@code face}. */
    private static InteractionResult useAt(GameTestHelper helper, FakePlayer player, BlockPos pos, Vec3 local, Direction face) {
        BlockPos abs = helper.absolutePos(pos);
        Vec3 hit = Vec3.atLowerCornerOf(abs).add(local);
        return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, abs, false));
    }

    private static List<HangingWireEntity> wires(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(HangingWireEntity.class, helper.getBounds().inflate(4),
                e -> e.isAlive());
    }

    // ---- terminals ----

    /** Brass fan: 5 terminals, socket 2..4 only on part 1; andesite unchanged with 2 terminals. */
    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void socketTerminalsOnlyOnBrassPart1(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos brass = fanOnFloor(helper, new BlockPos(2, 1, 3), PghvItems.BRASS_COOLING_FAN.get());
        BlockPos andesite = fanOnFloor(helper, new BlockPos(9, 1, 3), PghvItems.COOLING_FAN.get());
        helper.runAfterDelay(3, () -> {
            BlockState brassPart0 = helper.getBlockState(brass);
            BlockState brassPart1 = helper.getBlockState(HvCoolingFanBlock.cellOf(brass, FACING, 1));
            BlockState andesitePart1 = helper.getBlockState(HvCoolingFanBlock.cellOf(andesite, FACING, 1));
            HvCoolingFanBlock brassBlock = (HvCoolingFanBlock) brassPart0.getBlock();
            HvCoolingFanBlock andesiteBlock = (HvCoolingFanBlock) andesitePart1.getBlock();
            helper.assertValueEqual(brassBlock.terminalCount(), 5, "brass terminal count");
            helper.assertValueEqual(andesiteBlock.terminalCount(), 2, "andesite terminal count");
            for (int t = 2; t <= 4; ++t) {
                helper.assertTrue(brassBlock.terminal(brassPart1, t) != null, "socket terminal " + t + " on brass part 1");
                helper.assertTrue(brassBlock.terminal(brassPart0, t) == null, "no socket terminal " + t + " on part 0");
                helper.assertTrue(andesiteBlock.terminal(andesitePart1, t) == null, "andesite has no terminal " + t);
            }
            helper.assertTrue(brassBlock.terminal(brassPart0, 0) != null && brassBlock.terminal(brassPart0, 1) != null,
                    "power pins unchanged on part 0");
            HvCoolingFanBlockEntity be = helper.getBlockEntity(brass);
            helper.assertTrue(be.getElectricBehaviour().hasTerminal(4), "brass behaviour has 5 external nodes");
            HvCoolingFanBlockEntity andesiteBe = helper.getBlockEntity(andesite);
            helper.assertFalse(andesiteBe.getElectricBehaviour().hasTerminal(2), "andesite behaviour still has 2 nodes");
            helper.succeed();
        });
    }

    // ---- protection logic ----

    /** Hysteresis on the real fan: trips at 155 degC, stays tripped down to 100 degC, resets at 100 degC. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 300)
    public static void protectionHysteresis(GameTestHelper helper) {
        // Pure characteristic first.
        helper.assertFalse(HvCoolingFanBlockEntity.nextTripped(false, 154.9f), "154.9 degC does not trip");
        helper.assertTrue(HvCoolingFanBlockEntity.nextTripped(false, 155.0f), "155 degC trips");
        helper.assertTrue(HvCoolingFanBlockEntity.nextTripped(true, 100.1f), "stays tripped above 100 degC");
        helper.assertFalse(HvCoolingFanBlockEntity.nextTripped(true, 100.0f), "resets at 100 degC");
        helper.assertFalse(HvCoolingFanBlockEntity.nextTripped(false, Float.NaN), "no transformer: no trip");

        PghvTestUtil.floor(helper);
        BlockPos trafo = new BlockPos(5, 1, 3);
        placeTransformer(helper, trafo);
        BlockPos main = brassFanOnTransformer(helper, trafo);
        float[] hold = { 150.0f };
        for (int t = 3; t < 290; ++t)
            helper.runAfterDelay(t, () -> thermal(helper, trafo).setTemperature(hold[0]));
        helper.runAfterDelay(40, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertFalse(fan.isTripped(), "150 degC: ready");
            helper.assertValueEqual(fan.getControlVoltage(), 0.0f, "control voltage when ready");
            hold[0] = 156.0f;
        });
        helper.runAfterDelay(80, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertTrue(fan.isTripped(), "156 degC: tripped");
            helper.assertValueEqual(fan.getControlVoltage(), HvCoolingFanBlockEntity.CONTROL_VOLTAGE, "control voltage tripped");
            hold[0] = 120.0f;
        });
        helper.runAfterDelay(120, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertTrue(fan.isTripped(), "120 degC: still tripped (hysteresis)");
            HvCoolingFanBlockEntity reloaded = PghvTestUtil.reload(helper, main);
            helper.assertTrue(reloaded.isTripped(), "tripped state survives reload");
            hold[0] = 99.0f;
        });
        helper.runAfterDelay(160, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertFalse(fan.isTripped(), "99 degC: reset");
            helper.assertValueEqual(fan.getControlVoltage(), 0.0f, "control voltage after reset");
            helper.succeed();
        });
    }

    /** Free-standing brass fan (no transformer) never trips, whatever happens. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 100)
    public static void protectionWithoutTransformerNeverTrips(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos main = fanOnFloor(helper, new BlockPos(7, 1, 3), PghvItems.BRASS_COOLING_FAN.get());
        helper.runAfterDelay(60, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertFalse(fan.isTripped(), "no transformer -> ready");
            helper.succeed();
        });
    }

    /** Measured socket voltages with a servo as load: 48 V supply, control 0 / +2.5 / -2.5 V. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 300)
    public static void controlVoltageAndPolarity(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos main = fanOnFloor(helper, new BlockPos(3, 1, 3), PghvItems.BRASS_COOLING_FAN.get());
        BlockPos socket = HvCoolingFanBlock.cellOf(main, FACING, 1);
        BlockPos servoPos = new BlockPos(5, 1, 9);
        servo(helper, servoPos, Direction.UP);
        helper.runAfterDelay(3, () -> {
            DataCableItem.Result r = DataCableItem.connect(helper.getLevel(), helper.absolutePos(socket),
                    helper.absolutePos(servoPos));
            helper.assertTrue(r.success(), "data cable connects: " + r.message().getString());
        });
        helper.runAfterDelay(40, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            double supply = fan.getTerminalVoltage(HvCoolingFanBlock.SOCKET_POSITIVE, HvCoolingFanBlock.SOCKET_NEGATIVE);
            helper.assertTrue(supply > 44.0 && supply <= 48.05, "supply about 48 V under servo load, is " + supply);
            double ctrl = fan.getTerminalVoltage(HvCoolingFanBlock.SOCKET_CONTROL, HvCoolingFanBlock.SOCKET_NEGATIVE);
            helper.assertTrue(Math.abs(ctrl) < 0.05, "control 0 V when ready, is " + ctrl);
            fan.setTripped(true);
        });
        helper.runAfterDelay(80, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            double ctrl = fan.getTerminalVoltage(HvCoolingFanBlock.SOCKET_CONTROL, HvCoolingFanBlock.SOCKET_NEGATIVE);
            // 10 ohm internal resistance against the servo's 1000 ohm control input: 2.5 V x 1000/1010.
            PghvTestUtil.assertNear(helper, ctrl, 2.5 * 1000.0 / 1010.0, 0.02, "control voltage tripped, direction +1");
            fan.toggleServoDirection();
        });
        helper.runAfterDelay(120, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertValueEqual(fan.getServoDirection(), -1, "direction after toggle");
            double ctrl = fan.getTerminalVoltage(HvCoolingFanBlock.SOCKET_CONTROL, HvCoolingFanBlock.SOCKET_NEGATIVE);
            PghvTestUtil.assertNear(helper, ctrl, -2.5 * 1000.0 / 1010.0, 0.02, "control voltage tripped, direction -1");
            HvCoolingFanBlockEntity reloaded = PghvTestUtil.reload(helper, main);
            helper.assertValueEqual(reloaded.getServoDirection(), -1, "direction survives reload");
            helper.succeed();
        });
    }

    // ---- wrench ----

    /** Wrench on the socket toggles the direction; elsewhere it still switches the slot. Andesite unchanged. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 100)
    public static void wrenchOnSocketTogglesDirection(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos main = fanOnFloor(helper, new BlockPos(3, 1, 3), PghvItems.BRASS_COOLING_FAN.get());
        BlockPos andesiteMain = fanOnFloor(helper, new BlockPos(10, 1, 3), PghvItems.COOLING_FAN.get());
        BlockPos part1 = HvCoolingFanBlock.cellOf(main, FACING, 1);
        BlockPos andesitePart1 = HvCoolingFanBlock.cellOf(andesiteMain, FACING, 1);
        helper.runAfterDelay(3, () -> {
            FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, part1.relative(FACING, 2));
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertValueEqual(fan.getServoDirection(), 1, "default direction");

            BlockState state = helper.getBlockState(part1);
            int slot = state.getValue(HvCoolingFanBlock.SLOT);
            AABB area = HvCoolingFanBlock.socketArea(state);
            useAt(helper, player, part1, area.getCenter(), FACING);
            helper.assertValueEqual(fan.getServoDirection(), -1, "wrench on socket toggles direction");
            helper.assertValueEqual(helper.getBlockState(part1).getValue(HvCoolingFanBlock.SLOT), slot, "slot unchanged");

            // Upper half of part 1 (outside the socket plate): slot switch as before, direction unchanged.
            useAt(helper, player, part1, new Vec3(0.5, 0.85, area.getCenter().z), FACING);
            BlockState moved = helper.getBlockState(part1);
            helper.assertValueEqual(moved.getValue(HvCoolingFanBlock.SLOT), (slot + 1) % 3, "wrench elsewhere switches slot");
            helper.assertValueEqual(fan.getServoDirection(), -1, "direction unchanged by slot switch");

            // Socket follows the panel: hit the new socket area.
            useAt(helper, player, part1, HvCoolingFanBlock.socketArea(moved).getCenter(), FACING);
            helper.assertValueEqual(fan.getServoDirection(), 1, "second toggle back to +1");

            // Andesite fan: same spot only switches the slot.
            BlockState andesiteState = helper.getBlockState(andesitePart1);
            int andesiteSlot = andesiteState.getValue(HvCoolingFanBlock.SLOT);
            FakePlayer p2 = PghvTestUtil.player(helper, GameType.SURVIVAL, andesitePart1.relative(FACING, 2));
            p2.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            useAt(helper, p2, andesitePart1, HvCoolingFanBlock.socketArea(andesiteState).getCenter(), FACING);
            helper.assertValueEqual(helper.getBlockState(andesitePart1).getValue(HvCoolingFanBlock.SLOT),
                    (andesiteSlot + 1) % 3, "andesite: wrench switches slot");
            helper.succeed();
        });
    }

    // ---- data cable ----

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void dataCableRecipe(GameTestHelper helper) {
        Level level = helper.getLevel();
        Item copper = BuiltInRegistries.ITEM.get(CableCores.COPPER);
        Item iron = BuiltInRegistries.ITEM.get(CableCores.IRON);
        Item gold = BuiltInRegistries.ITEM.get(CableCores.GOLD);
        helper.assertTrue(copper != Items.AIR && iron != Items.AIR && gold != Items.AIR, "Power Grid wires exist");
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        grid.set(0, new ItemStack(gold));
        grid.set(4, new ItemStack(copper));
        grid.set(8, new ItemStack(iron));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(h -> h.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(PghvItems.DATA_CABLE.get()), "copper + iron + gold wire -> data cable, got " + result);
        helper.assertValueEqual(result.getCount(), 1, "data cable count");
        helper.succeed();
    }

    /** Fan -> servo with a player: three wires (copper/iron/gold) at the right terminals, 1 cable consumed. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 100)
    public static void dataCableCreatesThreeWires(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos main = fanOnFloor(helper, new BlockPos(3, 1, 3), PghvItems.BRASS_COOLING_FAN.get());
        BlockPos socket = HvCoolingFanBlock.cellOf(main, FACING, 1);
        BlockPos servoPos = new BlockPos(6, 1, 10);
        servo(helper, servoPos, Direction.UP);
        helper.runAfterDelay(3, () -> {
            FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, main.relative(FACING, 3));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PghvItems.DATA_CABLE.get(), 2));

            // Servo first: no start -> nothing happens.
            PghvTestUtil.use(helper, player, servoPos, Direction.UP);
            helper.assertTrue(wires(helper).isEmpty(), "no wires without start");

            // Start on part 0 (any part of the brass fan selects the socket on part 1).
            PghvTestUtil.use(helper, player, main, FACING);
            GlobalPos start = player.getMainHandItem().get(PghvDataComponents.DATA_CABLE_START.get());
            helper.assertTrue(start != null && start.pos().equals(helper.absolutePos(socket)), "start = socket cell, is " + start);

            PghvTestUtil.use(helper, player, servoPos, Direction.UP);
            List<HangingWireEntity> wires = wires(helper);
            helper.assertValueEqual(wires.size(), 3, "three hanging wires");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "one data cable consumed");
            helper.assertFalse(player.getMainHandItem().has(PghvDataComponents.DATA_CABLE_START.get()), "start cleared");

            BlockPos socketAbs = helper.absolutePos(socket);
            BlockPos servoAbs = helper.absolutePos(servoPos);
            for (int i = 0; i < 3; ++i) {
                int socketTerminal = DataCableItem.SOCKET_TERMINALS[i];
                int servoTerminal = DataCableItem.SERVO_TERMINALS[i];
                ResourceLocation wireId = DataCableItem.WIRES[i];
                HangingWireEntity match = null;
                for (HangingWireEntity w : wires) {
                    if (w.isConnectedTo(socketAbs, socketTerminal) && w.isConnectedTo(servoAbs, servoTerminal))
                        match = w;
                }
                helper.assertTrue(match != null, "wire socket " + socketTerminal + " <-> servo " + servoTerminal);
                helper.assertTrue(BuiltInRegistries.ITEM.getKey(match.getItem()).equals(wireId),
                        "wire " + i + " is " + wireId + ", is " + BuiltInRegistries.ITEM.getKey(match.getItem()));
                helper.assertValueEqual(match.getWireCount(), 1, "wire " + i + " holds one item (no dupe)");
                WireItemEntry entry = CableCores.entry(helper.getLevel().registryAccess(), wireId);
                double length = new BlockWireEndpoint(socketAbs, socketTerminal).getExactPosition(helper.getLevel())
                        .distanceTo(new BlockWireEndpoint(servoAbs, servoTerminal).getExactPosition(helper.getLevel()));
                PghvTestUtil.assertNear(helper, match.getResistance(), DataCableItem.wireResistance(entry, length), 0.01,
                        "resistance of wire " + i);
            }

            // Second attempt: already connected, nothing consumed.
            PghvTestUtil.use(helper, player, main, FACING);
            PghvTestUtil.use(helper, player, servoPos, Direction.UP);
            helper.assertValueEqual(wires(helper).size(), 3, "no duplicate wires");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "nothing consumed on duplicate");
            helper.succeed();
        });
    }

    /** Too long (> 16 blocks), andesite fan has no socket, creative does not consume. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 100)
    public static void dataCableLimits(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos main = fanOnFloor(helper, new BlockPos(1, 1, 1), PghvItems.BRASS_COOLING_FAN.get());
        BlockPos andesite = fanOnFloor(helper, new BlockPos(10, 1, 1), PghvItems.COOLING_FAN.get());
        BlockPos far = new BlockPos(14, 1, 14);
        BlockPos near = new BlockPos(4, 1, 6);
        servo(helper, far, Direction.UP);
        servo(helper, near, Direction.UP);
        helper.runAfterDelay(3, () -> {
            FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, main.relative(FACING, 3));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PghvItems.DATA_CABLE.get(), 1));
            PghvTestUtil.use(helper, player, andesite, FACING);
            helper.assertFalse(player.getMainHandItem().has(PghvDataComponents.DATA_CABLE_START.get()),
                    "andesite fan has no socket");

            PghvTestUtil.use(helper, player, main, FACING);
            PghvTestUtil.use(helper, player, far, Direction.UP);
            helper.assertTrue(wires(helper).isEmpty(), "too long: no wires");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "too long: nothing consumed");

            // Sneak + right-click into the air cancels the start.
            player.setShiftKeyDown(true);
            player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
            player.setShiftKeyDown(false);
            helper.assertFalse(player.getMainHandItem().has(PghvDataComponents.DATA_CABLE_START.get()), "cancelled");

            FakePlayer creative = PghvTestUtil.player(helper, GameType.CREATIVE, main.relative(FACING, 3));
            creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PghvItems.DATA_CABLE.get(), 1));
            PghvTestUtil.use(helper, creative, main, FACING);
            PghvTestUtil.use(helper, creative, near, Direction.UP);
            helper.assertValueEqual(wires(helper).size(), 3, "creative connects");
            helper.assertValueEqual(creative.getMainHandItem().getCount(), 1, "creative: not consumed");
            helper.succeed();
        });
    }

    // ---- end to end ----

    /**
     * Brass fan on a transformer, data cable to a servo that drives a Power Grid HV switch. Heating trips the
     * protection, the servo turns, the switch rod moves; cooling to 100 degC turns it back, heating again moves it
     * once more. The HV switch starts open (rod 0), so the test is direction-agnostic: the switch state after the
     * reset and after the second trip must differ, and the servo must have moved in every phase.
     * Note: the first servo move runs at about 7 RPM (48 V on the 64 ohm idle coil), later up to 32 RPM.
     */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 3000)
    public static void endToEndServoSwitchesHvSwitch(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos trafo = new BlockPos(2, 1, 2);
        placeTransformer(helper, trafo);
        BlockPos main = brassFanOnTransformer(helper, trafo);
        BlockPos socket = HvCoolingFanBlock.cellOf(main, FACING, 1);
        // HV switch facing north: shaft along x on part 0; servo west of it with the shaft pointing east.
        BlockPos switchPos = new BlockPos(9, 1, 9);
        BlockState sw = ModdedBlocks.HV_SWITCH.getDefaultState()
                .setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.NORTH);
        helper.setBlock(switchPos, sw.setValue(HvSwitchBlock.PART, 0));
        helper.setBlock(switchPos.north(), sw.setValue(HvSwitchBlock.PART, 1));
        BlockPos servoPos = switchPos.west();
        servo(helper, servoPos, Direction.EAST);
        helper.runAfterDelay(5, () -> {
            DataCableItem.Result r = DataCableItem.connect(helper.getLevel(), helper.absolutePos(socket),
                    helper.absolutePos(servoPos));
            helper.assertTrue(r.success(), "data cable connects: " + r.message().getString());
        });

        // Phases: 0 heat (trip), 1 cool (reset), 2 heat (trip again), 3 done.
        int[] phase = { 0 };
        boolean[] moved = { false };
        boolean[] closedAfter = new boolean[3];
        for (int t = 10; t < 2990; ++t) {
            helper.runAfterDelay(t, () -> {
                if (phase[0] >= 3)
                    return;
                boolean heat = phase[0] != 1;
                thermal(helper, trafo).setTemperature(heat ? 160.0f : 90.0f);
                HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
                ServoBlockEntity servo = helper.getBlockEntity(servoPos);
                HvSwitchBlockEntity hv = helper.getBlockEntity(switchPos);
                if (fan.isTripped() != heat)
                    return; // protection has not switched yet
                if (servo.getSpeed() != 0) {
                    moved[0] = true;
                    return;
                }
                if (!moved[0])
                    return; // servo has not started yet
                closedAfter[phase[0]] = hv.isClosed();
                moved[0] = false;
                if (++phase[0] == 3) {
                    helper.assertTrue(closedAfter[1] != closedAfter[2],
                            "switch state after reset (" + closedAfter[1] + ") and after the second trip ("
                                    + closedAfter[2] + ") must differ");
                    helper.succeed();
                }
            });
        }
        helper.runAfterDelay(2995, () -> helper.fail("end-to-end did not finish, phase " + phase[0]));
    }
}
