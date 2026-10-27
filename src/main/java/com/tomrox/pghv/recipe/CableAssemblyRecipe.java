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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Shapeless: any 3 bare Power Grid wires -> bare multi-core cable. Cores keep the grid order.
 * Matching uses Power Grid's wire registry (so wires of other addons work too); the
 * {@code wires} ingredient is only used for the recipe book display.
 */
public class CableAssemblyRecipe implements CraftingRecipe {
    public static final MapCodec<CableAssemblyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(r -> r.category),
            Ingredient.CODEC_NONEMPTY.fieldOf("wires").forGetter(r -> r.wires),
            Codec.intRange(1, 64).optionalFieldOf("count", 2).forGetter(r -> r.count)
    ).apply(instance, CableAssemblyRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CableAssemblyRecipe> STREAM_CODEC = StreamCodec.composite(
            CraftingBookCategory.STREAM_CODEC, r -> r.category,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.wires,
            ByteBufCodecs.VAR_INT, r -> r.count,
            CableAssemblyRecipe::new);

    private final CraftingBookCategory category;
    private final Ingredient wires;
    private final int count;

    public CableAssemblyRecipe(CraftingBookCategory category, Ingredient wires, int count) {
        this.category = category;
        this.wires = wires;
        this.count = count;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int found = 0;
        for (int i = 0; i < input.size(); ++i) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty())
                continue;
            if (!CableCores.isValidCore(level.registryAccess(), stack.getItem()))
                return false;
            ++found;
        }
        return found == CableCores.COUNT;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        List<ResourceLocation> cores = new ArrayList<>(CableCores.COUNT);
        for (int i = 0; i < input.size(); ++i) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty())
                cores.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        if (cores.size() != CableCores.COUNT)
            return ItemStack.EMPTY;
        ItemStack out = new ItemStack(PghvItems.BARE_CABLE.get(), count);
        out.set(PghvDataComponents.CABLE_CORES.get(), new CableCores(cores));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= CableCores.COUNT;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        ItemStack out = new ItemStack(PghvItems.BARE_CABLE.get(), count);
        out.set(PghvDataComponents.CABLE_CORES.get(), CableCores.DEFAULT);
        return out;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, wires, wires, wires);
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
        return PghvRecipes.CABLE_ASSEMBLY.get();
    }
}
