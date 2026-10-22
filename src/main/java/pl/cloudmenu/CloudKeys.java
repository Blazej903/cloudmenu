package pl.cloudmenu;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Klawisze moda. Widać je też w Opcje > Sterowanie > Cloud Menu. */
public final class CloudKeys {
    public static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(CloudMenuClient.MOD_ID, "main"));

    public static KeyBinding OPEN;
    public static KeyBinding ZOOM;
    public static KeyBinding FULLBRIGHT;
    public static KeyBinding SPRINT;
    public static KeyBinding TRACERS;

    private CloudKeys() {
    }

    public static void register() {
        OPEN = reg("key.cloudmenu.open", GLFW.GLFW_KEY_P);
        ZOOM = reg("key.cloudmenu.zoom", GLFW.GLFW_KEY_C);
        FULLBRIGHT = reg("key.cloudmenu.fullbright", InputUtil.UNKNOWN_KEY.getCode());
        SPRINT = reg("key.cloudmenu.sprint", InputUtil.UNKNOWN_KEY.getCode());
        TRACERS = reg("key.cloudmenu.tracers", InputUtil.UNKNOWN_KEY.getCode());
    }

    private static KeyBinding reg(String translationKey, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(translationKey, InputUtil.Type.KEYSYM, defaultKey, CATEGORY));
    }
}
