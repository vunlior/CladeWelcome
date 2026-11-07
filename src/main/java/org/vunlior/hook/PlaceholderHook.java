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
package org.vunlior.hook;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaceholderHook {
    private static final String PLUGIN_NAME = "PlaceholderAPI";
    private static final String API_CLASS = "me.clip.placeholderapi.PlaceholderAPI";
    private final boolean available;
    private final Method setPlaceholders;

    public PlaceholderHook(JavaPlugin plugin) {
        Method method = null;
        boolean hooked = false;
        if (Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null && Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
            try {
                Class<?> api = Class.forName(API_CLASS);
                method = api.getMethod("setPlaceholders", Player.class, String.class);
                hooked = true;
            }
            catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("PlaceholderAPI is installed but CladeWelcome could not hook it: " + exception.getMessage());
            }
        }
        this.setPlaceholders = method;
        this.available = hooked;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public String apply(Player player, String text) {
        if (!this.available || player == null || text == null || text.isEmpty()) {
            return text;
        }
        try {
            String parsed;
            Object result = this.setPlaceholders.invoke(null, player, text);
            return result instanceof String ? (parsed = (String)result) : text;
        }
        catch (ReflectiveOperationException exception) {
            return text;
        }
    }
}

