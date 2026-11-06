package com.gangs.ouxcompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/** Swaps a rank's text label (e.g. {@code [Owner]}) for its badge image, keeping every other segment's style. */
public final class BadgeText {
    private record Segment(Style style, String text) {
    }

    private BadgeText() {
    }

    /**
     * Replaces the first {@code [Label]} with the badge glyph. Without a label, the glyph goes before the first of
     * {@code names} found, or nowhere if {@code requireLabel} is set or no name is found.
     */
    public static Text apply(Text text, int badge, List<String> names, boolean requireLabel) {
        return apply(text, badge, names, requireLabel, false);
    }

    /** As above; with {@code prepend}, a text with neither label nor name gets the glyph at the start. */
    public static Text apply(Text text, int badge, List<String> names, boolean requireLabel, boolean prepend) {
        return apply(text, badge, names, requireLabel, prepend, -1);
    }

    static Text replaceLabelAt(Text text, int badge, int labelAt) {
        return apply(text, badge, List.of(), true, false, labelAt);
    }

    private static Text apply(Text text, int badge, List<String> names, boolean requireLabel, boolean prepend,
            int labelAt) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder full = new StringBuilder();
        text.visit((style, part) -> {
            segments.add(new Segment(style, part));
            full.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        String plain = full.toString();
        if (OuxBadgeKeys.hasBadge(plain)) return text;

        String label = "[" + OuxBadgeKeys.label(badge) + "]";
        int at = labelAt >= 0 ? labelAt : plain.indexOf(label);
        if (labelAt >= 0 && !plain.startsWith(label, labelAt)) return text;
        int removeEnd = at;
        boolean addSpace = false;
        if (at >= 0) {
            removeEnd = at + label.length();
        } else if (requireLabel) {
            return text;
        } else {
            at = firstName(plain, names);
            if (at < 0 && prepend) at = 0;
            if (at < 0) return text;
            removeEnd = at;
            addSpace = true;
        }

        MutableText result = Text.empty();
        int position = 0;
        boolean inserted = false;
        for (Segment segment : segments) {
            String part = segment.text();
            int end = position + part.length();
            for (int i = position; i < end; ) {
                if (!inserted && i == at) {
                    insert(result, badge, addSpace);
                    inserted = true;
                }
                if (i >= at && i < removeEnd) {
                    i = Math.min(end, removeEnd);
                    continue;
                }
                int stop = end;
                if (!inserted && at > i) stop = Math.min(stop, at);
                result.append(Text.literal(part.substring(i - position, stop - position)).setStyle(segment.style()));
                i = stop;
            }
            position = end;
        }
        if (!inserted) insert(result, badge, addSpace);
        return result;
    }

    private static void insert(MutableText result, int badge, boolean addSpace) {
        result.append(OuxBadgeKeys.glyphText(badge));
        if (addSpace) result.append(Text.literal(" "));
    }

    private static int firstName(String plain, List<String> names) {
        int best = -1;
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            int index = plain.indexOf(name);
            if (index >= 0 && (best < 0 || index < best)) best = index;
        }
        return best;
    }
}
