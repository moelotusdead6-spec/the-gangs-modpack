package com.gangs.invhistory;

import java.util.Locale;

public enum SnapshotEvent {
    JOIN, LEAVE, DEATH, DIMENSION;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SnapshotEvent fromId(String id) {
        for (SnapshotEvent event : values()) {
            if (event.id().equals(id)) return event;
        }
        return null;
    }
}
