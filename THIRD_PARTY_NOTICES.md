# Third-party notices

CladeWelcome itself is licensed under the Apache License 2.0 (see `LICENSE`).
It uses the following third-party software. **None of it is bundled inside the plugin jar.**

| Component | Version | License | How it is used |
|-----------|---------|---------|----------------|
| [Paper API](https://papermc.io/) (incl. Adventure, Gson) | 1.17.1 (compile-time) | MIT / Apache-2.0 | Provided by the server at runtime |
| [HikariCP](https://github.com/brettwooldridge/HikariCP) | 5.1.0 | Apache-2.0 | Downloaded by the server on first start via `libraries:` in `plugin.yml` (only used when `database.enabled: true`) |
| [MySQL Connector/J](https://dev.mysql.com/downloads/connector/j/) | 8.0.33 | GPL-2.0 with the Universal FOSS Exception | Downloaded by the server on first start via `libraries:` in `plugin.yml` (only used when `database.enabled: true`) |
| [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) | optional | GPL-3.0 | Optional soft dependency, accessed through its public API at runtime; not distributed with CladeWelcome |

Because HikariCP and MySQL Connector/J are fetched by the server from Maven Central instead of being shipped in
the CladeWelcome jar, the jar contains only Apache-2.0 licensed code.
