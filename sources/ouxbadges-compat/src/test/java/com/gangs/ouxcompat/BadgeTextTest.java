package com.gangs.ouxcompat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.junit.Test;

public class BadgeTextTest {
    private static final Identifier BADGE_FONT = new Identifier(CompatMixinPlugin.OUX_FONT_NAMESPACE, "badges");

    private static List<Object[]> segments(Text text) {
        List<Object[]> result = new ArrayList<>();
        text.visit((style, part) -> {
            result.add(new Object[]{style, part});
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static Style styleOf(Text text, String part) {
        for (Object[] segment : segments(text)) {
            if (segment[1].equals(part)) return (Style)segment[0];
        }
        throw new AssertionError("missing segment " + part);
    }

    /** Styled Chat shape: "<" + gradient "[Owner]" + " ~Oux" + "> " + message, all as nested siblings. */
    @Test
    public void replacesStyledChatLabelWithoutDuplicatingText() {
        MutableText name = Text.empty()
            .append(Text.literal("[Owner]").formatted(Formatting.GOLD))
            .append(Text.literal(" ~Oux").formatted(Formatting.AQUA));
        MutableText message = Text.literal("<").append(name).append(Text.literal("> ")).append(Text.literal("nope").formatted(Formatting.ITALIC));
        Text result = BadgeText.apply(message, 0, List.of("~Oux", "Oux_y"), false);

        assertEquals("<\uE000 ~Oux> nope", result.getString());
        assertEquals(BADGE_FONT, styleOf(result, "\uE000").getFont());
        assertEquals((int)Formatting.AQUA.getColorValue(), styleOf(result, " ~Oux").getColor().getRgb());
        assertEquals(true, styleOf(result, "nope").isItalic());
    }

    @Test
    public void insertsBeforeNameWhenThereIsNoLabel() {
        Text message = Text.translatable("%s", Text.literal("<Oux_y> hi"));
        assertEquals("<\uE001 Oux_y> hi", BadgeText.apply(Text.literal("<Oux_y> hi"), 1, List.of("~Oux", "Oux_y"), false).getString());
        assertEquals("<\uE001 Oux_y> hi", BadgeText.apply(message, 1, List.of("Oux_y"), false).getString());
    }

    @Test
    public void leavesUnrelatedAndAlreadyBadgedTextAlone() {
        Text unrelated = Text.literal("<Steve> hi");
        assertSame(unrelated, BadgeText.apply(unrelated, 0, List.of("Oux_y"), false));
        assertSame(unrelated, BadgeText.apply(unrelated, 0, List.of("Steve"), true));
        Text badged = Text.literal("<\uE000 Oux_y> hi");
        assertSame(badged, BadgeText.apply(badged, 0, List.of("Oux_y"), false));
    }

    @Test
    public void tabNamesGetLabelReplacedOrBadgePrepended() {
        assertEquals("\uE003 ~Oux", BadgeText.apply(Text.literal("[Drifter] ~Oux"), 3, List.of(), false, true).getString());
        assertEquals("\uE002 ~Oux", BadgeText.apply(Text.literal("~Oux"), 2, List.of(), false, true).getString());
    }
}
