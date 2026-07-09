package jeb.client;

import net.minecraft.client.RecipeBookCategories;

public record SearchHistoryEntry(String query, RecipeBookCategories category) {}
