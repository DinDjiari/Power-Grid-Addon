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
package com.tomrox.pghv.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.registry.PghvDataComponents;
import com.tomrox.pghv.registry.PghvItems;
import com.tomrox.pghv.registry.PghvRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 3x3: bare cable in the centre, insulation (dried kelp) all around -> underground cable.
 * The cores are copied from the bare cable.
 */
public class CableInsulationRecipe implements CraftingRecipe {
    private static final int CENTER = 4;

    public static final MapCodec<CableInsulationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(r -> r.category),
            Ingredient.CODEC_NONEMPTY.fieldOf("core").forGetter(r -> r.core),
            Ingredient.CODEC_NONEMPTY.fieldOf("insulation").forGetter(r -> r.insulation),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(r -> r.count)
    ).apply(instance, CableInsulationRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CableInsulationRecipe> STREAM_CODEC = StreamCodec.composite(
            CraftingBookCategory.STREAM_CODEC, r -> r.category,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.core,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.insulation,
            ByteBufCodecs.VAR_INT, r -> r.count,
            CableInsulationRecipe::new);

    private final CraftingBookCategory category;
    private final Ingredient core;
    private final Ingredient insulation;
    private final int count;

    public CableInsulationRecipe(CraftingBookCategory category, Ingredient core, Ingredient insulation, int count) {
        this.category = category;
        this.core = core;
        this.insulation = insulation;
        this.count = count;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3)
            return false;
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = input.getItem(i);
            if (i == CENTER ? !core.test(stack) : !insulation.test(stack))
                return false;
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack out = new ItemStack(PghvItems.UNDERGROUND_CABLE.get(), count);
        out.set(PghvDataComponents.CABLE_CORES.get(), CableCores.get(input.getItem(CENTER)));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        ItemStack out = new ItemStack(PghvItems.UNDERGROUND_CABLE.get(), count);
        out.set(PghvDataComponents.CABLE_CORES.get(), CableCores.DEFAULT);
        return out;
    }

    /** Row-major 3x3 layout, so the recipe book places the bare cable in the centre. */
    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY,
                insulation, insulation, insulation,
                insulation, core, insulation,
                insulation, insulation, insulation);
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PghvRecipes.CABLE_INSULATION.get();
    }
}
