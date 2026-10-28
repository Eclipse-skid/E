package dev.epitaph.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.epitaph.EpitaphClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Epitaph settings. Stored as {@code config/epitaph.json}.
 *
 * Loading is field-by-field: a missing field keeps its default, a field with the
 * wrong type is skipped (default kept), and a file that is not valid JSON at all is
 * moved aside to {@code epitaph.json.broken} and replaced by defaults. Loading never throws.
 */
public final class EpitaphConfig {
    public enum Side {
        LEFT, CENTER, RIGHT;
        public Side next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum HotbarStyle {
        VANILLA("Vanilla"), DARK("Dark glass"), VIOLET("Violet glass");
        public final String label;
        HotbarStyle(String label) { this.label = label; }
        public HotbarStyle next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum Accent {
        VIOLET("Violet", 0xA66CFF), PURPLE("Purple", 0x7B2CBF), MAGENTA("Magenta", 0xD05CFF), CYAN("Cyan", 0x5CE1FF);
        public final String label;
        public final int rgb;
        Accent(String label, int rgb) { this.label = label; this.rgb = rgb; }
        public Accent next() { return values()[(ordinal() + 1) % values().length]; }
    }

    // ---------------- General ----------------
    public boolean masterEnabled = true;

    // ---------------- Screenmates ----------------
    public boolean screenmatesEnabled = true;
    public boolean boykisserEnabled = true;
    public boolean mikeyEnabled = true;
    public double screenmateScale = 1.0;          // 0.3 .. 3.0
    public Side boykisserSide = Side.LEFT;
    public Side mikeySide = Side.RIGHT;
    public double boykisserX = 0, boykisserY = 0;  // extra pixel offsets, -300 .. 300
    public double mikeyX = 0, mikeyY = 0;
    public boolean boykisserAnimated = true;
    public double animationSpeed = 1.0;            // 0.25 .. 3.0

    // ---------------- Item textures (client resource packs) ----------------
    public boolean shieldTexture = true;
    public boolean totemTexture = true;
    public boolean swordTexture = true;
    public boolean axeTexture = true;
    public boolean maceTexture = true;
    public boolean spearTexture = true;

    // ---------------- Held item rendering (first person) ----------------
    public boolean heldItemsEnabled = true;
    public double globalScale = 1.0;               // 0.25 .. 2.0
    public double shieldScale = 1.0;
    public double totemScale = 1.0;
    public double maceScale = 1.0;
    public double axeScale = 1.0;
    public double swordScale = 1.0;
    public double spearScale = 1.0;
    public double otherScale = 1.0;
    public double offsetX = 0, offsetY = 0, offsetZ = 0;                  // -1.0 .. 1.0
    public double shieldOffsetX = 0, shieldOffsetY = 0, shieldOffsetZ = 0;
    /** Extra item ids treated as "spear". Anything whose path ends in _spear is always included. */
    public List<String> spearItemIds = new ArrayList<>(List.of("minecraft:trident"));

    // ---------------- HUD ----------------
    public HotbarStyle hotbarStyle = HotbarStyle.DARK;
    public boolean selectedSlotHighlight = true;
    public boolean hideScreenmatesInChat = false;

    // ---------------- Appearance ----------------
    public Accent accent = Accent.VIOLET;
    public boolean visualEffects = false;

    // ---------------- Advanced ----------------
    /** Set to false to stop the held-item mixin from being applied at all (safety valve). */
    public boolean enableHeldItemMixin = true;

    public static EpitaphConfig INSTANCE = new EpitaphConfig();
    private static boolean dirty;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("epitaph.json");
    }

    public static void markDirty() { dirty = true; }
    public static boolean isDirty() { return dirty; }

    public static void load() { INSTANCE = loadFrom(path()); dirty = false; }

    public static void save() { saveTo(path(), INSTANCE); dirty = false; }

    public static void resetToDefaults() { INSTANCE = new EpitaphConfig(); dirty = true; }

    // Package-visible / public so they can be exercised without Minecraft running.
    public static EpitaphConfig loadFrom(Path file) {
        EpitaphConfig cfg = new EpitaphConfig();
        try {
            if (!Files.exists(file)) {
                saveTo(file, cfg);
                return cfg;
            }
            JsonObject obj;
            try {
                JsonElement root = JsonParser.parseString(Files.readString(file));
                if (!root.isJsonObject()) throw new IllegalStateException("root is not a JSON object");
                obj = root.getAsJsonObject();
            } catch (Exception bad) {
                EpitaphClient.LOGGER.warn("[Epitaph] Config is malformed ({}). Using defaults; old file kept as epitaph.json.broken", bad.getMessage());
                try { Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING); }
                catch (Exception ignored) { }
                saveTo(file, cfg);
                return cfg;
            }
            for (Field f : EpitaphConfig.class.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || Modifier.isTransient(m) || !Modifier.isPublic(m) || !obj.has(f.getName())) continue;
                try {
                    Object v = GSON.fromJson(obj.get(f.getName()), f.getGenericType());
                    if (v != null) f.set(cfg, v);
                } catch (Exception e) {
                    EpitaphClient.LOGGER.warn("[Epitaph] Ignoring bad config value '{}' ({}); keeping default", f.getName(), e.getMessage());
                }
            }
            cfg.sanitize();
            return cfg;
        } catch (Exception e) {
            EpitaphClient.LOGGER.warn("[Epitaph] Could not read config ({}). Using defaults.", e.toString());
            return new EpitaphConfig();
        }
    }

    public static void saveTo(Path file, EpitaphConfig cfg) {
        try {
            cfg.sanitize();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(cfg));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            EpitaphClient.LOGGER.warn("[Epitaph] Could not save config: {}", e.toString());
        }
    }

    /** Clamp everything to a safe range and replace nulls (enums/lists can be null if the JSON said null). */
    public void sanitize() {
        screenmateScale = clamp(screenmateScale, 0.3, 3.0);
        animationSpeed = clamp(animationSpeed, 0.25, 3.0);
        boykisserX = clamp(boykisserX, -300, 300); boykisserY = clamp(boykisserY, -300, 300);
        mikeyX = clamp(mikeyX, -300, 300);         mikeyY = clamp(mikeyY, -300, 300);
        globalScale = clamp(globalScale, 0.25, 2.0);
        shieldScale = clamp(shieldScale, 0.25, 2.0); totemScale = clamp(totemScale, 0.25, 2.0);
        maceScale = clamp(maceScale, 0.25, 2.0);     axeScale = clamp(axeScale, 0.25, 2.0);
        swordScale = clamp(swordScale, 0.25, 2.0);   spearScale = clamp(spearScale, 0.25, 2.0);
        otherScale = clamp(otherScale, 0.25, 2.0);
        offsetX = clamp(offsetX, -1, 1); offsetY = clamp(offsetY, -1, 1); offsetZ = clamp(offsetZ, -1, 1);
        shieldOffsetX = clamp(shieldOffsetX, -1, 1); shieldOffsetY = clamp(shieldOffsetY, -1, 1); shieldOffsetZ = clamp(shieldOffsetZ, -1, 1);
        if (boykisserSide == null) boykisserSide = Side.LEFT;
        if (mikeySide == null) mikeySide = Side.RIGHT;
        if (hotbarStyle == null) hotbarStyle = HotbarStyle.DARK;
        if (accent == null) accent = Accent.VIOLET;
        if (spearItemIds == null) spearItemIds = new ArrayList<>();
        spearItemIds.removeIf(s -> s == null || s.isBlank());
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return Math.max(lo, Math.min(hi, 0 < lo ? lo : 0));
        return Math.max(lo, Math.min(hi, v));
    }
}
