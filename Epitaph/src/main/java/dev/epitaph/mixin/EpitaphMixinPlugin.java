package dev.epitaph.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Safety valve: the held-item mixin is skipped when epitaph.json has "enableHeldItemMixin": false
 * or the JVM is started with -Depitaph.disableMixins=true. Useful if a future Minecraft update
 * changes the target method and the game fails to start.
 */
public final class EpitaphMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (System.getProperty("epitaph.disableMixins") != null) return false;
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("epitaph.json");
            if (Files.exists(p)) {
                JsonElement root = JsonParser.parseString(Files.readString(p));
                if (root.isJsonObject() && root.getAsJsonObject().has("enableHeldItemMixin")) {
                    return root.getAsJsonObject().get("enableHeldItemMixin").getAsBoolean();
                }
            }
        } catch (Exception ignored) { /* malformed config -> default: apply */ }
        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode c, String m, IMixinInfo i) { }
    @Override public void postApply(String t, ClassNode c, String m, IMixinInfo i) { }
}
