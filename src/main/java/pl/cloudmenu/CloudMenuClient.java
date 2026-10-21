package pl.cloudmenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudMenuClient implements ClientModInitializer {
    public static final String MOD_ID = "cloudmenu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private boolean zooming = false;
    private int oldFov = 70;
    private boolean fullbrightApplied = false;

    @Override
    public void onInitializeClient() {
        CloudConfig.load();
        CloudKeys.register();

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::stopZoom);
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), CloudHud::render);
    }

    private void tick(MinecraftClient client) {
        CloudConfig cfg = CloudConfig.get();

        while (CloudKeys.OPEN.wasPressed()) {
            if (client.currentScreen == null && client.player != null) {
                client.setScreen(new CloudMenuScreen());
            }
        }
        while (CloudKeys.FULLBRIGHT.wasPressed()) {
            cfg.fullbright = !cfg.fullbright;
            CloudConfig.save();
        }
        while (CloudKeys.SPRINT.wasPressed()) {
            cfg.autoSprint = !cfg.autoSprint;
            CloudConfig.save();
        }

        ClientPlayerEntity player = client.player;
        if (player == null) {
            stopZoom(client);
            fullbrightApplied = false;
            return;
        }

        // Fullbright: lokalny efekt widzenia w nocy (tylko wizualny, serwer o nim nie wie)
        if (cfg.fullbright) {
            StatusEffectInstance current = player.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (current == null || current.getDuration() < 400) {
                player.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
            }
            fullbrightApplied = true;
        } else if (fullbrightApplied) {
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            fullbrightApplied = false;
        }

        // Auto sprint
        if (cfg.autoSprint
                && client.options.forwardKey.isPressed()
                && !player.isSneaking()
                && !player.isUsingItem()
                && !player.horizontalCollision
                && player.getHungerManager().getFoodLevel() > 6) {
            player.setSprinting(true);
        }

        // Zoom (przytrzymaj klawisz): zmniejsza FOV
        boolean zoomHeld = CloudKeys.ZOOM.isPressed() && client.currentScreen == null;
        SimpleOption<Integer> fov = client.options.getFov();
        if (zoomHeld && !zooming) {
            oldFov = fov.getValue();
            fov.setValue(30);
            zooming = true;
        } else if (!zoomHeld && zooming) {
            fov.setValue(oldFov);
            zooming = false;
        }
    }

    private void stopZoom(MinecraftClient client) {
        if (zooming) {
            client.options.getFov().setValue(oldFov);
            zooming = false;
        }
    }
}
