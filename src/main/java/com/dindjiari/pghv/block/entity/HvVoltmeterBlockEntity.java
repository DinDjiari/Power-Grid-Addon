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
 *
 * Modified from Create: Power Grid by patryk3211, Apache-2.0.
 */
package com.dindjiari.pghv.block.entity;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.gauge.GaugeBlockEntity;
import org.patryk3211.powergrid.electricity.gauge.GaugeValueBehaviour;
import org.patryk3211.powergrid.electricity.info.customdisplay.CustomDisplayBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.Unit;

import java.util.List;

/**
 * HV voltmeter: measures across two terminals via a fixed shunt resistance of
 * 1e4 ohm per volt of range. The resistance is hard-coded here because
 * Power Grid's resistance config only knows its own blocks.
 */
public class HvVoltmeterBlockEntity extends GaugeBlockEntity {
    /** Selectable ranges in volts: 20 kV, 200 kV, 2 MV. Index 0 is the default. */
    public static final float[] RANGES = new float[] { 20_000f, 200_000f, 2_000_000f };
    public static final float MAX_RANGE = RANGES[RANGES.length - 1];
    private static final float OHM_PER_VOLT = 1e4f;

    // Set in buildCircuit, deliberately without initializers.
    private IElectricNode node1;
    private IElectricNode node2;
    private ElectricWire connection;

    public HvVoltmeterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        maxValue = RANGES[0];
    }

    private static float resistanceFor(float range) {
        for (float r : RANGES) {
            if (r == range)
                return r * OHM_PER_VOLT;
        }
        throw new IllegalArgumentException("Maximum value must match valid range");
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        gaugeValue = new GaugeValueBehaviour(Component.translatable("powergrid.devices.gauge.voltage"),
                Unit.VOLTAGE.get().component(), RANGES, this, new BoxTransform());
        gaugeValue.withCallback(i -> {
            setMaxValue(RANGES[i]);
            sendData();
        });
        behaviours.add(gaugeValue);
    }

    /** Same as the original, but with SI prefixes so values show as kV / MV. */
    @Override
    protected CustomDisplayBehaviour displayBehaviour() {
        return new CustomDisplayBehaviour(this, getUnit(), true, this::getMaxValue, this::getColor, "x");
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        int index = Math.max(0, Math.min(RANGES.length - 1, gaugeValue.getValue()));
        setMaxValue(RANGES[index]);
    }

    @Override
    public void tick() {
        var potential = Math.abs(getValue());
        if (potential > maxValue) {
            dialTarget = 1.125f;
        } else {
            dialTarget = potential / maxValue;
        }
        super.tick();
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
        node1 = builder.terminalNode(0);
        node2 = builder.terminalNode(1);
        // buildCircuit läuft schon im Super-Konstruktor, dort ist maxValue noch 0
        float range = maxValue > 0 ? maxValue : RANGES[0];
        connection = builder.connect(resistanceFor(range), node1, node2);
    }

    @Override
    public float getMaxValue() {
        return maxValue;
    }

    private void setMaxValue(float value) {
        maxValue = value;
        if (connection != null)
            connection.setResistance(resistanceFor(value));
    }

    @Override
    public ChatFormatting getColor(float value) {
        return measurementColor(value, maxValue);
    }

    @Override
    public float getValue() {
        return (float) (node1.getVoltage() - node2.getVoltage());
    }

    @Override
    public Unit getUnit() {
        return Unit.VOLTAGE;
    }

    public static ChatFormatting measurementColor(float value, float maxValue) {
        if (value < maxValue * 0.01)
            return ChatFormatting.DARK_GRAY;
        else if (value < maxValue * 0.5)
            return ChatFormatting.GREEN;
        else if (value < maxValue * 0.75)
            return ChatFormatting.BLUE;
        else
            return ChatFormatting.LIGHT_PURPLE;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        Lang.builder().translate("gui.voltage_meter.title")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        display.format(getValue(), this).forGoggles(tooltip, 1);
        return true;
    }
}
