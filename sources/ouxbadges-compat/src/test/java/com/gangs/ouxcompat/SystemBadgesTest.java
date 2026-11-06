package com.gangs.ouxcompat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.Test;

public class SystemBadgesTest {
    @Test
    public void badgesAfkAndReturnWithEitherTabState() {
        for (String display : new String[]{"[Owner] ~Oux", "[AFK] [Owner] ~Oux", "~Oux", "[AFK] ~Oux"}) {
            for (String suffix : new String[]{" is now AFK.", " is no longer AFK."}) {
                Text message = Text.literal("[Owner] ~Oux" + suffix);
                assertEquals("\uE000 ~Oux" + suffix,
                    SystemBadges.decorate(message, 0, "Oux_y", display).getString());
            }
        }
    }

    @Test
    public void badgesAllRanksAndUnnicknamedPlayers() {
        String[] labels = {"Owner", "Mod", "Gang", "Drifter"};
        for (int badge = 0; badge < labels.length; badge++) {
            String subject = "[" + labels[badge] + "] Steve";
            Text message = Text.literal(subject + " is now AFK.");
            assertEquals(OuxBadgeKeys.glyph(badge) + " Steve is now AFK.",
                SystemBadges.decorate(message, badge, "Steve", subject).getString());
            assertEquals(OuxBadgeKeys.glyph(badge) + " Steve is now AFK.",
                SystemBadges.decorate(message, badge, "Steve", null).getString());
        }
    }

    @Test
    public void preservesStylesWhenLabelIsSplitAcrossSegments() {
        MutableText message = Text.empty()
            .append(Text.literal("[Ow").formatted(Formatting.GOLD))
            .append(Text.literal("ner]").formatted(Formatting.YELLOW))
            .append(Text.literal(" ~Oux").formatted(Formatting.AQUA))
            .append(Text.literal(" is now AFK.").formatted(Formatting.ITALIC));
        Text result = SystemBadges.decorate(message, 0, "Oux_y", "[Owner] ~Oux");

        assertEquals("\uE000 ~Oux is now AFK.", result.getString());
        assertEquals(OuxBadgeKeys.glyphText(0).getStyle().getFont(),
            result.getSiblings().get(0).getStyle().getFont());
        assertEquals((int)Formatting.AQUA.getColorValue(), result.getSiblings().get(1).getStyle().getColor().getRgb());
        assertEquals(true, result.getSiblings().get(2).getStyle().isItalic());
        assertEquals(Style.EMPTY.getColor(), result.getSiblings().get(0).getStyle().getColor());
    }

    @Test
    public void ignoresOtherPlayersWrongRanksPartialNamesAndExistingBadges() {
        for (String plain : new String[]{
            "[Owner] ~Oux2 is now AFK.",
            "[Owner] ~Oux Someone is now AFK.",
            "[Mod] ~Oux is now AFK.",
            "[Owner] ~Someone is no longer AFK.",
            "\uE000 ~Oux is now AFK.",
            "[Owner] Oux_y2 joined the game."
        }) {
            Text message = Text.literal(plain);
            assertSame(message, SystemBadges.decorate(message, 0, "Oux_y", "[Owner] ~Oux"));
        }
    }

    @Test
    public void replacesTheMatchedLabelNotAnEarlierUnrelatedLabel() {
        Text message = Text.literal("[Owner] Other saw [Owner] Oux_y join.");
        assertEquals("[Owner] Other saw \uE000 Oux_y join.",
            SystemBadges.decorate(message, 0, "Oux_y", "[Owner] ~Oux").getString());
    }
}
