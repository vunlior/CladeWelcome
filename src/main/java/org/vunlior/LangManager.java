// SPDX-License-Identifier: Apache-2.0
/*
 * Copyright 2026 Vunlior
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vunlior;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LangManager {
    private static final Set<String> ALLOWED_LANGS = Set.of("en", "ru", "uz", "de", "es", "tr", "fr", "pl", "pt", "it");
    private static final String FALLBACK = "en";
    private volatile YamlConfiguration lang;
    private volatile YamlConfiguration fallbackLang;
    private volatile String currentLang = "en";

    public synchronized void load(JavaPlugin plugin, String langName) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }
        String normalized = this.normalize(plugin, langName);
        this.saveDefault(plugin, FALLBACK);
        if (!normalized.equals(FALLBACK)) {
            this.saveDefault(plugin, normalized);
        }
        File file = new File(langFolder, normalized + ".yml");
        String resolvedLang = normalized;
        if (!file.exists()) {
            plugin.getLogger().warning("Lang file not found (" + normalized + ".yml), using fallback EN.");
            file = new File(langFolder, FALLBACK + ".yml");
            resolvedLang = FALLBACK;
        }
        // Build everything first, then publish it in one go so readers never see a half-loaded state.
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        File fallbackFile = new File(langFolder, FALLBACK + ".yml");
        YamlConfiguration loadedFallback = fallbackFile.exists() ? YamlConfiguration.loadConfiguration(fallbackFile) : null;
        this.lang = loaded;
        this.fallbackLang = loadedFallback;
        this.currentLang = resolvedLang;
    }

    public void reload(JavaPlugin plugin) {
        this.load(plugin, this.currentLang);
    }

    public String getCurrentLang() {
        return this.currentLang;
    }

    public boolean isSupported(String langName) {
        if (langName == null) {
            return false;
        }
        return ALLOWED_LANGS.contains(langName.toLowerCase(Locale.ROOT));
    }

    public Set<String> getAllowedLangs() {
        return ALLOWED_LANGS;
    }

    public String getRaw(String key) {
        YamlConfiguration current = this.lang;
        YamlConfiguration fallback = this.fallbackLang;
        if (current == null || key == null) {
            return key == null ? "" : key;
        }
        String value = current.getString(key);
        if (value != null) {
            return value;
        }
        // key is missing in this language: use the English text instead of printing the raw key
        if (fallback != null) {
            String fallbackValue = fallback.getString(key);
            if (fallbackValue != null) {
                return fallbackValue;
            }
        }
        return key;
    }

    public List<String> getList(String path) {
        YamlConfiguration current = this.lang;
        YamlConfiguration fallback = this.fallbackLang;
        if (current == null || path == null) {
            return List.of();
        }
        if (current.isList(path)) {
            return current.getStringList(path);
        }
        String single = current.getString(path);
        if (single != null) {
            return List.of(single);
        }
        if (fallback != null) {
            if (fallback.isList(path)) {
                return fallback.getStringList(path);
            }
            String fallbackSingle = fallback.getString(path);
            if (fallbackSingle != null) {
                return List.of(fallbackSingle);
            }
        }
        return List.of();
    }

    public String pickRandom(String path, Random random) {
        List<String> messages = this.getList(path);
        if (messages.isEmpty() || random == null) {
            return null;
        }
        return messages.get(random.nextInt(messages.size()));
    }

    public String pickJoinMessage(String group, Random random) {
        String message = this.pickRandom("join." + group, random);
        if (message != null) {
            return message;
        }
        message = this.pickRandom("join.default", random);
        if (message != null) {
            return message;
        }
        message = this.pickRandom("cladewelcome." + group, random);
        if (message != null) {
            return message;
        }
        YamlConfiguration current = this.lang;
        return current == null ? null : this.pickFromSection(current.getConfigurationSection("cladewelcome"), group, random);
    }

    public String pickQuitMessage(String group, Random random) {
        String message = this.pickRandom("quit." + group, random);
        if (message != null) {
            return message;
        }
        message = this.pickRandom("quit.default", random);
        if (message != null) {
            return message;
        }
        message = this.pickRandom("cladewelcome.quit." + group, random);
        if (message != null) {
            return message;
        }
        YamlConfiguration current = this.lang;
        if (current == null) {
            return null;
        }
        message = this.pickFromSection(current.getConfigurationSection("cladewelcome.quit"), group, random);
        if (message != null) {
            return message;
        }
        return this.pickFromSection(current.getConfigurationSection("quit"), group, random);
    }

    private String pickFromSection(ConfigurationSection section, String group, Random random) {
        if (section == null || group == null || random == null || !section.isList(group)) {
            return null;
        }
        List<String> messages = section.getStringList(group);
        if (messages.isEmpty()) {
            return null;
        }
        return (String)messages.get(random.nextInt(messages.size()));
    }

    private String normalize(JavaPlugin plugin, String langName) {
        if (langName == null || langName.isBlank()) {
            return FALLBACK;
        }
        String lowered = langName.toLowerCase(Locale.ROOT);
        if (!ALLOWED_LANGS.contains(lowered)) {
            plugin.getLogger().warning("Language '" + lowered + "' is not supported. Using EN.");
            return FALLBACK;
        }
        return lowered;
    }

    private void saveDefault(JavaPlugin plugin, String langName) {
        File file = new File(plugin.getDataFolder(), "lang/" + langName + ".yml");
        if (!file.exists()) {
            plugin.saveResource("lang/" + langName + ".yml", false);
        }
    }
}

