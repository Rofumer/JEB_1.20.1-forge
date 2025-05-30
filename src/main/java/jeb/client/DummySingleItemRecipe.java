package jeb.client;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class DummySingleItemRecipe implements Recipe<CraftingContainer> {
    private final ItemStack result;

    public DummySingleItemRecipe(ItemStack result) {
        this.result = result;
    }

    @Override
    public boolean matches(CraftingContainer inv, Level world) { return false; }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return false; }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return result.copy();
    }

    @Override
    public ResourceLocation getId() {
        return new ResourceLocation("jeb", "dummy_" + BuiltInRegistries.ITEM.getKey(result.getItem()).getPath());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SHAPELESS_RECIPE; // или свой, если есть
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }
}
