package com.gangs.ouxcompat;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Oux Badges 6.0.0's tab-list mixin shifts the player name drawn with {@code DrawContext.drawText(..., boolean)}, but
 * Minecraft 1.20.1's {@code PlayerListHud.render} draws it with {@code drawTextWithShadow(...)}. Its injector finds no
 * target and the game crashes when the tab list class loads. Before any mixin is applied to {@code PlayerListHud}, this
 * rewrites that call to the equivalent {@code drawText(..., true)}, so the Oux injector finds its target unchanged.
 */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
    public static final String OUX_ID = "oux-badges-hiec7dbm";
    public static final String OUX_FONT_NAMESPACE = OUX_ID;
    static final String BROKEN_OUX_VERSION = "6.0.0";
    private static final Pattern CLASS = Pattern.compile("L(net/minecraft/class_\\d+(?:\\$class_\\d+)*);");

    private boolean patchTabList;
    private String playerListHud;
    private String drawContext;
    private String shadowName;
    private String shadowDescriptor;
    private String drawTextName;
    private String drawTextDescriptor;

    @Override
    public void onLoad(String mixinPackage) {
        patchTabList = FabricLoader.getInstance().getModContainer(OUX_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals(BROKEN_OUX_VERSION))
            .orElse(false);
        MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();
        playerListHud = className(mappings, "net/minecraft/class_355");
        drawContext = className(mappings, "net/minecraft/class_332");
        String shadow = "(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;III)I";
        String text = "(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;IIIZ)I";
        shadowName = mappings.mapMethodName("intermediary", "net.minecraft.class_332", "method_27535", shadow);
        drawTextName = mappings.mapMethodName("intermediary", "net.minecraft.class_332", "method_51439", text);
        shadowDescriptor = descriptor(mappings, shadow);
        drawTextDescriptor = descriptor(mappings, text);
    }

    private static String className(MappingResolver mappings, String intermediary) {
        return mappings.mapClassName("intermediary", intermediary.replace('/', '.')).replace('.', '/');
    }

    private static String descriptor(MappingResolver mappings, String intermediary) {
        Matcher matcher = CLASS.matcher(intermediary);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("L" + className(mappings, matcher.group(1)) + ";"));
        }
        return matcher.appendTail(result).toString();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!patchTabList || !targetClass.name.equals(playerListHud)) return;
        for (MethodNode method : targetClass.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals(drawContext) && call.name.equals(shadowName) && call.desc.equals(shadowDescriptor)) {
                    method.instructions.insertBefore(call, new InsnNode(Opcodes.ICONST_1));
                    call.name = drawTextName;
                    call.desc = drawTextDescriptor;
                }
            }
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
