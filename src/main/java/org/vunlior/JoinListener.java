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

import java.time.Duration;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.vunlior.LangManager;
import org.vunlior.Main;
import org.vunlior.PlayerWelcomeEvent;
import org.vunlior.animation.ParticleSync;
import org.vunlior.util.AdventureHelper;

public final class JoinListener
implements Listener {
    private static final long JOIN_DELAY_TICKS = 5L;
    private final Main plugin;
    private final Map<UUID, Long> lastQuit = new ConcurrentHashMap<UUID, Long>();

    public JoinListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isFirstJoin = !player.hasPlayedBefore();
        String group = this.resolveGroup(player);
        long lastSeen = this.plugin.getDataStore().getLastSeen(player.getUniqueId());
        this.plugin.getDataStore().incrementJoinCount(player.getUniqueId());
        boolean quickRejoin = this.isQuickRejoin(player);
        this.adventure().hideJoinMessage(event);
        this.notifyUpdateIfNeeded(player);
        PlayerWelcomeEvent welcomeEvent = new PlayerWelcomeEvent(player, group);
        Bukkit.getPluginManager().callEvent((Event)welcomeEvent);
        if (welcomeEvent.isCancelled() || !this.plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            int unique = isFirstJoin ? this.plugin.nextUniqueJoinNumber() : this.plugin.getDataStore().getUniqueCounter();
            if (!quickRejoin) {
                if (this.plugin.getConfig().getBoolean("messages.enabled", true)) {
                    this.sendJoinMessage(player, group, isFirstJoin, unique, lastSeen);
                }
                this.notifyDiscordJoin(player, isFirstJoin, unique);
            }
            if (this.shouldShowInfoPanel(player)) {
                this.sendInfoPanel(player, group);
            }
            if (this.plugin.getConfig().getBoolean("sounds.enabled", true) && !this.plugin.isModuleDisabledForPlayer(player.getUniqueId(), "sounds")) {
                this.playSound(player, group);
            }
            if (isFirstJoin && this.plugin.getConfig().getBoolean("first-join-firework.enabled", true) && !this.plugin.isModuleDisabledForPlayer(player.getUniqueId(), "firework")) {
                this.spawnPrivateFirework(player);
            }
            this.sendSubtitle(player, isFirstJoin);
            this.sendActionBar(player, isFirstJoin);
            this.sendBossBar(player, isFirstJoin);
            this.spawnParticles(player);
            if (isFirstJoin) {
                this.runFirstJoinCommands(player);
            }
        }, 5L);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String group = this.resolveGroup(player);
        long now = System.currentTimeMillis();
        this.lastQuit.put(player.getUniqueId(), now);
        if (this.lastQuit.size() > 1000) {
            long cutoff = now - 3600000L;
            this.lastQuit.values().removeIf(time -> time < cutoff);
        }
        this.plugin.getDataStore().setLastSeen(player.getUniqueId(), now);
        this.plugin.getDataStore().saveAsync();
        this.adventure().hideQuitMessage(event);
        this.adventure().clearLegacyBossBar(player.getUniqueId());
        if (!this.plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.notifyDiscordQuit(player));
        if (!this.plugin.getConfig().getBoolean("messages.enabled", true)) {
            return;
        }
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.sendQuitMessage(player, group));
    }

    private AdventureHelper adventure() {
        return this.plugin.getAdventureHelper();
    }

    private String resolveGroup(Player player) {
        if (player.hasPermission("cladewelcome.admin")) {
            return "admin";
        }
        ConfigurationSection groups = this.plugin.getConfig().getConfigurationSection("groups");
        if (groups != null) {
            String best = null;
            int bestPriority = Integer.MIN_VALUE;
            for (String name : groups.getKeys(false)) {
                String permission = groups.getString(name + ".permission", "");
                if (permission == null || permission.isBlank() || !player.hasPermission(permission)) {
                    continue;
                }
                int priority = groups.getInt(name + ".priority", 0);
                if (best == null || priority > bestPriority) {
                    best = name;
                    bestPriority = priority;
                }
            }
            if (best != null) {
                return best;
            }
        }
        if (player.hasPermission("cladewelcome.op") || player.isOp()) {
            return "op";
        }
        return "default";
    }

    private boolean shouldShowInfoPanel(Player player) {
        if (!this.plugin.getConfig().getBoolean("info-panel.enabled", true)) {
            return false;
        }
        if (this.plugin.isModuleDisabledForPlayer(player.getUniqueId(), "panel")) {
            return false;
        }
        if (player.isOp() && this.plugin.getConfig().getBoolean("info-panel.disable-for-op", false)) {
            return false;
        }
        return !player.hasPermission("cladewelcome.admin") || !this.plugin.getConfig().getBoolean("info-panel.disable-for-admin", false);
    }

    private void notifyUpdateIfNeeded(Player player) {
        if (!this.plugin.hasUpdate()) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("cladewelcome.admin")) {
            return;
        }
        LangManager lang = this.plugin.getLangManager();
        Map<String, String> extra = Map.of("%version%", this.plugin.getLatestVersion());
        this.plugin.sendMessage((CommandSender)player, player, this.plugin.applyPlaceholders(player, lang.getRaw("update.line1"), extra));
        this.plugin.sendMessage((CommandSender)player, player, this.plugin.applyPlaceholders(player, lang.getRaw("update.line2"), extra));
        this.plugin.sendMessage((CommandSender)player, player, this.plugin.applyPlaceholders(player, lang.getRaw("update.line3"), extra));
    }

    private void sendJoinMessage(Player player, String group, boolean isFirstJoin, int unique, long lastSeen) {
        try {
            Component message = this.buildJoinComponent(player, group, isFirstJoin, unique, lastSeen);
            if (message != null) {
                this.broadcast(message);
            }
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to send join message for " + player.getName() + ": " + exception.getMessage());
        }
    }

    private Component buildJoinComponent(Player player, String group, boolean isFirstJoin, int unique, long lastSeen) {
        String msg = this.pickJoinText(group, isFirstJoin, lastSeen);
        if (msg == null || msg.isBlank()) {
            return null;
        }
        Map<String, String> extra = new HashMap<String, String>();
        extra.put("%unique%", String.valueOf(unique));
        extra.put("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        extra.put("%rank%", this.rankDisplay(group));
        extra.put("%days%", String.valueOf(this.daysSince(lastSeen)));
        String processedText = this.plugin.applyPlaceholders(player, msg, extra);
        Component finalMessage = this.plugin.parseColor(player, processedText);
        if (this.plugin.getAnimationManager() != null && this.plugin.getAnimationManager().isEnabled()) {
            String plainText = this.adventure().toLegacyHex(finalMessage);
            Component animated = this.plugin.getAnimationManager().animate(plainText, "welcome-message", player.getUniqueId());
            if (animated != null) {
                finalMessage = animated;
            }
        }
        if (this.plugin.getConfig().getBoolean("messages.clickable.enabled", false)) {
            finalMessage = this.applyClickableComponent(player, finalMessage);
        }
        return finalMessage;
    }

    private String pickJoinText(String group, boolean isFirstJoin, long lastSeen) {
        LangManager lang = this.plugin.getLangManager();
        if (isFirstJoin) {
            return lang.getRaw("firstjoin.message");
        }
        String returning = this.pickReturningText(lastSeen);
        if (returning != null) {
            return returning;
        }
        String custom = this.pickConfigText("groups." + group + ".join-messages");
        if (custom != null) {
            return custom;
        }
        return lang.pickJoinMessage(group, this.plugin.getRandom());
    }

    private Component applyClickableComponent(Player player, Component message) {
        String action = this.plugin.getConfig().getString("messages.clickable.action", "run_command");
        String command = this.plugin.getConfig().getString("messages.clickable.command", "");
        if (this.plugin.isDebugEnabled()) {
            this.plugin.getLogger().info("Applying clickable: action=" + action + ", command=" + command);
        }
        if (command.isBlank()) {
            if (this.plugin.isDebugEnabled()) {
                this.plugin.getLogger().warning("Clickable command is blank, skipping");
            }
            return message;
        }
        String[] commands = command.split(";");
        if (commands.length > 1) {
            StringBuilder compositeCommand = new StringBuilder();
            for (int i = 0; i < commands.length; ++i) {
                String cmd = commands[i].trim();
                if (i > 0) {
                    compositeCommand.append("; ");
                }
                compositeCommand.append(cmd.replace("%player%", player.getName()));
            }
            ClickEvent.Action clickAction = this.parseClickAction(action);
            Component result = message.clickEvent(ClickEvent.clickEvent((ClickEvent.Action)clickAction, (String)compositeCommand.toString()));
            if (this.plugin.isDebugEnabled()) {
                this.plugin.getLogger().info("Applied composite click event: " + compositeCommand.toString());
            }
            return result;
        }
        String processedCommand = command.replace("%player%", player.getName());
        ClickEvent.Action clickAction = this.parseClickAction(action);
        Component result = message.clickEvent(ClickEvent.clickEvent((ClickEvent.Action)clickAction, (String)processedCommand));
        if (this.plugin.isDebugEnabled()) {
            this.plugin.getLogger().info("Applied click event: " + processedCommand);
        }
        return result;
    }

    private ClickEvent.Action parseClickAction(String action) {
        return switch (action.toLowerCase()) {
            case "suggest_command" -> ClickEvent.Action.SUGGEST_COMMAND;
            case "open_url" -> ClickEvent.Action.OPEN_URL;
            case "copy_to_clipboard" -> ClickEvent.Action.COPY_TO_CLIPBOARD;
            default -> ClickEvent.Action.RUN_COMMAND;
        };
    }

    private void spawnParticles(Player player) {
        Particle particle;
        if (!this.plugin.isParticlesEnabled()) {
            return;
        }
        String particleType = this.plugin.getConfig().getString("particles.type", "HEART");
        int count = this.plugin.getConfig().getInt("particles.count", 5);
        double offsetX = this.plugin.getConfig().getDouble("particles.offset.x", 0.5);
        double offsetY = this.plugin.getConfig().getDouble("particles.offset.y", 1.0);
        double offsetZ = this.plugin.getConfig().getDouble("particles.offset.z", 0.5);
        double speed = this.plugin.getConfig().getDouble("particles.speed", 0.1);
        boolean syncWithAnimation = this.plugin.getConfig().getBoolean("particles.sync-with-animation", false);
        try {
            particle = Particle.valueOf((String)particleType.toUpperCase());
        }
        catch (IllegalArgumentException exception) {
            particle = Particle.HEART;
        }
        if (syncWithAnimation && this.plugin.getAnimationManager() != null && this.plugin.getAnimationManager().isEnabled()) {
            this.spawnAnimatedParticles(player, particle, count, offsetX, offsetY, offsetZ, speed);
        } else {
            this.spawnStaticParticles(player, particle, count, offsetX, offsetY, offsetZ, speed);
        }
    }

    private void spawnStaticParticles(Player player, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        player.spawnParticle(particle, player.getLocation().add(0.0, offsetY, 0.0), count, offsetX, 0.0, offsetZ, speed);
    }

    private void spawnAnimatedParticles(Player player, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        ParticleSync particleSync = new ParticleSync(null, particle, count, offsetX, offsetY, offsetZ, speed);
        Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            particleSync.spawnParticles(player, player.getLocation().add(0.0, offsetY, 0.0), 0);
        }, 0L, 5L);
    }

    private Component buildQuitComponent(Player player, String group) {
        LangManager lang = this.plugin.getLangManager();
        String msg = this.pickConfigText("groups." + group + ".quit-messages");
        if (msg == null) {
            msg = lang.pickQuitMessage(group, this.plugin.getRandom());
        }
        if (msg == null) {
            msg = lang.getRaw("quit.fallback");
        }
        if (msg == null || msg.isBlank()) {
            return null;
        }
        Map<String, String> extra = Map.of("%online%", String.valueOf(Math.max(0, Bukkit.getOnlinePlayers().size())), "%rank%", this.rankDisplay(group));
        return this.plugin.parseColor(player, this.plugin.applyPlaceholders(player, msg, extra));
    }

    private void sendQuitMessage(Player player, String group) {
        Component message = this.buildQuitComponent(player, group);
        if (message != null) {
            this.broadcast(message);
        }
    }

    private void broadcast(Component message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (this.plugin.isModuleDisabledForPlayer(online.getUniqueId(), "messages")) continue;
            this.adventure().sendMessage(online, message);
        }
        this.adventure().sendMessage((CommandSender)Bukkit.getConsoleSender(), message);
    }

    private void playSound(Player player, String group) {
            try {
                float pitch;
                float volume;
                String soundName;
                if (!this.plugin.getConfig().getBoolean("sounds.enabled", true)) {
                    return;
                }
                if (this.plugin.getConfig().getConfigurationSection("sounds.groups." + group) != null) {
                    soundName = this.plugin.getConfig().getString("sounds.groups." + group + ".sound", "ENTITY_PLAYER_LEVELUP");
                    volume = (float)this.plugin.getConfig().getDouble("sounds.groups." + group + ".volume", 1.0);
                    pitch = (float)this.plugin.getConfig().getDouble("sounds.groups." + group + ".pitch", 1.0);
                } else {
                    soundName = this.plugin.getConfig().getString("sounds.default.sound", this.plugin.getConfig().getString("sounds.type", "ENTITY_PLAYER_LEVELUP"));
                    volume = (float)this.plugin.getConfig().getDouble("sounds.default.volume", this.plugin.getConfig().getDouble("sounds.volume", 1.0));
                    pitch = (float)this.plugin.getConfig().getDouble("sounds.default.pitch", this.plugin.getConfig().getDouble("sounds.pitch", 1.0));
                }
                if (this.plugin.isDebugEnabled()) {
                    this.plugin.getLogger().info("Playing sound: " + soundName + " for group: " + group);
                }
                Sound sound = Sound.valueOf((String)soundName.toUpperCase(Locale.ROOT));
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
            catch (Exception exception) {
                this.plugin.getLogger().warning("Failed to play sound for " + player.getName() + ": " + exception.getMessage());
                if (this.plugin.isDebugEnabled()) {
                    exception.printStackTrace();
                }
            }
    }

    private void spawnPrivateFirework(Player player) {
        Firework firework = (Firework)player.getWorld().spawn(player.getLocation().add(0.0, 1.0, 0.0), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().withColor(new Color[]{Color.FUCHSIA, Color.AQUA, Color.YELLOW}).withFade(Color.WHITE).with(FireworkEffect.Type.BALL_LARGE).trail(true).flicker(true).build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> ((Firework)firework).detonate(), 2L);
    }

    private void sendSubtitle(Player player, boolean isFirstJoin) {
        try {
            if (!this.plugin.getConfig().getBoolean("subtitle.enabled", true) || this.plugin.isModuleDisabledForPlayer(player.getUniqueId(), "subtitle")) {
                return;
            }
            String key = isFirstJoin ? "firstjoin.subtitle" : "subtitle.welcome";
            String raw = this.plugin.getLangManager().getRaw(key);
            if (raw == null || raw.isBlank() || raw.equals(key)) {
                return;
            }
            String text = this.plugin.applyPlaceholders(player, raw);
            long stayTicks = this.plugin.getConfig().getLong("subtitle.delay", 40L);
            Component subtitleComponent = this.plugin.parseColor(player, text);
            if (this.plugin.getAnimationManager() != null && this.plugin.getAnimationManager().isEnabled()) {
                String plainText = this.adventure().toLegacyHex(subtitleComponent);
                Component animated = this.plugin.getAnimationManager().animate(plainText, "subtitle", player.getUniqueId());
                if (animated != null) {
                    subtitleComponent = animated;
                }
            }
            Title.Times times = Title.Times.of((Duration)Duration.ofMillis(500L), (Duration)Duration.ofMillis(stayTicks * 50L), (Duration)Duration.ofMillis(500L));
            Title title = Title.title((Component)Component.empty(), (Component)subtitleComponent, (Title.Times)times);
            this.adventure().showTitle(player, title);
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to send subtitle for " + player.getName() + ": " + exception.getMessage());
        }
    }

    private void sendInfoPanel(Player player, String group) {
        LangManager lang = this.plugin.getLangManager();
        List<String> lines = lang.getList("info.panel");
        if (lines.isEmpty()) {
            String fallback = lang.getRaw("info.panel");
            if (!"info.panel".equals(fallback)) {
                lines = Collections.singletonList(fallback);
            } else {
                return;
            }
        }
        String rank = this.rankDisplay(group);
        String currentTime = LocalTime.now().format(this.plugin.getTimeFormatter());
        Map<String, String> extra = Map.of("%rank%", rank, "%online%", String.valueOf(Bukkit.getOnlinePlayers().size()), "%time%", currentTime);
        for (String line : lines) {
            String processed = this.plugin.applyPlaceholders(player, line, extra);
            Component component = this.plugin.parseColor(player, processed);
            component = this.makeUrlsClickable(component);
            this.adventure().sendMessage(player, component);
        }
    }

    private Component makeUrlsClickable(Component component) {
        String text = this.adventure().toLegacyHex(component);
        Pattern urlPattern = Pattern.compile("(https?://[^\\s]+)");
        Matcher matcher = urlPattern.matcher(text);
        if (!matcher.find()) {
            return component;
        }
        matcher.reset();
        TextComponent builder = Component.empty();
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String beforeText = text.substring(lastEnd, matcher.start());
                builder = builder.append(this.adventure().deserialize(beforeText));
            }
            String url = matcher.group();
            builder = builder.append(((TextComponent)Component.text((String)url).color((TextColor)NamedTextColor.AQUA)).clickEvent(ClickEvent.openUrl((String)url)));
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            String afterText = text.substring(lastEnd);
            builder = builder.append(this.adventure().deserialize(afterText));
        }
        return builder;
    }

    private void sendActionBar(Player player, boolean isFirstJoin) {
        String text;
        if (!this.plugin.getConfig().getBoolean("actionbar.enabled", true) || this.plugin.isModuleDisabledForPlayer(player.getUniqueId(), "actionbar")) {
            return;
        }
        LangManager lang = this.plugin.getLangManager();
        if (isFirstJoin) {
            text = lang.getRaw("firstjoin.actionbar");
        } else {
            List<String> list = lang.getList("actionbar");
            String string = text = list.isEmpty() ? lang.getRaw("actionbar") : list.get(this.plugin.getRandom().nextInt(list.size()));
        }
        if (text == null || text.isBlank()) {
            return;
        }
        Component actionbarComponent = this.plugin.parseColor(player, this.plugin.applyPlaceholders(player, text));
        if (this.plugin.getAnimationManager() != null && this.plugin.getAnimationManager().isEnabled()) {
            String plainText = this.adventure().toLegacyHex(actionbarComponent);
            Component animated = this.plugin.getAnimationManager().animate(plainText, "actionbar", player.getUniqueId());
            if (animated != null) {
                actionbarComponent = animated;
            }
        }
        this.adventure().sendActionBar(player, actionbarComponent);
    }

    private void sendBossBar(Player player, boolean isFirstJoin) {
        String text;
        if (!this.plugin.getConfig().getBoolean("bossbar.enabled", true) || this.plugin.isModuleDisabledForPlayer(player.getUniqueId(), "bossbar")) {
            return;
        }
        LangManager lang = this.plugin.getLangManager();
        if (isFirstJoin) {
            text = lang.getRaw("firstjoin.bossbar");
        } else {
            List<String> list = lang.getList("bossbar");
            String string = text = list.isEmpty() ? lang.getRaw("bossbar") : list.get(this.plugin.getRandom().nextInt(list.size()));
        }
        if (text == null || text.isBlank()) {
            return;
        }
        BossBar.Color color = this.parseBossBarColor(this.plugin.getConfig().getString("bossbar.color", "BLUE"));
        Component bossbarComponent = this.plugin.parseColor(player, this.plugin.applyPlaceholders(player, text));
        if (this.plugin.getAnimationManager() != null && this.plugin.getAnimationManager().isEnabled()) {
            String plainText = this.adventure().toLegacyHex(bossbarComponent);
            Component animated = this.plugin.getAnimationManager().animate(plainText, "bossbar", player.getUniqueId());
            if (animated != null) {
                bossbarComponent = animated;
            }
        }
        long duration = this.plugin.getConfig().getLong("bossbar.duration", 1200L);
        this.adventure().showBossBar(player, bossbarComponent, color, duration);
    }

    private BossBar.Color parseBossBarColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossBar.Color.BLUE;
        }
        try {
            return BossBar.Color.valueOf((String)raw.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ignored) {
            return BossBar.Color.BLUE;
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean isQuickRejoin(Player player) {
        int cooldown = this.plugin.getConfig().getInt("anti-spam.join-cooldown-seconds", 0);
        if (cooldown <= 0) {
            return false;
        }
        Long quitAt = this.lastQuit.get(player.getUniqueId());
        return quitAt != null && System.currentTimeMillis() - quitAt < cooldown * 1000L;
    }

    private String rankDisplay(String group) {
        return this.plugin.getConfig().getString("groups." + group + ".display", group.toUpperCase(Locale.ROOT));
    }

    private int daysSince(long lastSeen) {
        if (lastSeen <= 0L) {
            return 0;
        }
        return (int) Math.max(0L, (System.currentTimeMillis() - lastSeen) / 86400000L);
    }

    private String pickConfigText(String path) {
        List<String> options = this.plugin.getConfig().getStringList(path);
        if (options.isEmpty()) {
            return null;
        }
        String chosen = options.get(this.plugin.getRandom().nextInt(options.size()));
        return chosen == null || chosen.isBlank() ? null : chosen;
    }

    private String pickReturningText(long lastSeen) {
        if (lastSeen <= 0L || !this.plugin.getConfig().getBoolean("returning.enabled", false)) {
            return null;
        }
        if (this.daysSince(lastSeen) < this.plugin.getConfig().getInt("returning.min-days", 3)) {
            return null;
        }
        return this.pickConfigText("returning.messages");
    }

    private void runFirstJoinCommands(Player player) {
        if (!this.plugin.getConfig().getBoolean("first-join-commands.enabled", false)) {
            return;
        }
        for (String raw : this.plugin.getConfig().getStringList("first-join-commands.commands")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String command = this.plugin.applyPlaceholders(player, raw.trim());
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            try {
                Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), command);
            }
            catch (Exception exception) {
                this.plugin.getLogger().warning("First-join command failed '" + command + "': " + exception.getMessage());
            }
        }
    }

    private String discordText(Player player, String template, int unique) {
        return template.replace("%player%", player.getName())
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%unique%", String.valueOf(unique))
                .replace("%joins%", String.valueOf(this.plugin.getDataStore().getJoinCount(player.getUniqueId())));
    }

    private void notifyDiscordJoin(Player player, boolean isFirstJoin, int unique) {
        DiscordWebhook hook = this.plugin.getDiscordWebhook();
        if (hook == null || !hook.isEnabled()) {
            return;
        }
        String template = isFirstJoin
                ? this.plugin.getConfig().getString("discord.first-join-message", ":tada: **%player%** joined for the first time! (#%unique%)")
                : this.plugin.getConfig().getString("discord.join-message", ":inbox_tray: **%player%** joined (%online%/%max%)");
        if (template == null || template.isBlank()) {
            return;
        }
        hook.send(this.discordText(player, template, unique));
    }

    private void notifyDiscordQuit(Player player) {
        DiscordWebhook hook = this.plugin.getDiscordWebhook();
        if (hook == null || !hook.isEnabled()) {
            return;
        }
        String template = this.plugin.getConfig().getString("discord.quit-message", ":outbox_tray: **%player%** left (%online%/%max%)");
        if (template == null || template.isBlank()) {
            return;
        }
        hook.send(this.discordText(player, template, this.plugin.getDataStore().getUniqueCounter()));
    }

    private void previewJoin(Player target, String group, boolean firstJoin, int unique) {
        Component message = this.buildJoinComponent(target, group, firstJoin, unique, 0L);
        if (message != null) {
            this.adventure().sendMessage(target, message);
        }
    }

    /**
     * Shows one effect only to the given player (nothing is broadcast and no counters change).
     * @return false when the type is unknown
     */
    public boolean preview(Player target, String type) {
        if (target == null || type == null) {
            return false;
        }
        String group = this.resolveGroup(target);
        int unique = this.plugin.getDataStore().getUniqueCounter();
        try {
            switch (type.toLowerCase(Locale.ROOT)) {
                case "join" -> this.previewJoin(target, group, false, unique);
                case "firstjoin" -> this.previewJoin(target, group, true, unique);
                case "quit" -> {
                    Component message = this.buildQuitComponent(target, group);
                    if (message != null) {
                        this.adventure().sendMessage(target, message);
                    }
                }
                case "panel" -> this.sendInfoPanel(target, group);
                case "subtitle" -> this.sendSubtitle(target, false);
                case "actionbar" -> this.sendActionBar(target, false);
                case "bossbar" -> this.sendBossBar(target, false);
                case "sound" -> this.playSound(target, group);
                case "particles" -> this.spawnParticles(target);
                case "all" -> {
                    this.previewJoin(target, group, false, unique);
                    this.sendInfoPanel(target, group);
                    this.playSound(target, group);
                    this.sendSubtitle(target, false);
                    this.sendActionBar(target, false);
                    this.sendBossBar(target, false);
                    this.spawnParticles(target);
                }
                default -> {
                    return false;
                }
            }
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("Preview '" + type + "' failed: " + exception.getMessage());
        }
        return true;
    }
}
