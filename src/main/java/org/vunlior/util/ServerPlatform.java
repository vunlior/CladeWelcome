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

public enum ServerPlatform {
    PAPER,
    PURPUR,
    SPIGOT,
    OTHER;


    public static ServerPlatform detect() {
        String name = Bukkit.getName().toLowerCase();
        if (name.contains("purpur")) {
            return PURPUR;
        }
        if (name.contains("paper")) {
            return PAPER;
        }
        if (name.contains("spigot")) {
            return SPIGOT;
        }
        return OTHER;
    }
}

