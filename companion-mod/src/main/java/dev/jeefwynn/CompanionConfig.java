package dev.jeefwynn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class CompanionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("jeefwynn.json");

    boolean spellHudEnabled = false;
    boolean discordEnabled = false;
    boolean setupComplete = false;
    String bridgeUrl = "https://replace-me.fly.dev";
    String bridgeToken = "";
    int hudX = -1;
    int hudY = -1;

    static CompanionConfig load() {
        if (!Files.exists(PATH)) return new CompanionConfig();
        try {
            CompanionConfig config = GSON.fromJson(Files.readString(PATH), CompanionConfig.class);
            return config == null ? new CompanionConfig() : config;
        } catch (Exception exception) {
            JeefWynnClient.LOGGER.warn("Could not read {}; using defaults", PATH, exception);
            return new CompanionConfig();
        }
    }

    void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException exception) {
            JeefWynnClient.LOGGER.error("Could not save {}", PATH, exception);
        }
    }
}
