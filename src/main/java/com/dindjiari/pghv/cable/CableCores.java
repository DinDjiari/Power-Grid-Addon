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
package com.dindjiari.pghv.cable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.dindjiari.pghv.registry.PghvDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The three cores of a multi-core cable (item ids of Power Grid wires, order = core 0, 1, 2).
 * Electrical values come from Power Grid's synced datapack registry {@code powergrid:wire_types}.
 */
public record CableCores(List<ResourceLocation> cores) {
    public static final int COUNT = 3;

    public static final ResourceLocation COPPER = ResourceLocation.fromNamespaceAndPath("powergrid", "wire");
    public static final ResourceLocation IRON = ResourceLocation.fromNamespaceAndPath("powergrid", "iron_wire");
    public static final ResourceLocation GOLD = ResourceLocation.fromNamespaceAndPath("powergrid", "golden_wire");

    public static final int COLOR_COPPER = 0xC87533;
    public static final int COLOR_IRON = 0xC9CCD1;
    public static final int COLOR_GOLD = 0xF2C94C;
    public static final int COLOR_OTHER = 0x9A9A9A;

    /** Fallback values (Power Grid copper wire) if a core is missing from the wire registry. */
    public static final double FALLBACK_OHM_PER_BLOCK = 0.0015 * 0.5;
    public static final double FALLBACK_MAX_CURRENT = 80.0;

    public static final CableCores DEFAULT = new CableCores(List.of(COPPER, COPPER, COPPER));

    public static final Codec<CableCores> CODEC = ResourceLocation.CODEC.listOf().comapFlatMap(
            list -> list.size() == COUNT
                    ? DataResult.success(new CableCores(list))
                    : DataResult.error(() -> "A cable needs exactly " + COUNT + " cores, got " + list.size()),
            CableCores::cores);

    public static final StreamCodec<ByteBuf, CableCores> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(COUNT)).map(CableCores::new, CableCores::cores);

    public CableCores {
        cores = List.copyOf(cores);
    }

    public static CableCores of(ResourceLocation c0, ResourceLocation c1, ResourceLocation c2) {
        return new CableCores(List.of(c0, c1, c2));
    }

    /** Cores of a cable stack; stacks without the component (e.g. /give) count as 3x copper. */
    public static CableCores get(ItemStack stack) {
        return stack.getOrDefault(PghvDataComponents.CABLE_CORES.get(), DEFAULT);
    }

    public ResourceLocation core(int index) {
        return index >= 0 && index < cores.size() ? cores.get(index) : COPPER;
    }

    // ---- colours ----

    public static int color(ResourceLocation id) {
        if (COPPER.equals(id))
            return COLOR_COPPER;
        if (IRON.equals(id))
            return COLOR_IRON;
        if (GOLD.equals(id))
            return COLOR_GOLD;
        return COLOR_OTHER;
    }

    public int color(int index) {
        return color(core(index));
    }

    // ---- wire registry ----

    @Nullable
    public static WireItemEntry entry(@Nullable HolderLookup.Provider registries, ResourceLocation id) {
        if (registries == null || id == null)
            return null;
        ResourceKey<WireItemEntry> key = ResourceKey.create(WireRegistry.KEY, id);
        return registries.lookup(WireRegistry.KEY)
                .flatMap(lookup -> lookup.get(key))
                .map(Holder::value)
                .orElse(null);
    }

    /** True if the item is a bare Power Grid wire (no cord, not insulated). */
    public static boolean isValidCore(@Nullable HolderLookup.Provider registries, Item item) {
        WireItemEntry entry = entry(registries, BuiltInRegistries.ITEM.getKey(item));
        return entry != null && !entry.cord() && !entry.insulated();
    }

    /**
     * Resistance per block (1 block = 1 m) of one core. Power Grid charges a hanging wire with
     * resistancePerItem x items, items = length x itemsPerMeter (see BaseWireEntity / WireItem tooltip),
     * so per metre this is resistancePerItem x itemsPerMeter.
     */
    public static double ohmPerBlock(@Nullable HolderLookup.Provider registries, ResourceLocation id) {
        WireItemEntry entry = entry(registries, id);
        if (entry == null)
            return FALLBACK_OHM_PER_BLOCK;
        return entry.resistancePerItem() * entry.itemsPerMeter();
    }

    public static double maxCurrent(@Nullable HolderLookup.Provider registries, ResourceLocation id) {
        WireItemEntry entry = entry(registries, id);
        return entry == null ? FALLBACK_MAX_CURRENT : entry.maximumCurrent();
    }

    public static Component name(ResourceLocation id) {
        if (!BuiltInRegistries.ITEM.containsKey(id))
            return Component.translatable("pghv.cable.unknown_core");
        return Component.translatable(BuiltInRegistries.ITEM.get(id).getDescriptionId());
    }

    // ---- NBT ----

    public ListTag toTag() {
        ListTag list = new ListTag();
        for (ResourceLocation id : cores)
            list.add(StringTag.valueOf(id.toString()));
        return list;
    }

    @Nullable
    public static CableCores fromTag(ListTag list) {
        if (list.size() != COUNT)
            return null;
        List<ResourceLocation> ids = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            ids.add(id == null ? COPPER : id);
        }
        return new CableCores(ids);
    }

    public static final int TAG_TYPE = Tag.TAG_STRING;

    // ---- formatting ----

    /** Formats a resistance with SI prefix (µΩ, mΩ, Ω, kΩ). */
    public static String formatOhm(double ohm) {
        double abs = Math.abs(ohm);
        if (abs < 1e-3)
            return String.format(Locale.ROOT, "%.3g µΩ", ohm * 1e6);
        if (abs < 1)
            return String.format(Locale.ROOT, "%.3g mΩ", ohm * 1e3);
        if (abs < 1e3)
            return String.format(Locale.ROOT, "%.3g Ω", ohm);
        return String.format(Locale.ROOT, "%.3g kΩ", ohm * 1e-3);
    }

    public static String formatAmps(double amps) {
        return String.format(Locale.ROOT, "%.1f", amps);
    }
}
