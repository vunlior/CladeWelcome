# Changelog

All notable changes to CladeWelcome are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/) starting with 1.3.0.

## [1.3.0] - 2026-11-07

### Added
- Project is licensed under the **Apache License 2.0** (`LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, SPDX headers in every source file).
- `update-check.source` / `update-check.project` / `update-check.download-url` in `config.yml`: the update checker can now use **Modrinth or Hangar**.
- `database.use-ssl` option in `config.yml`.
- New language keys `preview.*`, `command.help-preview` and `config-issues` (EN, RU, UZ; other languages fall back to English).
- `.gitattributes` and a complete `.gitignore`.

### Fixed
- **MySQL support crashed the plugin on enable** because HikariCP and the MySQL driver were not part of the jar. They are now loaded through `libraries:` in `plugin.yml`, and a failed database connection falls back to local file storage instead of disabling the plugin.
- **Stale placeholders:** `%ping%`, `%joins%` and PlaceholderAPI values were cached for up to 5 minutes in `heavy` performance mode. Placeholders are now resolved before the cache lookup.
- **Memory leak:** the component cache grew without limit. It is now capped (512 entries) and expired entries are purged.
- **`performance-mode: light` silently overwrote `animations.enabled` and `particles.enabled`** in `config.yml` the next time the config was saved. The config is no longer modified; light mode now only disables these features at runtime.
- **Race condition on startup:** language files were loaded asynchronously, so a player joining during startup could trigger a `NullPointerException`. Language loading is now synchronous and atomic.
- `/cw reload` and `/cw lang` no longer touch the Bukkit config from async threads. `/cw lang` now only rewrites the `language:` line, so comments in `config.yml` are preserved.
- The update checker reported an "update" whenever the version strings differed (including when you ran a newer build). It now only reports strictly newer versions and parses the response with Gson instead of string searching.
- `/cw toggle` rewrote the whole settings file/table on the main thread for every click. Only the affected player is saved now, asynchronously.
- Invalid `pom.xml` (`<n>` tag, wrong XSD URL, unused `dependencyManagement`) and removal of decompiler leftovers (unused variables, redundant casts).

### Changed
- **Versioning scheme is now `MAJOR.MINOR.PATCH`** ([Semantic Versioning](https://semver.org/)) instead of `x.y.z.w.a`. Previous releases map to: 1.2.0.0.a → 1.2.0.
- Hard-coded English/Russian command messages moved into the language files.
- Release jar is named `CladeWelcome_v<version>_1.17-1.21_Universal.jar` (e.g. `CladeWelcome_v1.3.0_1.17-1.21_Universal.jar`).
- MySQL connection pool reduced to 5 connections and the connection timeout to 10 seconds.

## [1.2.0.0.a]
- Restored sources from the release jar and added: permission groups, returning-player message, MiniMessage support, `%max%` `%joins%` `%days%` `%rank%` placeholders, `/cw preview`, tab completion, anti-spam on rejoin, first-join commands, Discord webhook, `data.yml`, English fallback for missing language keys.

## [0.4.0.0.a]
- BossBar, Title and ActionBar support, first-join firework and info panel.
