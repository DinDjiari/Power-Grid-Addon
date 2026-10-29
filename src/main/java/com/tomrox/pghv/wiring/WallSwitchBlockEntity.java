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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;

/** Wall switch / button: L switched between terminals 0 and 2, N always 1 - 3. Fields set in buildCircuit only. */
public class WallSwitchBlockEntity extends WallDeviceBlockEntity {
    private static final float CONTACT_RESISTANCE = 1e-3f;
    private SwitchedWire lineSwitch;

    public WallSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(4 + 1); // + wall connector
        buildWallConnector(builder);
        boolean on = getBlockState().hasProperty(WallSwitchBlock.POWERED) && getBlockState().getValue(WallSwitchBlock.POWERED);
        builder.connect(CONTACT_RESISTANCE, builder.terminalNode(1), builder.terminalNode(3));
        lineSwitch = builder.connectSwitch(CONTACT_RESISTANCE, builder.terminalNode(0), builder.terminalNode(2), on);
    }

    public void setOn(boolean on) {
        if (lineSwitch != null)
            lineSwitch.setState(on);
        notifyUpdate();
    }

    public boolean isOn() {
        return getBlockState().hasProperty(WallSwitchBlock.POWERED) && getBlockState().getValue(WallSwitchBlock.POWERED);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        line(tooltip, getBlockState().getBlock().getName().withStyle(ChatFormatting.GRAY), 0);
        line(tooltip, Component.translatable(isOn() ? "pghv.wall.goggles.on" : "pghv.wall.goggles.off")
                .withStyle(isOn() ? ChatFormatting.GREEN : ChatFormatting.GOLD), 1);
        cableLines(tooltip);
        return true;
    }
}
