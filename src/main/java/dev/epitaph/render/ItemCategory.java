package dev.epitaph.render;

import dev.epitaph.config.EpitaphConfig;

/**
 * Classifies an item by its registry id. Pure string logic so it works for modded items too
 * (e.g. a spear mod: add its id to {@code spearItemIds} in epitaph.json, or any id ending in
 * "_spear" is picked up automatically).
 */
public enum ItemCategory {
    SHIELD, TOTEM, MACE, AXE, SWORD, SPEAR, OTHER;

    /** @param id full registry id like "minecraft:iron_sword" */
    public static ItemCategory classify(String id, EpitaphConfig cfg) {
        if (id == null) return OTHER;
        if (cfg.spearItemIds != null && cfg.spearItemIds.contains(id)) return SPEAR;
        String path = id.substring(id.indexOf(':') + 1);
        if (path.contains("shield")) return SHIELD;
        if (path.equals("totem_of_undying")) return TOTEM;
        if (path.equals("mace")) return MACE;
        if (path.endsWith("spear") || path.equals("trident")) return SPEAR;
        if (path.endsWith("_axe") || path.equals("axe")) return AXE;
        if (path.endsWith("_sword") || path.equals("sword")) return SWORD;
        return OTHER;
    }

    public double scale(EpitaphConfig c) {
        double s = switch (this) {
            case SHIELD -> c.shieldScale;
            case TOTEM -> c.totemScale;
            case MACE -> c.maceScale;
            case AXE -> c.axeScale;
            case SWORD -> c.swordScale;
            case SPEAR -> c.spearScale;
            case OTHER -> c.otherScale;
        };
        return s * c.globalScale;
    }
}
