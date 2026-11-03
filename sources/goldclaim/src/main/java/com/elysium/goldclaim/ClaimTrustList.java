package com.elysium.goldclaim;

import com.elysium.goldclaim.data.Claim;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

final class ClaimTrustList {
    record Entry(String display, Claim.TrustLevel level, boolean unresolved) {}

    static Claim.TrustLevel publicLevel(Claim claim) {
        if (claim.managerAll) return Claim.TrustLevel.MANAGER;
        if (claim.trustAll) return Claim.TrustLevel.FULL;
        if (claim.interactAll) return Claim.TrustLevel.INTERACT;
        return null;
    }

    static List<Entry> entries(Claim claim, Function<UUID, String> resolveName) {
        Map<String, Claim.TrustLevel> uuidGrants = new HashMap<>();
        Map<String, Claim.TrustLevel> nameGrants = new HashMap<>();
        addGrants(uuidGrants, claim.interactPlayers, Claim.TrustLevel.INTERACT);
        addGrants(uuidGrants, claim.trustedPlayers, Claim.TrustLevel.FULL);
        addGrants(uuidGrants, claim.managerPlayers, Claim.TrustLevel.MANAGER);
        addGrants(nameGrants, claim.interactPlayerNames, Claim.TrustLevel.INTERACT);
        addGrants(nameGrants, claim.trustedPlayerNames, Claim.TrustLevel.FULL);
        addGrants(nameGrants, claim.managerPlayerNames, Claim.TrustLevel.MANAGER);

        Claim.TrustLevel publicLevel = publicLevel(claim);
        Set<String> matchedNames = new HashSet<>();
        List<Entry> result = new ArrayList<>();
        for (var grant : uuidGrants.entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(grant.getKey());
                if (!uuid.toString().equals(grant.getKey())) {
                    throw new IllegalArgumentException("Non-canonical UUID");
                }
            } catch (IllegalArgumentException invalid) {
                result.add(new Entry(grant.getKey() + " (invalid UUID; stored grant inactive)", grant.getValue(), true));
                continue;
            }
            String name = resolveName.apply(uuid);
            Claim.TrustLevel level = strongest(grant.getValue(), publicLevel);
            if (name == null || name.isBlank()) {
                result.add(new Entry(uuid.toString(), level, true));
                continue;
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            level = strongest(level, nameGrants.get(normalized));
            matchedNames.add(normalized);
            result.add(new Entry(name + " (" + uuid + ")", level, false));
        }
        for (var grant : nameGrants.entrySet()) {
            if (!grant.getKey().equals(grant.getKey().toLowerCase(Locale.ROOT))) {
                result.add(new Entry(grant.getKey() + " (invalid stored name; grant inactive)", grant.getValue(), true));
                continue;
            }
            if (!matchedNames.contains(grant.getKey())) {
                result.add(new Entry(grant.getKey(), strongest(grant.getValue(), publicLevel), false));
            }
        }
        result.sort(Comparator.comparing(Entry::display, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::display));
        return List.copyOf(result);
    }

    private static void addGrants(Map<String, Claim.TrustLevel> grants, Set<String> values,
                                  Claim.TrustLevel level) {
        for (String value : Objects.requireNonNull(values, "Missing claim trust set")) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Claim trust contains an empty player identifier");
            }
            grants.merge(value, level, ClaimTrustList::strongest);
        }
    }

    private static Claim.TrustLevel strongest(Claim.TrustLevel first, Claim.TrustLevel second) {
        if (first == null) return second;
        if (second == null) return first;
        return priority(first) >= priority(second) ? first : second;
    }

    private static int priority(Claim.TrustLevel level) {
        return switch (level) {
            case INTERACT -> 1;
            case FULL -> 2;
            case MANAGER -> 3;
        };
    }

    private ClaimTrustList() {}
}
