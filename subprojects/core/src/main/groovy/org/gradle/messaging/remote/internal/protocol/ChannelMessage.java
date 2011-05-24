/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.messaging.remote.internal.protocol;

import org.gradle.messaging.remote.internal.Message;

public class ChannelMessage extends AbstractPayloadMessage implements PayloadMessage {
    private final String channel;
    private final Object payload;

    public ChannelMessage(String channel, Object payload) {
        this.channel = channel;
        this.payload = payload;
    }

    public String getChannel() {
        return channel;
    }

    public Object getPayload() {
        return payload;
    }

    public Message withPayload(Object payload) {
        return new ChannelMessage(channel, payload);
    }

    @Override
    public String toString() {
        return String.format("[ChannelMessage channel: %s, payload: %s]", channel, payload);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        ChannelMessage other = (ChannelMessage) obj;
        return channel.equals(other.channel) && payload.equals(other.payload);
    }

    @Override
    public int hashCode() {
        return channel.hashCode() ^ payload.hashCode();
    }
}