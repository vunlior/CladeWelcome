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

import org.vunlior.util.VersionUtil;

public final class ServerVersion {
    private final int compact;

    private ServerVersion(int compact) {
        this.compact = compact;
    }

    public static ServerVersion detect() {
        String raw = VersionUtil.getServerVersion();
        String[] parts = raw.split("\\.");
        if (parts.length < 2) {
            return new ServerVersion(117);
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return new ServerVersion(major * 100 + minor);
        }
        catch (NumberFormatException ignored) {
            return new ServerVersion(117);
        }
    }

    public boolean isAtLeast(int major, int minor) {
        return this.compact >= major * 100 + minor;
    }

    public int compact() {
        return this.compact;
    }

    public String formatted() {
        int major = this.compact / 100;
        int minor = this.compact % 100;
        return major + "." + minor;
    }
}

