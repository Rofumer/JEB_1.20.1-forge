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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Unique;


import java.util.*;

import static jeb.Jeb.LOGGER;
import static jeb.Jeb.nonexistingResultItems;

public class RecipeIndex {
    // Раньше byResult/byMod/byTooltipWord хранили все подстроки id/имени/мода/слов тултипа
    // (O(n²) ключей на строку) — на больших сборках это сотни МБ (issue #10).
    // Теперь на каждую коллекцию хранится одна компактная запись, а подстроки ищутся
    // через contains() при поиске — это линейный проход, единицы мс даже на тысячах коллекций.
    public final Map<RecipeBookCategories, Map<RecipeCollection, SearchEntry>> searchEntries = new HashMap<>();
    public final Map<RecipeBookCategories, Map<String, Set<RecipeCollection>>> byIngredientWord = new HashMap<>();
    public final Map<RecipeBookCategories, Set<RecipeCollection>> allCollections = new HashMap<>();
    public static final RecipeIndex GLOBAL_RECIPE_INDEX = new RecipeIndex();

    public static boolean jebIndexReady = false;

    public static RecipeManager recipeManager;

    private static final String SEPARATOR = "\n";
    private static final int MIN_TOOLTIP_QUERY_LENGTH = 3;

    /**
     * Поисковые строки одной коллекции. Коллекция может содержать рецепты с разными
     * результатами, поэтому уникальные значения склеиваются через '\n'. Запрос не содержит '\n',
     * поэтому contains() по такой строке совпадает только внутри одного значения.
     */
    public static final class SearchEntry {
        final String results; // id и имена результатов
        final String mods;
        final String tooltipWords; // слова тултипа длиной >= 3

        SearchEntry(Set<String> results, Set<String> mods, Set<String> tooltipWords) {
            this.results = String.join(SEPARATOR, results);
            this.mods = String.join(SEPARATOR, mods);
            this.tooltipWords = String.join(SEPARATOR, tooltipWords);
        }

        boolean matchesResult(String query) {
            return !query.contains(SEPARATOR) && results.contains(query);
        }

        boolean matchesMod(String modName) {
            return !modName.contains(SEPARATOR) && mods.contains(modName);
        }

        boolean matchesTooltip(String query) {
            return query.length() >= MIN_TOOLTIP_QUERY_LENGTH
                    && !query.contains(SEPARATOR)
                    && tooltipWords.contains(query);
        }
    }


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

        GLOBAL_RECIPE_INDEX.searchEntries.clear();
        GLOBAL_RECIPE_INDEX.byIngredientWord.clear();
        GLOBAL_RECIPE_INDEX.allCollections.clear();

        int totalIndexedRecipes = 0;
        Set<ResourceLocation> uniqueRecipes = new HashSet<>();

        for (RecipeBookCategories category : RecipeBookCategories.values()) {
            List<RecipeCollection> collections;

            long filterstartTime = System.currentTimeMillis();
            LOGGER.info("[JEB] buildRecipeIndex filtering started at {}", new Date(filterstartTime));

            List<RecipeCollection> filteredCollections = new ArrayList<>();

            for (RecipeCollection collection : book.getCollection(category)) {
                // Оставляем только те рецепты, которые дают валидный результат
                List<Recipe<?>> filtered = collection.getRecipes().stream()
                        .filter(recipe -> {
                            ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
                            return result != null && !result.isEmpty() && result.getItem() != Items.AIR;
                        })
                        .toList();

                // Если после фильтрации в коллекции что-то осталось — добавляем новую коллекцию
                if (!filtered.isEmpty()) {
                    filteredCollections.add(new RecipeCollection(
                            minecraft.level.registryAccess(), filtered
                    ));
                }
            }

            long filterendTime = System.currentTimeMillis();
            long filterduration = filterendTime - filterstartTime;
            LOGGER.info("[JEB] buildRecipeIndex filter {} done at {} ({} ms)", category, new Date(filterendTime), filterduration);

            collections =filteredCollections;
            if (collections.isEmpty()) continue;

            Set<RecipeCollection> categoryCollections = new LinkedHashSet<>();
            Map<RecipeCollection, SearchEntry> entries = new HashMap<>();
            Map<String, Set<RecipeCollection>> ingredientIndex = new HashMap<>();

            for (RecipeCollection collection : collections) {
                categoryCollections.add(collection);
                Set<String> results = new LinkedHashSet<>();
                Set<String> mods = new LinkedHashSet<>();
                Set<String> tooltipWords = new LinkedHashSet<>();

                for (Recipe<?> recipe : collection.getRecipes()) {
                    ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
                    //if (result == null || result.isEmpty() || result.getItem() == Items.AIR) continue;
                    ResourceLocation recipeId = recipe.getId();
                    if (uniqueRecipes.add(recipeId)) {
                        totalIndexedRecipes++;
                    }
                    // --- Результат: id и имя (hoverName — учитывает кастомное имя предмета, например
                    // предметы датапаков, зарегистрированные под ванильным id) ---
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(result.getItem());
                    results.add(key.toString().toLowerCase(Locale.ROOT));
                    results.add(result.getHoverName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", ""));

                    // --- Мод ---
                    mods.add(key.getNamespace().toLowerCase(Locale.ROOT));

                    // --- Индекс по ингредиентам (точный id) ---
                    for (var ingredient : recipe.getIngredients()) {
                        for (ItemStack stack : ingredient.getItems()) {
                            String ingredientId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                            ingredientIndex.computeIfAbsent(ingredientId, k -> new LinkedHashSet<>()).add(collection);
                        }
                    }

                    // --- Слова тултипа ---
                    addTooltipWords(minecraft, result, tooltipWords);
                }

                entries.put(collection, new SearchEntry(results, mods, tooltipWords));
            }

            GLOBAL_RECIPE_INDEX.allCollections.put(category, categoryCollections);
            GLOBAL_RECIPE_INDEX.searchEntries.put(category, entries);
            GLOBAL_RECIPE_INDEX.byIngredientWord.put(category, ingredientIndex);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOGGER.info("[JEB] buildRecipeIndex category {} done at {} ({} ms)", category, new Date(endTime), duration);

        }

        jebIndexReady = true;
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        LOGGER.info("[JEB] buildRecipeIndex done at {} ({} ms), total indexed recipes: {}", new Date(endTime), duration, totalIndexedRecipes);
    }

    private static void addTooltipWords(Minecraft minecraft, ItemStack result, Set<String> words) {
        try {
            var tooltipFlag = minecraft.options.advancedItemTooltips
                    ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED
                    : net.minecraft.world.item.TooltipFlag.Default.NORMAL;
            for (var line : result.getTooltipLines(minecraft.player, tooltipFlag)) {
                String clean = net.minecraft.ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                for (String word : clean.split("[\\s,;.:!\\-]+")) {
                    if (word.length() >= MIN_TOOLTIP_QUERY_LENGTH) {
                        words.add(word);
                    }
                }
            }
        } catch (Exception e) {}
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
            Map<String, Set<RecipeCollection>> ingredientIndex = GLOBAL_RECIPE_INDEX.byIngredientWord.getOrDefault(category, Map.of());
            Set<RecipeCollection> all = GLOBAL_RECIPE_INDEX.allCollections.getOrDefault(category, Set.of());
            Map<RecipeCollection, SearchEntry> entries = GLOBAL_RECIPE_INDEX.searchEntries.getOrDefault(category, Map.of());

            if ((modName.isEmpty()) && query.isEmpty()) {
                result.addAll(all);
                continue;
            }

            if (!modName.isEmpty()) {
                List<RecipeCollection> modCollections = new ArrayList<>();
                List<SearchEntry> modEntries = new ArrayList<>();
                for (RecipeCollection rc : all) {
                    SearchEntry entry = entries.get(rc);
                    if (entry != null && entry.matchesMod(modName)) {
                        modCollections.add(rc);
                        modEntries.add(entry);
                    }
                }
                if (query.isEmpty()) {
                    result.addAll(modCollections);
                    continue;
                }
                for (String word : query.split("[\\s:_\\-]+")) {
                    if (word.isEmpty()) continue;
                    for (int i = 0; i < modCollections.size(); i++) {
                        if (modEntries.get(i).matchesResult(word)) {
                            result.add(modCollections.get(i));
                        }
                    }
                }
                continue;
            }

            if (searchIngredients) {
                result.addAll(ingredientIndex.getOrDefault(query, Set.of()));
                continue;
            }

            // Поиск по результату (id или имя), затем по тултипам
            List<RecipeCollection> byTooltip = new ArrayList<>();
            for (RecipeCollection rc : all) {
                SearchEntry entry = entries.get(rc);
                if (entry == null) continue;
                if (entry.matchesResult(query)) {
                    result.add(rc);
                } else if (entry.matchesTooltip(query)) {
                    byTooltip.add(rc);
                }
            }
            result.addAll(byTooltip);
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
