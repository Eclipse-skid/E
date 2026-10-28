package dev.epitaph.gui;

import dev.epitaph.config.EpitaphConfig;
import dev.epitaph.render.ItemCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/** Hotbar tint, selected-slot highlight, item visual effect and the Screenmates. */
public final class HudOverlay {
    private HudOverlay() { }

    public static int argb(int alpha, int rgb) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    private static boolean visible(MinecraftClient c) {
        EpitaphConfig cfg = EpitaphConfig.INSTANCE;
        return cfg.masterEnabled && c.player != null && !c.options.hudHidden;
    }

    /** Drawn before the vanilla hotbar so the tint shows through its translucent slots. */
    public static void renderHotbarStyle(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!visible(client)) return;
        EpitaphConfig cfg = EpitaphConfig.INSTANCE;
        int sw = client.getWindow().getScaledWidth(), sh = client.getWindow().getScaledHeight();
        int x = sw / 2 - 91, y = sh - 22;
        switch (cfg.hotbarStyle) {
            case DARK -> ctx.fill(x, y, x + 182, y + 22, argb(0xB0, 0x07030F));
            case VIOLET -> ctx.fill(x, y, x + 182, y + 22, argb(0xB0, 0x2A0F4D));
            default -> { }
        }
    }

    /** Drawn after the vanilla hotbar. */
    public static void renderOverlay(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!visible(client)) return;
        EpitaphConfig cfg = EpitaphConfig.INSTANCE;
        int sw = client.getWindow().getScaledWidth(), sh = client.getWindow().getScaledHeight();
        double time = System.currentTimeMillis() / 1000.0;

        if (cfg.selectedSlotHighlight || cfg.visualEffects) {
            int slot = client.player.getInventory().getSelectedSlot();
            int x = sw / 2 - 91 + slot * 20, y = sh - 22;
            int rgb = cfg.accent.rgb;
            if (cfg.selectedSlotHighlight) {
                int a = 0xE0;
                outline(ctx, x - 1, y - 1, x + 23, y + 23, argb(a, rgb));
            }
            if (cfg.visualEffects) {
                ItemStack held = client.player.getMainHandStack();
                boolean special = !held.isEmpty()
                        && ItemCategory.classify(Registries.ITEM.getId(held.getItem()).toString(), cfg) != ItemCategory.OTHER;
                double pulse = 0.5 + 0.5 * Math.sin(time * 3.0);
                int a = (int) ((special ? 0x70 : 0x30) + pulse * (special ? 0x70 : 0x30));
                // soft glow: two expanding rings plus an underline sweep
                outline(ctx, x - 3, y - 3, x + 25, y + 25, argb(a / 2, rgb));
                outline(ctx, x - 2, y - 2, x + 24, y + 24, argb(a, rgb));
                int sweep = (int) ((time * 40) % 24);
                ctx.fill(x + Math.max(0, sweep - 6), y + 21, x + Math.min(22, sweep), y + 22, argb(0xF0, 0xFFFFFF));
            }
        }
        ScreenmatesHud.render(ctx, client);
    }

    private static void outline(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }
}
