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

import net.kyori.adventure.text.Component;
import org.vunlior.animation.Animation;

public final class ScrollingAnimation
implements Animation {
    private final int duration;
    private final boolean loop;
    private final double speed;

    public ScrollingAnimation(int duration, boolean loop, double speed) {
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
        int scrollOffset = effectiveTick % cleanText.length();
        String scrolledClean = cleanText.substring(scrollOffset) + cleanText.substring(0, scrollOffset);
        return Component.text((String)scrolledClean);
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

