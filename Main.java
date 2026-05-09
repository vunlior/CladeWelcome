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

        long start = System.currentTimeMillis();

        saveDefaultConfig();

        langManager = new LangManager();

        String lang = getConfig().getString("language", "en");
        langManager.load(this, lang);

        getServer().getPluginManager().registerEvents(this, this);

        long time = System.currentTimeMillis() - start;

        getLogger().info("====================================");
        getLogger().info(" CladeWelcome ENABLED ");
        getLogger().info(" Version: v0.2.0.0.a ");
        getLogger().info(" Language: " + lang);
        getLogger().info(" Load time: " + time + "ms ");
        getLogger().info(" Status: ONLINE ");
        getLogger().info("====================================");
    }

    @Override
    public void onDisable() {

        getLogger().info("====================================");
        getLogger().info(" CladeWelcome DISABLED ");
        getLogger().info(" Status: OFFLINE ");
        getLogger().info(" Goodbye! ");
        getLogger().info("====================================");
    }

    // JOIN EVENT
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        String group = "default";

        // GROUPS
        if (player.hasPermission("cladewelcome.admin")) {
            group = "admin";
        } else if (player.isOp()) {
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
        if (getConfig().getBoolean("sounds.enabled", true)) {
            // play sound here
            if (player.hasPermission("cladewelcome.admin")) {

                player.playSound(
                        player.getLocation(),
                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        1.0f,
                        0.8f
                );

            } else if (player.isOp()) {

                player.playSound(
                        player.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        1.0f,
                        1.2f
                );

            } else {

                player.playSound(
                        player.getLocation(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        1.0f,
                        1.0f
                );
            }
        }

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
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!command.getName().equalsIgnoreCase("cw")) return true;

        String lang = getConfig().getString("language", "en");

        // HELP
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {

            sender.sendMessage("§6========== CladeWelcome ==========");
            sender.sendMessage("§e/cw help §7- show commands");
            sender.sendMessage("§e/cw status §7- plugin info");
            sender.sendMessage("§e/cw lang <ru|en|uz> §7- change language");
            sender.sendMessage("§e/cw reload §7- reload plugin");
            sender.sendMessage("§6================================");

            return true;
        }

        // STATUS
        if (args[0].equalsIgnoreCase("status")) {

            sender.sendMessage("§6========== CladeWelcome ==========");
            sender.sendMessage("§7Version: §fv0.2.0.0.a");
            sender.sendMessage("§7Language: §f" + lang);
            sender.sendMessage("§7Messages system: §aACTIVE");
            sender.sendMessage("§6================================");

            return true;
        }

        // LANG
        if (args[0].equalsIgnoreCase("lang")) {

            if (!sender.hasPermission("cladewelcome.lang")) {
                sender.sendMessage(langManager.get("no-permission"));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§eUsage: /cw lang <ru|en|uz|de|es|tr>");
                return true;
            }

            String newLang = args[1].toLowerCase();

            if (!newLang.equals("ru") && !newLang.equals("en") && !newLang.equals("uz") && !newLang.equals("de") && !newLang.equals("es") && !newLang.equals("tr")) {
                sender.sendMessage("§cAvailable languages: ru, en, uz, de, es, tr");
                return true;
            }

            getConfig().set("language", newLang);
            saveConfig();

            langManager.load(this, newLang);

            sender.sendMessage("§aLanguage changed to: " + newLang);
            return true;
        }

        // RELOAD
        if (args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission("cladewelcome.reload")) {
                sender.sendMessage(langManager.get("no-permission"));
                return true;
            }

            reloadConfig();

            lang = getConfig().getString("language", "en");
            langManager.load(this, lang);

            sender.sendMessage("§aCladeWelcome reloaded!");
            return true;
        }

        sender.sendMessage("§eUse /cw help");
        return true;
    }
}
