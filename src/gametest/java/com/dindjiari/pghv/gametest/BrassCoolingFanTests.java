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
package com.dindjiari.pghv.gametest;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.dindjiari.pghv.Pghv;
import com.dindjiari.pghv.block.HvCoolingFanBlock;
import com.dindjiari.pghv.block.entity.CoolingFanTopBlockEntity;
import com.dindjiari.pghv.block.entity.HvCoolingFanBlockEntity;
import com.dindjiari.pghv.registry.PghvBlockEntities;
import com.dindjiari.pghv.registry.PghvBlocks;
import com.dindjiari.pghv.registry.PghvItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
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
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.transformer.TransformerBlockEntity;
import org.patryk3211.powergrid.electricity.transformer.TransformerMediumBlock;

/** Brass transformer fan: recipe, automatic control, consumption, animation x2, cooling x2, target value box. */
@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class BrassCoolingFanTests {
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

    /** Snaps a fan of the given item onto the south face of the transformer at {@code trafo}; returns the main cell. */
    private static BlockPos fanOnTransformer(GameTestHelper helper, BlockPos trafo, Item fanItem) {
        BlockPos main = trafo.relative(FACING);
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, main.relative(FACING, 3));
        player.setYRot(FACING.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fanItem));
        PghvTestUtil.use(helper, player, trafo, FACING);
        return main;
    }

    /** Free-standing fan on the floor; returns the main cell. */
    private static BlockPos fanOnFloor(GameTestHelper helper, BlockPos main, Item fanItem) {
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, main.relative(FACING, 3));
        player.setYRot(FACING.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fanItem));
        PghvTestUtil.use(helper, player, main.below(), Direction.UP);
        return main;
    }

    /** 240 V source at {@code source} wired to the fan's main part (after 2 ticks). */
    private static void power(GameTestHelper helper, BlockPos source, BlockPos main) {
        helper.runAfterDelay(2, () -> {
            PghvTestUtil.voltageSource(helper, source, HvCoolingFanBlockEntity.RATED_VOLTAGE);
            PghvTestUtil.wire(helper, source, 0, main, 0, 0.01f);
            PghvTestUtil.wire(helper, source, 1, main, 1, 0.01f);
        });
    }

    private static ThermalBehaviour thermal(GameTestHelper helper, BlockPos trafoOrigin) {
        TransformerBlockEntity be = helper.getBlockEntity(trafoOrigin);
        ThermalBehaviour thermal = be.getBehaviour(ThermalBehaviour.TYPE);
        if (thermal == null)
            throw new IllegalStateException("transformer without thermal behaviour");
        return thermal;
    }

    private static int rawTarget(int degC) {
        return (degC - HvCoolingFanBlockEntity.TARGET_MIN) / HvCoolingFanBlockEntity.TARGET_STEP;
    }

    // ---- recipe ----

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void brassFanRecipe(GameTestHelper helper) {
        Level level = helper.getLevel();
        Item tube = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "electron_tube"));
        Item brass = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "brass_ingot"));
        helper.assertTrue(tube != Items.AIR && brass != Items.AIR, "Create items exist");

        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        grid.set(1, new ItemStack(tube));
        grid.set(4, new ItemStack(PghvItems.COOLING_FAN.get()));
        grid.set(7, new ItemStack(brass));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(h -> h.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(PghvItems.BRASS_COOLING_FAN.get()), "tube / fan / brass ingot -> brass fan, got " + result);
        helper.assertValueEqual(result.getCount(), 1, "brass fan count");

        // Wrong order (brass on top) must not craft.
        NonNullList<ItemStack> swapped = NonNullList.withSize(9, ItemStack.EMPTY);
        swapped.set(1, new ItemStack(brass));
        swapped.set(4, new ItemStack(PghvItems.COOLING_FAN.get()));
        swapped.set(7, new ItemStack(tube));
        CraftingInput wrong = CraftingInput.of(3, 3, swapped);
        boolean matches = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, wrong, level)
                .map(h -> h.value().assemble(wrong, level.registryAccess()).is(PghvItems.BRASS_COOLING_FAN.get()))
                .orElse(false);
        helper.assertFalse(matches, "swapped column must not craft the brass fan");
        helper.succeed();
    }

    // ---- control ----

    /** Cold transformer: brass fan stays off and draws (almost) nothing, the andesite fan runs at 300 W. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 300)
    public static void brassFanColdTransformerOffAndCheaper(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos brassTrafo = new BlockPos(2, 1, 3);
        BlockPos andesiteTrafo = new BlockPos(9, 1, 3);
        placeTransformer(helper, brassTrafo);
        placeTransformer(helper, andesiteTrafo);
        BlockPos brassMain = fanOnTransformer(helper, brassTrafo, PghvItems.BRASS_COOLING_FAN.get());
        BlockPos andesiteMain = fanOnTransformer(helper, andesiteTrafo, PghvItems.COOLING_FAN.get());
        power(helper, new BlockPos(2, 1, 10), brassMain);
        power(helper, new BlockPos(9, 1, 10), andesiteMain);

        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(PghvBlocks.BRASS_COOLING_FAN.get(), brassMain);
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(brassMain);
            helper.assertTrue(fan.isBrass(), "brass variant detected from the block");
            helper.assertValueEqual(fan.getType(), PghvBlockEntities.BRASS_COOLING_FAN.get(), "brass block entity type");
            helper.assertValueEqual(fan.getTargetTemperature(), 80, "default target temperature");
            // Keep the transformer at ambient temperature.
            float ambient = ThermalBehaviour.getAmbientTemperature(helper.getLevel(), helper.absolutePos(brassTrafo));
            thermal(helper, brassTrafo).setTemperature(ambient);
        });
        helper.runAfterDelay(120, () -> {
            HvCoolingFanBlockEntity brass = helper.getBlockEntity(brassMain);
            HvCoolingFanBlockEntity andesite = helper.getBlockEntity(andesiteMain);
            helper.assertValueEqual(brass.getAutoPercent(), 0, "regulator output at cold transformer");
            helper.assertTrue(brass.getFanFraction() < 0.05f, "brass fan should stand still, is " + brass.getFanFraction());
            helper.assertValueEqual(brass.getSpeed(), 0f, "no air flow from the stopped brass fan");
            helper.assertTrue(andesite.getFanFraction() > 0.9f, "andesite fan runs, is " + andesite.getFanFraction());
            double pBrass = brass.getElectricPower();
            double pAndesite = andesite.getElectricPower();
            helper.assertTrue(pAndesite > 250.0, "andesite fan draws about 300 W, is " + pAndesite);
            helper.assertTrue(pBrass < 1.0 && pBrass < pAndesite,
                    "brass fan at cold transformer must draw less: " + pBrass + " W vs " + pAndesite + " W");
            helper.succeed();
        });
    }

    /** Hot transformer: the regulator switches the fan on (full speed well above target + 25 degC). */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 300)
    public static void brassFanHotTransformerSpeedsUp(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos trafo = new BlockPos(2, 1, 3);
        placeTransformer(helper, trafo);
        BlockPos main = fanOnTransformer(helper, trafo, PghvItems.BRASS_COOLING_FAN.get());
        power(helper, new BlockPos(2, 1, 10), main);

        helper.runAfterDelay(60, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertTrue(fan.getFanFraction() < 0.05f, "off before heating, is " + fan.getFanFraction());
        });
        // Hold the transformer hot (target 80 degC -> full speed from 105 degC).
        for (int t = 61; t <= 140; ++t)
            helper.runAfterDelay(t, () -> thermal(helper, trafo).setTemperature(150.0f));
        helper.runAfterDelay(141, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertValueEqual(fan.getAutoPercent(), 100, "regulator output at 150 degC");
            helper.assertTrue(fan.getFanFraction() > 0.9f, "brass fan runs up, is " + fan.getFanFraction());
            helper.assertTrue(fan.getSpeed() > 0, "air flow when running");
            helper.assertTrue(fan.getElectricPower() > 250.0, "draws rated power, is " + fan.getElectricPower());
            // Characteristic: 5 degC below target -> 0, target + 25 degC -> 1, halfway at target + 10 degC.
            PghvTestUtil.assertNear(helper, HvCoolingFanBlockEntity.controlSpeed(90f, 80f), 0.5, 0.01, "s(target + 10)");
            helper.assertValueEqual(HvCoolingFanBlockEntity.controlSpeed(75f, 80f), 0f, "s(target - 5)");
            helper.assertValueEqual(HvCoolingFanBlockEntity.controlSpeed(105f, 80f), 1f, "s(target + 25)");
            helper.succeed();
        });
    }

    /** Free-standing brass fan (no transformer behind it) stays off even when powered. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void brassFanWithoutTransformerOff(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos main = fanOnFloor(helper, new BlockPos(7, 1, 3), PghvItems.BRASS_COOLING_FAN.get());
        power(helper, new BlockPos(2, 1, 2), main);
        helper.runAfterDelay(100, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertValueEqual(fan.getAutoPercent(), 0, "no transformer -> off");
            helper.assertTrue(fan.getFanFraction() < 0.05f, "fan stands still, is " + fan.getFanFraction());
            helper.succeed();
        });
    }

    // ---- x2 effects ----

    /** Both fans at full speed: rotor target speed of the brass fan is twice that of the andesite fan. */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 300)
    public static void brassFanRotorTwiceAsFast(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos brassTrafo = new BlockPos(2, 1, 3);
        placeTransformer(helper, brassTrafo);
        BlockPos brassMain = fanOnTransformer(helper, brassTrafo, PghvItems.BRASS_COOLING_FAN.get());
        BlockPos andesiteMain = fanOnFloor(helper, new BlockPos(10, 1, 3), PghvItems.COOLING_FAN.get());
        power(helper, new BlockPos(2, 1, 10), brassMain);
        power(helper, new BlockPos(10, 1, 10), andesiteMain);
        for (int t = 5; t <= 140; ++t)
            helper.runAfterDelay(t, () -> thermal(helper, brassTrafo).setTemperature(150.0f));
        helper.runAfterDelay(141, () -> {
            HvCoolingFanBlockEntity brass = helper.getBlockEntity(brassMain);
            HvCoolingFanBlockEntity andesite = helper.getBlockEntity(andesiteMain);
            helper.assertTrue(brass.getFanFraction() > 0.9f && andesite.getFanFraction() > 0.9f,
                    "both at full speed: " + brass.getFanFraction() + " / " + andesite.getFanFraction());
            double perUnitBrass = brass.getRotorTargetSpeed() / brass.getFanFraction();
            double perUnitAndesite = andesite.getRotorTargetSpeed() / andesite.getFanFraction();
            PghvTestUtil.assertNear(helper, perUnitBrass, 2.0 * perUnitAndesite, 0.02, "brass rotor speed per unit fraction");
            helper.assertValueEqual(brass.getCoolingGain(), 2.0f, "brass cooling gain");
            helper.assertValueEqual(andesite.getCoolingGain(), 1.0f, "andesite cooling gain unchanged");
            helper.succeed();
        });
    }

    /**
     * Cooling x2: three transformers start at 150 degC - one with the andesite fan (100 %), one with the brass fan
     * (target 40 degC, so it runs at 100 % down to 65 degC) and one without fan. With the decay rate k per
     * dissipation unit, ln(dT0/dT) = k t (1 + G f): the extra decay of the brass fan must be twice the andesite's.
     */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 400)
    public static void brassFanCoolsTwiceAsMuch(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos andesiteTrafo = new BlockPos(1, 1, 3);
        BlockPos brassTrafo = new BlockPos(6, 1, 3);
        BlockPos reference = new BlockPos(11, 1, 3);
        placeTransformer(helper, andesiteTrafo);
        placeTransformer(helper, brassTrafo);
        placeTransformer(helper, reference);
        BlockPos andesiteMain = fanOnTransformer(helper, andesiteTrafo, PghvItems.COOLING_FAN.get());
        BlockPos brassMain = fanOnTransformer(helper, brassTrafo, PghvItems.BRASS_COOLING_FAN.get());
        power(helper, new BlockPos(1, 1, 10), andesiteMain);
        power(helper, new BlockPos(6, 1, 10), brassMain);

        float start = 150.0f;
        helper.runAfterDelay(5, () -> {
            HvCoolingFanBlockEntity brass = helper.getBlockEntity(brassMain);
            brass.setSpeedValue(rawTarget(40));
        });
        // Bring the brass fan to full speed by holding its transformer hot, then start all three at 150 degC.
        for (int t = 6; t <= 120; ++t)
            helper.runAfterDelay(t, () -> thermal(helper, brassTrafo).setTemperature(start));
        helper.runAfterDelay(121, () -> {
            HvCoolingFanBlockEntity brass = helper.getBlockEntity(brassMain);
            HvCoolingFanBlockEntity andesite = helper.getBlockEntity(andesiteMain);
            helper.assertTrue(brass.getFanFraction() > 0.95f, "brass fan at full speed, is " + brass.getFanFraction());
            helper.assertTrue(andesite.getFanFraction() > 0.95f, "andesite fan at full speed, is " + andesite.getFanFraction());
            // An unwound transformer has a tiny thermal mass and loses a third of its excess heat within 4 ticks,
            // too fast for the evaluation below. Scaling the mass of all three equally only slows the decay down;
            // the compared ratios stay the same.
            for (BlockPos trafo : new BlockPos[] { andesiteTrafo, brassTrafo, reference }) {
                ThermalBehaviour th = thermal(helper, trafo);
                th.setThermalMass(thermalMass(th) * 8);
                th.setTemperature(start);
            }
        });
        // Evaluate as soon as the reference has lost 20 % of its excess temperature (the brass transformer is then
        // still well above 65 degC, i.e. the brass fan stays at 100 %).
        boolean[] done = { false };
        for (int t = 125; t < 390; ++t) {
            final boolean last = t == 389;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                float amb = ThermalBehaviour.getAmbientTemperature(helper.getLevel(), helper.absolutePos(reference));
                if (!last && thermal(helper, reference).getTemperature() - amb > (start - amb) * 0.8f)
                    return;
                done[0] = true;
                evaluateCooling(helper, start, andesiteTrafo, brassTrafo, reference);
            });
        }
    }

    private static float thermalMass(ThermalBehaviour thermal) {
        try {
            java.lang.reflect.Field f = ThermalBehaviour.class.getDeclaredField("thermalMass");
            f.setAccessible(true);
            return f.getFloat(thermal);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void evaluateCooling(GameTestHelper helper, float start, BlockPos andesiteTrafo, BlockPos brassTrafo,
                                        BlockPos reference) {
        float ambient = ThermalBehaviour.getAmbientTemperature(helper.getLevel(), helper.absolutePos(reference));
        double d0 = start - ambient;
        double dAndesite = thermal(helper, andesiteTrafo).getTemperature() - ambient;
        double dBrass = thermal(helper, brassTrafo).getTemperature() - ambient;
        double dRef = thermal(helper, reference).getTemperature() - ambient;
        helper.assertTrue(dBrass + ambient > 65.0, "brass transformer still above target + 25 degC (full speed)");
        helper.assertTrue(dRef > 0 && dAndesite > 0 && dBrass > 0 && dRef < d0, "temperatures decay: ref " + dRef);
        double lnRef = Math.log(d0 / dRef);
        double extraAndesite = Math.log(d0 / dAndesite) - lnRef;
        double extraBrass = Math.log(d0 / dBrass) - lnRef;
        helper.assertTrue(extraAndesite > 0, "andesite fan cools at all: " + extraAndesite);
        double ratio = extraBrass / extraAndesite;
        helper.assertTrue(ratio > 1.6 && ratio < 2.4,
                "brass fan should cool twice as much as the andesite fan, ratio " + ratio);
        helper.succeed();
    }

    // ---- value box ----

    /** The value box on top sets the target temperature of the brass fan (stored in the main part). */
    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void brassFanTopValueBoxSetsTarget(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos main = fanOnFloor(helper, new BlockPos(7, 1, 3), PghvItems.BRASS_COOLING_FAN.get());
        BlockPos top2 = HvCoolingFanBlock.cellOf(main, FACING, 2);
        BlockPos top3 = HvCoolingFanBlock.cellOf(main, FACING, 3);
        helper.runAfterDelay(5, () -> {
            CoolingFanTopBlockEntity box = helper.getBlockEntity(top2);
            helper.assertValueEqual(box.getType(), PghvBlockEntities.BRASS_COOLING_FAN_TOP.get(), "top block entity type");
            helper.assertTrue(helper.getBlockEntity(top3) instanceof CoolingFanTopBlockEntity, "upper right cell has a value box");
            ScrollValueBehaviour behaviour = box.getBehaviour(ScrollValueBehaviour.TYPE);
            helper.assertTrue(behaviour instanceof HvCoolingFanBlockEntity.FanSpeedBehaviour fsb && fsb.isTargetTemperature(),
                    "top value box is in target temperature mode");
            helper.assertValueEqual(behaviour.getValue(), rawTarget(80), "default 80 degC on top");
            behaviour.setValue(rawTarget(120));
        });
        helper.runAfterDelay(30, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertValueEqual(fan.getSpeedValue(), rawTarget(120), "raw value stored in the main part");
            helper.assertValueEqual(fan.getTargetTemperature(), 120, "target temperature from top box");
            HvCoolingFanBlockEntity reloaded = PghvTestUtil.reload(helper, main);
            helper.assertValueEqual(reloaded.getTargetTemperature(), 120, "target temperature after reload");
            helper.succeed();
        });
    }
}
