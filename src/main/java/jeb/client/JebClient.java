package jeb.client;

import com.mojang.blaze3d.platform.InputConstants;
import jeb.Jeb;
import jeb.client.DummySingleItemRecipe;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = Jeb.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class JebClient {


    public static KeyMapping FAVORITE_KEY;

    // --- Вот все клиентские коллекции! ---
    public static List<RecipeCollection> filtered = new ArrayList<>();
    public static List<RecipeCollection> emptysearch = new ArrayList<>();
    public static List<RecipeCollection> PREGENERATED_RECIPES = new ArrayList<>();
    // --------------------------------------

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        Jeb.CONFIG_PATH = Paths.get(
                Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "config", "JEB.json"
        );
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        FAVORITE_KEY = new KeyMapping(
                "Add/Remove Favorite Recipes",
                InputConstants.Type.valueOf(KeyMapping.CATEGORY_GAMEPLAY), // или свой CATEGORY
                GLFW.GLFW_KEY_A,
                "JEB (Just Enough Book)"
        );
        event.register(FAVORITE_KEY);
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Jeb.recipesLoaded = false;
        Jeb.search = "-";
        Jeb.existingResultItems.clear();
        Jeb.nonexistingResultItems.clear();
        filtered.clear();
        emptysearch.clear();
        PREGENERATED_RECIPES.clear();
        // Можно добавить логику инициализации по желанию
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            // PREGENERATED_RECIPES.addAll(generateCustomRecipeList(""));
        }
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Если нужно добавить предметы на клиенте — реализуй тут
    }

    public static List<RecipeCollection> generateCustomRecipeList(String filter) {
        List<RecipeCollection> list = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();

        String query = "";
        String modName = null;

        filter = filter.trim();
        if (filter.startsWith("@")) {
            String[] parts = filter.substring(1).split(" ", 2);
            modName = parts[0].toLowerCase(Locale.ROOT);
            if (parts.length > 1) {
                query = parts[1].toLowerCase(Locale.ROOT);
            }
        } else {
            query = filter.toLowerCase(Locale.ROOT);
        }

        for (Item item : Jeb.nonexistingResultItems.toArray(new Item[0])) {
            if (item == Items.AIR) continue;

            ResourceLocation id = item.builtInRegistryHolder().key().location();
            String idStr = id.toString().toLowerCase(Locale.ROOT);
            String name = item.getDefaultInstance().getDisplayName().getString().toLowerCase(Locale.ROOT);
            String key = "";

            Component nameComponent = item.getDefaultInstance().getDisplayName();
            if (nameComponent.getContents() instanceof TranslatableContents translatable) {
                key = translatable.getKey().toLowerCase(Locale.ROOT);
            }

            // Фильтрация по мод-нейму
            if (modName != null && !id.getNamespace().toLowerCase(Locale.ROOT).contains(modName)) {
                continue;
            }

            // Основной поиск
            boolean matchesBasic = name.contains(query) || idStr.contains(query) || key.contains(query);
            boolean matchesTooltip = false;

            if (!matchesBasic && query.length() >= 3 && client.level != null) {
                TooltipFlag tooltipFlag = client.options.advancedItemTooltips
                        ? TooltipFlag.Default.ADVANCED
                        : TooltipFlag.Default.NORMAL;

                try {
                    List<Component> tooltip = item.getDefaultInstance().getTooltipLines(client.player, tooltipFlag);
                    for (Component line : tooltip) {
                        String clean = net.minecraft.ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                        if (clean.contains(query)) {
                            matchesTooltip = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!matchesBasic && !matchesTooltip) continue;

            DummySingleItemRecipe dummy = new DummySingleItemRecipe(item.getDefaultInstance());
            list.add(new RecipeCollection(client.level.registryAccess(), List.of(dummy)));
        }

        return list;
    }
}
