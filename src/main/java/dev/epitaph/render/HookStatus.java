package dev.epitaph.render;

/** Plain holder for state set by mixins (mixin classes themselves may not contain public static fields). */
public final class HookStatus {
    public static volatile boolean heldItemHookRan = false;
    private HookStatus() { }
}
