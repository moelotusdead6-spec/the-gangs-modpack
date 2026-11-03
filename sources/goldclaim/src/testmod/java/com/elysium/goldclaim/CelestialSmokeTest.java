package com.elysium.goldclaim;

import dev.corgitaco.enhancedcelestials.EnhancedCelestials;
import dev.corgitaco.enhancedcelestials.api.EnhancedCelestialsRegistry;
import dev.corgitaco.enhancedcelestials.lunarevent.EnhancedCelestialsLunarForecastWorldData;
import dev.corgitaco.enhancedcelestials.lunarevent.LunarEventInstance;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

final class CelestialSmokeTest {
    static void verify(MinecraftServer server) throws ReflectiveOperationException {
        ServerWorld rsw = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(GoldClaimMod.RSW_DIMENSION)));
        require(rsw != null, "RSW was not created");
        var data = EnhancedCelestials.lunarForecastWorldData(rsw).orElseThrow(
            () -> new IllegalStateException("RSW has no native celestial forecast"));
        var wild = EnhancedCelestials.lunarForecastWorldData(server.getOverworld()).orElseThrow();
        Set<String> expectedRsw = Set.of("blood_moon", "super_blood_moon", "blue_moon", "super_blue_moon",
            "harvest_moon", "super_harvest_moon", "super_moon");
        require(events(data).equals(expectedRsw), "Wrong RSW lunar events: " + events(data));
        require(events(wild).equals(Set.of("blue_moon", "super_blue_moon", "harvest_moon",
            "super_harvest_moon", "super_moon")), "Wild lunar events changed: " + events(wild));
        var blood = RegistryKey.of(EnhancedCelestialsRegistry.LUNAR_EVENT_KEY,
            new Identifier("enhancedcelestials:blood_moon"));
        var wildBefore = wild.save();
        wild.addEventToForecast(0, new LunarEventInstance(blood, wild.getCurrentDay() + 1));
        require(CelestialPolicy.updateForecast(server.getOverworld(), false), "Wild forecast missing");
        require(wild.save().equals(wildBefore), "Wild migration did not preserve non-blood forecast/history exactly");
        data.removeFromForecastIf(event -> true);
        data.addEventToForecast(new LunarEventInstance(blood, data.getCurrentDay() + 1));
        require(CelestialPolicy.updateForecast(rsw, true), "RSW forecast missing");
        require(data.eventsByDay().keySet().stream().anyMatch(key -> !key.getValue().getPath().contains("blood")),
            "RSW migration retained the stale blood-only forecast");
        rsw.setWeather(0, 0, false, false);
        rsw.setTimeOfDay(data.getCurrentDay() * 24000L + 18000L);
        rsw.calculateAmbientDarkness();
        for (String event : expectedRsw) {
            var key = RegistryKey.of(EnhancedCelestialsRegistry.LUNAR_EVENT_KEY,
                new Identifier("enhancedcelestials", event));
            data.recomputeForecast();
            data.setLunarEvent(key);
            data.tick();
            data.tick();
            require(data.currentLunarEventHolder().matchesKey(key), "RSW did not activate " + event);
            if (event.contains("blood")) {
                double expected = event.startsWith("super_") ? 4.5 : 2.25;
                require(data.currentLunarEvent().getSpawnMultiplierForMonsterCategory(SpawnGroup.MONSTER) == expected,
                    "RSW blood moon lost its monster multiplier");
            }
        }
        System.out.println("PASS: native RSW forecast contains all seven lunar events; Wild retains five non-blood events; "
            + "every RSW moon activates and both blood-moon monster multipliers are intact.");
    }

    private static Set<String> events(EnhancedCelestialsLunarForecastWorldData data)
            throws ReflectiveOperationException {
        var field = EnhancedCelestialsLunarForecastWorldData.class.getDeclaredField("lunarEventSpawnRequirements");
        field.setAccessible(true);
        var map = (Map<?, ?>)field.get(data);
        Set<String> names = new HashSet<>();
        for (Object key : map.keySet()) {
            if (!(key instanceof RegistryEntry<?> entry)) throw new IllegalStateException("Invalid lunar event holder");
            names.add(entry.getKey().orElseThrow().getValue().getPath());
        }
        return names;
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }
}
