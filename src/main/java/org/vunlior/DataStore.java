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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stores runtime statistics in data.yml: the global unique-join counter,
 * how many times each player joined and when each player was last seen.
 * Keeping this out of config.yml means the config (and its comments) is never rewritten.
 */
public final class DataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> joinCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSeen = new ConcurrentHashMap<>();
    private final AtomicInteger uniqueCounter = new AtomicInteger();
    private final Object saveLock = new Object();

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    /**
     * @param legacyCounter value of stats.unique-join-counter from old config.yml (used once for migration)
     */
    public void load(int legacyCounter) {
        int counter = legacyCounter;
        boolean existed = this.file.exists();
        if (existed) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
                if (yaml.contains("unique-join-counter")) {
                    counter = yaml.getInt("unique-join-counter", legacyCounter);
                }
                ConfigurationSection players = yaml.getConfigurationSection("players");
                if (players != null) {
                    for (String key : players.getKeys(false)) {
                        try {
                            UUID id = UUID.fromString(key);
                            int joins = players.getInt(key + ".joins", 0);
                            long seen = players.getLong(key + ".last-seen", 0L);
                            if (joins > 0) {
                                this.joinCounts.put(id, joins);
                            }
                            if (seen > 0L) {
                                this.lastSeen.put(id, seen);
                            }
                        } catch (IllegalArgumentException ignored) {
                            // not a UUID key, skip
                        }
                    }
                }
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Failed to read data.yml: " + exception.getMessage());
            }
        }
        this.uniqueCounter.set(Math.max(0, counter));
        if (!existed) {
            this.save();
        }
    }

    public int getUniqueCounter() {
        return this.uniqueCounter.get();
    }

    public int nextUnique() {
        return this.uniqueCounter.incrementAndGet();
    }

    public int getJoinCount(UUID uuid) {
        return this.joinCounts.getOrDefault(uuid, 0);
    }

    public int incrementJoinCount(UUID uuid) {
        return this.joinCounts.merge(uuid, 1, Integer::sum);
    }

    /** Epoch millis of the last time the player left, or 0 if unknown. */
    public long getLastSeen(UUID uuid) {
        return this.lastSeen.getOrDefault(uuid, 0L);
    }

    public void setLastSeen(UUID uuid, long epochMillis) {
        this.lastSeen.put(uuid, epochMillis);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("unique-join-counter", this.uniqueCounter.get());
        for (Map.Entry<UUID, Integer> entry : this.joinCounts.entrySet()) {
            yaml.set("players." + entry.getKey() + ".joins", entry.getValue());
        }
        for (Map.Entry<UUID, Long> entry : this.lastSeen.entrySet()) {
            yaml.set("players." + entry.getKey() + ".last-seen", entry.getValue());
        }
        synchronized (this.saveLock) {
            try {
                File parent = this.file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                yaml.save(this.file);
            } catch (IOException exception) {
                this.plugin.getLogger().warning("Failed to save data.yml: " + exception.getMessage());
            }
        }
    }

    public void saveAsync() {
        if (!this.plugin.isEnabled()) {
            this.save();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, this::save);
    }
}
