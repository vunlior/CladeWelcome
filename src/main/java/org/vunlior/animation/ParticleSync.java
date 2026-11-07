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

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.vunlior.animation.Animation;

public final class ParticleSync {
    private final Animation animation;
    private final Particle particle;
    private final int particleCount;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double speed;

    public ParticleSync(Animation animation, Particle particle, int particleCount, double offsetX, double offsetY, double offsetZ, double speed) {
        this.animation = animation;
        this.particle = particle;
        this.particleCount = particleCount;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.speed = speed;
    }

    public void spawnParticles(Player player, Location location, int tick) {
        if (!this.animation.shouldLoop() && tick > this.animation.getDuration()) {
            return;
        }
        double intensity = this.calculateIntensity(tick);
        int adjustedCount = (int)((double)this.particleCount * intensity);
        if (adjustedCount <= 0) {
            return;
        }
        player.spawnParticle(this.particle, location, adjustedCount, this.offsetX, this.offsetY, this.offsetZ, this.speed);
    }

    private double calculateIntensity(int tick) {
        double speed = this.animation.getSpeed();
        int adjustedTick = (int)((double)tick * speed);
        if (this.animation.shouldLoop()) {
            return 0.5 + 0.5 * Math.sin((double)adjustedTick * 0.1);
        }
        double progress = (double)adjustedTick / (double)this.animation.getDuration();
        return progress < 0.5 ? progress * 2.0 : 2.0 - progress * 2.0;
    }

    public Particle getParticle() {
        return this.particle;
    }
}

