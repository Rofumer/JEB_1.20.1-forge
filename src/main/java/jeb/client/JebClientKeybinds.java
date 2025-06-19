package jeb.client;

import com.mojang.blaze3d.platform.InputConstants;
import jeb.Jeb;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Paths;

/**
 * Регистрирует хоткеи JEB через MOD-бус.
 * В этом классе не нужно ничего, кроме KeyBinding!
 */
@Mod.EventBusSubscriber(modid = Jeb.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class JebClientKeybinds {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        JebClient.FAVORITE_KEY = new KeyMapping(
                "key.jeb.add_remove_favorite_recipes",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_A,
                "JEB (Just Enough Book)"
        );
        event.register(JebClient.FAVORITE_KEY);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        Jeb.CONFIG_PATH = Paths.get(
                Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "config", "JEB.json"
        );
        Jeb.loadConfig();
    }
}
