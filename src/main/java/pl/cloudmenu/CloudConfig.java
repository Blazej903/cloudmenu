package pl.cloudmenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ustawienia zapisywane w .minecraft/config/cloudmenu.json */
public final class CloudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CloudConfig instance = new CloudConfig();

    // Wygląd
    public int accent = 0x58B4FF;   // kolor motywu (RGB)
    public boolean light = false;   // jasny motyw
    public int opacity = 235;       // przezroczystość panelu 120..255

    // Moduły
    public boolean fullbright = false;
    public boolean autoSprint = false;

    // HUD
    public boolean hud = true;
    public boolean hudFps = true;
    public boolean hudCoords = true;
    public boolean hudPing = true;

    public static CloudConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cloudmenu.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                CloudConfig loaded = GSON.fromJson(Files.readString(p), CloudConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            }
            instance.opacity = Math.max(120, Math.min(255, instance.opacity));
            save();
        } catch (Exception e) {
            CloudMenuClient.LOGGER.error("Nie udało się wczytać configu, używam domyślnego", e);
            instance = new CloudConfig();
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (IOException e) {
            CloudMenuClient.LOGGER.error("Nie udało się zapisać configu", e);
        }
    }
}
