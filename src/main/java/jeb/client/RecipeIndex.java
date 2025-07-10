package jeb.client;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Unique;


import java.util.*;

import static jeb.Jeb.LOGGER;
import static jeb.Jeb.nonexistingResultItems;

public class RecipeIndex {
    // Индексы по категориям
    public final Map<RecipeBookCategories, Map<String, List<RecipeCollection>>> byResult = new HashMap<>();
    public final Map<RecipeBookCategories, Map<String, List<RecipeCollection>>> byMod = new HashMap<>();
    public final Map<RecipeBookCategories, Map<String, List<RecipeCollection>>> byIngredientWord = new HashMap<>();
    public final Map<RecipeBookCategories, Map<String, List<RecipeCollection>>> byTooltipWord = new HashMap<>();
    public final Map<RecipeBookCategories, Set<RecipeCollection>> allCollections = new HashMap<>();
    public static final RecipeIndex GLOBAL_RECIPE_INDEX = new RecipeIndex();

    public static boolean jebIndexReady = false;

    public static RecipeManager recipeManager;


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void getAllRecipesUnsafe(RecipeManager manager, RecipeType type, Minecraft minecraft) {
        for (Object obj : manager.getAllRecipesFor(type)) {
            Recipe<?> holder = (Recipe<?>) obj;
            System.out.println("    " + holder.getResultItem(minecraft.level.registryAccess()));
        }
    }

    public static void buildRecipeIndex() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("[JEB] buildRecipeIndex started at {}", new Date(startTime));
        jebIndexReady = false;
        Minecraft minecraft = Minecraft.getInstance();
        ClientRecipeBook book = minecraft.player.getRecipeBook();

        GLOBAL_RECIPE_INDEX.byResult.clear();
        GLOBAL_RECIPE_INDEX.byMod.clear();
        GLOBAL_RECIPE_INDEX.byIngredientWord.clear();
        GLOBAL_RECIPE_INDEX.byTooltipWord.clear();
        GLOBAL_RECIPE_INDEX.allCollections.clear();

        int totalIndexedRecipes = 0;
        Set<ResourceLocation> uniqueRecipes = new HashSet<>();

        for (RecipeBookCategories category : RecipeBookCategories.values()) {
            List<RecipeCollection> collections = book.getCollection(category);
            if (collections.isEmpty()) continue;

            Set<RecipeCollection> categoryCollections = new LinkedHashSet<>();
            Map<String, List<RecipeCollection>> resultIndex = new HashMap<>();
            Map<String, List<RecipeCollection>> modIndex = new HashMap<>();
            Map<String, List<RecipeCollection>> ingredientIndex = new HashMap<>();
            Map<String, List<RecipeCollection>> tooltipIndex = new HashMap<>();

            for (RecipeCollection collection : collections) {
                categoryCollections.add(collection);
                for (Recipe<?> recipe : collection.getRecipes()) {
                    ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
                    if (result == null || result.isEmpty()) continue;
                    ResourceLocation recipeId = recipe.getId();
                    if (uniqueRecipes.add(recipeId)) {
                        totalIndexedRecipes++;
                    }
                    // --- Индекс по результату ---
                    String resultId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString().toLowerCase(Locale.ROOT);
                    resultIndex.computeIfAbsent(resultId, k -> new ArrayList<>()).add(collection);
                    // По имени (displayName)
                    String name = result.getDisplayName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", "");
                    ///resultIndex.computeIfAbsent(name, k -> new ArrayList<>()).add(collection);
                    for (String source : List.of(resultId, name)) {
                        for (int i = 0; i < source.length(); i++) {
                            for (int j = i + 1; j <= source.length(); j++) {
                                String substr = source.substring(i, j);
                                if (substr.isEmpty()) continue;
                                resultIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(collection);
                            }
                        }
                    }


                    // --- Индекс по модам ---
                    String mod = BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace().toLowerCase(Locale.ROOT);
                    ///modIndex.computeIfAbsent(mod, k -> new ArrayList<>()).add(collection);

                    for (int i = 0; i < mod.length(); i++) {
                        for (int j = i + 1; j <= mod.length(); j++) {
                            String substr = mod.substring(i, j);
                            if (substr.isEmpty()) continue;
                            modIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(collection);
                        }
                    }


                    // --- Индекс по ингредиентам ---
                    for (var ingredient : recipe.getIngredients()) {
                        for (ItemStack stack : ingredient.getItems()) {
                            String ingredientId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                            ingredientIndex.computeIfAbsent(ingredientId, k -> new ArrayList<>()).add(collection);
                        }
                    }

                    // --- Индекс по тултипам ---
                    List<String> tooltipLines = new ArrayList<>();
                    try {
                        var tooltipFlag = minecraft.options.advancedItemTooltips
                                ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED
                                : net.minecraft.world.item.TooltipFlag.Default.NORMAL;
                        tooltipLines = result.getTooltipLines( minecraft.player, tooltipFlag)
                                .stream()
                                .map(c -> net.minecraft.ChatFormatting.stripFormatting(c.getString()).toLowerCase(Locale.ROOT).trim())
                                .toList();
                    } catch (Exception e) {}

                    for (String tooltipLine : tooltipLines) {
                        for (String word : tooltipLine.split("[\\s,;.:!\\-]+")) {
                            if (word.length() < 3) continue;
                            ///tooltipIndex.computeIfAbsent(word, k -> new ArrayList<>()).add(collection);
                            for (int i = 0; i <= word.length() - 3; i++) {
                                for (int j = i + 3; j <= word.length(); j++) {
                                    String substr = word.substring(i, j);
                                    if (substr.isEmpty()) continue;
                                    tooltipIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(collection);
                                }
                            }

                        }
                    }
                }
            }

            GLOBAL_RECIPE_INDEX.allCollections.put(category, categoryCollections);
            GLOBAL_RECIPE_INDEX.byResult.put(category, resultIndex);
            GLOBAL_RECIPE_INDEX.byMod.put(category, modIndex);
            GLOBAL_RECIPE_INDEX.byIngredientWord.put(category, ingredientIndex);
            GLOBAL_RECIPE_INDEX.byTooltipWord.put(category, tooltipIndex);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOGGER.info("[JEB] buildRecipeIndex category {} done at {} ({} ms)", category, new Date(endTime), duration);

        }

        jebIndexReady = true;
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        LOGGER.info("[JEB] buildRecipeIndex done at {} ({} ms), total indexed recipes: {}", new Date(endTime), duration, totalIndexedRecipes);
    }


    public static List<RecipeCollection> fastSearch(
            List<RecipeBookCategories> categories,
            String query,
            String modName,
            boolean searchIngredients
    ) {
        if (!jebIndexReady) return List.of();

        query = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        modName = modName == null ? "" : modName.toLowerCase(Locale.ROOT).trim();

        Set<RecipeCollection> result = new LinkedHashSet<>();

        for (RecipeBookCategories category : categories) {
            Map<String, List<RecipeCollection>> modIndex = GLOBAL_RECIPE_INDEX.byMod.getOrDefault(category, Map.of());
            Map<String, List<RecipeCollection>> ingredientIndex = GLOBAL_RECIPE_INDEX.byIngredientWord.getOrDefault(category, Map.of());
            Set<RecipeCollection> all = GLOBAL_RECIPE_INDEX.allCollections.getOrDefault(category, Set.of());
            Map<String, List<RecipeCollection>> resultIndex = GLOBAL_RECIPE_INDEX.byResult.getOrDefault(category, Map.of());
            Map<String, List<RecipeCollection>> tooltipIndex = GLOBAL_RECIPE_INDEX.byTooltipWord.getOrDefault(category, Map.of());

            if ((modName.isEmpty()) && query.isEmpty()) {
                result.addAll(all);
                continue;
            }

            if (!modName.isEmpty()) {
                List<RecipeCollection> modCollections = modIndex.getOrDefault(modName, List.of());
                if (query.isEmpty()) {
                    result.addAll(modCollections);
                    continue;
                }
                for (String word : query.split("[\\s:_\\-]+")) {
                    List<RecipeCollection> byWord = resultIndex.getOrDefault(word, List.of());
                    for (RecipeCollection rc : byWord) {
                        if (modCollections.contains(rc))
                            result.add(rc);
                    }
                }
                continue;
            }

            if (searchIngredients && !query.isEmpty()) {
                List<RecipeCollection> byIng = ingredientIndex.getOrDefault(query, List.of());
                result.addAll(byIng);
                continue;
            }

            if (!query.isEmpty() && !searchIngredients) {
                List<RecipeCollection> byResult = resultIndex.getOrDefault(query, List.of());
                result.addAll(byResult);
                List<RecipeCollection> byTooltip = tooltipIndex.getOrDefault(query, List.of());
                result.addAll(byTooltip);
            }
        }

        return new ArrayList<>(result);
    }


    public static List<RecipeCollection> fastSearch(
            Object categoryOrType, String query, String modName, boolean searchIngredients
    ) {
        List<RecipeBookCategories> categories;
        if (categoryOrType instanceof List) {
            categories = (List<RecipeBookCategories>) categoryOrType;
        } else if (categoryOrType instanceof RecipeBookCategories) {
            categories = List.of((RecipeBookCategories) categoryOrType);
        } else if (categoryOrType instanceof RecipeBookType) {
            // Если хочешь — сделай маппинг из RecipeBookType к RecipeBookCategories
            categories = List.of(); // (или свой маппинг)
        } else {
            categories = List.of();
        }
        return fastSearch(categories, query, modName, searchIngredients);
    }

    public static List<RecipeCollection> generateCustomRecipeList(String filter) {
        List<RecipeCollection> list = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();

        filter = filter.trim();
        String _modName = null;
        String _query = "";

        if (filter.startsWith("@")) {
            String[] parts = filter.substring(1).split(" ", 2);
            _modName = parts[0].toLowerCase(java.util.Locale.ROOT);
            if (parts.length > 1) {
                _query = parts[1].toLowerCase(java.util.Locale.ROOT);
            }
        } else {
            _query = filter.toLowerCase(java.util.Locale.ROOT);
        }

        final String modName = _modName;
        final String query = _query;

        ITEM_INDEX.stream()
                .filter(idx ->
                        (modName == null || idx.mod.contains(modName)) &&
                                (query.isEmpty() ||
                                        idx.name.contains(query) ||
                                        idx.id.contains(query) ||
                                        idx.key.contains(query) ||
                                        idx.tooltip.stream().anyMatch(line -> line.contains(query))
                                )
                )
                .forEach(idx -> {
                    var dummy = new DummySingleItemRecipe(idx.item.getDefaultInstance());
                    RecipeCollection collection = new RecipeCollection(Minecraft.getInstance().level.registryAccess(),List.of(dummy));
                    list.add(collection);
                });

        return list;
    }


    public static class IndexedItem {
        public final Item item;
        public final String id;
        public final String name;
        public final String mod;
        public final String key;
        public final List<String> tooltip;

        public IndexedItem(Item item, String id, String name, String mod, String key, List<String> tooltip) {
            this.item = item;
            this.id = id;
            this.name = name;
            this.mod = mod;
            this.key = key;
            this.tooltip = tooltip;
        }
    }


    public static List<IndexedItem> ITEM_INDEX = new ArrayList<>();

    public static void fillItemIndex(Minecraft client) {
        ITEM_INDEX.clear();
        for (Item item : nonexistingResultItems) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            var holder = item.builtInRegistryHolder().key().location();
            String id = holder.toString().toLowerCase(java.util.Locale.ROOT);
            String name = item.getDefaultInstance().getDisplayName().getString().toLowerCase(java.util.Locale.ROOT);
            String mod = holder.getNamespace().toLowerCase(java.util.Locale.ROOT);
            String key = "";
            List<String> tooltipLines = List.of();

            var nameComponent = item.getDefaultInstance().getDisplayName();
            if (nameComponent.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
                key = t.getKey().toLowerCase(java.util.Locale.ROOT);
            }

            // Можно закэшировать тултипы заранее
            if (client.level != null) {
                try {
                    var tooltipFlag = client.options.advancedItemTooltips
                            ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED
                            : net.minecraft.world.item.TooltipFlag.Default.NORMAL;
                    tooltipLines = item.getDefaultInstance().getTooltipLines(
                                    client.player, tooltipFlag)
                            .stream()
                            .map(c -> net.minecraft.ChatFormatting.stripFormatting(c.getString()).toLowerCase(java.util.Locale.ROOT).trim())
                            .toList();
                } catch (Exception e) {}
            }

            ITEM_INDEX.add(new IndexedItem(item, id, name, mod, key, tooltipLines));
        }
    }


}
