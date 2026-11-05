package com.elysium.goldclaim;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankServiceTest {
    @Test
    public void onlyTheStatueRosterReceivesGangAndStaffAlwaysTakePrecedence() {
        assertEquals(10, RankService.GANG.size());
        for (String name : RankService.GANG) {
            assertEquals(RankService.Rank.GANG, RankService.rank(name.toUpperCase(java.util.Locale.ROOT), false, false));
            assertEquals(RankService.Rank.OWNER, RankService.rank(name, true, true));
            assertEquals(RankService.Rank.MOD, RankService.rank(name, false, true));
        }
        assertEquals(RankService.Rank.DRIFTERS, RankService.rank("NewPlayer", false, false));
        assertEquals(RankService.Rank.OWNER, RankService.rank("Oux_y", false, false));
        assertEquals(RankService.Rank.MOD, RankService.rank("Rootamiss", false, false));
    }

    @Test
    public void badgeTeamsSortInExactRankOrder() {
        List<String> teams = java.util.Arrays.stream(RankService.Rank.values()).map(RankService.Rank::team).toList();
        assertEquals(List.of("oux_badges_1_owner", "oux_badges_2_mod", "oux_badges_3_gang", "oux_badges_4_drifter"), teams);
        assertEquals(teams, teams.stream().sorted().toList());
        assertEquals("oux.badges.4_drifter", RankService.Rank.DRIFTERS.permission());
    }

    @Test
    public void promotionTracksReplacePlayerWithoutDuplicatesOrLosingOtherGroups() {
        assertEquals(List.of("default", "drifters", "mod", "owner"),
            RankService.trackGroups(List.of("default", "player", "drifters", "mod", "owner")));
    }

    @Test
    public void mergingExistingDriftersNeverIntroducesAnInheritanceCycle() {
        assertThrows(IllegalStateException.class, () -> RankService.validateInheritance(
            java.util.Map.of("drifters", java.util.Set.of("member"), "member", java.util.Set.of("drifters")),
            "drifters", new java.util.HashSet<>()));
        RankService.validateInheritance(java.util.Map.of("drifters", java.util.Set.of("default"),
            "default", java.util.Set.of()), "drifters", new java.util.HashSet<>());
    }
}
