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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.vunlior.animation.Animation;

public final class GradientAnimation
implements Animation {
    private final Color[] colors;
    private final int duration;
    private final boolean loop;
    private final double speed;

    public GradientAnimation(Color[] colors, int duration, boolean loop, double speed) {
        this.colors = colors;
        this.duration = duration;
        this.loop = loop;
        this.speed = speed;
    }

    @Override
    public Component animate(String text, int tick) {
        int adjustedTick = (int)((double)tick * this.speed);
        int effectiveTick = this.loop ? adjustedTick % this.duration : Math.min(adjustedTick, this.duration);
        TextComponent.Builder builder = Component.text();
        int actualCharCount = 0;
        int i = 0;
        while (i < text.length()) {
            char next;
            int end;
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && text.charAt(i + 1) == '#' && (end = text.indexOf(59, i)) != -1) {
                i = end + 1;
                continue;
            }
            if (c == '&' && i + 1 < text.length() && "0123456789abcdefklmnorx".indexOf(next = text.charAt(i + 1)) != -1) {
                i += 2;
                continue;
            }
            ++actualCharCount;
            ++i;
        }
        float step = actualCharCount > 0 ? (float)this.colors.length / (float)actualCharCount : 0.0f;
        int charIndex = 0;
        i = 0;
        while (i < text.length()) {
            char next;
            int end;
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && text.charAt(i + 1) == '#' && (end = text.indexOf(59, i)) != -1) {
                i = end + 1;
                continue;
            }
            if (c == '&' && i + 1 < text.length() && "0123456789abcdefklmnorx".indexOf(next = text.charAt(i + 1)) != -1) {
                i += 2;
                continue;
            }
            int colorIndex = (int)((float)(charIndex + effectiveTick) * step) % this.colors.length;
            Color color = this.colors[colorIndex];
            builder.append(Component.text((String)String.valueOf(c)).color(TextColor.color((int)color.getRed(), (int)color.getGreen(), (int)color.getBlue())));
            ++charIndex;
            ++i;
        }
        return builder.build();
    }

    @Override
    public int getDuration() {
        return this.duration;
    }

    @Override
    public boolean shouldLoop() {
        return this.loop;
    }

    @Override
    public double getSpeed() {
        return this.speed;
    }

    @Override
    public void reset() {
    }
}

