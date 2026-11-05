package com.gangs.ouxcompat.mixin;

import com.gangs.ouxcompat.CompatMixinPlugin;
import com.gangs.ouxcompat.OuxBadgeKeys;
import net.minecraft.client.font.BitmapFont;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.io.IOException;
import java.io.InputStream;

/** Oux Badges' images are ~480x200; glyphs that large do not fit the 256px font atlas and render as empty boxes. */
@Mixin(BitmapFont.Loader.class)
public abstract class BitmapFontLoaderMixin {
    @Shadow @Final private Identifier file;
    @Shadow @Final private int[][] codepointGrid;

    @Redirect(method = "load", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/texture/NativeImage;read(Lnet/minecraft/client/texture/NativeImage$Format;Ljava/io/InputStream;)Lnet/minecraft/client/texture/NativeImage;"))
    private NativeImage gangsOux$shrinkBadge(NativeImage.Format format, InputStream stream) throws IOException {
        NativeImage image = NativeImage.read(format, stream);
        if (!file.getNamespace().equals(CompatMixinPlugin.OUX_FONT_NAMESPACE)
                || codepointGrid.length != 1 || codepointGrid[0].length != 1) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] size = OuxBadgeKeys.shrunkSize(width, height);
        if (size == null) return image;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) pixels[y * width + x] = image.getColor(x, y);
        }
        int[] shrunk = OuxBadgeKeys.shrink(pixels, width, height, size[0], size[1]);
        NativeImage result = new NativeImage(size[0], size[1], false);
        for (int y = 0; y < size[1]; y++) {
            for (int x = 0; x < size[0]; x++) result.setColor(x, y, shrunk[y * size[0] + x]);
        }
        image.close();
        return result;
    }
}
