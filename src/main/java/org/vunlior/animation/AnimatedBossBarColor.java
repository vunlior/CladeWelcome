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
package org.vunlior.animation;

import java.awt.Color;
import net.kyori.adventure.bossbar.BossBar;

public final class AnimatedBossBarColor {
    private final Color[] colors;
    private final int duration;
    private final boolean loop;
    private final double speed;
    private int currentTick = 0;

    public AnimatedBossBarColor(Color[] colors, int duration, boolean loop, double speed) {
        this.colors = colors;
        this.duration = duration;
        this.loop = loop;
        this.speed = speed;
    }

    public BossBar.Color getCurrentColor() {
        if (this.colors == null || this.colors.length == 0) {
            return BossBar.Color.BLUE;
        }
        int adjustedTick = (int)((double)this.currentTick * this.speed);
        int effectiveTick = this.loop ? adjustedTick % this.duration : Math.min(adjustedTick, this.duration);
        int colorIndex = effectiveTick / (this.duration / this.colors.length) % this.colors.length;
        Color color = this.colors[colorIndex];
        return this.mapColorToBossBarColor(color);
    }

    public void tick() {
        ++this.currentTick;
    }

    public void reset() {
        this.currentTick = 0;
    }

    private BossBar.Color mapColorToBossBarColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0];
        if ((double)hue < 0.08 || (double)hue >= 0.92) {
            return BossBar.Color.RED;
        }
        if ((double)hue < 0.17) {
            return BossBar.Color.PINK;
        }
        if ((double)hue < 0.33) {
            return BossBar.Color.PURPLE;
        }
        if ((double)hue < 0.5) {
            return BossBar.Color.BLUE;
        }
        if ((double)hue < 0.67) {
            return BossBar.Color.GREEN;
        }
        if ((double)hue < 0.83) {
            return BossBar.Color.YELLOW;
        }
        return BossBar.Color.WHITE;
    }

    public boolean isLoop() {
        return this.loop;
    }

    public int getDuration() {
        return this.duration;
    }
}

