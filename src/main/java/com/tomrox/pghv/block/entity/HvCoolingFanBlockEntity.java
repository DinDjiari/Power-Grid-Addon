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
 * Modified from Create: Power Grid by patryk3211, Apache-2.0
 * (based on ElectricFanBlockEntity and the removed HvTransformerBlockEntity).
 */
package com.tomrox.pghv.block.entity;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.tomrox.pghv.block.HvCoolingFanBlock;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.transformer.TransformerBlock;
import org.patryk3211.powergrid.electricity.transformer.TransformerBlockEntity;

import java.util.ArrayList;
import java.util.List;

/*
 * Transformer cooling fan (block entity of the main part only).
 *
 * ELECTRIC
 *   Rated 240 V / 300 W at 100 % speed -> R0 = 240^2 / 300 = 192 ohm.
 *   Speed setting s (0..1, menu 0..100 % in 1 % steps): R(s) = R0 / s^2 for s >= 0.05, otherwise R_OFF = 1e9.
 *   At rated voltage P = U^2 s^2 / R0 = 300 W * s^2, i.e. the speed fraction f = sqrt(P / 300) = U/240 * s.
 *   Under-voltage therefore slows the fan down proportionally. f is taken from the real wire power
 *   (wire.power()), low-pass filtered on the server (factor 0.1/tick) and synchronised as integer steps
 *   (NBT "FanSteps", FAN_STEPS = 100 %). The wire resistance depends only on the setting, so it is
 *   written exactly when the setting changes (no hysteresis, so every 1 % step takes effect).
 *
 * COOLING (no mixin, no persistent state)
 *   ThermalBehaviour dissipates P_diss = df * (T - Ta) every tick (df = dissipationFactor, thermal mass m).
 *   The fan removes an additional heat flow
 *       P_fan = COOLING_GAIN * f * df * (T - Ta)
 *   through ThermalBehaviour.applyTickPower(-P_fan * interval) (energy / m is subtracted from T). Every
 *   fan does this on its own, so several fans add up (2 fans at f = 1: total dissipation factor 3 * df) and
 *   nothing stays modified when a fan stops or is removed: the transformer's own df is never touched.
 *   Steady state: allowed continuous power = df * (T_max - Ta) * (1 + sum f). With
 *   CThermal.maxPower(4000, 16) of the medium transformer (4000 W at 150 degC) one full fan gives 8000 W,
 *   two give 12000 W (thermal mass unchanged, so +100 % per fan on the continuous power).
 *   df is not readable directly: df = th.maxPower() / (OVERHEAT_TEMPERATURE - Ta), assuming the default
 *   overheat temperature 175 degC that every Power Grid transformer uses (ThermalBehaviour.fromConfig).
 *   Ta = ThermalBehaviour.getAmbientTemperature(level, pos) (same formula as the behaviour's cached value).
 *   The temperature is never pushed below Ta.
 *
 * AIR FLOW
 *   Like Power Grid's electric fan: IAirCurrentSource, direction = facing, strength ~ f (speed value 256 * f,
 *   no flow below f = 0.05). One Create AirCurrent per cell, so the whole 2x2 face blows (parts 1..3 use PartAirSource).
 */
public class HvCoolingFanBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, IAirCurrentSource {
    // ---- electrical ----
    public static final float RATED_VOLTAGE = 240.0f;
    public static final float RATED_POWER = 300.0f;
    public static final double R0 = (double) RATED_VOLTAGE * RATED_VOLTAGE / RATED_POWER;
    public static final double R_OFF = 1.0e9;
    /** Menu value 0..SETTING_MAX = 0..100 % in 1 % steps (like Create's rotation speed controller, % instead of RPM). */
    public static final int SETTING_MAX = 100;
    public static final int SETTING_STEP_PERCENT = 1;
    /** Milestone interval of the value settings board (like the speed controller's 32 RPM marks). */
    private static final int BOARD_MILESTONE = 10;

    // ---- cooling ----
    /** Added dissipation factor per unit fan fraction (1 = +100 % per full fan). */
    public static final float COOLING_GAIN = 1.0f;
    public static final float OVERHEAT_TEMPERATURE = 175.0f;
    private static final int COOLING_INTERVAL = 2;
    private static final float MAX_FRACTION = 1.2f;

    // ---- animation / sync ----
    public static final int FAN_STEPS = 100;
    public static final int FAN_MAX_STEPS = 120;
    public static final float FAN_MAX_DEG_PER_TICK = 60.0f;
    private static final float AIR_SPEED_AT_FULL = 256.0f;
    private static final float OFF_THRESHOLD = 0.05f;

    // Set in addBehaviours / buildCircuit (called during the super constructor): no initializers!
    private FanSpeedBehaviour speed;
    private ElectricWire fanWire;
    private int appliedSetting;

    // Server side
    private float fanSmooth;
    private int coolingTimer;
    private float lastTarget;

    // Synchronised (0..FAN_MAX_STEPS)
    private int fanSteps;

    // Client side animation
    private float fanRotSpeed;
    private float fanAngle;
    private float fanAnglePrev;

    // Air current (as in ElectricFanBlockEntity)
    private AirCurrent airCurrent;
    /** Air sources of the other three cells (part 1..3); same speed and direction as the main cell. */
    private PartAirSource[] partSources;
    private int airCurrentUpdateCooldown;
    private int entitySearchCooldown;
    private boolean updateAirFlow;
    private int prevAirSpeed;

    public HvCoolingFanBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        airCurrent = new AirCurrent(this);
        partSources = new PartAirSource[] { new PartAirSource(1), new PartAirSource(2), new PartAirSource(3) };
        updateAirFlow = true;
    }

    // ---- behaviours ----

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        speed = new FanSpeedBehaviour(Component.translatable("pghv.gui.cooling_fan.speed"), this, new SpeedBox());
        behaviours.add(speed);
    }

    /** Raw menu value 0..SETTING_MAX (shown by the value box on top of the fan, see CoolingFanTopBlockEntity). */
    public int getSpeedValue() {
        return speed == null ? SETTING_MAX : speed.getValue();
    }

    /** Sets the menu value (server side, forwarded from the top value box). */
    public void setSpeedValue(int value) {
        if (speed != null && speed.getValue() != value)
            speed.setValue(value);
    }

    /** Speed setting 0..1 from the menu. */
    public float getSetting() {
        return speed == null ? 1.0f : speed.getValue() / (float) SETTING_MAX;
    }

    private static double resistanceFor(float setting) {
        if (setting < OFF_THRESHOLD)
            return R_OFF;
        return R0 / ((double) setting * setting);
    }

    // ---- circuit ----

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        // Runs in the super constructor: do not touch 'speed' or any field with an initializer here.
        // resistance() is deliberately not used (it throws for blocks of other mods).
        builder.setTerminalCount(2);
        fanWire = builder.connect((float) R0, builder.terminalNode(0), builder.terminalNode(1));
        appliedSetting = -1; // force the next server tick to write the real resistance
    }

    // ---- tick ----

    @Override
    public void tick() {
        super.tick();
        if (level == null)
            return;
        if (level.isClientSide)
            tickFanAnimation();
        else
            tickServer();
        tickAir();
    }

    private void tickServer() {
        if (speed != null && fanWire != null && speed.getValue() != appliedSetting) {
            appliedSetting = speed.getValue();
            fanWire.setResistance(resistanceFor(getSetting()));
        }

        float target = lastTarget;
        if (fanWire != null && fanWire.isConverged()) {
            double p = Math.abs(fanWire.power());
            target = Double.isFinite(p) ? (float) Math.sqrt(p / RATED_POWER) : 0.0f;
            target = Mth.clamp(target, 0.0f, MAX_FRACTION);
            lastTarget = target;
        }
        fanSmooth += (target - fanSmooth) * 0.1f;
        if (fanSmooth < 0.002f && target == 0.0f)
            fanSmooth = 0.0f;
        int steps = Mth.clamp(Math.round(fanSmooth * FAN_STEPS), 0, FAN_MAX_STEPS);
        if (steps != fanSteps) {
            fanSteps = steps;
            sendData();
        }

        if (++coolingTimer >= COOLING_INTERVAL) {
            coolingTimer = 0;
            if (fanSmooth >= OFF_THRESHOLD)
                coolTransformers(fanSmooth);
        }
    }

    private void tickFanAnimation() {
        float target = FAN_MAX_DEG_PER_TICK * fanSteps / FAN_STEPS;
        fanRotSpeed += (target - fanRotSpeed) * 0.08f;
        if (target == 0 && fanRotSpeed < 0.05f)
            fanRotSpeed = 0;
        fanAnglePrev = fanAngle;
        fanAngle += fanRotSpeed;
        if (fanAngle >= 360.0f) {
            fanAngle -= 360.0f;
            fanAnglePrev -= 360.0f;
        }
    }

    /** Fan rotation in degrees (client), interpolated between ticks. */
    public float getFanAngle(float partialTicks) {
        return Mth.lerp(partialTicks, fanAnglePrev, fanAngle);
    }

    /** Fan speed as fraction (synchronised, quantised); 1.0 = rated speed. */
    public float getFanFraction() {
        return fanSteps / (float) FAN_STEPS;
    }

    // ---- cooling ----

    /** Transformer block entities in the four cells behind the fan (distinct). */
    public List<TransformerBlockEntity> findTransformers() {
        List<TransformerBlockEntity> result = new ArrayList<>(2);
        if (level == null)
            return result;
        BlockState state = getBlockState();
        if (!state.hasProperty(HvCoolingFanBlock.FACING))
            return result;
        Direction facing = state.getValue(HvCoolingFanBlock.FACING);
        for (int part = 0; part < 4; ++part) {
            BlockPos behind = HvCoolingFanBlock.cellOf(worldPosition, facing, part).relative(facing.getOpposite());
            if (!level.isLoaded(behind))
                continue;
            BlockState behindState = level.getBlockState(behind);
            if (behindState.getBlock() instanceof TransformerBlock block) {
                block.getBlockEntity(level, behind, behindState).ifPresent(be -> {
                    for (TransformerBlockEntity known : result) {
                        if (known == be)
                            return;
                    }
                    result.add(be);
                });
            }
        }
        return result;
    }

    private void coolTransformers(float fraction) {
        for (TransformerBlockEntity trafo : findTransformers()) {
            ThermalBehaviour thermal = trafo.getBehaviour(ThermalBehaviour.TYPE);
            if (thermal == null)
                continue;
            float temperature = thermal.getTemperature();
            if (temperature < ThermalBehaviour.ABSOLUTE_ZERO)
                continue; // behaviour not initialised yet
            float ambient = ThermalBehaviour.getAmbientTemperature(level, trafo.getBlockPos());
            if (temperature <= ambient)
                continue;
            float df = thermal.maxPower() / (OVERHEAT_TEMPERATURE - ambient);
            if (!Float.isFinite(df) || df <= 0)
                continue;
            double power = COOLING_GAIN * fraction * df * (temperature - ambient);
            thermal.applyTickPower(-power * COOLING_INTERVAL);
            if (thermal.getTemperature() < ambient)
                thermal.setTemperature(ambient);
        }
    }

    // ---- air current ----

    private void tickAir() {
        boolean server = !level.isClientSide || isVirtual();
        int airSpeed = (int) getSpeed();
        if (airSpeed != prevAirSpeed) {
            updateAirFlow = true;
            prevAirSpeed = airSpeed;
        }
        if (server && airCurrentUpdateCooldown-- <= 0) {
            airCurrentUpdateCooldown = AllConfigs.server().kinetics.fanBlockCheckRate.get();
            updateAirFlow = true;
        }
        if (updateAirFlow) {
            updateAirFlow = false;
            rebuildAllCurrents();
            sendData();
        }
        if (getSpeed() == 0)
            return;
        if (entitySearchCooldown-- <= 0) {
            entitySearchCooldown = 5;
            airCurrent.findEntities();
            for (PartAirSource part : partSources)
                part.current.findEntities();
        }
        airCurrent.tick();
        for (PartAirSource part : partSources)
            part.current.tick();
    }

    private void rebuildAllCurrents() {
        airCurrent.rebuild();
        if (partSources != null) {
            for (PartAirSource part : partSources)
                part.current.rebuild();
        }
    }

    /** Air current source for one of the three secondary cells; everything but the position comes from the main BE. */
    private final class PartAirSource implements IAirCurrentSource {
        private final int part;
        private final AirCurrent current;

        private PartAirSource(int part) {
            this.part = part;
            this.current = new AirCurrent(this);
        }

        @Override
        public AirCurrent getAirCurrent() {
            return current;
        }

        @Nullable
        @Override
        public Level getAirCurrentWorld() {
            return level;
        }

        @Override
        public BlockPos getAirCurrentPos() {
            BlockState state = getBlockState();
            if (!state.hasProperty(HvCoolingFanBlock.FACING))
                return worldPosition;
            return HvCoolingFanBlock.cellOf(worldPosition, state.getValue(HvCoolingFanBlock.FACING), part);
        }

        @Override
        public float getSpeed() {
            return HvCoolingFanBlockEntity.this.getSpeed();
        }

        @Override
        public Direction getAirflowOriginSide() {
            return HvCoolingFanBlockEntity.this.getAirflowOriginSide();
        }

        @Nullable
        @Override
        public Direction getAirFlowDirection() {
            return HvCoolingFanBlockEntity.this.getAirFlowDirection();
        }

        @Override
        public boolean isSourceRemoved() {
            return HvCoolingFanBlockEntity.this.isRemoved();
        }
    }

    public void blockInFrontChanged() {
        updateAirFlow = true;
    }

    @Override
    public AirCurrent getAirCurrent() {
        return airCurrent;
    }

    @Nullable
    @Override
    public Level getAirCurrentWorld() {
        return level;
    }

    @Override
    public BlockPos getAirCurrentPos() {
        return worldPosition;
    }

    @Override
    public float getSpeed() {
        float f = getFanFraction();
        if (f < OFF_THRESHOLD)
            return 0;
        return Math.min(f, 1.0f) * AIR_SPEED_AT_FULL;
    }

    @Override
    public Direction getAirflowOriginSide() {
        return getBlockState().getValue(HvCoolingFanBlock.FACING);
    }

    @Nullable
    @Override
    public Direction getAirFlowDirection() {
        return getSpeed() == 0 ? null : getBlockState().getValue(HvCoolingFanBlock.FACING);
    }

    @Override
    public boolean isSourceRemoved() {
        return isRemoved();
    }

    // ---- render bounds (NeoForge IBlockEntityExtension; unverified) ----

    @Override
    public AABB getRenderBoundingBox() {
        BlockState state = getBlockState();
        if (!state.hasProperty(HvCoolingFanBlock.FACING))
            return new AABB(worldPosition);
        BlockPos far = HvCoolingFanBlock.cellOf(worldPosition, state.getValue(HvCoolingFanBlock.FACING), 3);
        return new AABB(worldPosition).minmax(new AABB(far)).inflate(0.25);
    }

    // ---- persistence / sync ----

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        fanSteps = Mth.clamp(tag.getInt("FanSteps"), 0, FAN_MAX_STEPS);
        if (!clientPacket)
            fanSmooth = fanSteps / (float) FAN_STEPS;
        if (!clientPacket)
            lastTarget = fanSmooth;
        if (clientPacket && airCurrent != null)
            rebuildAllCurrents();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("FanSteps", fanSteps);
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("pghv.gui.cooling_fan.speed").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("     ")
                .append(Component.literal(Math.round(getFanFraction() * 100) + " %").withStyle(ChatFormatting.AQUA)));

        double voltage = fanWire == null ? 0 : Math.abs(fanWire.potentialDifference());
        double power = voltage * voltage / resistanceFor(getSetting());
        if (!Double.isFinite(power))
            power = 0;
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("pghv.gui.cooling_fan.power").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("     ")
                .append(Component.literal(Math.round(voltage) + " V, " + Math.round(power) + " W")
                        .withStyle(ChatFormatting.AQUA)));

        List<TransformerBlockEntity> trafos = findTransformers();
        if (trafos.isEmpty()) {
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable("pghv.gui.cooling_fan.no_trafo").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable("pghv.gui.cooling_fan.cooling").withStyle(ChatFormatting.GRAY)));
            ThermalBehaviour thermal = trafos.get(0).getBehaviour(ThermalBehaviour.TYPE);
            MutableComponent line = Component.literal("     ")
                    .append(Component.translatable("pghv.gui.cooling_fan.trafo").withStyle(ChatFormatting.AQUA));
            if (thermal != null && thermal.getTemperature() > ThermalBehaviour.ABSOLUTE_ZERO)
                line.append(Component.literal(", " + Math.round(thermal.getTemperature()) + " °C")
                        .withStyle(ChatFormatting.AQUA));
            tooltip.add(line);
        }
        return true;
    }

    // ---- value box ----

    /** Fan speed menu like Create's rotation speed controller: 0..100 % in 1 % steps, default 100 %. */
    public static class FanSpeedBehaviour extends ScrollValueBehaviour {
        public FanSpeedBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(0, SETTING_MAX);
            withFormatter(i -> (i * SETTING_STEP_PERCENT) + " %");
            value = SETTING_MAX;
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, max, BOARD_MILESTONE, ImmutableList.of(Component.literal("%")),
                    new ValueSettingsFormatter(this::formatSettings));
        }

        public MutableComponent formatSettings(ValueSettings settings) {
            return Component.literal((settings.value() * SETTING_STEP_PERCENT) + " %");
        }
    }

    /**
     * Value box of the main part: kept for the stored setting, but hidden and not clickable -
     * the visible box sits centred on top of the fan (CoolingFanTopBlockEntity.TopBox).
     */
    public static class SpeedBox extends ValueBoxTransform {
        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            return false;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            // Expressed for facing SOUTH (front = +z); rotateHorizontally turns it to the real facing.
            double z0 = state.getValue(HvCoolingFanBlock.SLOT) * HvCoolingFanBlock.SLOT_SIZE;
            return rotateHorizontally(state, VecHelper.voxelSpace(10, 7, 16 - z0 - 0.2));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            // Wie ValueBoxTransform.Sided fuer die Seite = facing (Front zeigt nach facing).
            float yRot = AngleHelper.horizontalAngle(state.getValue(HvCoolingFanBlock.FACING)) + 180;
            TransformStack.of(ms).rotateYDegrees(yRot);
        }
    }
}
