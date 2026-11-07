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
import net.kyori.adventure.text.format.TextColor;
import org.vunlior.animation.Animation;

public final class PulsingAnimation
implements Animation {
    private final Color color;
    private final int duration;
    private final boolean loop;
    private final double speed;

    public PulsingAnimation(Color color, int duration, boolean loop, double speed) {
        this.color = color;
        this.duration = duration;
        this.loop = loop;
        this.speed = speed;
    }

    @Override
    public Component animate(String text, int tick) {
        int adjustedTick = (int)((double)tick * this.speed);
        int effectiveTick = this.loop ? adjustedTick % this.duration : Math.min(adjustedTick, this.duration);
        StringBuilder cleanText = new StringBuilder();
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
            cleanText.append(c);
            ++i;
        }
        if (cleanText.length() == 0) {
            return Component.text((String)text);
        }
        float pulse = (float)Math.sin((double)effectiveTick * 0.3);
        int brightness = (int)(128.0f + pulse * 127.0f);
        Color adjustedColor = new Color(Math.min(255, this.color.getRed() * brightness / 128), Math.min(255, this.color.getGreen() * brightness / 128), Math.min(255, this.color.getBlue() * brightness / 128));
        return Component.text((String)cleanText.toString()).color(TextColor.color((int)adjustedColor.getRed(), (int)adjustedColor.getGreen(), (int)adjustedColor.getBlue()));
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

