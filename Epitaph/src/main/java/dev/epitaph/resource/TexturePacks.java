package dev.epitaph.resource;

import dev.epitaph.EpitaphClient;
import dev.epitaph.config.EpitaphConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The custom item textures ship as six small built-in resource packs inside the jar
 * (resourcepacks/epitaph_shield, _totem, _swords, _axes, _maces, _spears). Each one is a normal
 * client-side resource pack, so nothing here touches server behaviour. The Epitaph menu simply
 * switches those packs on/off, which is how every texture category gets an independent toggle.
 *
 * Registration goes through Fabric API. The class that offers it has been renamed between Fabric API
 * versions (ResourceManagerHelper -> ResourceLoader), so it is looked up by reflection and both
 * spellings are tried; a clear log line says which one worked.
 */
public final class TexturePacks {
    /** pack name -> "is it enabled in the config?" */
    private static final Map<String, Predicate<EpitaphConfig>> PACKS = new LinkedHashMap<>();
    static {
        PACKS.put("epitaph_shield", c -> c.shieldTexture);
        PACKS.put("epitaph_totem", c -> c.totemTexture);
        PACKS.put("epitaph_swords", c -> c.swordTexture);
        PACKS.put("epitaph_axes", c -> c.axeTexture);
        PACKS.put("epitaph_maces", c -> c.maceTexture);
        PACKS.put("epitaph_spears", c -> c.spearTexture);
    }

    private static boolean registered;

    private TexturePacks() { }

    public static void registerAll() {
        ModContainer mod = FabricLoader.getInstance().getModContainer(EpitaphClient.MOD_ID).orElse(null);
        if (mod == null) { EpitaphClient.LOGGER.warn("[Epitaph] Mod container missing; item textures unavailable."); return; }
        int ok = 0;
        for (String name : PACKS.keySet()) if (register(mod, name)) ok++;
        registered = ok > 0;
        if (ok != PACKS.size()) {
            EpitaphClient.LOGGER.warn("[Epitaph] Registered {}/{} built-in texture packs. Item textures may be missing; "
                    + "you can still enable them manually in Options > Resource Packs.", ok, PACKS.size());
        } else {
            EpitaphClient.LOGGER.info("[Epitaph] Registered {} built-in texture packs.", ok);
        }
    }

    private static boolean register(ModContainer mod, String name) {
        Identifier id = Identifier.of(EpitaphClient.MOD_ID, name);
        Text title = Text.literal("Epitaph: " + name.substring("epitaph_".length()));
        String[] classes = {"net.fabricmc.fabric.api.resource.v1.ResourceLoader", "net.fabricmc.fabric.api.resource.ResourceManagerHelper"};
        for (String cn : classes) {
            try {
                Class<?> cls = Class.forName(cn);
                for (Method m : cls.getMethods()) {
                    if (!m.getName().startsWith("registerBuiltin")) continue;
                    Class<?>[] p = m.getParameterTypes();
                    boolean withTitle = p.length == 4 && p[2].isAssignableFrom(Text.class);
                    boolean plain = p.length == 3;
                    if (!(withTitle || plain) || p[0] != Identifier.class || !p[1].isAssignableFrom(ModContainer.class)) continue;
                    Class<?> typeCls = p[p.length - 1];
                    if (!typeCls.isEnum()) continue;
                    Object type = enumConst(typeCls, "DEFAULT_ENABLED");
                    if (type == null) continue;
                    Object target = null;
                    if (!Modifier.isStatic(m.getModifiers())) target = instanceFor(cls);
                    if (!Modifier.isStatic(m.getModifiers()) && target == null) continue;
                    Object r = withTitle ? m.invoke(target, id, mod, title, type) : m.invoke(target, id, mod, type);
                    if (r instanceof Boolean b && !b) continue;
                    return true;
                }
            } catch (ClassNotFoundException ignored) {
                // try next spelling
            } catch (Throwable t) {
                EpitaphClient.LOGGER.warn("[Epitaph] Pack registration via {} failed: {}", cn, t.toString());
            }
        }
        EpitaphClient.LOGGER.warn("[Epitaph] Could not register built-in pack {} (no compatible Fabric API entry point found).", name);
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConst(Class<?> cls, String n) {
        try { return Enum.valueOf((Class) cls, n); } catch (Exception e) { return null; }
    }

    /** ResourceLoader.get(ResourceType.CLIENT_RESOURCES) style accessor, if the API is instance based. */
    private static Object instanceFor(Class<?> cls) {
        try {
            for (Method g : cls.getMethods()) {
                if (!g.getName().equals("get") || !Modifier.isStatic(g.getModifiers()) || g.getParameterCount() != 1) continue;
                Class<?> pt = g.getParameterTypes()[0];
                if (pt == net.minecraft.resource.ResourceType.class) return g.invoke(null, net.minecraft.resource.ResourceType.CLIENT_RESOURCES);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /**
     * Enable/disable the Epitaph packs so they match the config. Reloads resources only if something changed.
     * @return true when a reload was started
     */
    public static boolean syncWithConfig(MinecraftClient client, boolean force) {
        if (client == null || !registered) return false;
        try {
            ResourcePackManager mgr = client.getResourcePackManager();
            EpitaphConfig cfg = EpitaphConfig.INSTANCE;
            List<String> enabled = new ArrayList<>(mgr.getEnabledIds());
            boolean changed = false;
            for (ResourcePackProfile profile : mgr.getProfiles()) {
                String pid = profile.getId();
                for (Map.Entry<String, Predicate<EpitaphConfig>> e : PACKS.entrySet()) {
                    if (!pid.contains(e.getKey())) continue;
                    boolean want = cfg.masterEnabled && e.getValue().test(cfg);
                    boolean has = enabled.contains(pid);
                    if (want && !has) { enabled.add(pid); changed = true; }
                    if (!want && has) { enabled.remove(pid); changed = true; }
                }
            }
            if (changed) {
                mgr.setEnabledProfiles(enabled);
            }
            if (changed || force) {
                client.reloadResources();
                return true;
            }
        } catch (Throwable t) {
            EpitaphClient.LOGGER.warn("[Epitaph] Could not apply texture pack settings: {}", t.toString());
        }
        return false;
    }
}
