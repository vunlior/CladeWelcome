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
import net.kyori.adventure.text.TextComponent;
import org.vunlior.animation.Animation;

public final class SmoothTransition {
    private final Animation from;
    private final Animation to;
    private final int transitionDuration;
    private final double easing;
    private int currentTick = 0;

    public SmoothTransition(Animation from, Animation to, int transitionDuration, double easing) {
        this.from = from;
        this.to = to;
        this.transitionDuration = transitionDuration;
        this.easing = easing;
    }

    public Component animate(String text, int tick) {
        if (tick >= this.transitionDuration) {
            return this.to.animate(text, tick - this.transitionDuration);
        }
        double progress = this.easeInOut((double)tick / (double)this.transitionDuration);
        Component fromComponent = this.from.animate(text, tick);
        Component toComponent = this.to.animate(text, tick);
        return this.interpolateComponents(fromComponent, toComponent, progress);
    }

    private double easeInOut(double t) {
        return t < 0.5 ? 2.0 * Math.pow(t, this.easing) : 1.0 - Math.pow(-2.0 * t + 2.0, this.easing) / 2.0;
    }

    private Component interpolateComponents(Component from, Component to, double progress) {
        if (progress <= 0.0) {
            return from;
        }
        if (progress >= 1.0) {
            return to;
        }
        return ((TextComponent)Component.empty().append(from)).append(to);
    }

    public void tick() {
        ++this.currentTick;
    }

    public void reset() {
        this.currentTick = 0;
    }

    public boolean isComplete() {
        return this.currentTick >= this.transitionDuration;
    }

    public int getTransitionDuration() {
        return this.transitionDuration;
    }
}

