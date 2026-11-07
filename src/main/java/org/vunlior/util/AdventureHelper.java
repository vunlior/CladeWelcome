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
package org.vunlior.util;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.vunlior.Main;

public final class AdventureHelper {
    private final JavaPlugin plugin;
    private final LegacyComponentSerializer legacyHex;
    private final LegacyComponentSerializer legacySection;
    private final Map<UUID, org.bukkit.boss.BossBar> legacyBossBars = new ConcurrentHashMap<UUID, org.bukkit.boss.BossBar>();
    private final boolean adventureAudience;
    private final Method joinMessageMethod;
    private final Method quitMessageMethod;
    private static final Pattern MINI_TAG = Pattern.compile("<(/?)(#[0-9a-fA-F]{6}|gradient|rainbow|transition|bold|b|italic|i|em|underlined|u|strikethrough|st|obfuscated|obf|reset|click|hover|newline|br|font|insertion|keybind|key|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white|color|colour|c)(:[^<>]*)?>", Pattern.CASE_INSENSITIVE);
    private final Object miniMessage;
    private final Method miniDeserialize;

    public AdventureHelper(JavaPlugin plugin) {
        this.plugin = plugin;
        this.legacyHex = LegacyComponentSerializer.builder().character('&').hexCharacter('#').hexColors().build();
        this.legacySection = LegacyComponentSerializer.legacySection();
        this.adventureAudience = this.hasMethod(Player.class, "sendActionBar", Component.class);
        this.joinMessageMethod = this.findMethod(PlayerJoinEvent.class, "joinMessage", Component.class);
        this.quitMessageMethod = this.findMethod(PlayerQuitEvent.class, "quitMessage", Component.class);
        Object mini = null;
        Method miniMethod = null;
        try {
            Class<?> miniClass = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            mini = miniClass.getMethod("miniMessage").invoke(null);
            try {
                miniMethod = miniClass.getMethod("deserialize", String.class);
            } catch (NoSuchMethodException noStringOverload) {
                miniMethod = miniClass.getMethod("deserialize", Object.class);
            }
        } catch (Throwable unavailable) {
            // MiniMessage is not bundled on this server (e.g. Spigot or Paper 1.17): feature stays off
            mini = null;
            miniMethod = null;
        }
        this.miniMessage = mini;
        this.miniDeserialize = miniMethod;
    }

    public boolean isMiniMessageAvailable() {
        return this.miniMessage != null && this.miniDeserialize != null;
    }

    public boolean isAdventureAudience() {
        return this.adventureAudience;
    }

    public Component deserialize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        Component mini = this.tryMiniMessage(text);
        if (mini != null) {
            return mini;
        }
        return this.legacyHex.deserialize(text);
    }

    private Component tryMiniMessage(String text) {
        if (!this.isMiniMessageAvailable() || !this.plugin.getConfig().getBoolean("messages.minimessage", true)) {
            return null;
        }
        if (!MINI_TAG.matcher(text).find()) {
            return null;
        }
        try {
            Object result = this.miniDeserialize.invoke(this.miniMessage, legacyToMiniTags(text));
            return result instanceof Component ? (Component) result : null;
        } catch (Exception exception) {
            return null;
        }
    }

    /** Lets old-style &c and &#RRGGBB codes be mixed with MiniMessage tags in the same text. */
    private static String legacyToMiniTags(String text) {
        String prepared = text.replaceAll("&#([0-9a-fA-F]{6})", "<#$1>");
        StringBuilder sb = new StringBuilder(prepared.length() + 16);
        for (int i = 0; i < prepared.length(); i++) {
            char c = prepared.charAt(i);
            if (c == '&' && i + 1 < prepared.length()) {
                String tag = legacyTag(Character.toLowerCase(prepared.charAt(i + 1)));
                if (tag != null) {
                    sb.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String legacyTag(char code) {
        return switch (code) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            case 'r' -> "reset";
            default -> null;
        };
    }

    public String toLegacyHex(Component component) {
        return this.legacyHex.serialize(component);
    }

    public String toLegacySection(Component component) {
        return this.legacySection.serialize(component);
    }

    public void hideJoinMessage(PlayerJoinEvent event) {
        if (this.invokeComponentMessage(this.joinMessageMethod, event, null)) {
            return;
        }
        event.setJoinMessage(null);
    }

    public void hideQuitMessage(PlayerQuitEvent event) {
        if (this.invokeComponentMessage(this.quitMessageMethod, event, null)) {
            return;
        }
        event.setQuitMessage(null);
    }

    public void sendMessage(CommandSender sender, Component component) {
        if (sender == null) {
            return;
        }
        boolean debug = this.plugin instanceof Main && ((Main) this.plugin).isDebugEnabled();
        if (this.tryInvoke(sender, "sendMessage", Component.class, component)) {
            if (debug) {
                this.plugin.getLogger().info("Sent message using native Adventure API");
            }
            return;
        }
        if (sender instanceof Player) {
            Player player = (Player) sender;
            try {
                String legacy = this.toLegacyHex(component);
                Object spigot = player.getClass().getMethod("spigot", new Class[0]).invoke(player, new Object[0]);
                Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
                Object textComponent = textComponentClass.getMethod("fromLegacyText", String.class).invoke(null, legacy);
                Class<?> baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
                Object components = Array.newInstance(baseComponentClass, 1);
                Array.set(components, 0, textComponent);
                spigot.getClass().getMethod("sendMessage", components.getClass()).invoke(spigot, components);
                if (debug) {
                    this.plugin.getLogger().info("Sent message using BungeeCord legacy components");
                }
                return;
            } catch (ReflectiveOperationException exception) {
                if (debug) {
                    this.plugin.getLogger().warning("Failed to send with BungeeCord components: " + exception.getMessage());
                }
            }
        }
        sender.sendMessage(this.toLegacyHex(component));
        if (debug) {
            this.plugin.getLogger().warning("Sent message using legacy format (click events lost)");
        }
    }

    public void sendMessage(Player player, Component component) {
        this.sendMessage((CommandSender)player, component);
    }

    public void sendActionBar(Player player, Component component) {
        if (player == null) {
            return;
        }
        if (this.tryInvoke(player, "sendActionBar", Component.class, component)) {
            return;
        }
        this.sendLegacyActionBar(player, component);
    }

    public void showTitle(Player player, Title title) {
        if (player == null || title == null) {
            return;
        }
        if (this.tryInvoke(player, "showTitle", Title.class, title)) {
            return;
        }
        this.sendLegacyTitle(player, title);
    }

    public void showBossBar(Player player, Component text, BossBar.Color color, long durationTicks) {
        if (player == null) {
            return;
        }
        BossBar adventureBar = BossBar.bossBar((Component)text, (float)1.0f, (BossBar.Color)color, (BossBar.Overlay)BossBar.Overlay.PROGRESS);
        if (this.tryInvoke(player, "showBossBar", BossBar.class, adventureBar)) {
            Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
                if (player.isOnline()) {
                    this.tryInvoke(player, "hideBossBar", BossBar.class, adventureBar);
                }
            }, durationTicks);
            return;
        }
        this.showLegacyBossBar(player, text, color, durationTicks);
    }

    public void clearLegacyBossBar(UUID playerId) {
        org.bukkit.boss.BossBar bar = this.legacyBossBars.remove(playerId);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void showLegacyBossBar(Player player, Component text, BossBar.Color color, long durationTicks) {
        this.clearLegacyBossBar(player.getUniqueId());
        String legacy = this.toLegacySection(text);
        org.bukkit.boss.BossBar bar = Bukkit.createBossBar((String)legacy, (BarColor)this.mapBarColor(color), (BarStyle)BarStyle.SOLID, (BarFlag[])new BarFlag[0]);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        this.legacyBossBars.put(player.getUniqueId(), bar);
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            if (player.isOnline()) {
                this.clearLegacyBossBar(player.getUniqueId());
            }
        }, durationTicks);
    }

    private BarColor mapBarColor(BossBar.Color color) {
        if (color == null) {
            return BarColor.BLUE;
        }
        return switch (color) {
            case PINK -> BarColor.PINK;
            case BLUE -> BarColor.BLUE;
            case RED -> BarColor.RED;
            case GREEN -> BarColor.GREEN;
            case YELLOW -> BarColor.YELLOW;
            case PURPLE -> BarColor.PURPLE;
            case WHITE -> BarColor.WHITE;
            default -> BarColor.BLUE;
        };
    }

    private void sendLegacyActionBar(Player player, Component component) {
        try {
            String legacy = this.toLegacyHex(component);
            Object spigot = player.getClass().getMethod("spigot", new Class[0]).invoke((Object)player, new Object[0]);
            Class<?> chatTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = chatTypeClass.getField("ACTION_BAR").get(null);
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object textComponent = textComponentClass.getMethod("fromLegacyText", String.class).invoke(null, legacy);
            Class<?> baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Object components = Array.newInstance(baseComponentClass, 1);
            Array.set(components, 0, textComponent);
            spigot.getClass().getMethod("sendMessage", chatTypeClass, components.getClass()).invoke(spigot, actionBar, components);
        }
        catch (ReflectiveOperationException exception) {
            player.sendMessage(this.toLegacyHex(component));
        }
    }

    private void sendLegacyTitle(Player player, Title title) {
        String subtitle = this.toLegacySection(title.subtitle());
        String main = this.toLegacySection(title.title());
        int fadeIn = (int)(title.times() == null ? 10L : title.times().fadeIn().toMillis() / 50L);
        int stay = (int)(title.times() == null ? 40L : title.times().stay().toMillis() / 50L);
        int fadeOut = (int)(title.times() == null ? 10L : title.times().fadeOut().toMillis() / 50L);
        player.sendTitle(main, subtitle, fadeIn, stay, fadeOut);
    }

    private boolean tryInvoke(Object target, String methodName, Class<?> parameterType, Object argument) {
        if (target == null || argument == null || parameterType == null) {
            return false;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            method.invoke(target, argument);
            return true;
        }
        catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean invokeComponentMessage(Method method, Object event, Component component) {
        if (method == null || event == null) {
            return false;
        }
        try {
            method.invoke(event, component);
            return true;
        }
        catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Method findMethod(Class<?> type, String name, Class<?> ... params) {
        try {
            return type.getMethod(name, params);
        }
        catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private boolean hasMethod(Class<?> type, String name, Class<?> ... params) {
        return this.findMethod(type, name, params) != null;
    }
}

