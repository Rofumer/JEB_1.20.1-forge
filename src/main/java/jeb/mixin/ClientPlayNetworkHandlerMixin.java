package jeb.mixin;

import jeb.client.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.RecipeBookCategories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static jeb.Jeb.existingResultItems;
import static jeb.Jeb.nonexistingResultItems;
import static jeb.client.RecipeIndex.buildRecipeIndex;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void onSyncRecipes(ClientboundUpdateRecipesPacket p_105132_, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ClientRecipeBook book = mc.player.getRecipeBook();

        List<RecipeCollection> originalList;

        originalList = new ArrayList<>();

        for (RecipeBookCategories group : RecipeBookCategories.CRAFTING_CATEGORIES) {
            originalList.addAll(mc.player.getRecipeBook().getCollection(group));
        }


        for (RecipeCollection collection : originalList) {

            for (Recipe<?> entry : collection.getRecipes()) {
                ItemStack stack = entry.getResultItem(mc.level.registryAccess());
                if (!stack.isEmpty()) {
                    existingResultItems.add(stack.getItem());
                }
            }

        }


        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            if (existingResultItems.contains(item)) continue;
            nonexistingResultItems.add(item);
        }

        // Здесь он уже заполнен — безопасно использовать

        RecipeIndex.fillItemIndex(Minecraft.getInstance());
        buildRecipeIndex();

    }

}

