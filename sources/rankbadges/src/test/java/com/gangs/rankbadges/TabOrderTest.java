package com.gangs.rankbadges;

import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TabOrderTest {
    record Entry(String team, boolean spectator, String name) {}

    @Test
    public void rankAlwaysPrecedesSpectatorStateAndAlphabeticalName() {
        var vanilla = Comparator.comparing(Entry::spectator).thenComparing(Entry::name);
        var order = Comparator.comparingInt((Entry entry) -> TabOrder.priority(entry.team())).thenComparing(vanilla);
        var entries = List.of(new Entry("03_drifters", false, "A"), new Entry("02_gang", false, "B"),
            new Entry("01_mod", false, "C"), new Entry("00_owner", true, "Z"));
        assertEquals(List.of("00_owner", "01_mod", "02_gang", "03_drifters"),
            entries.stream().sorted(order).map(Entry::team).toList());
        assertEquals(4, TabOrder.priority("unrelated"));
    }
}
