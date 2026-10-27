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
import com.tomrox.pghv.Pghv;
import com.tomrox.pghv.block.CableSegmentBlock;
import com.tomrox.pghv.block.entity.CableBoxBlockEntity;
import com.tomrox.pghv.block.entity.CableSegmentBlockEntity;
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.cable.CablePath;
import com.tomrox.pghv.registry.PghvBlocks;
import com.tomrox.pghv.registry.PghvDataComponents;
import com.tomrox.pghv.registry.PghvItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableTests {
    private static final BlockPos BOX_A = new BlockPos(2, 1, 2);
    private static final BlockPos ANCHOR_FLOOR = new BlockPos(6, 0, 2);
    private static final BlockPos ANCHOR = ANCHOR_FLOOR.above();
    private static final BlockPos BOX_B = new BlockPos(10, 1, 8);
    private static final List<BlockPos> POINTS = List.of(BOX_A, ANCHOR, BOX_B);
    /** Path cells without both boxes. */
    private static final int LENGTH = CablePath.length(CablePath.cells(POINTS));

    private static final CableCores MIXED = CableCores.of(CableCores.COPPER, CableCores.IRON, CableCores.GOLD);

    private static ItemStack cable(Item item, CableCores cores, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(PghvDataComponents.CABLE_CORES.get(), cores);
        return stack;
    }

    private static boolean isCable(ItemStack stack, CableCores cores) {
        return stack.is(PghvItems.UNDERGROUND_CABLE.get()) && CableCores.get(stack).equals(cores);
    }

    /** Boxes A and B, laid by a player in the given mode via start / anchor / target clicks. */
    private static FakePlayer lay(GameTestHelper helper, GameType mode, int items) {
        PghvTestUtil.floor(helper);
        helper.setBlock(BOX_A, PghvBlocks.CABLE_BOX.get());
        helper.setBlock(BOX_B, PghvBlocks.CABLE_BOX.get());
        FakePlayer player = PghvTestUtil.player(helper, mode, new BlockPos(6, 1, 6));
        player.setItemInHand(InteractionHand.MAIN_HAND, cable(PghvItems.UNDERGROUND_CABLE.get(), MIXED, items));
        InteractionResult r1 = PghvTestUtil.use(helper, player, BOX_A, Direction.UP);
        helper.assertTrue(r1.consumesAction(), "start click on box A: " + r1);
        helper.assertTrue(player.getMainHandItem().has(PghvDataComponents.CABLE_LAYING.get()), "laying state after start");
        InteractionResult r2 = PghvTestUtil.use(helper, player, ANCHOR_FLOOR, Direction.UP);
        helper.assertTrue(r2.consumesAction(), "anchor click: " + r2);
        PghvTestUtil.use(helper, player, BOX_B, Direction.UP);
        return player;
    }

    private static ElectricWire[] wires(CableBoxBlockEntity box) {
        try {
            Field f = CableBoxBlockEntity.class.getDeclaredField("wires");
            f.setAccessible(true);
            return (ElectricWire[]) f.get(box);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static IElectricNode terminal(CableBoxBlockEntity box, int index) {
        ElectricBehaviour behaviour = box.getBehaviour(ElectricBehaviour.TYPE);
        return behaviour.getExternalNodes().get(index);
    }

    private static List<BlockPos> innerCells() {
        List<BlockPos> cells = CablePath.cells(POINTS);
        return new ArrayList<>(cells.subList(1, cells.size() - 1));
    }

    private static void assertNoSegments(GameTestHelper helper) {
        for (BlockPos cell : innerCells())
            helper.assertBlockNotPresent(PghvBlocks.CABLE_SEGMENT.get(), cell);
    }

    private static void assertBoxFree(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof CableBoxBlockEntity box)
            helper.assertFalse(box.hasCable(), "box " + pos + " still connected");
    }

    private static double ohmPerBlock(GameTestHelper helper, ResourceLocation id) {
        WireItemEntry entry = helper.getLevel().registryAccess().registryOrThrow(WireRegistry.KEY).get(id);
        if (entry == null)
            throw new IllegalStateException("no wire type " + id);
        return entry.resistancePerItem() * entry.itemsPerMeter();
    }

    /** Checks that A built one wire per core from terminal i of A to terminal i of B with R = R/block x length. */
    private static void assertWires(GameTestHelper helper, CableBoxBlockEntity a, CableBoxBlockEntity b) {
        ElectricWire[] wires = wires(a);
        for (int i = 0; i < CableCores.COUNT; ++i) {
            ElectricWire wire = wires[i];
            if (wire == null) {
                helper.fail("core " + i + " has no wire");
                return;
            }
            double expected = ohmPerBlock(helper, MIXED.core(i)) * LENGTH;
            PghvTestUtil.assertNear(helper, wire.getResistance(), expected, 1e-4, "resistance of core " + i);
            PghvTestUtil.assertNear(helper, a.totalResistance(helper.getLevel().registryAccess(), i), expected, 1e-4,
                    "totalResistance of core " + i);
            IElectricNode na = terminal(a, i), nb = terminal(b, i);
            boolean ok = (wire.getNode1() == na && wire.getNode2() == nb) || (wire.getNode1() == nb && wire.getNode2() == na);
            helper.assertTrue(ok, "core " + i + " does not connect terminal " + i + " of A and B");
            helper.assertTrue(wire.getNetwork() != null, "core " + i + " wire not in a network");
        }
    }

    // ---- recipes ----

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void recipeAssemblyMixedCores(GameTestHelper helper) {
        Level level = helper.getLevel();
        Item copper = BuiltInRegistries.ITEM.get(CableCores.COPPER);
        Item iron = BuiltInRegistries.ITEM.get(CableCores.IRON);
        Item gold = BuiltInRegistries.ITEM.get(CableCores.GOLD);

        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        grid.set(0, new ItemStack(copper));
        grid.set(4, new ItemStack(iron));
        grid.set(8, new ItemStack(copper));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(h -> h.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(PghvItems.BARE_CABLE.get()), "3 wires -> bare cable, got " + result);
        helper.assertValueEqual(result.getCount(), 2, "bare cable count");
        helper.assertValueEqual(CableCores.get(result), CableCores.of(CableCores.COPPER, CableCores.IRON, CableCores.COPPER),
                "cores in grid order");

        NonNullList<ItemStack> gold3 = NonNullList.withSize(4, ItemStack.EMPTY);
        gold3.set(0, new ItemStack(gold));
        gold3.set(1, new ItemStack(gold));
        gold3.set(3, new ItemStack(gold));
        CraftingInput small = CraftingInput.of(2, 2, gold3);
        Optional<?> goldRecipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, small, level);
        helper.assertTrue(goldRecipe.isPresent(), "3 gold wires in a 2x2 grid must craft");

        // 2 wires, 4 wires and an insulated wire must not match
        NonNullList<ItemStack> two = NonNullList.withSize(9, ItemStack.EMPTY);
        two.set(0, new ItemStack(copper));
        two.set(1, new ItemStack(copper));
        helper.assertFalse(matchesBareCable(helper, CraftingInput.of(3, 3, two)), "2 wires must not craft");
        NonNullList<ItemStack> four = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 4; ++i)
            four.set(i, new ItemStack(copper));
        helper.assertFalse(matchesBareCable(helper, CraftingInput.of(3, 3, four)), "4 wires must not craft");
        Item insulated = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("powergrid", "insulated_copper_wire"));
        NonNullList<ItemStack> withInsulated = NonNullList.withSize(9, ItemStack.EMPTY);
        withInsulated.set(0, new ItemStack(copper));
        withInsulated.set(1, new ItemStack(copper));
        withInsulated.set(2, new ItemStack(insulated));
        helper.assertTrue(insulated != Items.AIR, "insulated wire item exists");
        helper.assertFalse(matchesBareCable(helper, CraftingInput.of(3, 3, withInsulated)), "insulated wire must not craft");
        helper.succeed();
    }

    private static boolean matchesBareCable(GameTestHelper helper, CraftingInput input) {
        Level level = helper.getLevel();
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(h -> h.value().assemble(input, level.registryAccess()).is(PghvItems.BARE_CABLE.get()))
                .orElse(false);
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void recipeInsulationKeepsCores(GameTestHelper helper) {
        Level level = helper.getLevel();
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; ++i)
            grid.set(i, i == 4 ? cable(PghvItems.BARE_CABLE.get(), MIXED, 1) : new ItemStack(Items.DRIED_KELP));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(h -> h.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(PghvItems.UNDERGROUND_CABLE.get()), "bare cable + 8 kelp -> underground cable, got " + result);
        helper.assertValueEqual(result.getCount(), 1, "underground cable count");
        helper.assertValueEqual(CableCores.get(result), MIXED, "cores kept");

        grid.set(0, ItemStack.EMPTY);
        CraftingInput missing = CraftingInput.of(3, 3, grid);
        boolean matches = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, missing, level)
                .map(h -> h.value().assemble(missing, level.registryAccess()).is(PghvItems.UNDERGROUND_CABLE.get()))
                .orElse(false);
        helper.assertFalse(matches, "7 kelp must not craft");
        helper.succeed();
    }

    // ---- laying ----

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void cableLayConnectsAndBuildsWires(GameTestHelper helper) {
        FakePlayer player = lay(helper, GameType.SURVIVAL, LENGTH + 7);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 7, "cable items left after laying");
        helper.assertFalse(player.getMainHandItem().has(PghvDataComponents.CABLE_LAYING.get()), "laying state cleared");

        List<BlockPos> cells = CablePath.cells(POINTS);
        BlockPos ownerAbs = helper.absolutePos(BOX_A);
        for (int i = 1; i < cells.size() - 1; ++i) {
            BlockPos cell = cells.get(i);
            BlockState state = helper.getBlockState(cell);
            if (!state.is(PghvBlocks.CABLE_SEGMENT.get())) {
                helper.fail("segment missing", cell);
                return;
            }
            Direction prev = CablePath.direction(cell, cells.get(i - 1));
            Direction next = CablePath.direction(cell, cells.get(i + 1));
            for (Direction d : Direction.values()) {
                boolean expected = d == prev || d == next;
                helper.assertValueEqual(state.getValue(CableSegmentBlock.PROPERTY_BY_DIRECTION.get(d)), expected,
                        "connection " + d + " at " + cell);
            }
            CableSegmentBlockEntity segment = helper.getBlockEntity(cell);
            helper.assertValueEqual(segment.getOwner(), ownerAbs, "segment owner at " + cell);
        }

        CableBoxBlockEntity a = helper.getBlockEntity(BOX_A);
        CableBoxBlockEntity b = helper.getBlockEntity(BOX_B);
        helper.assertTrue(a.isOwner() && a.hasCable(), "box A is owner");
        helper.assertTrue(b.hasCable() && !b.isOwner(), "box B is partner");
        helper.assertValueEqual(a.getCores(), MIXED, "cores on A");
        helper.assertValueEqual(b.getCores(), MIXED, "cores on B");
        helper.assertValueEqual(helper.getBlockState(BOX_A).getValue(HorizontalElectricBlock.HORIZONTAL_FACING), Direction.EAST,
                "box A socket towards cable");
        helper.assertValueEqual(helper.getBlockState(BOX_B).getValue(HorizontalElectricBlock.HORIZONTAL_FACING), Direction.WEST,
                "box B socket towards cable");

        helper.runAfterDelay(30, () -> {
            assertWires(helper, helper.getBlockEntity(BOX_A), helper.getBlockEntity(BOX_B));
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableNotEnoughItems(GameTestHelper helper) {
        FakePlayer player = lay(helper, GameType.SURVIVAL, LENGTH - 1);
        helper.assertValueEqual(player.getMainHandItem().getCount(), LENGTH - 1, "nothing consumed");
        assertNoSegments(helper);
        assertBoxFree(helper, BOX_A);
        assertBoxFree(helper, BOX_B);
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableTakesFromInventoryToo(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        helper.setBlock(BOX_A, PghvBlocks.CABLE_BOX.get());
        helper.setBlock(BOX_B, PghvBlocks.CABLE_BOX.get());
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(6, 1, 6));
        player.setItemInHand(InteractionHand.MAIN_HAND, cable(PghvItems.UNDERGROUND_CABLE.get(), MIXED, 5));
        player.getInventory().setItem(10, cable(PghvItems.UNDERGROUND_CABLE.get(), MIXED, LENGTH));
        player.getInventory().setItem(11, cable(PghvItems.UNDERGROUND_CABLE.get(), CableCores.DEFAULT, 64));
        PghvTestUtil.use(helper, player, BOX_A, Direction.UP);
        PghvTestUtil.use(helper, player, ANCHOR_FLOOR, Direction.UP);
        PghvTestUtil.use(helper, player, BOX_B, Direction.UP);
        CableBoxBlockEntity a = helper.getBlockEntity(BOX_A);
        helper.assertTrue(a.isOwner(), "connected with held + inventory cable");
        int left = player.getMainHandItem().getCount() + player.getInventory().getItem(10).getCount();
        helper.assertValueEqual(left, 5, "mixed-core cable left");
        helper.assertValueEqual(player.getInventory().getItem(11).getCount(), 64, "other cores untouched");
        helper.succeed();
    }

    // ---- cutting (no dupes) ----

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableCutSegmentDropsLength(GameTestHelper helper) {
        lay(helper, GameType.SURVIVAL, LENGTH);
        FakePlayer breaker = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(6, 1, 6));
        BlockPos cut = innerCells().get(LENGTH / 2);
        PghvTestUtil.breakBlock(helper, breaker, cut);
        assertNoSegments(helper);
        assertBoxFree(helper, BOX_A);
        assertBoxFree(helper, BOX_B);
        helper.assertBlockPresent(PghvBlocks.CABLE_BOX.get(), BOX_A);
        helper.assertBlockPresent(PghvBlocks.CABLE_BOX.get(), BOX_B);
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, s -> isCable(s, MIXED)), LENGTH, "cable drops");
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.UNDERGROUND_CABLE.get()), LENGTH, "all cable drops");
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableBreakBoxDropsLengthPlusBox(GameTestHelper helper) {
        lay(helper, GameType.SURVIVAL, LENGTH);
        FakePlayer breaker = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(9, 1, 6));
        breaker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        PghvTestUtil.breakBlock(helper, breaker, BOX_B);
        helper.assertBlockNotPresent(PghvBlocks.CABLE_BOX.get(), BOX_B);
        assertNoSegments(helper);
        assertBoxFree(helper, BOX_A);
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, s -> isCable(s, MIXED)), LENGTH, "cable drops");
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.CABLE_BOX.get()), 1, "box drops");

        // breaking the (now free) owner box afterwards must not drop cable again
        helper.killAllEntitiesOfClass(ItemEntity.class);
        PghvTestUtil.breakBlock(helper, breaker, BOX_A);
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.UNDERGROUND_CABLE.get()), 0, "second cut drops");
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableBreakOwnerBoxDropsLength(GameTestHelper helper) {
        lay(helper, GameType.SURVIVAL, LENGTH);
        FakePlayer breaker = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(3, 1, 4));
        breaker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        PghvTestUtil.breakBlock(helper, breaker, BOX_A);
        assertNoSegments(helper);
        assertBoxFree(helper, BOX_B);
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, s -> isCable(s, MIXED)), LENGTH, "cable drops");
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableWrenchCuts(GameTestHelper helper) {
        lay(helper, GameType.SURVIVAL, LENGTH);
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(9, 1, 6));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH.get()));
        PghvTestUtil.use(helper, player, BOX_B, Direction.UP);
        helper.assertBlockPresent(PghvBlocks.CABLE_BOX.get(), BOX_B);
        assertNoSegments(helper);
        assertBoxFree(helper, BOX_A);
        assertBoxFree(helper, BOX_B);
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, s -> isCable(s, MIXED)), LENGTH, "cable drops");
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableExplosionNoDupe(GameTestHelper helper) {
        lay(helper, GameType.SURVIVAL, LENGTH);
        BlockPos abs = helper.absolutePos(new BlockPos(6, 1, 5));
        helper.getLevel().explode(null, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 2.0f,
                Level.ExplosionInteraction.BLOCK);
        assertNoSegments(helper);
        assertBoxFree(helper, BOX_A);
        assertBoxFree(helper, BOX_B);
        int drops = PghvTestUtil.countDrops(helper, PghvItems.UNDERGROUND_CABLE.get());
        helper.assertValueEqual(drops, LENGTH, "cable drops after explosion");
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableCreativeLaidNoDrops(GameTestHelper helper) {
        FakePlayer player = lay(helper, GameType.CREATIVE, 1);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "creative laying uses nothing");
        CableBoxBlockEntity a = helper.getBlockEntity(BOX_A);
        helper.assertTrue(a.isOwner(), "creative laying connects");
        FakePlayer breaker = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(6, 1, 6));
        PghvTestUtil.breakBlock(helper, breaker, innerCells().get(2));
        assertNoSegments(helper);
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.UNDERGROUND_CABLE.get()), 0,
                "creative-laid cable must not drop");
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableCreativeBreakNoDrops(GameTestHelper helper) {
        lay(helper, GameType.SURVIVAL, LENGTH);
        FakePlayer breaker = PghvTestUtil.player(helper, GameType.CREATIVE, new BlockPos(6, 1, 6));
        PghvTestUtil.breakBlock(helper, breaker, innerCells().get(3));
        assertNoSegments(helper);
        assertBoxFree(helper, BOX_A);
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.UNDERGROUND_CABLE.get()), 0,
                "creative break must not drop");
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableBoxOccupied(GameTestHelper helper) {
        lay(helper, GameType.SURVIVAL, LENGTH);
        // a second cable from A must be refused
        BlockPos boxC = new BlockPos(2, 1, 12);
        helper.setBlock(boxC, PghvBlocks.CABLE_BOX.get());
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(3, 1, 6));
        player.setItemInHand(InteractionHand.MAIN_HAND, cable(PghvItems.UNDERGROUND_CABLE.get(), MIXED, 64));
        PghvTestUtil.use(helper, player, BOX_A, Direction.UP);
        helper.assertFalse(player.getMainHandItem().has(PghvDataComponents.CABLE_LAYING.get()), "occupied box must not start");
        PghvTestUtil.use(helper, player, boxC, Direction.UP);
        PghvTestUtil.use(helper, player, BOX_B, Direction.UP);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 64, "occupied target uses nothing");
        CableBoxBlockEntity c = helper.getBlockEntity(boxC);
        helper.assertFalse(c.hasCable(), "box C not connected");
        helper.succeed();
    }

    // ---- electrics ----

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void cableConductsPerCore(GameTestHelper helper) {
        lay(helper, GameType.SURVIVAL, LENGTH);
        BlockPos source = new BlockPos(2, 1, 6);
        float volts = 100f;
        float load = 10f;
        helper.runAfterDelay(30, () -> {
            PghvTestUtil.voltageSource(helper, source, volts);
            PghvTestUtil.wire(helper, source, 0, BOX_A, 0, 0.01f);
            PghvTestUtil.wire(helper, source, 1, BOX_A, 1, 0.01f);
            PghvTestUtil.wire(helper, BOX_B, 0, BOX_B, 1, load);
        });
        helper.runAfterDelay(80, () -> {
            CableBoxBlockEntity a = helper.getBlockEntity(BOX_A);
            ElectricWire[] wires = wires(a);
            double r0 = ohmPerBlock(helper, MIXED.core(0)) * LENGTH;
            double r1 = ohmPerBlock(helper, MIXED.core(1)) * LENGTH;
            double expected = volts / (load + r0 + r1 + 0.02);
            PghvTestUtil.assertNear(helper, Math.abs(wires[0].current()), expected, 0.02, "current in core 0");
            PghvTestUtil.assertNear(helper, Math.abs(wires[1].current()), expected, 0.02, "current in core 1");
            helper.assertTrue(Math.abs(wires[2].current()) < 1e-3, "core 2 must carry no current, has " + wires[2].current());
            helper.succeed();
        });
    }

    // ---- unload / reload ----

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 300)
    public static void cableReloadRebuildsWires(GameTestHelper helper) {
        lay(helper, GameType.SURVIVAL, LENGTH);
        List<ElectricWire> old = new ArrayList<>();
        helper.runAfterDelay(30, () -> {
            CableBoxBlockEntity a = helper.getBlockEntity(BOX_A);
            assertWires(helper, a, helper.getBlockEntity(BOX_B));
            old.addAll(List.of(wires(a)));
            PghvTestUtil.reload(helper, BOX_B); // partner chunk unloaded and loaded again
        });
        helper.runAfterDelay(31, () -> {
            for (ElectricWire w : old)
                helper.assertTrue(w.getNetwork() == null, "old wire still in the network after partner unload");
        });
        helper.runAfterDelay(70, () -> {
            CableBoxBlockEntity a = helper.getBlockEntity(BOX_A);
            CableBoxBlockEntity b = helper.getBlockEntity(BOX_B);
            helper.assertTrue(b.hasCable() && !b.isOwner(), "B keeps its link after reload");
            assertWires(helper, a, b);
            old.clear();
            old.addAll(List.of(wires(a)));
            PghvTestUtil.reload(helper, BOX_A); // owner chunk unloaded and loaded again
        });
        helper.runAfterDelay(71, () -> {
            for (ElectricWire w : old)
                helper.assertTrue(w.getNetwork() == null, "old wire still in the network after owner unload");
        });
        helper.runAfterDelay(110, () -> {
            CableBoxBlockEntity a = helper.getBlockEntity(BOX_A);
            CableBoxBlockEntity b = helper.getBlockEntity(BOX_B);
            helper.assertTrue(a.isOwner(), "A keeps ownership after reload");
            assertWires(helper, a, b);
            // both at once
            PghvTestUtil.reload(helper, BOX_A);
            PghvTestUtil.reload(helper, BOX_B);
            for (BlockPos cell : innerCells())
                PghvTestUtil.reload(helper, cell);
        });
        helper.runAfterDelay(150, () -> {
            assertWires(helper, helper.getBlockEntity(BOX_A), helper.getBlockEntity(BOX_B));
            // the reloaded cable can still be cut cleanly
            FakePlayer breaker = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(6, 1, 6));
            PghvTestUtil.breakBlock(helper, breaker, innerCells().get(1));
            assertNoSegments(helper);
            helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.UNDERGROUND_CABLE.get()), LENGTH,
                    "drops after reload");
            helper.succeed();
        });
    }
}
