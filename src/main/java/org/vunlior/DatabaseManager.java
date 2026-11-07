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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional MySQL/MariaDB storage for per-player toggle settings.
 * HikariCP and the MySQL driver are loaded by the server through the "libraries" section of plugin.yml.
 */
public final class DatabaseManager {
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private final JavaPlugin plugin;
    private volatile HikariDataSource dataSource;
    private volatile boolean enabled;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }

    public void initialize(String host, int port, String database, String username, String password, boolean useSsl) {
        if (host == null || host.isBlank() || database == null || database.isBlank()) {
            this.plugin.getLogger().info("MySQL not configured, using local file storage");
            return;
        }
        try {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName(DRIVER_CLASS);
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + useSsl + "&autoReconnect=true&characterEncoding=utf8");
            config.setUsername(username);
            config.setPassword(password);
            config.setPoolName("CladeWelcome-Pool");
            config.setMaximumPoolSize(5);
            config.setConnectionTimeout(10000L);
            config.setIdleTimeout(600000L);
            config.setMaxLifetime(1800000L);
            this.dataSource = new HikariDataSource(config);
            this.createTable();
            this.enabled = true;
            this.plugin.getLogger().info("MySQL connected successfully");
        } catch (Exception | LinkageError throwable) {
            // LinkageError covers NoClassDefFoundError if the driver library could not be downloaded.
            this.plugin.getLogger().warning("Failed to connect to MySQL, falling back to local file storage: " + throwable.getMessage());
            HikariDataSource failed = this.dataSource;
            this.dataSource = null;
            this.enabled = false;
            if (failed != null) {
                failed.close();
            }
        }
    }

    public void close() {
        HikariDataSource source = this.dataSource;
        this.enabled = false;
        this.dataSource = null;
        if (source != null && !source.isClosed()) {
            source.close();
        }
    }

    public boolean isEnabled() {
        HikariDataSource source = this.dataSource;
        return this.enabled && source != null && !source.isClosed();
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_settings (uuid VARCHAR(36) PRIMARY KEY, disabled_modules TEXT NOT NULL)";
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private static Set<String> parseModules(String data) {
        Set<String> modules = new HashSet<>();
        if (data == null || data.isBlank()) {
            return modules;
        }
        for (String part : data.split(",")) {
            if (!part.isBlank()) {
                modules.add(part.trim());
            }
        }
        return modules;
    }

    public Set<String> loadDisabledModules(UUID uuid) {
        if (!this.isEnabled()) {
            return new HashSet<>();
        }
        String sql = "SELECT disabled_modules FROM player_settings WHERE uuid = ?";
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return parseModules(resultSet.getString("disabled_modules"));
                }
            }
        } catch (SQLException exception) {
            this.plugin.getLogger().warning("Failed to load settings for " + uuid + ": " + exception.getMessage());
        }
        return new HashSet<>();
    }

    public Map<UUID, Set<String>> loadAllPlayerSettings() {
        Map<UUID, Set<String>> allSettings = new HashMap<>();
        if (!this.isEnabled()) {
            return allSettings;
        }
        String sql = "SELECT uuid, disabled_modules FROM player_settings";
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                try {
                    UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    Set<String> modules = parseModules(resultSet.getString("disabled_modules"));
                    if (!modules.isEmpty()) {
                        allSettings.put(uuid, modules);
                    }
                } catch (IllegalArgumentException invalidUuid) {
                    this.plugin.getLogger().fine("Skipping row with invalid UUID in player_settings");
                }
            }
        } catch (SQLException exception) {
            this.plugin.getLogger().warning("Failed to load all player settings: " + exception.getMessage());
        }
        return allSettings;
    }

    public void saveDisabledModules(UUID uuid, Set<String> modules) {
        if (!this.isEnabled()) {
            return;
        }
        String data = String.join(",", modules);
        String sql = "INSERT INTO player_settings (uuid, disabled_modules) VALUES (?, ?) ON DUPLICATE KEY UPDATE disabled_modules = VALUES(disabled_modules)";
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, data);
            statement.executeUpdate();
        } catch (SQLException exception) {
            this.plugin.getLogger().warning("Failed to save settings for " + uuid + ": " + exception.getMessage());
        }
    }

    public void deletePlayerSettings(UUID uuid) {
        if (!this.isEnabled()) {
            return;
        }
        String sql = "DELETE FROM player_settings WHERE uuid = ?";
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            this.plugin.getLogger().warning("Failed to delete settings for " + uuid + ": " + exception.getMessage());
        }
    }
}
