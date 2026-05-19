package org.vunlior;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class LangManager {

    private YamlConfiguration lang;
    private final Random random = new Random();

    private static final Set<String> ALLOWED_LANGS = Set.of(
            "en", "ru", "uz", "de", "es", "tr", "fr", "pl", "pt", "it"
    );

    private static final String FALLBACK = "en";

    public void load(JavaPlugin plugin, String langName) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        langName = normalize(plugin, langName);

        saveDefault(plugin, FALLBACK);
        saveDefault(plugin, langName);

        File file = new File(langFolder, langName + ".yml");

        if (!file.exists()) {
            plugin.getLogger().warning("Lang file not found (" + langName + ".yml) → using fallback EN");
            file = new File(langFolder, FALLBACK + ".yml");
        }

        lang = YamlConfiguration.loadConfiguration(file);
    }

    private String normalize(JavaPlugin plugin, String langName) {
        if (langName == null) return FALLBACK;

        langName = langName.toLowerCase();

        if (!ALLOWED_LANGS.contains(langName)) {
            plugin.getLogger().warning("Language '" + langName + "' is not supported. Backing up to EN.");
            return FALLBACK;
        }

        return langName;
    }

    private void saveDefault(JavaPlugin plugin, String langName) {
        File file = new File(plugin.getDataFolder(), "lang/" + langName + ".yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("lang/" + langName + ".yml", false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Could not find default lang resource for: " + langName);
            }
        }
    }

    public String get(String key) {
        if (lang == null) return key;

        List<String> list = lang.getStringList(key);

        if (!list.isEmpty()) {
            return list.get(random.nextInt(list.size()));
        }

        return lang.getString(key, key);
    }

    public String getRaw(String key) {
        if (lang == null) return key;

        List<String> list = lang.getStringList(key);

        if (!list.isEmpty()) {
            return String.join("\n", list);
        }

        return lang.getString(key, key);
    }
}
