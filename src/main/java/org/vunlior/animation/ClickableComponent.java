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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

public final class ClickableComponent {
    private final Component component;
    private final ClickEvent clickEvent;
    private final HoverEvent<Component> hoverEvent;

    private ClickableComponent(Builder builder) {
        this.component = builder.component;
        this.clickEvent = builder.clickEvent;
        this.hoverEvent = builder.hoverEvent;
    }

    public Component build() {
        Component result = this.component;
        if (this.clickEvent != null) {
            result = result.clickEvent(this.clickEvent);
        }
        if (this.hoverEvent != null) {
            result = result.hoverEvent(this.hoverEvent);
        }
        return result;
    }

    public static Builder builder(Component component) {
        return new Builder(component);
    }

    public static final class Builder {
        private final Component component;
        private ClickEvent clickEvent;
        private HoverEvent<Component> hoverEvent;

        private Builder(Component component) {
            this.component = component;
        }

        public Builder click(ClickEvent.Action action, String value) {
            this.clickEvent = ClickEvent.clickEvent((ClickEvent.Action)action, (String)value);
            return this;
        }

        public Builder click(ClickEvent clickEvent) {
            this.clickEvent = clickEvent;
            return this;
        }

        public Builder hover(HoverEvent<Component> hoverEvent) {
            this.hoverEvent = hoverEvent;
            return this;
        }

        public Builder hoverText(String text) {
            this.hoverEvent = HoverEvent.showText((Component)Component.text((String)text));
            return this;
        }

        public Builder hoverComponent(Component component) {
            this.hoverEvent = HoverEvent.showText((Component)component);
            return this;
        }

        public ClickableComponent build() {
            return new ClickableComponent(this);
        }
    }
}

