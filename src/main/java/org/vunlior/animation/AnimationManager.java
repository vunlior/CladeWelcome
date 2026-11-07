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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.vunlior.animation.Animation;
import org.vunlior.animation.GradientAnimation;
import org.vunlior.animation.PulsingAnimation;
import org.vunlior.animation.RainbowAnimation;
import org.vunlior.animation.ScrollingAnimation;
import org.vunlior.animation.TypingAnimation;
import org.vunlior.animation.WaveAnimation;

public final class AnimationManager {
    private final JavaPlugin plugin;
    private final Map<String, Animation> globalAnimations = new HashMap<String, Animation>();
    private final Map<UUID, Map<String, Animation>> playerAnimations = new ConcurrentHashMap<UUID, Map<String, Animation>>();
    private final Map<UUID, Integer> playerTicks = new ConcurrentHashMap<UUID, Integer>();
    private boolean enabled;
    private int globalTick = 0;

    public AnimationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.startAnimationTask();
        }
    }

    public void registerGlobalAnimation(String name, Animation animation) {
        this.globalAnimations.put(name, animation);
    }

    public void registerPlayerAnimation(UUID uuid, String name, Animation animation) {
        this.playerAnimations.computeIfAbsent(uuid, k -> new HashMap()).put(name, animation);
    }

    public Component animate(String text, String animationName) {
        return this.animate(text, animationName, null);
    }

    public Component animate(String text, String animationName, UUID uuid) {
        if (!this.enabled) {
            return Component.text((String)text);
        }
        Animation animation = this.getAnimation(animationName, uuid);
        if (animation == null) {
            return Component.text((String)text);
        }
        int tick = uuid != null ? this.playerTicks.getOrDefault(uuid, 0) : this.globalTick;
        return animation.animate(text, tick);
    }

    private Animation getAnimation(String name, UUID uuid) {
        Animation playerAnimation;
        if (uuid != null && this.playerAnimations.containsKey(uuid) && (playerAnimation = this.playerAnimations.get(uuid).get(name)) != null) {
            return playerAnimation;
        }
        return this.globalAnimations.get(name);
    }

    public Animation createAnimation(String type, Color[] colors, int duration, boolean loop, double speed) {
        return switch (type.toLowerCase()) {
            case "gradient" -> new GradientAnimation(colors, duration, loop, speed);
            case "rainbow" -> new RainbowAnimation(duration, loop, speed);
            case "wave" -> new WaveAnimation(colors != null && colors.length > 0 ? colors[0] : Color.WHITE, duration, loop, speed);
            case "pulsing" -> new PulsingAnimation(colors != null && colors.length > 0 ? colors[0] : Color.WHITE, duration, loop, speed);
            default -> null;
        };
    }

    public Animation createAnimation(String type, int duration, boolean loop, double speed) {
        return switch (type.toLowerCase()) {
            case "typing" -> new TypingAnimation(duration, loop, speed);
            case "scrolling" -> new ScrollingAnimation(duration, loop, speed);
            default -> null;
        };
    }

    private void startAnimationTask() {
        Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> {
            ++this.globalTick;
            for (UUID uuid : this.playerAnimations.keySet()) {
                this.playerTicks.put(uuid, this.playerTicks.getOrDefault(uuid, 0) + 1);
            }
        }, 1L, 1L);
    }

    public void removePlayerAnimations(UUID uuid) {
        this.playerAnimations.remove(uuid);
        this.playerTicks.remove(uuid);
    }

    public void resetPlayerAnimation(UUID uuid, String animationName) {
        if (this.playerAnimations.containsKey(uuid) && this.playerAnimations.get(uuid).containsKey(animationName)) {
            this.playerAnimations.get(uuid).get(animationName).reset();
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }
}

