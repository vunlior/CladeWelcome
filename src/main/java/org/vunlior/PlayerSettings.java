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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerSettings {
    private final UUID uuid;
    private final Set<String> disabledModules;

    public PlayerSettings(UUID uuid) {
        this.uuid = uuid;
        this.disabledModules = new HashSet<String>();
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public boolean isModuleDisabled(String module) {
        return this.disabledModules.contains(module.toLowerCase());
    }

    public void setModuleDisabled(String module, boolean disabled) {
        String key = module.toLowerCase();
        if (disabled) {
            this.disabledModules.add(key);
        } else {
            this.disabledModules.remove(key);
        }
    }

    public void toggleModule(String module) {
        String key = module.toLowerCase();
        if (this.disabledModules.contains(key)) {
            this.disabledModules.remove(key);
        } else {
            this.disabledModules.add(key);
        }
    }

    public boolean isAnythingDisabled() {
        return !this.disabledModules.isEmpty();
    }

    public Set<String> getDisabledModules() {
        return new HashSet<String>(this.disabledModules);
    }

    public void clearAll() {
        this.disabledModules.clear();
    }
}

