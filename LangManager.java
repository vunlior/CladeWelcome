package org.vunlior;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Random;

public class LangManager {

    private YamlConfiguration lang;

    private final String fallbackLang = "en";

    private final Random random = new Random();

    public void load(JavaPlugin plugin, String langName) {

        // LANG FOLDER
        File langFolder = new File(plugin.getDataFolder(), "lang");

        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // DEFAULT LANGS (save only if missing)
        saveLang(plugin, "en");
        saveLang(plugin, "ru");
        saveLang(plugin, "uz");
        saveLang(plugin, "de");
        saveLang(plugin, "es");
        saveLang(plugin, "tr");

        if (langName == null || langName.isEmpty()) {
            langName = fallbackLang;
        }

        File file = new File(plugin.getDataFolder(), "lang/" + langName + ".yml");

        // FALLBACK SYSTEM
        if (!file.exists()) {

            plugin.getLogger().warning(
                    "Lang not found: " + langName + ".yml → using EN fallback"
            );

            file = new File(plugin.getDataFolder(), "lang/" + fallbackLang + ".yml");
        }

        lang = YamlConfiguration.loadConfiguration(file);
    }

    // SAVE LANGUAGE FILE (safe)
    private void saveLang(JavaPlugin plugin, String lang) {

        File file = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");

        if (!file.exists()) {
            plugin.saveResource("lang/" + lang + ".yml", false);
        }
    }

    // GET MESSAGE
    public String get(String key) {

        if (lang == null) return key;

        // RANDOM LIST SUPPORT
        List<String> list = lang.getStringList(key);

        if (list != null && !list.isEmpty()) {

            String value = list.get(random.nextInt(list.size()));

            return ChatColor.translateAlternateColorCodes('&', value);
        }

        // SINGLE VALUE
        String value = lang.getString(key);

        if (value == null) {
            return ChatColor.translateAlternateColorCodes('&', key);
        }

        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
