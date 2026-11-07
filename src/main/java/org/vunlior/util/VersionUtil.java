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
package org.vunlior.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class VersionUtil {
    private VersionUtil() {
    }

    public static String getPluginVersion(JavaPlugin plugin) {
        try {
            String value;
            Object meta = plugin.getClass().getMethod("getPluginMeta", new Class[0]).invoke((Object)plugin, new Object[0]);
            Object version = meta.getClass().getMethod("getVersion", new Class[0]).invoke(meta, new Object[0]);
            if (version instanceof String && !(value = (String)version).isBlank()) {
                return value;
            }
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        return plugin.getDescription().getVersion();
    }

    public static String getServerVersion() {
        try {
            return Bukkit.getMinecraftVersion();
        }
        catch (NoSuchMethodError ignored) {
            String bukkitVersion = Bukkit.getBukkitVersion();
            int separator = bukkitVersion.indexOf(45);
            return separator > 0 ? bukkitVersion.substring(0, separator) : bukkitVersion;
        }
    }
}

