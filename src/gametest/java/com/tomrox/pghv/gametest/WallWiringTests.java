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

import com.tomrox.pghv.Pghv;
import com.tomrox.pghv.registry.PghvBlocks;
import com.tomrox.pghv.registry.PghvDataComponents;
import com.tomrox.pghv.wiring.InWallCable;
import com.tomrox.pghv.wiring.InWallLaying;
import com.tomrox.pghv.wiring.InWallRouting;
import com.tomrox.pghv.wiring.LampBlockEntity;
import com.tomrox.pghv.wiring.WallBoxBlockEntity;
import com.tomrox.pghv.wiring.WallDeviceBlock;
import com.tomrox.pghv.wiring.WallDeviceBlockEntity;
import com.tomrox.pghv.wiring.WallOutletBlock;
import com.tomrox.pghv.wiring.WallOutletBlockEntity;
import com.tomrox.pghv.wiring.WallSwitchBlock;
import com.tomrox.pghv.wiring.WallSwitchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-wall installation: devices, laying with waypoints, switch function, auto route, outlet voltage, removal, reload.
 * Layout: stone wall z = 5 (x 0..13, y 1..3), devices at z = 4 facing north (out of the wall).
 */
@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class WallWiringTests {
    private static final Direction OUT = Direction.NORTH;
    private static final BlockPos BOX = new BlockPos(2, 2, 4);
    private static final BlockPos SWITCH = new BlockPos(6, 2, 4);
    private static final BlockPos LOAD_BOX = new BlockPos(11, 2, 4);
    private static final BlockPos SOURCE = new BlockPos(2, 1, 1);

    private static void wall(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        for (int x = 0; x <= 13; ++x)
            for (int y = 1; y <= 3; ++y)
                helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
    }

    private static void device(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block.defaultBlockState().setValue(WallDeviceBlock.HORIZONTAL_FACING, OUT));
    }

    private static Item wire() {
        return BuiltInRegistries.ITEM.get(WallDeviceBlockEntity.WIRE_ID);
    }

    private static FakePlayer layer(GameTestHelper helper, GameType mode, int wires) {
        FakePlayer p = PghvTestUtil.player(helper, mode, new BlockPos(6, 1, 2));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(wire(), wires));
        return p;
    }

    /** 230 V source on the box terminals (L = 0, N = 1). */
    private static void feed(GameTestHelper helper) {
        PghvTestUtil.voltageSource(helper, SOURCE, 230f);
    }

    private static void feedWires(GameTestHelper helper) {
        PghvTestUtil.wire(helper, SOURCE, 0, BOX, 0, 0.01f);
        PghvTestUtil.wire(helper, SOURCE, 1, BOX, 1, 0.01f);
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void wallDevicesTerminalsAndPorts(GameTestHelper helper) {
        wall(helper);
        device(helper, BOX, PghvBlocks.WALL_BOX.get());
        device(helper, SWITCH, PghvBlocks.WALL_SWITCH.get());
        device(helper, new BlockPos(8, 2, 4), PghvBlocks.WALL_BUTTON.get());
        device(helper, new BlockPos(9, 2, 4), PghvBlocks.OUTLET_EU.get());
        device(helper, new BlockPos(10, 2, 4), PghvBlocks.OUTLET_US.get());
        helper.runAfterDelay(2, () -> {
            WallSwitchBlockEntity sw = helper.getBlockEntity(SWITCH);
            helper.assertTrue(sw.getElectricBehaviour().hasTerminal(3), "switch has 4 terminals");
            helper.assertValueEqual(((WallSwitchBlock) PghvBlocks.WALL_SWITCH.get()).portCount(), 2, "switch ports");
            WallBoxBlockEntity box = helper.getBlockEntity(BOX);
            helper.assertTrue(box.getElectricBehaviour().hasTerminal(1), "box has 2 terminals");
            helper.assertValueEqual(((WallOutletBlock) PghvBlocks.OUTLET_EU.get()).nominalVoltage(), 230.0, "EU nominal");
            helper.assertValueEqual(((WallOutletBlock) PghvBlocks.OUTLET_US.get()).nominalVoltage(), 120.0, "US nominal");
            helper.assertValueEqual(WallOutletBlockEntity.status(229, 230), "ok", "status ok");
            helper.assertValueEqual(WallOutletBlockEntity.status(120, 230), "under", "status under");
            helper.assertValueEqual(WallOutletBlockEntity.status(230, 120), "over", "status over");
            helper.assertValueEqual(WallOutletBlockEntity.status(1, 120), "dead", "status dead");
            helper.succeed();
        });
    }

    /** Box -> (waypoint) -> switch -> load box; switch off: no voltage at the load, on: 230 V. Wire items used by length. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void switchControlsLoadOverInWallCables(GameTestHelper helper) {
        wall(helper);
        feed(helper);
        device(helper, BOX, PghvBlocks.WALL_BOX.get());
        device(helper, SWITCH, PghvBlocks.WALL_SWITCH.get());
        device(helper, LOAD_BOX, PghvBlocks.WALL_BOX.get());
        helper.runAfterDelay(3, () -> {
            feedWires(helper);
            PghvTestUtil.wire(helper, LOAD_BOX, 0, LOAD_BOX, 1, 100f); // the "lamp"
            FakePlayer p = layer(helper, GameType.SURVIVAL, 64);
            PghvTestUtil.use(helper, p, BOX, OUT);
            helper.assertTrue(p.getMainHandItem().has(PghvDataComponents.INWALL_LAYING.get()), "laying started");
            PghvTestUtil.use(helper, p, new BlockPos(4, 3, 5), OUT); // waypoint in the wall
            helper.assertValueEqual(InWallLaying.points(p.getMainHandItem().get(PghvDataComponents.INWALL_LAYING.get())).size(),
                    1, "one waypoint");
            PghvTestUtil.use(helper, p, SWITCH, OUT);
            helper.assertFalse(p.getMainHandItem().has(PghvDataComponents.INWALL_LAYING.get()), "laying finished");
            WallBoxBlockEntity box = helper.getBlockEntity(BOX);
            helper.assertValueEqual(box.getOwned().size(), 1, "box owns the first cable");
            InWallCable c1 = box.getOwned().get(0);
            helper.assertValueEqual(c1.portB(), 0, "first cable on the switch input");
            helper.assertValueEqual(p.getMainHandItem().getCount(), 64 - c1.wires(), "wires used by length");
            helper.assertTrue(c1.wires() == InWallLaying.wiresFor(helper.getLevel(), c1.length()), "wire count from length");

            PghvTestUtil.use(helper, p, SWITCH, OUT);
            PghvTestUtil.use(helper, p, LOAD_BOX, OUT);
            WallSwitchBlockEntity sw = helper.getBlockEntity(SWITCH);
            helper.assertValueEqual(sw.getOwned().size(), 1, "switch owns the second cable");
            helper.assertValueEqual(sw.getOwned().get(0).portA(), 1, "second cable on the switch output");
        });
        helper.runAfterDelay(40, () -> {
            WallBoxBlockEntity load = helper.getBlockEntity(LOAD_BOX);
            helper.assertTrue(Math.abs(load.portVoltage(0)) < 1.0, "switch off: no voltage at the load, is " + load.portVoltage(0));
            FakePlayer hand = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(6, 1, 2));
            PghvTestUtil.use(helper, hand, SWITCH, OUT);
            helper.assertTrue(helper.getBlockState(SWITCH).getValue(WallSwitchBlock.POWERED), "switch on");
        });
        helper.runAfterDelay(80, () -> {
            WallBoxBlockEntity load = helper.getBlockEntity(LOAD_BOX);
            helper.assertTrue(Math.abs(load.portVoltage(0)) > 200.0, "switch on: 230 V at the load, is " + load.portVoltage(0));
            helper.succeed();
        });
    }

    /** Key R: auto route from the box through the wall to an outlet; outlet shows 230 V (ok). */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void autoRouteToOutlet(GameTestHelper helper) {
        wall(helper);
        feed(helper);
        BlockPos outlet = new BlockPos(10, 2, 4);
        device(helper, BOX, PghvBlocks.WALL_BOX.get());
        device(helper, outlet, PghvBlocks.OUTLET_EU.get());
        helper.runAfterDelay(3, () -> {
            feedWires(helper);
            FakePlayer p = layer(helper, GameType.SURVIVAL, 64);
            PghvTestUtil.use(helper, p, BOX, OUT);
            InWallLaying.autoRoute(p, helper.absolutePos(outlet));
            WallBoxBlockEntity box = helper.getBlockEntity(BOX);
            helper.assertValueEqual(box.getOwned().size(), 1, "auto route laid one cable");
            InWallCable c = box.getOwned().get(0);
            for (Vec3 pt : c.points())
                helper.assertTrue(InWallRouting.isWall(helper.getLevel(), BlockPos.containing(pt)), "waypoint inside the wall: " + pt);
            WallOutletBlockEntity o = helper.getBlockEntity(outlet);
            helper.assertValueEqual(o.incomingCount(), 1, "outlet knows the cable");
        });
        helper.runAfterDelay(40, () -> {
            WallOutletBlockEntity o = helper.getBlockEntity(outlet);
            helper.assertValueEqual(WallOutletBlockEntity.status(o.portVoltage(0), o.nominal()), "ok",
                    "outlet voltage ok, is " + o.portVoltage(0));
            helper.succeed();
        });
    }

    /** Breaking the far end drops the wire items there and removes the cable; reload keeps cables. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void removalDropsWireAndReloadKeepsCable(GameTestHelper helper) {
        wall(helper);
        device(helper, BOX, PghvBlocks.WALL_BOX.get());
        device(helper, SWITCH, PghvBlocks.WALL_SWITCH.get());
        BlockPos outlet = new BlockPos(9, 2, 4);
        device(helper, outlet, PghvBlocks.OUTLET_US.get());
        helper.runAfterDelay(3, () -> {
            FakePlayer p = layer(helper, GameType.SURVIVAL, 64);
            PghvTestUtil.use(helper, p, BOX, OUT);
            PghvTestUtil.use(helper, p, SWITCH, OUT);
            PghvTestUtil.use(helper, p, BOX, OUT);
            PghvTestUtil.use(helper, p, outlet, OUT);
            WallBoxBlockEntity box = helper.getBlockEntity(BOX);
            helper.assertValueEqual(box.getOwned().size(), 2, "two cables");
            int toSwitch = box.getOwned().get(0).wires();
            WallBoxBlockEntity reloaded = PghvTestUtil.reload(helper, BOX);
            helper.assertValueEqual(reloaded.getOwned().size(), 2, "cables survive reload");
            helper.destroyBlock(SWITCH);
            helper.assertValueEqual(((WallBoxBlockEntity) helper.getBlockEntity(BOX)).getOwned().size(), 1, "cable to the switch removed");
            helper.assertValueEqual(PghvTestUtil.countDrops(helper, wire()), toSwitch, "wire items dropped once");
            helper.succeed();
        });
    }

    /** Sneak + right-click into the air cancels; the button switches off by itself. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 100)
    public static void cancelAndButton(GameTestHelper helper) {
        wall(helper);
        BlockPos button = new BlockPos(7, 2, 4);
        device(helper, BOX, PghvBlocks.WALL_BOX.get());
        device(helper, button, PghvBlocks.WALL_BUTTON.get());
        helper.runAfterDelay(3, () -> {
            FakePlayer p = layer(helper, GameType.SURVIVAL, 8);
            PghvTestUtil.use(helper, p, BOX, OUT);
            p.setShiftKeyDown(true);
            p.gameMode.useItem(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
            p.setShiftKeyDown(false);
            helper.assertFalse(p.getMainHandItem().has(PghvDataComponents.INWALL_LAYING.get()), "cancelled");
            helper.assertValueEqual(p.getMainHandItem().getCount(), 8, "nothing used");
            FakePlayer hand = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(7, 1, 2));
            PghvTestUtil.use(helper, hand, button, OUT);
            helper.assertTrue(helper.getBlockState(button).getValue(WallSwitchBlock.POWERED), "button pressed");
        });
        helper.runAfterDelay(3 + WallSwitchBlock.BUTTON_TICKS + 5, () -> {
            helper.assertFalse(helper.getBlockState(button).getValue(WallSwitchBlock.POWERED), "button released");
            helper.succeed();
        });
    }

    /** 230 V lamps: switch -> wall light and ceiling lamp light up at 230 V (light 15), dark when off; 120 V is dim. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void lamps230VoltSwitched(GameTestHelper helper) {
        wall(helper);
        feed(helper);
        BlockPos light = new BlockPos(10, 2, 4);
        BlockPos ceiling = new BlockPos(9, 3, 3);
        for (int x = 7; x <= 11; ++x)
            for (int z = 1; z <= 5; ++z)
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE); // ceiling, joined to the wall top
        device(helper, BOX, PghvBlocks.WALL_BOX.get());
        device(helper, SWITCH, PghvBlocks.WALL_SWITCH.get());
        device(helper, light, PghvBlocks.WALL_LIGHT.get());
        helper.setBlock(ceiling, PghvBlocks.CEILING_LAMP.get().defaultBlockState());
        helper.runAfterDelay(3, () -> {
            feedWires(helper);
            FakePlayer p = layer(helper, GameType.CREATIVE, 1);
            PghvTestUtil.use(helper, p, BOX, OUT);
            PghvTestUtil.use(helper, p, SWITCH, OUT);
            PghvTestUtil.use(helper, p, SWITCH, OUT);
            PghvTestUtil.use(helper, p, light, OUT);
            PghvTestUtil.use(helper, p, light, OUT);
            InWallLaying.autoRoute(p, helper.absolutePos(ceiling));
            LampBlockEntity wl = helper.getBlockEntity(light);
            helper.assertValueEqual(wl.getOwned().size(), 1, "wall light feeds the ceiling lamp");
        });
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(helper.getBlockState(light).getValue(LampBlockEntity.LIGHT), 0, "switch off: wall light dark");
            FakePlayer hand = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(6, 1, 2));
            PghvTestUtil.use(helper, hand, SWITCH, OUT);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(helper.getBlockState(light).getValue(LampBlockEntity.LIGHT), 15, "switch on: wall light full");
            helper.assertValueEqual(helper.getBlockState(ceiling).getValue(LampBlockEntity.LIGHT), 15, "ceiling lamp full");
            helper.assertValueEqual(helper.getLevel().getLightEmission(helper.absolutePos(light)), 15, "block light emitted");
            helper.assertTrue(LampBlockEntity.lightFor(120) > 0 && LampBlockEntity.lightFor(120) < 8, "120 V is dim");
            helper.succeed();
        });
    }
}
