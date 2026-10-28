package dev.epitaph.gui;

import dev.epitaph.config.EpitaphConfig;
import dev.epitaph.mixin.HeldItemRendererMixin;
import dev.epitaph.resource.TexturePacks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** The EPITAPH configuration menu (LEFT SHIFT + ENTER). Dark / purple, tabbed, scrollable. */
public final class EpitaphScreen extends Screen {
    private static final String[] TABS = {"General", "Screenmates", "Items", "Position", "HUD", "Appearance"};
    private static int lastTab = 0;

    private interface Cell { ClickableWidget make(int x, int y, int w); }

    /** One line of the content list: a header, a note, one full-width cell, or up to two cells side by side. */
    private record Row(String header, String note, Cell left, Cell right, boolean full, int height) {
        static Row header(String t) { return new Row(t, null, null, null, false, 16); }
        static Row note(String t) { return new Row(null, t, null, null, false, 12); }
        static Row full(Cell c) { return new Row(null, null, c, null, true, 24); }
        static Row pair(Cell a, Cell b) { return new Row(null, null, a, b, false, 24); }
    }

    private int tab = lastTab;
    private int scroll = 0;
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private final List<Row> rows = new ArrayList<>();
    private boolean packsDirty;
    private long savedAt;

    public EpitaphScreen() { super(Text.literal("EPITAPH")); }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        panelW = Math.min(480, width - 16);
        panelH = Math.min(310, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        contentX = panelX + 8 + 92 + 8;
        contentY = panelY + 44;
        contentW = panelX + panelW - 14 - contentX;
        contentH = panelY + panelH - 36 - contentY;
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        rows.clear();
        buildRows();
        int total = totalHeight();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - contentH)));

        // tab buttons
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            MutableText label = Text.literal(TABS[i]);
            if (i == tab) label = label.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD);
            addDrawableChild(ButtonWidget.builder(label, b -> { tab = idx; lastTab = idx; scroll = 0; rebuild(); })
                    .dimensions(panelX + 8, contentY + i * 24, 92, 20).build());
        }

        // footer
        int fy = panelY + panelH - 28, fw = (panelW - 16 - 16) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Save Settings"), b -> saveNow())
                .dimensions(panelX + 8, fy, fw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset to Defaults"), b -> {
            EpitaphConfig.resetToDefaults();
            packsDirty = true;
            rebuild();
        }).dimensions(panelX + 8 + fw + 8, fy, fw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + 8 + 2 * (fw + 8), fy, fw, 20).build());

        // visible content widgets
        int y = contentY - scroll;
        int colW = (contentW - 6) / 2;
        for (Row r : rows) {
            if (r.left() != null && y >= contentY && y + 20 <= contentY + contentH) {
                if (r.full()) addDrawableChild(r.left().make(contentX, y, contentW));
                else {
                    addDrawableChild(r.left().make(contentX, y, colW));
                    if (r.right() != null) addDrawableChild(r.right().make(contentX + colW + 6, y, colW));
                }
            }
            y += r.height();
        }
    }

    private int totalHeight() {
        int h = 0;
        for (Row r : rows) h += r.height();
        return h;
    }

    private void saveNow() {
        EpitaphConfig.save();
        if (packsDirty) { TexturePacks.syncWithConfig(client, false); ScreenmatesHud.invalidate(); packsDirty = false; }
        savedAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ content

    private void buildRows() {
        EpitaphConfig c = EpitaphConfig.INSTANCE;
        switch (tab) {
            case 0 -> { // General
                rows.add(Row.header("EPITAPH"));
                rows.add(Row.full(toggle("Epitaph (everything)", () -> c.masterEnabled, v -> { c.masterEnabled = v; packsDirty = true; })));
                rows.add(Row.note("Turns every Epitaph visual on or off at once."));
                rows.add(Row.header("Quick switches"));
                rows.add(Row.pair(toggle("Screenmates", () -> c.screenmatesEnabled, v -> c.screenmatesEnabled = v),
                        toggle("Held-item tweaks", () -> c.heldItemsEnabled, v -> c.heldItemsEnabled = v)));
                rows.add(Row.pair(toggle("Slot highlight", () -> c.selectedSlotHighlight, v -> c.selectedSlotHighlight = v),
                        toggle("Visual effects", () -> c.visualEffects, v -> c.visualEffects = v)));
                rows.add(Row.header("Settings file"));
                rows.add(Row.note("config/epitaph.json  (saved automatically when you close this menu)"));
                rows.add(Row.note("Open this menu any time with LEFT SHIFT + ENTER."));
            }
            case 1 -> { // Screenmates
                rows.add(Row.header("Screenmates"));
                rows.add(Row.pair(toggle("All Screenmates", () -> c.screenmatesEnabled, v -> c.screenmatesEnabled = v),
                        slider("Size", 0.3, 3.0, 0.05, "%.2fx", () -> c.screenmateScale, v -> c.screenmateScale = v)));
                rows.add(Row.header("BoyKisser"));
                rows.add(Row.pair(toggle("BoyKisser", () -> c.boykisserEnabled, v -> c.boykisserEnabled = v),
                        cycle("Side", () -> c.boykisserSide.name(), () -> c.boykisserSide = c.boykisserSide.next())));
                rows.add(Row.pair(slider("Horizontal", -300, 300, 1, "%.0f px", () -> c.boykisserX, v -> c.boykisserX = v),
                        slider("Vertical", -300, 300, 1, "%.0f px", () -> c.boykisserY, v -> c.boykisserY = v)));
                rows.add(Row.pair(toggle("Animate", () -> c.boykisserAnimated, v -> c.boykisserAnimated = v),
                        slider("Anim speed", 0.25, 3.0, 0.05, "%.2fx", () -> c.animationSpeed, v -> c.animationSpeed = v)));
                rows.add(Row.header("Mikey"));
                rows.add(Row.pair(toggle("Mikey", () -> c.mikeyEnabled, v -> c.mikeyEnabled = v),
                        cycle("Side", () -> c.mikeySide.name(), () -> c.mikeySide = c.mikeySide.next())));
                rows.add(Row.pair(slider("Horizontal", -300, 300, 1, "%.0f px", () -> c.mikeyX, v -> c.mikeyX = v),
                        slider("Vertical", -300, 300, 1, "%.0f px", () -> c.mikeyY, v -> c.mikeyY = v)));
                rows.add(Row.header("Visibility"));
                rows.add(Row.full(toggle("Hide while a menu/chat is open", () -> c.hideScreenmatesInChat, v -> c.hideScreenmatesInChat = v)));
                rows.add(Row.note("Screenmates also hide with F1 (hide HUD)."));
            }
            case 2 -> { // Items
                rows.add(Row.header("Custom textures (resource packs, client side)"));
                rows.add(Row.pair(toggle("Shield", () -> c.shieldTexture, v -> { c.shieldTexture = v; packsDirty = true; }),
                        toggle("Totem of Undying", () -> c.totemTexture, v -> { c.totemTexture = v; packsDirty = true; })));
                rows.add(Row.pair(toggle("Swords", () -> c.swordTexture, v -> { c.swordTexture = v; packsDirty = true; }),
                        toggle("Axes", () -> c.axeTexture, v -> { c.axeTexture = v; packsDirty = true; })));
                rows.add(Row.pair(toggle("Mace", () -> c.maceTexture, v -> { c.maceTexture = v; packsDirty = true; }),
                        toggle("Spears", () -> c.spearTexture, v -> { c.spearTexture = v; packsDirty = true; })));
                rows.add(Row.note("Texture changes apply (resources reload) when you press Save Settings or Done."));
                rows.add(Row.header("First-person item scale"));
                rows.add(Row.pair(toggle("Held-item tweaks", () -> c.heldItemsEnabled, v -> c.heldItemsEnabled = v),
                        slider("Global scale", 0.25, 2.0, 0.05, "%.2fx", () -> c.globalScale, v -> c.globalScale = v)));
                rows.add(Row.pair(slider("Shield", 0.25, 2.0, 0.05, "%.2fx", () -> c.shieldScale, v -> c.shieldScale = v),
                        slider("Totem", 0.25, 2.0, 0.05, "%.2fx", () -> c.totemScale, v -> c.totemScale = v)));
                rows.add(Row.pair(slider("Mace", 0.25, 2.0, 0.05, "%.2fx", () -> c.maceScale, v -> c.maceScale = v),
                        slider("Axe", 0.25, 2.0, 0.05, "%.2fx", () -> c.axeScale, v -> c.axeScale = v)));
                rows.add(Row.pair(slider("Sword", 0.25, 2.0, 0.05, "%.2fx", () -> c.swordScale, v -> c.swordScale = v),
                        slider("Spear", 0.25, 2.0, 0.05, "%.2fx", () -> c.spearScale, v -> c.spearScale = v)));
                rows.add(Row.pair(slider("Other items", 0.25, 2.0, 0.05, "%.2fx", () -> c.otherScale, v -> c.otherScale = v),
                        button("Reset scales", () -> {
                            c.globalScale = c.shieldScale = c.totemScale = c.maceScale = c.axeScale = c.swordScale = c.spearScale = c.otherScale = 1.0;
                            EpitaphConfig.markDirty(); rebuild();
                        })));
                rows.add(Row.header("Spear mods"));
                rows.add(Row.note("Any item id ending in _spear counts as a spear. Add other ids to"));
                rows.add(Row.note("\"spearItemIds\" in config/epitaph.json (e.g. \"mymod:glaive\")."));
                rows.add(Row.note(HeldItemRendererMixin.hookActive
                        ? "Held-item hook: ACTIVE"
                        : "Held-item hook: not triggered yet (look at a held item in first person)"));
            }
            case 3 -> { // Position
                rows.add(Row.header("First-person position (all held items)"));
                rows.add(Row.pair(slider("Horizontal", -1, 1, 0.01, "%.2f", () -> c.offsetX, v -> c.offsetX = v),
                        slider("Vertical", -1, 1, 0.01, "%.2f", () -> c.offsetY, v -> c.offsetY = v)));
                rows.add(Row.pair(slider("Depth", -1, 1, 0.01, "%.2f", () -> c.offsetZ, v -> c.offsetZ = v),
                        toggle("Held-item tweaks", () -> c.heldItemsEnabled, v -> c.heldItemsEnabled = v)));
                rows.add(Row.header("Shield (added on top)"));
                rows.add(Row.pair(slider("Horizontal", -1, 1, 0.01, "%.2f", () -> c.shieldOffsetX, v -> c.shieldOffsetX = v),
                        slider("Vertical", -1, 1, 0.01, "%.2f", () -> c.shieldOffsetY, v -> c.shieldOffsetY = v)));
                rows.add(Row.pair(slider("Depth", -1, 1, 0.01, "%.2f", () -> c.shieldOffsetZ, v -> c.shieldOffsetZ = v),
                        button("Reset Item Position", () -> {
                            c.offsetX = c.offsetY = c.offsetZ = 0;
                            c.shieldOffsetX = c.shieldOffsetY = c.shieldOffsetZ = 0;
                            EpitaphConfig.markDirty(); rebuild();
                        })));
                rows.add(Row.note("Positive depth moves items away from the camera. Visual only."));
            }
            case 4 -> { // HUD
                rows.add(Row.header("Hotbar"));
                rows.add(Row.pair(cycle("Hotbar", () -> c.hotbarStyle.label, () -> c.hotbarStyle = c.hotbarStyle.next()),
                        toggle("Slot highlight", () -> c.selectedSlotHighlight, v -> c.selectedSlotHighlight = v)));
                rows.add(Row.header("Screenmates placement"));
                rows.add(Row.pair(cycle("BoyKisser side", () -> c.boykisserSide.name(), () -> c.boykisserSide = c.boykisserSide.next()),
                        cycle("Mikey side", () -> c.mikeySide.name(), () -> c.mikeySide = c.mikeySide.next())));
                rows.add(Row.pair(toggle("Screenmates", () -> c.screenmatesEnabled, v -> c.screenmatesEnabled = v),
                        toggle("Hide in menus", () -> c.hideScreenmatesInChat, v -> c.hideScreenmatesInChat = v)));
                rows.add(Row.note("The whole HUD layer respects F1 (hide HUD)."));
            }
            case 5 -> { // Appearance
                rows.add(Row.header("Accent"));
                rows.add(Row.pair(cycle("Accent colour", () -> c.accent.label, () -> c.accent = c.accent.next()),
                        toggle("Visual effects", () -> c.visualEffects, v -> c.visualEffects = v)));
                rows.add(Row.note("Accent colours the slot highlight and effects."));
                rows.add(Row.note("Visual effects: pulsing glow, stronger when holding a custom item."));
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ widget factories

    private Cell toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {
        return (x, y, w) -> {
            ButtonWidget b = ButtonWidget.builder(onOff(name, get.getAsBoolean()), btn -> {
                set.accept(!get.getAsBoolean());
                EpitaphConfig.markDirty();
                btn.setMessage(onOff(name, get.getAsBoolean()));
            }).dimensions(x, y, w, 20).build();
            return b;
        };
    }

    private static Text onOff(String name, boolean on) {
        return Text.literal(name + ": ").append(on
                ? Text.literal("ON").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                : Text.literal("OFF").formatted(Formatting.RED));
    }

    private Cell cycle(String name, Supplier<String> get, Runnable next) {
        return (x, y, w) -> ButtonWidget.builder(Text.literal(name + ": " + get.get()), btn -> {
            next.run();
            EpitaphConfig.markDirty();
            btn.setMessage(Text.literal(name + ": " + get.get()));
        }).dimensions(x, y, w, 20).build();
    }

    private Cell button(String name, Runnable action) {
        return (x, y, w) -> ButtonWidget.builder(Text.literal(name), b -> action.run()).dimensions(x, y, w, 20).build();
    }

    private Cell slider(String name, double min, double max, double step, String fmt, DoubleSupplier get, DoubleConsumer set) {
        return (x, y, w) -> new Slider(x, y, w, name, min, max, step, fmt, get, set);
    }

    private static final class Slider extends SliderWidget {
        private final String name, fmt;
        private final double min, max, step;
        private final DoubleConsumer set;

        Slider(int x, int y, int w, String name, double min, double max, double step, String fmt,
               DoubleSupplier get, DoubleConsumer set) {
            super(x, y, w, 20, Text.empty(), 0.0);
            this.name = name; this.fmt = fmt; this.min = min; this.max = max; this.step = step; this.set = set;
            this.value = Math.max(0, Math.min(1, (get.getAsDouble() - min) / (max - min)));
            updateMessage();
        }

        private double current() {
            double v = min + value * (max - min);
            v = Math.round(v / step) * step;
            return Math.max(min, Math.min(max, v));
        }

        @Override protected void updateMessage() {
            if (name == null) return; // called from the super constructor before fields exist
            setMessage(Text.literal(name + ": " + String.format(Locale.ROOT, fmt, current())));
        }

        @Override protected void applyValue() {
            set.accept(current());
            EpitaphConfig.markDirty();
        }
    }

    // ------------------------------------------------------------------ rendering

    /** Custom background; deliberately does not call super (vanilla blur can only run once per frame). */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int accent = HudOverlay.argb(0xFF, EpitaphConfig.INSTANCE.accent.rgb);
        ctx.fill(0, 0, width, height, 0xCC05020B);
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, HudOverlay.argb(0xA0, EpitaphConfig.INSTANCE.accent.rgb));
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFA0C0714);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 36, 0xFF150A26);
        ctx.fill(panelX, panelY + 36, panelX + panelW, panelY + 37, accent);
        ctx.fill(panelX + 4, contentY - 4, panelX + 104, panelY + panelH - 34, 0x66000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // background + widgets

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("EPITAPH").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), width / 2, panelY + 8, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("visual client  |  1.0-SNAPSHOT  |  MC 1.21.11").formatted(Formatting.DARK_GRAY), width / 2, panelY + 22, 0xFFFFFFFF);

        // selected tab marker
        ctx.fill(panelX + 4, contentY + tab * 24, panelX + 6, contentY + tab * 24 + 20,
                HudOverlay.argb(0xFF, EpitaphConfig.INSTANCE.accent.rgb));

        // headers and notes
        int y = contentY - scroll;
        for (Row r : rows) {
            if (y >= contentY - 2 && y + r.height() <= contentY + contentH + 2) {
                if (r.header() != null) {
                    ctx.drawTextWithShadow(textRenderer, Text.literal(r.header().toUpperCase(Locale.ROOT)).formatted(Formatting.LIGHT_PURPLE),
                            contentX, y + 4, 0xFFFFFFFF);
                    ctx.fill(contentX, y + 14, contentX + contentW, y + 15, 0x55A66CFF);
                } else if (r.note() != null) {
                    ctx.drawTextWithShadow(textRenderer, Text.literal(r.note()).formatted(Formatting.GRAY), contentX, y + 2, 0xFFFFFFFF);
                }
            }
            y += r.height();
        }

        // scrollbar
        int total = totalHeight();
        if (total > contentH) {
            int trackX = contentX + contentW + 4;
            int barH = Math.max(16, contentH * contentH / total);
            int barY = contentY + (int) ((long) (contentH - barH) * scroll / Math.max(1, total - contentH));
            ctx.fill(trackX, contentY, trackX + 3, contentY + contentH, 0x44000000);
            ctx.fill(trackX, barY, trackX + 3, barY + barH, HudOverlay.argb(0xFF, EpitaphConfig.INSTANCE.accent.rgb));
        }

        // footer status
        String status = System.currentTimeMillis() - savedAt < 1800 ? "Saved." : (EpitaphConfig.isDirty() ? "Unsaved changes (saved on close)" : "");
        if (!status.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status).formatted(Formatting.GRAY), width / 2, panelY + panelH - 40 + 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= contentX && mouseX <= contentX + contentW + 8 && mouseY >= contentY && mouseY <= contentY + contentH) {
            int max = Math.max(0, totalHeight() - contentH);
            int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount) * 24));
            if (next != scroll) { scroll = next; rebuild(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean shouldPause() { return false; }

    /** Runs whenever the screen closes (Done, Escape, ...): persist + apply texture packs. */
    @Override
    public void removed() {
        EpitaphConfig.save();
        if (packsDirty) { TexturePacks.syncWithConfig(client, false); ScreenmatesHud.invalidate(); }
        super.removed();
    }
}
