package com.gangs.ouxcompat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.Test;

public class PlayerNameplateTest {
    @Test
    public void usesNicknameAndPreservesBadgeAndNameStyle() {
        Text original = Text.literal("\uE000 Oux_y");
        Text display = Text.literal("[Owner]").formatted(Formatting.GOLD)
            .append(Text.literal(" ~Oux").formatted(Formatting.AQUA));
        Text result = ChatBadges.nameplate(original, display, 0);

        assertEquals("\uE000 ~Oux", result.getString());
        assertEquals(OuxBadgeKeys.glyphText(0).getStyle().getFont(),
            result.getSiblings().get(0).getStyle().getFont());
        assertEquals((int)Formatting.AQUA.getColorValue(), result.getSiblings().get(1).getStyle().getColor().getRgb());
    }

    @Test
    public void handlesAfkUnnicknamedAndUnbadgedPlayers() {
        Text original = Text.literal("Steve");
        assertEquals("[AFK] \uE001 ~Steve",
            ChatBadges.nameplate(original, Text.literal("[AFK] [Mod] ~Steve"), 1).getString());
        assertEquals("\uE003 Steve",
            ChatBadges.nameplate(original, Text.literal("[Drifter] Steve"), 3).getString());
        assertEquals("\uE002 ~Steve",
            ChatBadges.nameplate(original, Text.literal("~Steve"), 2).getString());
        Text noBadge = Text.literal("~Steve");
        assertSame(noBadge, ChatBadges.nameplate(original, noBadge, -1));
        assertSame(original, ChatBadges.nameplate(original, null, 0));
    }
}
