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

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.tomrox.pghv.Pghv;
import com.tomrox.pghv.block.entity.HvVoltmeterBlockEntity;
import com.tomrox.pghv.registry.PghvBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class VoltmeterTests {
    private static final BlockPos METER = new BlockPos(2, 1, 2);
    private static final BlockPos SOURCE = new BlockPos(6, 1, 2);

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void voltmeterPlaceAndRanges(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        helper.setBlock(METER, PghvBlocks.HV_VOLTMETER.get());
        helper.runAfterDelay(2, () -> {
            if (!(helper.getBlockEntity(METER) instanceof HvVoltmeterBlockEntity meter)) {
                helper.fail("No HV voltmeter block entity", METER);
                return;
            }
            helper.assertValueEqual(meter.getMaxValue(), 20_000f, "default range");
            ScrollValueBehaviour range = meter.getBehaviour(ScrollValueBehaviour.TYPE);
            helper.assertTrue(range != null, "range behaviour missing");
            range.setValue(1);
            helper.assertValueEqual(meter.getMaxValue(), 200_000f, "range 1");
            range.setValue(2);
            helper.assertValueEqual(meter.getMaxValue(), 2_000_000f, "range 2");

            HvVoltmeterBlockEntity reloaded = PghvTestUtil.reload(helper, METER);
            helper.assertValueEqual(reloaded.getMaxValue(), 2_000_000f, "range after reload");
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void voltmeterMeasures150kV(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        helper.setBlock(METER, PghvBlocks.HV_VOLTMETER.get());
        PghvTestUtil.voltageSource(helper, SOURCE, 150_000f);
        helper.runAfterDelay(2, () -> {
            HvVoltmeterBlockEntity meter = helper.getBlockEntity(METER);
            meter.getBehaviour(ScrollValueBehaviour.TYPE).setValue(1); // 200 kV
            PghvTestUtil.wire(helper, SOURCE, 0, METER, 0, 0.01f);
            PghvTestUtil.wire(helper, SOURCE, 1, METER, 1, 0.01f);
        });
        helper.runAfterDelay(60, () -> {
            HvVoltmeterBlockEntity meter = helper.getBlockEntity(METER);
            PghvTestUtil.assertNear(helper, Math.abs(meter.getValue()), 150_000, 0.01, "measured voltage");
            helper.assertTrue(meter.dialTarget > 0.7f && meter.dialTarget < 0.8f,
                    "dial at 150 kV / 200 kV should be 0.75, is " + meter.dialTarget);
            helper.succeed();
        });
    }
}
