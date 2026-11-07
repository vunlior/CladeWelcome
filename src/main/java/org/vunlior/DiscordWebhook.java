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

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Sends plain text messages to a Discord webhook (asynchronously, never blocks the main thread). */
public final class DiscordWebhook {
    private static final int MAX_CONTENT_LENGTH = 1900;
    private final JavaPlugin plugin;

    public DiscordWebhook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        if (!this.plugin.getConfig().getBoolean("discord.enabled", false)) {
            return false;
        }
        String url = this.plugin.getConfig().getString("discord.url", "");
        return url != null && url.startsWith("https://");
    }

    public void send(String content) {
        if (!this.isEnabled() || content == null || content.isBlank() || !this.plugin.isEnabled()) {
            return;
        }
        String text = content.length() > MAX_CONTENT_LENGTH ? content.substring(0, MAX_CONTENT_LENGTH) : content;
        String url = this.plugin.getConfig().getString("discord.url", "");
        String username = this.plugin.getConfig().getString("discord.username", "CladeWelcome");
        // allowed_mentions.parse = [] makes sure nobody can ping @everyone through a nickname
        String json = "{\"username\":\"" + escape(username) + "\",\"content\":\"" + escape(text)
                + "\",\"allowed_mentions\":{\"parse\":[]}}";
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.post(url, json));
    }

    private void post(String url, String json) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("User-Agent", "CladeWelcome");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                this.plugin.getLogger().warning("Discord webhook returned HTTP " + code);
            }
        } catch (IOException | RuntimeException exception) {
            this.plugin.getLogger().warning("Discord webhook failed: " + exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
