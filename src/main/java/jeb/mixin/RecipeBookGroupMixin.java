package jeb.mixin;

import net.minecraft.client.RecipeBookCategories;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeBookCategories.class)
public abstract class RecipeBookGroupMixin {

    @Inject(method = "getCategories", at = @At("RETURN"), cancellable = true)
    private static void addCustomGroup(RecipeBookType p_92270_, CallbackInfoReturnable<List<RecipeBookCategories>> cir) {
        if (p_92270_ == RecipeBookType.CRAFTING) {
            List<RecipeBookCategories> original = new ArrayList<>(cir.getReturnValue());

            // Вставляем CAMPFIRE вторым (после CRAFTING_SEARCH)
            original.add(1, RecipeBookCategories.CAMPFIRE);
            //int index = original.indexOf(RecipeBookGroup.CRAFTING_SEARCH);
            //original.add(index + 1, RecipeBookGroup.CAMPFIRE);

            cir.setReturnValue(original);
        }
    }
}


