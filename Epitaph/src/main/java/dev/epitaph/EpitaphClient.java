package dev.epitaph;

import dev.epitaph.config.EpitaphConfig;
import dev.epitaph.gui.EpitaphScreen;
import dev.epitaph.gui.HudOverlay;
import dev.epitaph.resource.TexturePacks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EpitaphClient implements ClientModInitializer {
    public static final String MOD_ID = "epitaph";
    public static final Logger LOGGER = LoggerFactory.getLogger("Epitaph");

    private static boolean comboWasDown;

    @Override
    public void onInitializeClient() {
        EpitaphConfig.load();
        TexturePacks.registerAll();

        // Hotbar tint is drawn BEFORE the vanilla hotbar, highlight + screenmates AFTER it.
        HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR,
                Identifier.of(MOD_ID, "hotbar_style"), (ctx, tick) -> {
                    HudOverlay.renderHotbarStyle(ctx);
                    pollOpenKey();
                });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.of(MOD_ID, "overlay"), (ctx, tick) -> HudOverlay.renderOverlay(ctx));

        // Also polled every tick (covers the case where the HUD is hidden).
        ClientTickEvents.END_CLIENT_TICK.register(client -> pollOpenKey());

        // Make the enabled texture packs match the config once resource packs are known.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> TexturePacks.syncWithConfig(client, false));
        LOGGER.info("[Epitaph] initialised. Press LEFT SHIFT + ENTER in game to open the menu.");
    }

    /** LEFT SHIFT + ENTER, edge-triggered. Called per frame (HUD) and per tick so quick presses are not missed. */
    private static void pollOpenKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        long handle = client.getWindow().getHandle();
        boolean shift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        boolean enter = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;
        boolean combo = shift && enter;
        if (combo && !comboWasDown && client.currentScreen == null && client.player != null) {
            client.setScreen(new EpitaphScreen());
        }
        comboWasDown = combo;
    }
}
