package org.vunlior;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Sound;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;

public class Main extends JavaPlugin implements Listener {

    private LangManager langManager;

    private final Random random = new Random();

    @Override
    public void onEnable() {

        saveDefaultConfig();

        langManager = new LangManager();

        String lang = getConfig().getString("language", "en");

        langManager.load(this, lang);

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("CladeWelcome enabled!");
    }

    @Override
    public void onDisable() {

        getLogger().info("CladeWelcome disabled!");
    }

    // JOIN EVENT
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        String group = "default";

        // GROUPS
        if (player.hasPermission("cladewelcome.admin")) {
            group = "admin";
        }
        else if (player.hasPermission("cladewelcome.vip")) {
            group = "vip";
        }
        else if (player.isOp()) {
            group = "op";
        }

        List<String> messages =
                getConfig().getStringList("messages." + group);

        if (messages.isEmpty()) return;

        String key =
                messages.get(random.nextInt(messages.size()));

        String msg = langManager.get(key);

        if (msg == null) return;

        msg = msg.replace("%player%", player.getName());

        // BROADCAST
        Bukkit.broadcast(Component.text(msg));

        // SOUND
        player.playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP,
                1.0f,
                1.0f
        );

        // FIRST JOIN
        if (!player.hasPlayedBefore()) {

            String firstJoin =
                    langManager.get("first-join");

            if (firstJoin != null) {

                firstJoin = firstJoin.replace(
                        "%player%",
                        player.getName()
                );

                Bukkit.broadcast(Component.text(firstJoin));
            }
        }
    }

    // COMMANDS
    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!command.getName().equalsIgnoreCase("cw")) {
            return false;
        }

        // /cw reload
        if (args.length == 1 &&
                args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission("cladewelcome.reload")) {

                sender.sendMessage(
                        langManager.get("no-permission")
                );

                return true;
            }

            reloadConfig();

            String lang =
                    getConfig().getString("language", "en");

            langManager.load(this, lang);

            sender.sendMessage(
                    langManager.get("reload-success")
            );

            return true;
        }

        // /cw lang <lang>
        if (args.length == 2 &&
                args[0].equalsIgnoreCase("lang")) {

            if (!sender.hasPermission("cladewelcome.lang")) {

                sender.sendMessage(
                        langManager.get("no-permission")
                );

                return true;
            }

            String newLang = args[1].toLowerCase();

            if (!newLang.equals("ru") &&
                    !newLang.equals("en") &&
                    !newLang.equals("uz")) {

                sender.sendMessage(
                        langManager.get("invalid-lang")
                );

                return true;
            }

            getConfig().set("language", newLang);

            saveConfig();

            langManager.load(this, newLang);

            sender.sendMessage(
                    langManager.get("lang-changed")
                            .replace("%lang%", newLang)
            );

            return true;
        }

        sender.sendMessage(
                langManager.get("usage")
        );

        return true;
    }
}