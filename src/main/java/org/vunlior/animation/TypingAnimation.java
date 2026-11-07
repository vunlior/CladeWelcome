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

public final class TypingAnimation
implements Animation {
    private final int duration;
    private final boolean loop;
    private final double speed;

    public TypingAnimation(int duration, boolean loop, double speed) {
        this.duration = duration;
        this.loop = loop;
        this.speed = speed;
    }

    @Override
    public Component animate(String text, int tick) {
        char next;
        char c;
        int end;
        int adjustedTick = (int)((double)tick * this.speed);
        int effectiveTick = this.loop ? adjustedTick % this.duration : Math.min(adjustedTick, this.duration);
        int targetCharCount = Math.min(effectiveTick / 2 + 1, text.length());
        int actualCharCount = 0;
        int endPos = 0;
        for (int i = 0; i < text.length() && actualCharCount < targetCharCount; ++i) {
            char next2;
            char c2 = text.charAt(i);
            if (c2 == '&' && i + 1 < text.length() && text.charAt(i + 1) == '#' && (end = text.indexOf(59, i)) != -1) {
                endPos = end + 1;
                i = end;
                continue;
            }
            if (c2 == '&' && i + 1 < text.length() && "0123456789abcdefklmnorx".indexOf(next2 = text.charAt(i + 1)) != -1) {
                endPos = i + 2;
                ++i;
                continue;
            }
            ++actualCharCount;
            endPos = i + 1;
        }
        while (endPos < text.length() && (c = text.charAt(endPos)) == '&' && endPos + 1 < text.length() && "0123456789abcdefklmnorx#".indexOf(next = text.charAt(endPos + 1)) != -1) {
            if (next == '#') {
                end = text.indexOf(59, endPos);
                if (end == -1) break;
                endPos = end + 1;
                continue;
            }
            endPos += 2;
        }
        return Component.text((String)text.substring(0, endPos));
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

