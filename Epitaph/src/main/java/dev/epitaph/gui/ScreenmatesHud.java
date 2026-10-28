package dev.epitaph.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.epitaph.EpitaphClient;
import dev.epitaph.config.EpitaphConfig;
import dev.epitaph.config.EpitaphConfig.Side;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws BoyKisser and Mikey above the hotbar.
 *
 * BoyKisser animation pipeline (see tools/gif_to_frames.py):
 *   textures/gui/screenmates/boykisser/frame_000.png ... frame_NNN.png + animation.json
 *   {"frames": N, "frametime_ms": 80}
 * If animation.json / frames are present they are played frame by frame. If only the single still
 * boykisser.png exists, a procedural "dance" (bounce + sway) is applied so it still moves.
 */
public final class ScreenmatesHud {
    private record Frame(Identifier id, int w, int h) { }

    private static final String NS = EpitaphClient.MOD_ID;
    private static final Identifier BK_STILL = Identifier.of(NS, "textures/gui/screenmates/boykisser.png");
    private static final Identifier MIKEY = Identifier.of(NS, "textures/gui/screenmates/mikey.png");
    private static final Identifier BK_META = Identifier.of(NS, "textures/gui/screenmates/boykisser/animation.json");

    private static boolean loaded;
    private static final List<Frame> bkFrames = new ArrayList<>();
    private static int bkFrameMs = 80;
    private static boolean bkIsRealAnimation;
    private static Frame mikey;

    private ScreenmatesHud() { }

    /** Called after resource reloads so newly dropped-in frames are picked up. */
    public static void invalidate() { loaded = false; }

    private static void load(ResourceManager rm) {
        loaded = true;
        bkFrames.clear();
        bkIsRealAnimation = false;
        mikey = probe(rm, MIKEY);
        if (mikey == null) EpitaphClient.LOGGER.warn("[Epitaph] Screenmate asset missing: {} (Mikey will not be drawn)", MIKEY);

        Optional<Resource> meta = rm.getResource(BK_META);
        if (meta.isPresent()) {
            try (InputStream in = meta.get().getInputStream()) {
                JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                int n = o.get("frames").getAsInt();
                if (o.has("frametime_ms")) bkFrameMs = Math.max(16, o.get("frametime_ms").getAsInt());
                for (int i = 0; i < n; i++) {
                    Frame f = probe(rm, Identifier.of(NS, String.format("textures/gui/screenmates/boykisser/frame_%03d.png", i)));
                    if (f == null) { EpitaphClient.LOGGER.warn("[Epitaph] BoyKisser frame {} missing; animation truncated", i); break; }
                    bkFrames.add(f);
                }
                bkIsRealAnimation = bkFrames.size() > 1;
            } catch (Exception e) {
                EpitaphClient.LOGGER.warn("[Epitaph] Bad BoyKisser animation.json ({}); falling back to still image", e.toString());
                bkFrames.clear();
            }
        }
        if (bkFrames.isEmpty()) {
            Frame still = probe(rm, BK_STILL);
            if (still != null) bkFrames.add(still);
            else EpitaphClient.LOGGER.warn("[Epitaph] Screenmate asset missing: {} (BoyKisser will not be drawn)", BK_STILL);
        }
        EpitaphClient.LOGGER.info("[Epitaph] Screenmates loaded: BoyKisser frames={} (real animation={}), Mikey={}",
                bkFrames.size(), bkIsRealAnimation, mikey != null);
    }

    private static Frame probe(ResourceManager rm, Identifier id) {
        Optional<Resource> r = rm.getResource(id);
        if (r.isEmpty()) return null;
        try (InputStream in = r.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
            return new Frame(id, img.getWidth(), img.getHeight());
        } catch (Exception e) {
            EpitaphClient.LOGGER.warn("[Epitaph] Could not read texture {}: {}", id, e.toString());
            return null;
        }
    }

    public static void render(DrawContext ctx, MinecraftClient client) {
        EpitaphConfig c = EpitaphConfig.INSTANCE;
        if (!c.masterEnabled || !c.screenmatesEnabled || client.player == null || client.options.hudHidden) return;
        if (c.hideScreenmatesInChat && client.currentScreen != null) return;
        if (!loaded) load(client.getResourceManager());

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        double t = System.currentTimeMillis() / 1000.0 * c.animationSpeed;

        if (c.boykisserEnabled && !bkFrames.isEmpty()) {
            Frame f;
            double bob = 0, sway = 0;
            if (bkIsRealAnimation && c.boykisserAnimated) {
                int idx = (int) ((long) (System.currentTimeMillis() * c.animationSpeed) / bkFrameMs % bkFrames.size());
                f = bkFrames.get(idx);
            } else {
                f = bkFrames.get(0);
                if (c.boykisserAnimated) { // procedural dance for the single still
                    bob = Math.abs(Math.sin(t * Math.PI * 1.8)) * 5.0 * c.screenmateScale;
                    sway = Math.sin(t * Math.PI * 1.8) * 2.5 * c.screenmateScale;
                }
            }
            draw(ctx, f, 46, c.boykisserSide, c.boykisserX + sway, c.boykisserY - bob, c, sw, sh);
        }
        if (c.mikeyEnabled && mikey != null) {
            draw(ctx, mikey, 70, c.mikeySide, c.mikeyX, c.mikeyY, c, sw, sh);
        }
    }

    private static void draw(DrawContext ctx, Frame f, int baseHeight, Side side, double ox, double oy,
                             EpitaphConfig c, int sw, int sh) {
        double h = baseHeight * c.screenmateScale;
        double w = h * f.w() / f.h();
        // The hotbar is 182px wide and centred; the status bars sit ~10-20px above it.
        double hotbarLeft = sw / 2.0 - 91, hotbarRight = sw / 2.0 + 91;
        double x = switch (side) {
            case LEFT -> hotbarLeft;
            case CENTER -> sw / 2.0 - w / 2.0;
            case RIGHT -> hotbarRight - w;
        };
        double bottom = sh - 22 - 30; // above hotbar + armour/health row
        int ix = (int) Math.round(x + ox), iy = (int) Math.round(bottom - h + oy);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, f.id(), ix, iy, 0f, 0f,
                (int) Math.round(w), (int) Math.round(h), f.w(), f.h(), f.w(), f.h());
    }
}
