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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.vunlior.util.VersionUtil;

/**
 * Asks Modrinth or Hangar for the newest published version of the plugin.
 * Runs asynchronously; the callback is invoked on the main thread.
 */
public final class UpdateChecker {
    private static final String MODRINTH_URL = "https://api.modrinth.com/v2/project/%s/version";
    private static final String HANGAR_URL = "https://hangar.papermc.io/api/v1/projects/%s/versions?limit=1&offset=0";
    private final Main plugin;
    private final String source;
    private final String project;

    public UpdateChecker(Main plugin, String source, String project) {
        this.plugin = plugin;
        this.source = source == null ? "modrinth" : source.toLowerCase(Locale.ROOT);
        this.project = project == null ? "" : project.trim();
    }

    public void getVersion(Consumer<String> consumer) {
        if (this.project.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            HttpURLConnection connection = null;
            try {
                String encoded = URLEncoder.encode(this.project, StandardCharsets.UTF_8);
                String url = (this.source.equals("hangar") ? HANGAR_URL : MODRINTH_URL).formatted(encoded);
                connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "Vunlior/CladeWelcome/" + VersionUtil.getPluginVersion(this.plugin));
                if (connection.getResponseCode() != 200) {
                    return;
                }
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                String latest = this.extractVersion(JsonParser.parseString(response.toString()));
                if (latest != null && !latest.isBlank()) {
                    Bukkit.getScheduler().runTask((Plugin) this.plugin, () -> consumer.accept(latest));
                }
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Update check failed (" + this.source + "): " + exception.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String extractVersion(JsonElement root) {
        if (this.source.equals("hangar")) {
            if (!root.isJsonObject()) {
                return null;
            }
            JsonArray result = root.getAsJsonObject().getAsJsonArray("result");
            if (result == null || result.size() == 0) {
                return null;
            }
            JsonElement name = result.get(0).getAsJsonObject().get("name");
            return name == null ? null : name.getAsString();
        }
        if (!root.isJsonArray() || root.getAsJsonArray().size() == 0) {
            return null;
        }
        JsonObject newest = root.getAsJsonArray().get(0).getAsJsonObject();
        JsonElement number = newest.get("version_number");
        return number == null ? null : number.getAsString();
    }

    /** True only if {@code latest} is strictly newer than {@code current}; "1.2.0.0.a" style suffixes are ignored. */
    public static boolean isNewer(String latest, String current) {
        int[] a = numericParts(latest);
        int[] b = numericParts(current);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] numericParts(String version) {
        if (version == null) {
            return new int[0];
        }
        String cleaned = version.trim().replaceFirst("^[vV]", "");
        String[] parts = cleaned.split("[.\\-+]");
        int[] numbers = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            if (!part.matches("\\d+")) {
                break;
            }
            try {
                numbers[count++] = Integer.parseInt(part);
            } catch (NumberFormatException tooLarge) {
                break;
            }
        }
        return java.util.Arrays.copyOf(numbers, count);
    }
}
