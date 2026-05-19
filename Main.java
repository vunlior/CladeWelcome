package org.vunlior;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main extends JavaPlugin implements Listener {

    private LangManager langManager;
    private final Random random = new Random();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private boolean usePAPI = false;

    private static final List<String> ALLOWED_LANGUAGES = Arrays.asList(
            "en", "ru", "uz", "de", "es", "tr", "fr", "pl", "pt", "it"
    );

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexCharacter('#').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        saveDefaultConfig();

        String configLang = getConfig().getString("language", "en").toLowerCase();

        if (!ALLOWED_LANGUAGES.contains(configLang)) {
            getLogger().warning("==================================================");
            getLogger().warning(" Invalid language '" + configLang + "' found in config.yml!");
            getLogger().warning(" Resetting fallback language to 'en' (English).");
            getLogger().warning("==================================================");
            configLang = "en";
            getConfig().set("language", "en");
            saveConfig();
        }

        langManager = new LangManager();
        langManager.load(this, configLang);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            usePAPI = true;
            getLogger().info(" PlaceholderAPI hook found and enabled!");
        }

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("==================================");
        getLogger().info(" CladeWelcome ENABLED ✔");
        getLogger().info(" Version: " + getDescription().getVersion());
        getLogger().info(" Active Lang: " + configLang.toUpperCase());
        getLogger().info("==================================");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("CladeWelcome DISABLED ✖");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isFirstJoin = !player.hasPlayedBefore();
        String group = getGroup(player);

        if (getConfig().getBoolean("messages.enabled", true)) {
            sendJoinMessage(player, group, isFirstJoin);
        }

        if (getConfig().getBoolean("sounds.enabled", true)) {
            playSound(player, group);
        }

        if (isFirstJoin && getConfig().getBoolean("first-join-firework.enabled", true)) {
            spawnPrivateFirework(player);
        }

        sendSubtitle(player, isFirstJoin);
        sendInfoPanel(player, group);
        sendActionBar(player, isFirstJoin);
        sendBossBar(player, isFirstJoin);
    }

    private String getGroup(Player player) {
        if (player.hasPermission("cladewelcome.admin")) return "admin";
        if (player.isOp()) return "op";
        return "default";
    }

    private void sendJoinMessage(Player player, String group, boolean isFirstJoin) {
        String key = isFirstJoin ? "firstjoin.message" : "cladewelcome." + group;
        String msg = langManager.get(key);

        if (msg.equals(key) && isFirstJoin) {
            msg = langManager.get("cladewelcome." + group);
        }

        msg = msg.replace("%player%", player.getName());
        msg = msg.replace("%unique%", String.valueOf(Bukkit.getOfflinePlayers().length));

        Bukkit.broadcast(parseColor(player, msg));
    }

    private void playSound(Player player, String group) {
        boolean isAdmin = group.equals("admin") || group.equals("op");
        player.playSound(
                player.getLocation(),
                isAdmin ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                isAdmin ? 0.8f : 1.0f
        );
    }

    private void spawnPrivateFirework(Player player) {
        Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();

        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.FUCHSIA, Color.AQUA, Color.YELLOW)
                .withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build());

        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    private void sendSubtitle(Player player, boolean isFirstJoin) {
        if (!getConfig().getBoolean("subtitle.enabled", true)) return;

        String key = isFirstJoin ? "firstjoin.subtitle" : "subtitle.welcome";
        String text = langManager.getRaw(key);

        if (text.equals(key) && isFirstJoin) {
            text = langManager.getRaw("subtitle.welcome");
        }

        text = text.replace("%player%", player.getName());
        long stayTicks = getConfig().getLong("subtitle.delay", 40L);

        Duration fadeIn = Duration.ofMillis(10 * 50L);
        Duration stay = Duration.ofMillis(stayTicks * 50L);
        Duration fadeOut = Duration.ofMillis(10 * 50L);

        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(fadeIn, stay, fadeOut);
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(Component.empty(), parseColor(player, text), times);

        player.showTitle(title);
    }

    private void sendInfoPanel(Player player, String group) {
        if (!getConfig().getBoolean("info-panel.enabled", true)) return;

        String rank = group.toUpperCase();
        String currentTime = LocalTime.now().format(timeFormatter);

        String text = langManager.getRaw("info.panel")
                .replace("%rank%", rank)
                .replace("%ping%", String.valueOf(player.getPing()))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%player%", player.getName())
                .replace("%time%", currentTime);

        player.sendMessage(parseColor(player, "&7" + text));
    }

    private void sendActionBar(Player player, boolean isFirstJoin) {
        if (!getConfig().getBoolean("actionbar.enabled", true)) return;

        String key = isFirstJoin ? "firstjoin.actionbar" : "actionbar";
        String text = langManager.get(key);

        if (text.equals(key) && isFirstJoin) {
            text = langManager.get("actionbar");
        }

        text = text.replace("%player%", player.getName());
        player.sendActionBar(parseColor(player, text));
    }

    private void sendBossBar(Player player, boolean isFirstJoin) {
        if (!getConfig().getBoolean("bossbar.enabled", true)) return;

        String key = isFirstJoin ? "firstjoin.bossbar" : "bossbar";
        String text = langManager.get(key);

        if (text.equals(key) && isFirstJoin) {
            text = langManager.get("bossbar");
        }

        text = text.replace("%player%", player.getName());

        String colorStr = getConfig().getString("bossbar.color", "BLUE").toUpperCase();
        BossBar.Color color;
        try { color = BossBar.Color.valueOf(colorStr); }
        catch (IllegalArgumentException e) { color = BossBar.Color.BLUE; }

        String styleStr = getConfig().getString("bossbar.style", "PROGRESS").toUpperCase();
        if (styleStr.equals("SOLID")) styleStr = "PROGRESS";

        BossBar.Overlay overlay;
        try { overlay = BossBar.Overlay.valueOf(styleStr); }
        catch (IllegalArgumentException e) { overlay = BossBar.Overlay.PROGRESS; }

        BossBar bar = BossBar.bossBar(parseColor(player, text), 1.0f, color, overlay);
        player.showBossBar(bar);

        long duration = getConfig().getLong("bossbar.duration", 1200L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.hideBossBar(bar);
                }
            }
        }.runTaskLater(this, duration);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("cw")) return true;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(parseColor(null, "&6===== CladeWelcome ====="));
            sender.sendMessage(parseColor(null, "&e/cw help &7- show commands"));
            sender.sendMessage(parseColor(null, "&e/cw status &7- plugin status"));
            sender.sendMessage(parseColor(null, "&e/cw reload &7- reload config"));
            sender.sendMessage(parseColor(null, "&e/cw lang <code> &7- change language"));
            sender.sendMessage(parseColor(null, "&6======================="));
            return true;
        }

        if (!sender.hasPermission("cladewelcome.admin")) {
            sender.sendMessage(parseColor(null, langManager.getRaw("no-permission")));
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(parseColor(null, "&aCladeWelcome ONLINE"));
            sender.sendMessage(parseColor(null, "&7Version: &f" + getDescription().getVersion()));
            sender.sendMessage(parseColor(null, "&7Lang: &f" + getConfig().getString("language", "en")));
            sender.sendMessage(parseColor(null, "&7PlaceholderAPI: " + (usePAPI ? "&aHooked" : "&cNot Found")));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();

            String configLang = getConfig().getString("language", "en").toLowerCase();
            if (!ALLOWED_LANGUAGES.contains(configLang)) {
                configLang = "en";
            }

            langManager.load(this, configLang);
            sender.sendMessage(parseColor(null, langManager.getRaw("reload-success")));
            return true;
        }

        if (args[0].equalsIgnoreCase("lang")) {
            if (args.length < 2) {
                sender.sendMessage(parseColor(null, langManager.getRaw("usage")));
                return true;
            }

            String lang = args[1].toLowerCase();

            if (!ALLOWED_LANGUAGES.contains(lang)) {
                sender.sendMessage(parseColor(null, "&c[CladeWelcome] This language is not supported! Allowed: " + ALLOWED_LANGUAGES.toString()));
                return true;
            }

            getConfig().set("language", lang);
            saveConfig();
            langManager.load(this, lang);

            String langChanged = langManager.getRaw("lang-changed").replace("%lang%", lang);
            sender.sendMessage(parseColor(null, langChanged));
            return true;
        }

        return true;
    }

    private Component parseColor(Player player, String text) {
        if (text == null) return Component.empty();

        if (usePAPI && player != null) {
            text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }

        return LEGACY_SERIALIZER.deserialize(text.replace("§", "&"));
    }
}
