package jeb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Jeb.MODID)
public class Jeb {

    public static Set<Item> existingResultItems = new HashSet<>();
    public static Set<Item> nonexistingResultItems = new HashSet<>();
    public static String search = "-";
    public static List<RecipeCollection> filtered = new ArrayList<>();
    public static List<RecipeCollection> emptysearch = new ArrayList<>();

    public static boolean recipesLoaded = false;

    public static boolean customToggleEnabled = true;

    public static List<RecipeCollection> PREGENERATED_RECIPES;


    public static Path CONFIG_PATH;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json.has("customToggleEnabled")) {
                        customToggleEnabled = json.get("customToggleEnabled").getAsBoolean();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("customToggleEnabled", customToggleEnabled);

            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static List<RecipeCollection> generateCustomRecipeList(String filter) {
        List<RecipeCollection> list = new ArrayList<>();

        Minecraft client = Minecraft.getInstance();

        String query;

        String modName = null;
        if (filter.startsWith("@")) {
            // Извлекаем имя мода, если оно присутствует в начале строки
            int endIndex = filter.indexOf(" ");
            if (endIndex != -1) {
                modName = filter.substring(1, endIndex).trim();  // Извлекаем имя мода
                query = filter.substring(endIndex + 1).toLowerCase();  // Остальная часть это обычный запрос
            } else {
                modName = filter.substring(1).trim();  // Имя мода без строки запроса
                query = "";  // Если нет строки запроса, то фильтровать только по имени мода
            }
        }
        else
        {
            query = filter.toLowerCase();
        }

        //for (Item item : BuiltInRegistries.ITEM) {
        for (Item item : nonexistingResultItems.toArray(new Item[0])) {
            if (item == Items.AIR) continue;
            //if (existingResultItems.contains(item)) continue;


            String name = item.getDefaultInstance().getDisplayName().getString().toLowerCase(Locale.ROOT);
            String id_item = item.toString().toLowerCase(Locale.ROOT);
            String key = "";
            Component nameComponent = item.getDefaultInstance().getDisplayName(); // или getDisplayName()
            if (nameComponent.getContents() instanceof TranslatableContents translatable) {
                key = translatable.getKey().toLowerCase(Locale.ROOT);
            }


            if (modName != null && !modName.isEmpty() && !BuiltInRegistries.ITEM.getKey(item).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
                continue;
            }



            boolean tooltip_bool = false;


            if (client.level != null)
            {
                // Поиск по тултипам
                TooltipFlag tooltipFlag = client.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;


                try {
                    List<Component> tooltip = item.getDefaultInstance().getTooltipLines(client.player, tooltipFlag);
                    for (Component line : tooltip) {

                        String clean = net.minecraft.ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                        if (clean.contains(query)) {
                            tooltip_bool = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Можно также записать лог или безопасно проигнорировать ошибку
                }

            }


            if (!(name.contains(query) || id_item.contains(query) || key.contains(query) || tooltip_bool)) continue;
            ///////if (!(name.contains(query) || id_item.contains(query) || key.contains(query))) continue;


            ///////if (!translate(item.getTranslationKey()).toLowerCase().contains(filter.toLowerCase())) continue;


            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);



            Recipe<?> recipe = new CraftingRecipe() {
                @Override
                public CraftingBookCategory category() {
                    return null;
                }

                @Override
                public boolean matches(CraftingContainer p_44002_, Level p_44003_) {
                    return false;
                }

                @Override
                public ItemStack assemble(CraftingContainer p_44001_, RegistryAccess p_267165_) {
                    return null;
                }

                @Override
                public boolean canCraftInDimensions(int p_43999_, int p_44000_) {
                    return false;
                }

                @Override
                public ItemStack getResultItem(RegistryAccess p_267052_) {


                    return new ItemStack(item);

                    //return null;
                }

                @Override
                public ResourceLocation getId() {
                    return null;
                }

                @Override
                public RecipeSerializer<?> getSerializer() {
                    return null;
                }
            };

           /* NetworkRecipeId recipeId = new NetworkRecipeId(9999);

            List<SlotDisplay> slots = List.of(
                    new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", id.getPath())))
            );

            SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item, 1));
            SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                    Registries.ITEM.get(Identifier.of("minecraft", "crafting_table"))
            );

            OptionalInt group = OptionalInt.empty();
            RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

            List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
            RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));*/
            list.add(new RecipeCollection(client.level.registryAccess(),List.of(recipe)));
        }

        return list;
    }




    // Define mod id in a common place for everything to reference
    public static final String MODID = "jeb";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "jeb" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "jeb" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "jeb" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "jeb:example_block", combining the namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    // Creates a new BlockItem with the id "jeb:example_block", combining the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    // Creates a new food item with the id "jeb:example_id", nutrition 1 and saturation 2
    public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register("example_item", () -> new Item(new Item.Properties().food(new FoodProperties.Builder().alwaysEat().nutrition(1).saturationMod(2f).build())));

    // Creates a creative tab with the id "jeb:example_tab" for the example item, that is placed after the combat tab
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder().withTabsBefore(CreativeModeTabs.COMBAT).icon(() -> EXAMPLE_ITEM.get().getDefaultInstance()).displayItems((parameters, output) -> {
        output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
    }).build());

    public Jeb() {


        Jeb.loadConfig();
        Runtime.getRuntime().addShutdownHook(new Thread(Jeb::saveConfig));

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        if (Config.logDirtBlock) LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) event.accept(EXAMPLE_BLOCK_ITEM);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // Сброс данных при заходе на сервер
    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        recipesLoaded = false;
        search = "-";
        emptysearch.clear();
        existingResultItems.clear();
        nonexistingResultItems.clear();
    }

    /*private static KeyMapping keyBinding;
    public static KeyMapping keyBinding2;

    public void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        keyBinding = new KeyMapping(
                "Optional recipes loading screen",
                GLFW.GLFW_KEY_APOSTROPHE,
                "JEB (Just Enough Book)"
        );
        event.register(keyBinding);

        keyBinding2 = new KeyMapping(
                "Add/remove favorite recipes",
                GLFW.GLFW_KEY_A,
                "JEB (Just Enough Book)"
        );
        event.register(keyBinding2);
    }*/


    @Mod.EventBusSubscriber(modid = "jeb", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class KeybindRegistry {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            ClientModEvents.FAVORITE_KEY = new KeyMapping(
                    "Add/Remove Favorite Recipes",        // перевод будет в lang: key.jeb.favorite
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_A,           // по умолчанию клавиша A
                    "JEB (Just Enough Book)"   // категория для группировки
            );
            event.register(ClientModEvents.FAVORITE_KEY);
        }
    }


    @Mod.EventBusSubscriber(modid = "jeb", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public class ClientEvents {

        @SubscribeEvent
        public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            // Твой код
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                //RegistryAccess access = level.registryAccess();
                PREGENERATED_RECIPES = generateCustomRecipeList("");
                // делай что нужно
            }
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {


        public static final String CATEGORY = "key.categories.jeb";
        public static KeyMapping FAVORITE_KEY;


        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            CONFIG_PATH = Paths.get(
                    Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                    "config", "JEB.json"
            );

        }
    }
}
