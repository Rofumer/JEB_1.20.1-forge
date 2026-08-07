package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import jeb.accessor.RecipeBookWidgetBridge;
import jeb.client.DummySingleItemRecipe;
import jeb.client.FavoritesManager;
import jeb.Jeb;
import jeb.client.JebClient;
import jeb.client.RecipeIndex;
import jeb.client.SearchHistoryEntry;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import static jeb.Jeb.*;
import static jeb.client.JebClient.emptysearch;
import static jeb.client.JebClient.filtered;


@Mixin(RecipeBookComponent.class)
//public abstract class RecipeBookWidgetSearchMixin<T extends AbstractRecipeScreenHandler> implements RecipeBookWidgetBridge {
public abstract class RecipeBookWidgetSearchMixin implements RecipeBookWidgetBridge {

    // Это будет вызов приватного метода
    @Shadow
    public void recipesUpdated() {}

    @Override
    public void jeb$refresh() {
        this.recipesUpdated();
    }

    @Shadow
    private ClientRecipeBook book;

    @Shadow
    private RecipeBookTabButton selectedTab;

    @Shadow
    protected Minecraft minecraft;

    @Final
    @Shadow
    private RecipeBookPage recipeBookPage;

    @Shadow
    private EditBox searchBox;

    @Shadow
    @Final
    private List<RecipeBookTabButton> tabButtons;


    //@Shadow
    //private RecipeBookTabButton selectedTab;


    @Shadow
    protected StateSwitchingButton filterButton;

    @Unique
    private StateSwitchingButton jeb$customToggleButton;

    @Unique
    private boolean jeb$customToggleState = false;

    @Shadow
    protected RecipeBookMenu<?> menu;

    @Unique
    private static final int JEB_HISTORY_LIMIT = 30;

    @Unique
    private Button jeb$backButton;

    // true после первого initVisuals() этого экземпляра — не даёт повторно
    // затирать текст поиска при каждом invokeReset() в рамках одной сессии.
    @Unique
    private boolean jeb$searchRestored = false;

    // Стек истории хранится в JebClient (по RecipeBookType), а не в @Unique-поле
    // этого миксина, чтобы переживать закрытие/переоткрытие экрана крафта —
    // по той же причине, что и восстановление текста поиска.
    @Unique
    private Deque<SearchHistoryEntry> jeb$history() {
        return JebClient.searchHistoryByType.computeIfAbsent(menu.getRecipeBookType(), k -> new ArrayDeque<>());
    }

    @Override
    public void jeb$pushHistory(String query, RecipeBookTabButton tab) {
        RecipeBookCategories category = tab != null ? tab.getCategory() : null;
        Deque<SearchHistoryEntry> history = jeb$history();
        SearchHistoryEntry top = history.peekLast();
        if (top != null && top.query().equals(query) && Objects.equals(top.category(), category)) return;

        history.addLast(new SearchHistoryEntry(query, category));
        if (history.size() > JEB_HISTORY_LIMIT) {
            history.removeFirst();
        }
    }

    @Override
    public boolean jeb$goBack() {
        SearchHistoryEntry entry = jeb$history().pollLast();
        if (entry == null) return false;

        searchBox.setValue(entry.query());
        if (entry.category() != null) {
            // Кнопки вкладок пересоздаются при каждом initVisuals(), поэтому
            // сохранённую вкладку ищем заново по категории, а не по ссылке на объект.
            for (RecipeBookTabButton candidate : tabButtons) {
                if (candidate.getCategory().equals(entry.category())) {
                    selectedTab = candidate;
                    break;
                }
            }
        }
        ((RecipeBookWidgetAccessor) (Object) this).invokeReset();
        return true;
    }

    @Override
    public boolean jeb$hasHistory() {
        return !jeb$history().isEmpty();
    }

    /*
    @Unique
    private static final ButtonTextures TEXTURES_ALT = new ButtonTextures(
            Identifier.ofVanilla("recipe_book/crafting_overlay"),
            Identifier.ofVanilla("recipe_book/crafting_overlay_highlighted")
    );

    @Unique
    private static final ButtonTextures TEXTURES_DEFAULT = new ButtonTextures(
            Identifier.ofVanilla("recipe_book/crafting_overlay_disabled"),
            Identifier.ofVanilla("recipe_book/crafting_overlay_disabled_highlighted")
    );*/



    // Подменяем локальную переменную "s" ровно в момент её вычисления в оригинальном
    // initVisuals(): ванильный код тут же (в конце того же метода) создаёт новый
    // searchBox и записывает в него это значение, поэтому восстанавливать текст нужно
    // именно здесь, а не в отдельном @Inject(at = TAIL) уже после создания searchBox.
    @ModifyVariable(method = "initVisuals", at = @At("STORE"), ordinal = 0)
    private String jeb$restoreSearchOnFirstInit(String s) {
        if (this.searchBox == null && !jeb$searchRestored) {
            jeb$searchRestored = true;
            String saved = JebClient.lastSearchByType.get(menu.getRecipeBookType());
            if (saved != null && !saved.isEmpty()) {
                return saved;
            }
        }
        return s;
    }

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void jeb$addCustomToggleButton(CallbackInfo ci) {
        int x = this.filterButton.getX();
        int y = this.filterButton.getY()+120;

        jeb$customToggleButton = new StateSwitchingButton(x, y, 24, 24, false);
        if(Jeb.customToggleEnabled){
            jeb$customToggleButton.setTooltip(Tooltip.create(Component.literal("Show 3x3")));
            jeb$customToggleButton.initTextureValues(
                    152, 78, 26, 26,           // pressedUOffset (сдвиг по X при активном состоянии), hoverVOffset (сдвиг по Y при наведении)
                    new ResourceLocation("minecraft", "textures/gui/recipe_book.png")  // текстура
            );
            jeb$customToggleButton.setStateTriggered(false);
        }
        else
        {
            jeb$customToggleButton.setTooltip(Tooltip.create(Component.literal("Show 2x2")));
            jeb$customToggleButton.initTextureValues(
                    152, 78, 26, 26,           // pressedUOffset (сдвиг по X при активном состоянии), hoverVOffset (сдвиг по Y при наведении)
                    new ResourceLocation("minecraft", "textures/gui/recipe_book.png")
            );
            jeb$customToggleButton.setStateTriggered(true);
        }
        jeb$customToggleButton.setMessage(Component.literal("!"));
        jeb$customToggleButton.visible = true;

        jeb$backButton = Button.builder(Component.literal("<"), button -> { this.jeb$goBack(); })
                .tooltip(Tooltip.create(Component.translatable("jeb.recipe_book.back")))
                .pos(x + 22, y)
                .size(16, 16)
                .build();

    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/StateSwitchingButton;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    ordinal = 0, // если их несколько, выбирай нужный
                    shift = At.Shift.AFTER
            )
    )
    private void jeb$renderCustomToggle(GuiGraphics p_283597_, int p_282668_, int p_283506_, float p_282813_, CallbackInfo ci) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.visible) {
            jeb$customToggleButton.render(p_283597_, p_282668_, p_283506_, p_282813_);
        }

        if (jeb$backButton != null) {
            jeb$backButton.visible = jeb$hasHistory();
            if (jeb$backButton.visible) {
                jeb$backButton.render(p_283597_, p_282668_, p_283506_, p_282813_);
            }
        }
    }


    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    private void jeb$clickCustomToggle(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (jeb$backButton != null && jeb$backButton.visible && jeb$backButton.mouseClicked(mouseX, mouseY, button)) {
            // jeb$goBack() уже вызван в callback у builder
            cir.setReturnValue(true);
            return;
        }

        if (jeb$customToggleButton != null && jeb$customToggleButton.mouseClicked(mouseX, mouseY, button)) {
            jeb$customToggleState = !jeb$customToggleState;
            jeb$customToggleButton.setStateTriggered(jeb$customToggleState);
            Jeb.customToggleEnabled = !Jeb.customToggleEnabled;

            Jeb.saveConfig();
            // Меняем текстуру в зависимости от состояния
            //jeb$customToggleButton.setTextures(JEBClient.customToggleEnabled ? TEXTURES_ALT : TEXTURES_DEFAULT);
            if(Jeb.customToggleEnabled){
                jeb$customToggleButton.initTextureValues(
                        152, 78, 26, 26,           // pressedUOffset (сдвиг по X при активном состоянии), hoverVOffset (сдвиг по Y при наведении)
                        new ResourceLocation("minecraft", "textures/gui/recipe_book.png")
                );
                jeb$customToggleButton.setStateTriggered(false);
            }
            else
            {
                jeb$customToggleButton.initTextureValues(
                        152, 78, 26, 26,           // pressedUOffset (сдвиг по X при активном состоянии), hoverVOffset (сдвиг по Y при наведении)
                        new ResourceLocation("minecraft", "textures/gui/recipe_book.png")
                );
                jeb$customToggleButton.setStateTriggered(true);
            }

            jeb$customToggleButton.setTooltip(Jeb.customToggleEnabled ? (Tooltip.create(Component.literal("Show 3x3"))):(Tooltip.create(Component.literal("Show 2x2"))));

            //System.out.println("Кастомная кнопка: " + (jeb$customToggleState ? "включена" : "выключена"));

            // Рефреш через reflection
            /*try {
                Method method = RecipeBookWidget.class.getDeclaredMethod("refresh");
                method.setAccessible(true);
                method.invoke(this);
            } catch (Exception e) {
                e.printStackTrace();
            }*/

            ((RecipeBookWidgetBridge) this).jeb$refresh();

            cir.setReturnValue(true);
        }
    }


    /*@Inject(
            method = "reset",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;clear()V",
                    shift = At.Shift.AFTER
            )
    )
    private void injectCustomTab(CallbackInfo ci) {


        // Создаём кнопку вкладки
        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        RecipeGroupButtonWidget tabButton = new RecipeGroupButtonWidget(newTab);
        tabButton.setMessage(Text.of("Favorites"));


        this.tabButtons.add(tabButton);

    }*/

    /*@Inject(method = "<init>", at = @At("RETURN"))
    private void injectAfterConstructor(T craftingScreenHandler, List<RecipeBookWidget.Tab> tabs, CallbackInfo ci) {

        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        //RecipeGroupButtonWidget tabButton = new RecipeGroupButtonWidget(newTab);
        //tabButton.setMessage(Text.of("Favorites"));
        this.tabs.add(newTab);

    }*/

    /*@Inject(
            method = "reset",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeGroupButtonWidget;setToggled(Z)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void jeb$replaceFavoritesAsDefaultTab(CallbackInfo ci) {
        if (this.currentTab == tabButtons.get(0) && tabButtons.size() > 1) {
            RecipeGroupButtonWidget maybeFavorites = tabButtons.get(0);
            //if ("Favorites".equals(maybeFavorites.getMessage().getString())) {
                // Сбросить подсветку со старой
                //maybeFavorites.setToggled(true);
                maybeFavorites.setToggled(true);

            //this.refreshTabButtons(bl);

            ((RecipeBookWidgetAccessor) this).jeb$populateAllRecipes();

            ((RecipeBookWidgetAccessor) this).jeb$refreshTabButtons(true);

                // Назначить новую
                this.currentTab = tabButtons.get(1);

            ((RecipeBookWidgetAccessor) this).jeb$populateAllRecipes();

            ((RecipeBookWidgetAccessor) this).jeb$refreshTabButtons(true);
            //}
        }
    }*/

    /*@Unique
    private boolean isFavoritesTabActive() {
        if (currentTab == null) return false;

        return tabButtons.stream()
                .filter(button -> button.isSelected())
                .anyMatch(button -> "Favorites".equals(button.getMessage().getString()));
    }*/

    @Unique
    private boolean isFavoritesTabActive() {
        //return currentTab != null
        //        && currentTab.getMessage() != null
        //        && "Favorites".equals(currentTab.getMessage().getString());
        return selectedTab.getCategory() == RecipeBookCategories.CAMPFIRE;
    }


    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // Проверка на нужную клавишу (например, клавиша G, keyCode = 71)
        //if (keyCode == GLFW.GLFW_KEY_A) {
        if (JebClient.FAVORITE_KEY != null && JebClient.FAVORITE_KEY.matches(keyCode, scanCode)) {
            RecipeButton hovered = ((RecipeBookResultsAccessor) recipeBookPage).getHoveredResultButton();
            if (hovered != null) {
                //System.out.println("Над кнопкой: " + hovered.getDisplayStack().getItem().toString());
                //ItemStack stack = hovered.getDisplayStack();
                if (isFavoritesTabActive()) {
                    FavoritesManager.removeFavorite(hovered.getRecipe().getResultItem(hovered.getCollection().registryAccess()));
                    // Рефреш через reflection
                    /*try {
                        Method method = RecipeBookWidget.class.getDeclaredMethod("refresh");
                        method.setAccessible(true);
                        method.invoke(this);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    ((RecipeBookWidgetBridge) this).jeb$refresh();
                } else {
                    FavoritesManager.saveFavorite(hovered.getRecipe().getResultItem(hovered.getCollection().registryAccess()));
                }
                //FavoritesManager.saveFavorite(stack);
                ((AnimatedResultButtonExtension) hovered).jeb$flash();
                // Здесь можно выполнить любое действие, например, выбрать рецепт, показать информацию и т.д.
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/Recipe;Z)V",
            shift = At.Shift.AFTER
    ))
    private void onRecipeClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();

        RecipeManager recipeManager = client.level.getRecipeManager();


        Recipe<?> recipe = this.recipeBookPage.getLastClickedRecipe();

        RecipeCollection collection = this.recipeBookPage.getLastClickedRecipeCollection();

        Screen screenHandler = client.screen;

        if(collection != null && recipe != null && screenHandler != null && !collection.hasCraftable()) {
            recipeManager.byKey(recipe.getId()).ifPresent((recipe1 -> {
                if (screenHandler instanceof RecipeUpdateListener) {
                    RecipeBookComponent recipeBookWidget = ((RecipeUpdateListener) client.screen).getRecipeBookComponent();
                    AbstractContainerMenu menu = client.player.containerMenu;
                    recipeBookWidget.setupGhostRecipe(recipe1, menu.slots);
                }
            }));
        }



        ///if (screen instanceof RecipeBookProvider provider && entry != null) {
        ///    //System.out.println("РецептL " + entry.display().toString());
        ///    if(!results.isCraftable(recipeId) && recipeId.index()!=9999) {
        ///        provider.onCraftFailed(entry.display());
        ///    }
        ///}
    }


    /*@Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomIngredientSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String string = searchField.getText();
        if (!string.startsWith("#")) return;

        String query = string.substring(1).toLowerCase(Locale.ROOT);
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForCategory(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = Lists.newArrayList();

        for (RecipeResultCollection collection : originalList) {
            if (!collection.hasDisplayableRecipes()) continue;

            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                if (recipeDisplayMatchesIngredientQuery(entry, query)) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }

        recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }*/

    @Unique
    private boolean recipeDisplayMatchesIngredientQuery(Recipe<?> recipe, String query) {
        query = query.toLowerCase(Locale.ROOT);

        for (Ingredient ingredient : recipe.getIngredients()) {
            for (ItemStack stack : ingredient.getItems()) {
                String itemName = stack.getItem().asItem().toString().toLowerCase(Locale.ROOT);
                if (itemName.contains(query)) {
                    return true;
                }
            }
        }

        return false;
    }


    /****@Unique
    private boolean recipeResultMatchesQuery(RecipeDisplayEntry entry, String query) {
        if (entry.display() == null || entry.display().result() == null) return false;

        SlotDisplay resultSlot = entry.display().result();

        ContextParameterMap context = SlotDisplayContexts.createParameters(
                Objects.requireNonNull(this.client.world)
        );

        List<ItemStack> stacks = resultSlot.getStacks(context);
        if (stacks.isEmpty()) return false;

        ItemStack stack = stacks.get(0);
        if (stack == null || stack.isEmpty()) return false;

        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String key = stack.getItem().getTranslationKey().toLowerCase(Locale.ROOT);

        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        // Поиск по тултипам
        RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
        TooltipType tooltipType = TooltipType.Default.BASIC;

        List<Text> tooltip = stack.getTooltip(tooltipContext, client.player, tooltipType);
        for (Text line : tooltip) {
            String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
            if (clean.contains(query)) return true;
        }

        return false;
    }



    @Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String string = searchField.getText();
        //if (string.isEmpty()) return;

        boolean searchIngredients = string.startsWith("#");
        String query = (searchIngredients ? string.substring(1) : string).toLowerCase();

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForCategory(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = Lists.newArrayList();

        for (RecipeResultCollection collection : originalList) {
            if (!collection.hasDisplayableRecipes()) continue;

            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                boolean match =
                        recipeResultMatchesQuery(entry, query) ||
                                (searchIngredients && recipeDisplayMatchesIngredientQuery(entry, query));

                if (match) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }****/

        //System.out.println("filteredList содержит " + filteredList.size() + " рецептов");

        // Получаем доступ к searchField через наш accessor

        // Получаем текст из поля поиска

        // 🔹 Собираем все предметы, уже встречающиеся в filteredList как результат
        /*Set<Item> existingResultItems = new HashSet<>();
        for (RecipeResultCollection collection : filteredList) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                getItemFromSlotDisplay(entry.display().result()).ifPresent(existingResultItems::add);
            }
        }*/


        /// ////////
        /*for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            //if (existingResultItems.contains(item)) continue;

            if (!translate(item.getTranslationKey()).toLowerCase().contains(string.toLowerCase())) continue;

            Identifier id = Registries.ITEM.getId(item);
            System.out.println("Item: " + id);

            NetworkRecipeId recipeId = new NetworkRecipeId(9999);

            List<SlotDisplay> slots = new ArrayList<>();
            slots.add(new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", id.getPath()))));

            SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item, 1));

            SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                    Registries.ITEM.get(Identifier.of("minecraft", "crafting_table"))
            );

            OptionalInt group = OptionalInt.empty();
            RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

            List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
            RecipeDisplayEntry recipeDisplayEntry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
            RecipeResultCollection myCustomRecipeResultCollection = new RecipeResultCollection(List.of(recipeDisplayEntry));

            filteredList.add(myCustomRecipeResultCollection);
        }*/

        /****filteredList.addAll(JEBClient.generateCustomRecipeList(string));****/

        //if (!string.isEmpty()) {
            /*for (Item item : Registries.ITEM) {
                if (item == Items.AIR) continue;
                if (existingResultItems.contains(item)) continue;

                Identifier id = Registries.ITEM.getId(item);
                String idString = id.toString().toLowerCase(); // без Locale
                String name = item.getName().getString().toLowerCase(); // без Locale
                String searchLower = string.toLowerCase(); // без Locale

                // Если id или имя содержит текст поиска
                if (!idString.contains(searchLower) && !name.contains(searchLower)) continue;

                NetworkRecipeId recipeId = new NetworkRecipeId(9999);

                List<SlotDisplay> slots = List.of(
                        new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, id))
                );

                SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item));
                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE);

                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);

                OptionalInt group = OptionalInt.empty();
                RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;
                List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
                RecipeResultCollection resultCollection = new RecipeResultCollection(List.of(entry));

                filteredList.add(resultCollection);
            }*/
        //}



        //System.out.println("2: filteredList содержит " + filteredList.size() + " рецептов");
        //System.out.println("Текст в поисковом поле: " + string);
        
    /****    recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }****/

    @Unique
    private boolean recipeResultMatchesQuery(Recipe<?> recipe, String query, String modName) {
        if (recipe == null || recipe.getResultItem(minecraft.level.registryAccess()) == null || recipe.getResultItem(minecraft.level.registryAccess()).isEmpty()) {
            return false;
        }

        ItemStack stack = recipe.getResultItem(minecraft.level.registryAccess());
        if (stack == null || stack.isEmpty()) return false;

        Minecraft client = Minecraft.getInstance();

        String name = stack.getDisplayName().getString().toLowerCase(Locale.ROOT);
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String key = "";
        Component nameComponent = stack.getHoverName(); // или getDisplayName()
        if (nameComponent.getContents() instanceof TranslatableContents translatable) {
            key = translatable.getKey().toLowerCase(Locale.ROOT);
        }


        // Проверка на имя мода
        if (modName != null && !modName.isEmpty() && !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(modName)) {
            return false;  // Не принадлежит указанному моду
        }


        // Поиск по имени, ID или translationKey
        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        // Поиск по тултипам
        TooltipFlag tooltipFlag = minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;



        try {
            List<Component> tooltip = stack.getTooltipLines(minecraft.player, tooltipFlag);

            for (Component line : tooltip) {
                String clean = net.minecraft.ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                if (clean.contains(query)) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Можно также записать лог или безопасно проигнорировать ошибку
        }

        return false;

    }


    ///@Shadow
    ///private final RecipeMatcher recipeFinder = new RecipeMatcher();

    ///@Shadow public abstract void reset();


    @Inject(method = "updateCollections", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, CallbackInfo ci) {
        String string = searchBox.getValue();

        JebClient.lastSearchByType.put(menu.getRecipeBookType(), string);

        boolean searchIngredients = string.startsWith("#");
        boolean searchByResult = string.startsWith("~");
        String query = (searchIngredients || searchByResult ? string.substring(1) : string).toLowerCase();

        String modName = null;
        if (string.startsWith("@")) {
            int endIndex = string.indexOf(" ");
            if (endIndex != -1) {
                modName = string.substring(1, endIndex).trim();
                query = string.substring(endIndex + 1).toLowerCase();
            } else {
                modName = string.substring(1).trim();
                query = "";
            }
        }

        List<RecipeCollection> filteredList = new ArrayList<>();


        if (string.startsWith("~") && !isFavoritesTabActive()) {
            List<RecipeCollection> ingredientsList = new ArrayList<>();

            for (RecipeCollection collection : book.getCollection(selectedTab.getCategory())) {
                for (Recipe<?> recipe : collection.getRecipes()) {
                    ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
                    // Сравниваем по видимому имени, а не по id предмета: на серверах с датапаками
                    // разные предметы часто зарегистрированы под одним ванильным id и отличаются
                    // только кастомным именем.
                    String resultName = result.getHoverName().getString().toLowerCase(Locale.ROOT).trim();
                    if (resultName.equals(query)) {
                        for (Ingredient ingredient : recipe.getIngredients()) {
                            for (ItemStack stack : ingredient.getItems()) {
                                if (!stack.isEmpty()) {
                                    // Создаём фиктивный RecipeCollection с одним "рецептом" — результат stack
                                    // Проверяем, есть ли коллекция рецептов, где результат — этот ингредиент
                                    boolean foundReal = false;
                                    for (RecipeCollection subCollection : book.getCollection(RecipeBookCategories.CRAFTING_SEARCH)) {
                                        for (Recipe<?> subRecipe : subCollection.getRecipes()) {
                                            ItemStack subResult = subRecipe.getResultItem(minecraft.level.registryAccess());
                                            if (!subResult.isEmpty() && ItemStack.isSameItemSameTags(subResult, stack)) {
                                                StackedContents contents = ((RecipeBookWidgetAccessor) (Object) this).getRecipeFinder();
                                                subCollection.canCraft(contents,
                                                        3,
                                                        3,
                                                        book
                                                );
                                                ingredientsList.add(subCollection);
                                                foundReal = true;
                                                break;
                                            }
                                        }
                                        if (foundReal) break;
                                    }

// Если не нашли настоящих рецептов — добавим фейковую коллекцию
                                    if (!foundReal) {
                                        Recipe<?> fakeRecipe = new DummySingleItemRecipe(stack);
                                        RecipeCollection dummycollection = new RecipeCollection(minecraft.level.registryAccess(), List.of(fakeRecipe));
                                        StackedContents contents = ((RecipeBookWidgetAccessor) (Object) this).getRecipeFinder();
                                        dummycollection.canCraft(contents,
                                                3,
                                                3,
                                                book
                                        );
                                        ingredientsList.add(dummycollection);
                                    }

                                    break; // только один stack из одного ingredient
                                }
                            }
                        }
                    }
                }
            }

            filteredList.addAll(ingredientsList);
            jEB$sortCraftableFirst(filteredList);
            recipeBookPage.updateCollections(filteredList, resetCurrentPage);
            ci.cancel();
            return;
        }


        // === Favorites tab ===
        if (isFavoritesTabActive()) {
            Set<ResourceLocation> favoriteItems = FavoritesManager.loadFavoriteItemIds();
            List<RecipeCollection> favorites = new ArrayList<>();

            for (RecipeCollection collection : book.getCollection(RecipeBookCategories.CRAFTING_SEARCH)) {
                for (Recipe<?> recipe : collection.getRecipes()) {
                    ItemStack stack = recipe.getResultItem(minecraft.level.registryAccess());
                    if (!stack.isEmpty()) {
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        if (favoriteItems.contains(id)) {
                            RecipeCollection dummycollection = new RecipeCollection(minecraft.level.registryAccess(), List.of(recipe));
                            StackedContents contents = ((RecipeBookWidgetAccessor) (Object) this).getRecipeFinder();
                            dummycollection.canCraft(contents,
                                    3,
                                    3,
                                    book
                            );
                            favorites.add(dummycollection);
                            break; // no need to keep scanning this collection
                        }
                    }
                }
            }

            filteredList.addAll(favorites);
            jEB$sortCraftableFirst(filteredList);
            recipeBookPage.updateCollections(filteredList, resetCurrentPage);
            ci.cancel();
            return;
        }

               // === Standard search ===
            /*for (RecipeCollection collection : book.getCollection(selectedTab.getCategory())) {
                if (!collection.hasFitting()) continue;

                for (Recipe<?> recipe : collection.getRecipes()) {
                    boolean match = searchIngredients
                            ? recipeDisplayMatchesIngredientQuery(recipe, query)
                            : recipeResultMatchesQuery(recipe, query, modName);

                    if (match) {
                        filteredList.add(collection);
                        break;
                    }
                }
            }*/

            filteredList = new ArrayList<>(RecipeIndex.fastSearch(selectedTab.getCategory(),query, modName, searchIngredients));

            StackedContents contents = ((RecipeBookWidgetAccessor) (Object) this).getRecipeFinder();

            if(!(((RecipeBookWidgetAccessor) this).getSearchField().isActive() && ((RecipeBookWidgetAccessor) this).getSearchField().isVisible() && ((RecipeBookWidgetAccessor) this).getSearchField().isFocused())) {
                for (RecipeCollection rc : filteredList) {
                    rc.canCraft(contents,
                            ((RecipeBookWidgetAccessor) this).getCraftingScreenHandler().getGridWidth(),
                            ((RecipeBookWidgetAccessor) this).getCraftingScreenHandler().getGridHeight(),
                            book
                    );
                }
            }

            if (jeb$customToggleState) {
                filteredList.removeIf(rc -> !rc.hasFitting());
            }

            if (book.isFiltering(menu)) {
                filteredList.removeIf(rc -> !rc.hasCraftable());
            }




        if(!Objects.equals(search, string))
        {
            filtered = RecipeIndex.generateCustomRecipeList(string);
        }

        if(!filterButton.isStateTriggered()) {
            filteredList.addAll(filtered);
        }

        search = string;

        jEB$sortCraftableFirst(filteredList);
        recipeBookPage.updateCollections(filteredList, resetCurrentPage);
        ci.cancel();
    }

    // Сначала показываем то, что игрок может скрафтить прямо сейчас.
    // Сортировка стабильная, поэтому внутри групп порядок не меняется.
    @Unique
    private static void jEB$sortCraftableFirst(List<RecipeCollection> collections) {
        collections.sort(Comparator.comparing(RecipeCollection::hasCraftable).reversed());
    }


    /*@Inject(method = "updateCollections", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, CallbackInfo ci) {
        String string = searchBox.getValue();
        boolean searchIngredients = string.startsWith("#");
        String query = (searchIngredients ? string.substring(1) : string).toLowerCase();

        String modName = null;
        if (string.startsWith("@")) {
            int endIndex = string.indexOf(" ");
            if (endIndex != -1) {
                modName = string.substring(1, endIndex).trim();
                query = string.substring(endIndex + 1).toLowerCase();
            } else {
                modName = string.substring(1).trim();
                query = "";
            }
        }

        ///ClientPlayNetworkHandler handler = client.getNetworkHandler();
        ///if (handler == null) return;

        List<RecipeCollection> originalList = book.getCollection(selectedTab.getCategory());
        //List<RecipeCollection>originalList =book.getCollection(RecipeBookCategories.CRAFTING_SEARCH);
        List<RecipeCollection> filteredList = Lists.newArrayList();
        // === Если на вкладке избранного (используем CAMPFIRE как временную категорию) ===
        if (isFavoritesTabActive()) {
            originalList = new ArrayList<>();

            //for (RecipeBookGroup group : RecipeBookGroup.CRAFTING) {
                //originalList.addAll(recipeBook.getResultsForGroup(group));
            originalList.addAll(book.getCollection(RecipeBookCategories.CRAFTING_SEARCH));
            //}

            Set<ResourceLocation> favoriteItems = FavoritesManager.loadFavoriteItemIds();

            List<RecipeCollection> matching = null;
            for (RecipeCollection collection : originalList) {

                matching = new ArrayList<>();
                for (Recipe<?> entry : collection.getRecipes()) {
                    ItemStack stack = entry.getResultItem(minecraft.level.registryAccess());
                    if (!stack.isEmpty()) {
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        if (favoriteItems.contains(itemId)) {
                            matching.add(new RecipeCollection(minecraft.level.registryAccess(),List.of(entry)));
                        }
                    }
                }

                if(!matching.isEmpty()) {
                    filteredList.add(collection);
                }

            }

            //if (!matching.isEmpty()) {
            //    filteredList.addAll(matching);
            //}

            //if (filteringCraftable) {
            //    filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
            //}

            ///recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
            recipeBookPage.updateCollections(filteredList, resetCurrentPage);
            ci.cancel();
            return;
        }

        // === Обычный поиск ===
        for (RecipeCollection collection : originalList) {

            if (!collection.hasFitting()) continue;

            for (Recipe<?> entry : collection.getRecipes()) {

                boolean match;
                if (searchIngredients) {
                    match = recipeDisplayMatchesIngredientQuery(entry, query);
                } else {
                    match = recipeResultMatchesQuery(entry, query, modName);
                }
                if (match) {
                    filteredList.add(collection);
                    break;
                }
            }
        }


        ///@Shadow public abstract void reset();

       StackedContents contents = ((RecipeBookWidgetAccessor)(Object)this).getRecipeFinder();

        filteredList.forEach((resultCollection) -> resultCollection.canCraft(contents , ((RecipeBookWidgetAccessor) this).getCraftingScreenHandler().getGridWidth(), ((RecipeBookWidgetAccessor) this).getCraftingScreenHandler().getGridHeight(), book));

        if(jeb$customToggleState) {
            filteredList.removeIf((resultCollection) -> !resultCollection.hasFitting());
        }

        if (book.isFiltering(menu)) {
            filteredList.removeIf(rc -> !rc.hasCraftable());
        }

        filteredList.addAll(Jeb.generateCustomRecipeList(string));

        ///recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ///List<RecipeResultCollection> filteredList1 = Lists.newArrayList(filteredList);
        /////recipesArea.setResults(filteredList1, resetCurrentPage);
        ///if(jeb$customToggleState) {
        ///    filteredList1.removeIf((resultCollection) -> !resultCollection.hasFittingRecipes());
        ///}

        ///if (this.recipeBook.isFilteringCraftable(craftingScreenHandler)) {
        ///    filteredList1.removeIf(rc -> !rc.hasCraftableRecipes());
        ///}
        recipeBookPage.updateCollections(filteredList, resetCurrentPage);
        ci.cancel();
    }*/



    /*
    @Unique
    private static Optional<Item> getItemFromSlotDisplay(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.StackSlotDisplay(ItemStack stack)) {
            return Optional.of(stack.getItem());
        }

        if (slot instanceof SlotDisplay.ItemSlotDisplay(RegistryEntry<Item> item)) {
            return Optional.of(item.value());
        }

        if (slot instanceof SlotDisplay.TagSlotDisplay(TagKey<Item> tag)) {

            // В 1.21.5 можно безопасно использовать iterateEntries
            for (RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(tag)) {
                return Optional.of(entry.value());
            }
        }

        if (slot instanceof SlotDisplay.CompositeSlotDisplay(List<SlotDisplay> contents)) {
            for (SlotDisplay inner : contents) {
                Optional<Item> maybeItem = getItemFromSlotDisplay(inner);
                if (maybeItem.isPresent()) return maybeItem;
            }
        }

        return Optional.empty();
    }*/

    /*@Inject(method = "reset", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", shift = At.Shift.AFTER))
    private void addNewTab(CallbackInfo ci) {
        RecipeBookWidget<?> recipeBookWidget = (RecipeBookWidget<?>) (Object) this;

        // Получаем список вкладок через @Accessor
        List<RecipeBookWidget.Tab> tabs = ((RecipeBookWidgetAccessor) recipeBookWidget).gettabs();

        // Создаем изменяемую копию списка tabs
        List<RecipeBookWidget.Tab> newTabs = new ArrayList<>(tabs);

        // Создаем новую вкладку (Tab) с иконкой и категорией
        ItemStack primaryIcon = new ItemStack(Items.WRITABLE_BOOK);  // Иконка из алмаза
        RecipeBookCategory category = RecipeBookCategories.CAMPFIRE; // Категория рецептов
        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(primaryIcon.getItem(), category);

        // Добавляем новую кнопку вкладки в список tabButtons
        newTabs.add(newTab);  // Добавляем новую кнопку вкладки

        try {
            java.lang.reflect.Field tabsField = RecipeBookWidget.class.getDeclaredField("tabs");
            tabsField.setAccessible(true);  // Даем доступ к приватному полю
            tabsField.set(recipeBookWidget, newTabs);  // Устанавливаем новое значение
        } catch (Exception e) {
            e.printStackTrace();
        }

    }*/

}

