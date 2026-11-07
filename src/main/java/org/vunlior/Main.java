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

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.vunlior.DatabaseManager;
import org.vunlior.JoinListener;
import org.vunlior.LangManager;
import org.vunlior.PlayerSettings;
import org.vunlior.UpdateChecker;
import org.vunlior.animation.Animation;
import org.vunlior.animation.AnimationManager;
import org.vunlior.hook.PlaceholderHook;
import org.vunlior.util.AdventureHelper;
import org.vunlior.util.ServerPlatform;
import org.vunlior.util.ServerVersion;
import org.vunlior.util.VersionUtil;

public final class Main
extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "oAd9YfsP";
    private final Random random = new Random();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<UUID, PlayerSettings>();
    private final Map<String, CacheEntry> componentCache = new ConcurrentHashMap<String, CacheEntry>();
    private static final long CACHE_TTL_MS = 300000L;
    private static final int MAX_CACHE_ENTRIES = 512;
    private final Object settingsLock = new Object();
    private boolean isLightMode = false;
    private volatile LangManager langManager;
    private AdventureHelper adventureHelper;
    private PlaceholderHook placeholderHook;
    private ServerVersion serverVersion;
    private ServerPlatform serverPlatform;
    private UpdateChecker updateChecker;
    private DatabaseManager databaseManager;
    private AnimationManager animationManager;
    private static final List<String> PREVIEW_TYPES = List.of("join", "firstjoin", "quit", "panel", "subtitle", "actionbar", "bossbar", "sound", "particles", "all");
    private static final List<String> TOGGLE_MODULES = List.of("sounds", "subtitle", "actionbar", "bossbar", "messages", "firework", "panel");
    private static final List<String> LANGUAGES = List.of("en", "ru", "uz", "de", "es", "tr", "fr", "pl", "pt", "it");
    private DataStore dataStore;
    private DiscordWebhook discordWebhook;
    private JoinListener joinListener;
    private volatile boolean hasUpdate;
    private volatile String latestVersion = "";

    public void onEnable() {
        long startedAt = System.currentTimeMillis();
        this.saveDefaultConfig();
        this.dataStore = new DataStore(this);
        this.dataStore.load(this.getConfig().getInt("stats.unique-join-counter", 0));
        this.discordWebhook = new DiscordWebhook(this);
        this.applyPerformanceMode();
        this.validateConfig();
        this.serverVersion = ServerVersion.detect();
        this.serverPlatform = ServerPlatform.detect();
        this.adventureHelper = new AdventureHelper(this);
        this.langManager = new LangManager();
        String configLang = this.readLanguageFromConfig();
        // Lang files are tiny: load them synchronously so no event can ever see a half-initialised LangManager.
        this.langManager.load(this, configLang);
        this.animationManager = new AnimationManager(this);
        this.initializeAnimations();
        this.initializeDatabase();
        this.loadPlayerSettings();
        this.placeholderHook = new PlaceholderHook(this);
        this.joinListener = new JoinListener(this);
        this.getServer().getPluginManager().registerEvents((Listener)this.joinListener, (Plugin)this);
        if (this.getConfig().getBoolean("update-check.enabled", true)) {
            this.startUpdateCheck();
        }
        this.printStartupBanner(configLang, System.currentTimeMillis() - startedAt);
    }

    public void onDisable() {
        this.getLogger().info("Disabling CladeWelcome...");
        Bukkit.getScheduler().cancelTasks((Plugin)this);
        this.savePlayerSettings();
        if (this.dataStore != null) {
            this.dataStore.save();
        }
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
        this.playerSettings.clear();
        this.clearComponentCache();
        this.printShutdownBanner();
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub;
        if (!command.getName().equalsIgnoreCase("cw")) {
            return false;
        }
        return switch (sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                if (!this.isStaff(sender)) {
                    yield true;
                }
                this.sendHelp(sender);
                yield true;
            }
            case "reload" -> this.handleReload(sender);
            case "lang" -> this.handleLang(sender, args);
            case "status" -> this.handleStatus(sender);
            case "toggle" -> this.handleToggle(sender, args);
            case "preview" -> this.handlePreview(sender, args);
            default -> {
                if (!this.isStaff(sender)) {
                    yield true;
                }
                this.sendMessage(sender, null, this.langManager.getRaw("usage"));
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("cw") || args.length == 0) {
            return List.of();
        }
        boolean staff = !(sender instanceof Player) || sender.isOp() || sender.hasPermission("cladewelcome.admin");
        List<String> options = new ArrayList<String>();
        if (args.length == 1) {
            options.add("toggle");
            if (staff) {
                options.addAll(List.of("help", "reload", "lang", "status", "preview"));
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("toggle")) {
                options.addAll(TOGGLE_MODULES);
            } else if (staff && sub.equals("lang")) {
                options.addAll(LANGUAGES);
            } else if (staff && sub.equals("preview")) {
                options.addAll(PREVIEW_TYPES);
            }
        } else if (args.length == 3 && staff && args[0].equalsIgnoreCase("preview")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }

    private boolean handlePreview(CommandSender sender, String[] args) {
        if (!this.isStaff(sender)) {
            return true;
        }
        if (args.length < 2) {
            this.sendMessage(sender, null, this.langManager.getRaw("preview.usage").replace("%types%", String.join("|", PREVIEW_TYPES)));
            return true;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                this.sendMessage(sender, null, this.langManager.getRaw("preview.player-not-found").replace("%player%", args[2]));
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player)sender;
        } else {
            this.sendMessage(sender, null, this.langManager.getRaw("preview.specify-player"));
            return true;
        }
        if (!this.joinListener.preview(target, args[1])) {
            this.sendMessage(sender, null, this.langManager.getRaw("preview.unknown-type").replace("%types%", String.join(", ", PREVIEW_TYPES)));
        }
        return true;
    }

    public DataStore getDataStore() {
        return this.dataStore;
    }

    public DiscordWebhook getDiscordWebhook() {
        return this.discordWebhook;
    }

    public boolean isStaff(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player)sender;
        if (player.isOp() || player.hasPermission("cladewelcome.admin")) {
            return true;
        }
        this.sendMessage(sender, player, this.langManager.getRaw("no-permission"));
        return false;
    }

    public LangManager getLangManager() {
        return this.langManager;
    }

    public AdventureHelper getAdventureHelper() {
        return this.adventureHelper;
    }

    public ServerVersion getServerVersion() {
        return this.serverVersion;
    }

    public ServerPlatform getServerPlatform() {
        return this.serverPlatform;
    }

    public Random getRandom() {
        return this.random;
    }

    public DateTimeFormatter getTimeFormatter() {
        return this.timeFormatter;
    }

    public PlayerSettings getPlayerSettings(UUID uuid) {
        return this.playerSettings.computeIfAbsent(uuid, PlayerSettings::new);
    }

    public boolean isModuleDisabledForPlayer(UUID uuid, String module) {
        PlayerSettings settings = this.playerSettings.get(uuid);
        return settings != null && settings.isModuleDisabled(module);
    }

    public AnimationManager getAnimationManager() {
        return this.animationManager;
    }

    public boolean isPlaceholderApiEnabled() {
        return this.placeholderHook != null && this.placeholderHook.isAvailable();
    }

    public boolean hasUpdate() {
        return this.hasUpdate;
    }

    public String getLatestVersion() {
        return this.latestVersion;
    }

    public boolean isLightMode() {
        return this.isLightMode;
    }

    /** Particles are forced off in LIGHT performance mode (the config file itself is never modified). */
    public boolean isParticlesEnabled() {
        return !this.isLightMode && this.getConfig().getBoolean("particles.enabled", false);
    }

    /** Animations are forced off in LIGHT performance mode (the config file itself is never modified). */
    public boolean isAnimationsEnabled() {
        return !this.isLightMode && this.getConfig().getBoolean("animations.enabled", false);
    }

    public boolean isDebugEnabled() {
        return this.getConfig().getBoolean("debug.enabled", false);
    }

    public Component parseColor(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        // Placeholders are resolved BEFORE the cache lookup and the resolved text is the cache key,
        // so values such as %ping% or %joins% can never be served stale.
        String resolved = this.applyPlaceholders(player, text);
        if (this.isLightMode) {
            return this.adventureHelper.deserialize(resolved);
        }
        CacheEntry cached = this.componentCache.get(resolved);
        if (cached != null && !cached.isExpired()) {
            return cached.component;
        }
        Component component = this.adventureHelper.deserialize(resolved);
        if (this.componentCache.size() >= MAX_CACHE_ENTRIES) {
            this.componentCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            if (this.componentCache.size() >= MAX_CACHE_ENTRIES) {
                this.componentCache.clear();
            }
        }
        this.componentCache.put(resolved, new CacheEntry(component));
        return component;
    }

    public void clearComponentCache() {
        this.componentCache.clear();
    }

    public void sendMessage(CommandSender sender, Player context, String pathOrText) {
        Player p;
        if (sender == null || pathOrText == null) {
            return;
        }
        Player player = context != null ? context : (sender instanceof Player ? (p = (Player)sender) : null);
        Component component = this.parseColor(player, pathOrText);
        this.adventureHelper.sendMessage(sender, component);
    }

    public String applyPlaceholders(Player player, String text) {
        if (text == null) {
            return "";
        }
        String result = text;
        if (player != null) {
            result = result.replace("%player%", player.getName()).replace("%ping%", String.valueOf(this.resolvePing(player))).replace("%max%", String.valueOf(Bukkit.getMaxPlayers())).replace("%joins%", String.valueOf(this.dataStore != null ? this.dataStore.getJoinCount(player.getUniqueId()) : 0));
        }
        if (this.placeholderHook != null && player != null) {
            result = this.placeholderHook.apply(player, result);
        }
        return result;
    }

    public String applyPlaceholders(Player player, String text, Map<String, String> extra) {
        String result = this.applyPlaceholders(player, text);
        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public int nextUniqueJoinNumber() {
        int next = this.dataStore.nextUnique();
        this.dataStore.saveAsync();
        return next;
    }

    private int resolvePing(Player player) {
        try {
            return player.getPing();
        }
        catch (NoSuchMethodError ignored) {
            return 0;
        }
    }

    private String readLanguageFromConfig() {
        String configLang = this.getConfig().getString("language", "en");
        if (configLang == null || !this.langManager.isSupported(configLang)) {
            configLang = "en";
        }
        return configLang.toLowerCase(Locale.ROOT);
    }

    private void initializeDatabase() {
        boolean enabled = this.getConfig().getBoolean("database.enabled", false);
        if (!enabled) {
            return;
        }
        String host = this.getConfig().getString("database.host", "localhost");
        int port = this.getConfig().getInt("database.port", 3306);
        String database = this.getConfig().getString("database.database", "cladewelcome");
        String username = this.getConfig().getString("database.username", "root");
        String password = this.getConfig().getString("database.password", "");
        if ("root".equalsIgnoreCase(username) || password == null || password.isBlank() || "password".equals(password) || "change-me".equals(password)) {
            this.getLogger().warning("MySQL is using a default or weak login (user '" + username + "'). Create a dedicated database user with a strong password.");
        }
        this.databaseManager = new DatabaseManager(this);
        boolean useSsl = this.getConfig().getBoolean("database.use-ssl", false);
        this.databaseManager.initialize(host, port, database, username, password, useSsl);
    }

    private void initializeAnimations() {
        boolean enabled = this.isAnimationsEnabled();
        this.animationManager.setEnabled(enabled);
        if (!enabled) {
            return;
        }
        this.setupAnimation("welcome-message");
        this.setupAnimation("subtitle");
        this.setupAnimation("actionbar");
        this.setupAnimation("bossbar");
    }

    private void setupAnimation(String name) {
        Animation animation;
        String type = this.getConfig().getString("animations." + name + ".type", "none");
        if ("none".equalsIgnoreCase(type)) {
            return;
        }
        double speed = this.getConfig().getDouble("animations." + name + ".speed", 1.0);
        boolean loop = this.getConfig().getBoolean("animations." + name + ".loop", true);
        int duration = this.getConfig().getInt("animations." + name + ".duration", 60);
        Color[] colors = null;
        if ("bossbar".equals(name)) {
            List<String> colorStrings = this.getConfig().getStringList("animations.bossbar.colors");
            colors = new Color[colorStrings.size()];
            for (int i = 0; i < colorStrings.size(); ++i) {
                colors[i] = this.parseColor((String)colorStrings.get(i));
            }
        }
        if ((animation = "typing".equalsIgnoreCase(type) || "scrolling".equalsIgnoreCase(type) ? this.animationManager.createAnimation(type, duration, loop, speed) : this.animationManager.createAnimation(type, colors, duration, loop, speed)) != null) {
            this.animationManager.registerGlobalAnimation(name, animation);
        }
    }

    private Color parseColor(String hex) {
        try {
            return Color.decode(hex);
        }
        catch (NumberFormatException exception) {
            return Color.WHITE;
        }
    }

    private void loadPlayerSettings() {
        if (this.databaseManager != null && this.databaseManager.isEnabled()) {
            this.loadPlayerSettingsFromDatabase();
        } else {
            this.loadPlayerSettingsFromFile();
        }
    }

    private void loadPlayerSettingsFromDatabase() {
        try {
            Map<UUID, Set<String>> allSettings = this.databaseManager.loadAllPlayerSettings();
            for (Map.Entry<UUID, Set<String>> entry : allSettings.entrySet()) {
                UUID uuid = entry.getKey();
                Set<String> disabledModules = entry.getValue();
                PlayerSettings settings = new PlayerSettings(uuid);
                for (String module : disabledModules) {
                    settings.setModuleDisabled(module, true);
                }
                this.playerSettings.put(uuid, settings);
            }
        }
        catch (Exception exception) {
            this.getLogger().warning("Failed to load player settings from database: " + exception.getMessage());
        }
    }

    private void loadPlayerSettingsFromFile() {
        File settingsFile = new File(this.getDataFolder(), "player-settings.yml");
        if (!settingsFile.exists()) {
            return;
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration((File)settingsFile);
            ConfigurationSection playersSection = config.getConfigurationSection("players");
            if (playersSection == null) {
                return;
            }
            for (String uuidString : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    PlayerSettings settings = new PlayerSettings(uuid);
                    ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidString);
                    if (playerSection != null) {
                        for (String module : playerSection.getKeys(false)) {
                            if (!playerSection.getBoolean(module)) continue;
                            settings.setModuleDisabled(module, true);
                        }
                    }
                    this.playerSettings.put(uuid, settings);
                }
                catch (IllegalArgumentException illegalArgumentException) {}
            }
        }
        catch (Exception exception) {
            this.getLogger().warning("Failed to load player settings: " + exception.getMessage());
        }
    }

    /** Full synchronous save, used on shutdown. */
    private void savePlayerSettings() {
        if (this.databaseManager != null && this.databaseManager.isEnabled()) {
            this.savePlayerSettingsToDatabase();
        } else {
            this.savePlayerSettingsToFile(false);
        }
    }

    /** Saves one player's settings off the main thread (called after /cw toggle). */
    private void savePlayerSettingsAsync(UUID uuid) {
        if (!this.isEnabled()) {
            this.savePlayerSettings();
            return;
        }
        if (this.databaseManager != null && this.databaseManager.isEnabled()) {
            PlayerSettings settings = this.playerSettings.get(uuid);
            if (settings == null) {
                return;
            }
            boolean anything = settings.isAnythingDisabled();
            Set<String> disabled = new java.util.HashSet<>(settings.getDisabledModules());
            Bukkit.getScheduler().runTaskAsynchronously((Plugin)this, () -> {
                if (anything) {
                    this.databaseManager.saveDisabledModules(uuid, disabled);
                } else {
                    this.databaseManager.deletePlayerSettings(uuid);
                }
            });
        } else {
            YamlConfiguration snapshot = this.buildSettingsYaml();
            Bukkit.getScheduler().runTaskAsynchronously((Plugin)this, () -> this.writeSettingsYaml(snapshot, true));
        }
    }

    private void savePlayerSettingsToDatabase() {
        for (Map.Entry<UUID, PlayerSettings> entry : this.playerSettings.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerSettings settings = entry.getValue();
            if (settings.isAnythingDisabled()) {
                this.databaseManager.saveDisabledModules(uuid, settings.getDisabledModules());
                continue;
            }
            this.databaseManager.deletePlayerSettings(uuid);
        }
    }

    private YamlConfiguration buildSettingsYaml() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection playersSection = config.createSection("players");
        for (Map.Entry<UUID, PlayerSettings> entry : this.playerSettings.entrySet()) {
            PlayerSettings settings = entry.getValue();
            if (!settings.isAnythingDisabled()) continue;
            ConfigurationSection playerSection = playersSection.createSection(entry.getKey().toString());
            for (String module : settings.getDisabledModules()) {
                playerSection.set(module, (Object)true);
            }
        }
        return config;
    }

    private void writeSettingsYaml(YamlConfiguration config, boolean quiet) {
        File settingsFile = new File(this.getDataFolder(), "player-settings.yml");
        synchronized (this.settingsLock) {
            try {
                config.save(settingsFile);
                if (!quiet) {
                    this.getLogger().info("Player settings saved to " + settingsFile.getAbsolutePath());
                }
            }
            catch (IOException exception) {
                this.getLogger().warning("Failed to save player settings: " + exception.getMessage());
            }
        }
    }

    private void savePlayerSettingsToFile(boolean quiet) {
        this.writeSettingsYaml(this.buildSettingsYaml(), quiet);
    }

    private void startUpdateCheck() {
        String source = this.getConfig().getString("update-check.source", "modrinth");
        String project = this.getConfig().getString("update-check.project", MODRINTH_PROJECT_ID);
        String downloadUrl = this.getConfig().getString("update-check.download-url", "https://modrinth.com/plugin/cladewelcome");
        this.updateChecker = new UpdateChecker(this, source, project);
        this.updateChecker.getVersion(latest -> {
            if (latest == null || latest.isBlank()) {
                return;
            }
            this.latestVersion = latest;
            String current = VersionUtil.getPluginVersion(this);
            if (UpdateChecker.isNewer(latest, current)) {
                this.hasUpdate = true;
                this.getLogger().info("==================================================");
                this.getLogger().info("  Update available for CladeWelcome!");
                this.getLogger().info("  Current version: " + current);
                this.getLogger().info("  Latest version: " + latest);
                this.getLogger().info("  Download: " + downloadUrl);
                this.getLogger().info("==================================================");
            }
        });
    }

    private boolean handleReload(CommandSender sender) {
        if (!this.isStaff(sender)) {
            return true;
        }
        this.reloadConfig();
        int issues = this.validateConfig();
        String lang = this.readLanguageFromConfig();
        this.langManager.load(this, lang);
        this.clearComponentCache();
        this.applyPerformanceMode();
        this.initializeAnimations();
        this.placeholderHook = new PlaceholderHook(this);
        this.sendMessage(sender, null, this.langManager.getRaw("reload-success"));
        if (issues > 0) {
            this.sendMessage(sender, null, this.langManager.getRaw("config-issues").replace("%count%", String.valueOf(issues)));
        }
        return true;
    }

    private boolean handleLang(CommandSender sender, String[] args) {
        if (!this.isStaff(sender)) {
            return true;
        }
        if (args.length < 2) {
            this.sendMessage(sender, null, this.langManager.getRaw("usage"));
            return true;
        }
        String requested = args[1].toLowerCase(Locale.ROOT);
        if (!this.langManager.isSupported(requested)) {
            this.sendMessage(sender, null, this.langManager.getRaw("invalid-lang"));
            return true;
        }
        this.langManager.load(this, requested);
        this.getConfig().set("language", (Object)requested);
        this.writeLanguageToConfigFile(requested);
        this.clearComponentCache();
        Player player = sender instanceof Player ? (Player)sender : null;
        String message = this.langManager.getRaw("lang-changed").replace("%lang%", requested.toUpperCase(Locale.ROOT));
        this.sendMessage(sender, player, message);
        return true;
    }

    /**
     * Rewrites only the "language:" line of config.yml so comments and formatting survive
     * (JavaPlugin#saveConfig drops comments on older server versions).
     */
    private void writeLanguageToConfigFile(String lang) {
        File configFile = new File(this.getDataFolder(), "config.yml");
        try {
            String content = java.nio.file.Files.readString(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?m)^language:.*$").matcher(content);
            if (matcher.find()) {
                String updated = matcher.replaceFirst(java.util.regex.Matcher.quoteReplacement("language: " + lang));
                java.nio.file.Files.writeString(configFile.toPath(), updated, java.nio.charset.StandardCharsets.UTF_8);
                return;
            }
        } catch (IOException exception) {
            this.getLogger().warning("Could not update language in config.yml: " + exception.getMessage());
        }
        this.saveConfig();
    }

    private boolean handleToggle(CommandSender sender, String[] args) {
        boolean validModule;
        String module;
        if (!(sender instanceof Player)) {
            this.sendMessage(sender, null, this.langManager.getRaw("command.only-players"));
            return true;
        }
        Player player = (Player)sender;
        if (!player.hasPermission("cladewelcome.toggle")) {
            this.sendMessage(sender, player, this.langManager.getRaw("no-permission"));
            return true;
        }
        UUID uuid = player.getUniqueId();
        PlayerSettings settings = this.getPlayerSettings(uuid);
        if (args.length < 2) {
            settings.toggleModule("messages");
            boolean disabled = settings.isModuleDisabled("messages");
            String key = disabled ? "toggle.disabled" : "toggle.enabled";
            this.sendMessage(sender, player, this.langManager.getRaw(key));
            this.savePlayerSettingsAsync(uuid);
            return true;
        }
        module = args[1].toLowerCase(Locale.ROOT);
        validModule = switch (module) {
            case "sounds", "subtitle", "actionbar", "bossbar", "messages", "firework", "panel" -> true;
            default -> false;
        };
        if (!validModule) {
            this.sendMessage(sender, player, this.langManager.getRaw("toggle.invalid-module"));
            return true;
        }
        settings.toggleModule(module);
        boolean disabled = settings.isModuleDisabled(module);
        String key = disabled ? "toggle.module-disabled" : "toggle.module-enabled";
        String message = this.langManager.getRaw(key).replace("%module%", module);
        this.sendMessage(sender, player, message);
        this.savePlayerSettingsAsync(uuid);
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        Player p;
        if (!this.isStaff(sender)) {
            return true;
        }
        Player player = sender instanceof Player ? (p = (Player)sender) : null;
        Map<String, String> placeholders = Map.ofEntries(Map.entry("%version%", VersionUtil.getPluginVersion(this)), Map.entry("%platform%", this.serverPlatform.name()), Map.entry("%mc%", this.serverVersion.formatted()), Map.entry("%lang%", this.langManager.getCurrentLang().toUpperCase(Locale.ROOT)), Map.entry("%papi%", this.isPlaceholderApiEnabled() ? "ON" : "OFF"), Map.entry("%adventure%", this.adventureHelper.isAdventureAudience() ? "native" : "legacy"), Map.entry("%mysql%", this.databaseManager != null && this.databaseManager.isEnabled() ? "ON" : "OFF"), Map.entry("%animations%", this.animationManager != null && this.animationManager.isEnabled() ? "ON" : "OFF"), Map.entry("%clickable%", this.formatEnabled("messages.clickable.enabled")), Map.entry("%particles%", (this.isParticlesEnabled() ? "ON" : "OFF")), Map.entry("%messages%", this.formatEnabled("messages.enabled")), Map.entry("%sounds%", this.formatEnabled("sounds.enabled")), Map.entry("%subtitle%", this.formatEnabled("subtitle.enabled")), Map.entry("%actionbar%", this.formatEnabled("actionbar.enabled")), Map.entry("%bossbar%", this.formatEnabled("bossbar.enabled")), Map.entry("%firework%", this.formatEnabled("first-join-firework.enabled")), Map.entry("%panel%", this.formatEnabled("info-panel.enabled")), Map.entry("%online%", String.valueOf(Bukkit.getOnlinePlayers().size())), Map.entry("%muted%", String.valueOf(this.playerSettings.size())));
        Iterator<String> iterator = this.langManager.getList("command.status").iterator();
        while (iterator.hasNext()) {
            String line;
            String processed = line = iterator.next();
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                processed = processed.replace(entry.getKey(), entry.getValue());
            }
            this.sendMessage(sender, player, processed);
        }
        return true;
    }

    private String formatEnabled(String configPath) {
        return this.getConfig().getBoolean(configPath, true) ? "ON" : "OFF";
    }

    private void sendHelp(CommandSender sender) {
        Player p;
        Player player = sender instanceof Player ? (p = (Player)sender) : null;
        for (String line : this.langManager.getList("command.help")) {
            this.sendMessage(sender, player, line);
        }
        this.sendMessage(sender, player, this.langManager.getRaw("command.help-preview"));
    }

    private void applyPerformanceMode() {
        String mode = this.getConfig().getString("performance-mode", "light").toLowerCase();
        this.isLightMode = "light".equals(mode);
        if (this.isLightMode) {
            this.getLogger().info("Performance Mode: LIGHT - Optimized for performance");
            this.componentCache.clear();
        } else {
            this.getLogger().info("Performance Mode: HEAVY - All features enabled");
        }
    }

    private int validateConfig() {
        String command;
        List<String> warnings = new ArrayList<>();
        String[] validAnimationTypes = new String[]{"gradient", "rainbow", "typing", "wave", "pulsing", "scrolling", "none"};
        for (String name : new String[]{"welcome-message", "subtitle", "actionbar", "bossbar"}) {
            String string = this.getConfig().getString("animations." + name + ".type", "none");
            if (string == null || Arrays.asList(validAnimationTypes).contains(string.toLowerCase())) continue;
            warnings.add("Invalid animation type '" + string + "' for " + name);
        }
        List<String> bossbarColors = this.getConfig().getStringList("animations.bossbar.colors");
        for (String color : bossbarColors) {
            if (color.matches("^#[0-9A-Fa-f]{6}$")) continue;
            warnings.add("Invalid bossbar color format: " + color + " (expected #RRGGBB)");
        }
        String clickableAction = this.getConfig().getString("messages.clickable.action", "run_command");
        String[] validActions = new String[]{"run_command", "suggest_command", "open_url", "copy_to_clipboard"};
        if (!Arrays.asList(validActions).contains(clickableAction.toLowerCase())) {
            warnings.add("Invalid clickable action: " + clickableAction);
        }
        if ("open_url".equalsIgnoreCase(clickableAction) && !(command = this.getConfig().getString("messages.clickable.command", "")).matches("^https?://.*")) {
            warnings.add("Clickable command is not a valid URL: " + command);
        }
        ConfigurationSection groupsSection = this.getConfig().getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String groupName : groupsSection.getKeys(false)) {
                String groupPermission = groupsSection.getString(groupName + ".permission", "");
                if (groupPermission == null || groupPermission.isBlank()) {
                    warnings.add("Group '" + groupName + "' has no permission set and will never match");
                }
            }
        }
        if (this.getConfig().getBoolean("discord.enabled", false)) {
            String webhookUrl = this.getConfig().getString("discord.url", "");
            if (webhookUrl == null || !webhookUrl.startsWith("https://")) {
                warnings.add("discord.url must be a https:// webhook URL");
            }
        }
        if (!warnings.isEmpty()) {
            this.getLogger().warning("Configuration validation found issues:");
            for (String string : warnings) {
                this.getLogger().warning("  - " + string);
            }
        }
        return warnings.size();
    }

    private void printStartupBanner(String language, long loadMs) {
        String version = VersionUtil.getPluginVersion(this);
        String papi = this.isPlaceholderApiEnabled() ? "connected (optional)" : "not installed (optional)";
        String adventure = this.adventureHelper.isAdventureAudience() ? "native" : "legacy-fallback";
        String mysql = this.databaseManager != null && this.databaseManager.isEnabled() ? "ON" : "OFF";
        String animations = this.animationManager != null && this.animationManager.isEnabled() ? "ON" : "OFF";
        String updateStatus = this.latestVersion == null || this.latestVersion.isBlank() ? "Checking..." : (this.hasUpdate ? "Update available: v" + this.latestVersion : "Up to date");
        this.getLogger().info("==================================================");
        this.getLogger().info("  CladeWelcome v" + version + " enabled");
        this.getLogger().info("  Platform: " + this.serverPlatform.name() + " | MC: " + this.serverVersion.formatted());
        this.getLogger().info("  Support: Spigot/Paper/Purpur 1.17.x - 1.21.x");
        this.getLogger().info("  Language: " + language.toUpperCase(Locale.ROOT) + " | Load: " + loadMs + "ms");
        this.getLogger().info("  Adventure: " + adventure + " | PlaceholderAPI: " + papi);
        this.getLogger().info("  MySQL: " + mysql + " | Animations: " + animations);
        this.getLogger().info("  MiniMessage: " + (this.adventureHelper.isMiniMessageAvailable() ? "available" : "not available on this server") + " | Discord: " + (this.discordWebhook != null && this.discordWebhook.isEnabled() ? "ON" : "OFF"));
        this.getLogger().info("  Update Checker: " + updateStatus);
        this.getLogger().info("  No required plugin dependencies");
        this.getLogger().info("==================================================");
    }

    private void printShutdownBanner() {
        this.getLogger().info("==================================================");
        this.getLogger().info("  CladeWelcome disabled");
        this.getLogger().info("  Tasks cancelled | Sessions cleared");
        this.getLogger().info("  Thank you for using CladeWelcome");
        this.getLogger().info("==================================================");
    }

    private static class CacheEntry {
        final Component component;
        final long timestamp;

        CacheEntry(Component component) {
            this.component = component;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - this.timestamp > 300000L;
        }
    }
}

