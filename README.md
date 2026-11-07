# CladeWelcome

Customisable join/quit messages, titles, action bar, boss bar, sounds, fireworks, info panel and Discord notifications
for **Paper, Spigot and Purpur 1.17.x – 1.21.x**.

[Hangar](https://hangar.papermc.io/Vunlior/CladeWelcome) ·
[Modrinth](https://modrinth.com/project/oAd9YfsP) ·
[SpigotMC](https://www.spigotmc.org/resources/cladewelcome.135054/) ·
[Changelog](CHANGELOG.md)

## Features
- Permission based groups (`admin`, `op`, `default` and your own, e.g. VIP) with their own messages and sounds
- MiniMessage, legacy `&` codes and `&#RRGGBB` hex colours
- Welcome-back message for returning players, first-join firework and first-join commands
- Placeholders: `%player%` `%online%` `%max%` `%ping%` `%joins%` `%days%` `%rank%` (+ PlaceholderAPI)
- Animations (gradient, rainbow, typing, wave, pulsing, scrolling) in `heavy` performance mode
- Per-player `/cw toggle` for every module (file or MySQL storage)
- Discord webhook for join/quit events
- 10 languages: en, ru, uz, de, es, tr, fr, pl, pt, it

## Installation
1. Put the jar into `plugins/` and start the server.
2. Edit `plugins/CladeWelcome/config.yml`, then run `/cw reload`.
3. After updating, delete the old `config.yml` if you want the new options (a backup is a good idea).

MySQL is optional. When `database.enabled: true`, the server downloads HikariCP and the MySQL driver
automatically on the first start (internet access to Maven Central is required once).

## Commands and permissions
| Command | Permission | Description |
|---------|-----------|-------------|
| `/cw help` | `cladewelcome.admin` | Command list |
| `/cw reload` | `cladewelcome.reload` | Reload config and language |
| `/cw lang <code>` | `cladewelcome.lang` | Change language |
| `/cw status` | `cladewelcome.status` | Plugin status |
| `/cw preview <type> [player]` | `cladewelcome.admin` | Preview an effect |
| `/cw toggle [module]` | `cladewelcome.toggle` (default: everyone) | Mute/unmute modules for yourself |

## Building
Requires Java 17+ and Maven.

```
mvn clean package
```

The jar is written to `target/CladeWelcome_v<version>_1.17-1.21_Universal.jar`.

## License
Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.

---

## Кратко по-русски
CladeWelcome — плагин приветствий для Paper/Spigot/Purpur 1.17–1.21: сообщения входа и выхода, титры, actionbar,
bossbar, звуки, салют, группы по правам, плейсхолдеры, Discord-вебхук, 10 языков.
Установка: положите jar в `plugins/`, запустите сервер, настройте `config.yml` и выполните `/cw reload`.
MySQL необязателен: драйвер и HikariCP скачиваются сервером автоматически при первом запуске.
Сборка: `mvn clean package` (Java 17+). Лицензия — Apache 2.0.
