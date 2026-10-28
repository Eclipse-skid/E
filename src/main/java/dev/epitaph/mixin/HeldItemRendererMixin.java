package dev.epitaph.mixin;

import dev.epitaph.config.EpitaphConfig;
import dev.epitaph.render.ItemCategory;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Epitaph's first-person offset + scale around the held item. Client only, visual only.
 *
 * Target: HeldItemRenderer#renderFirstPersonItem (the method that positions and draws the item in
 * each hand). The render-queue parameter is @Coerce'd to Object so the handler stays valid whether
 * that parameter is a VertexConsumerProvider (older) or an OrderedRenderCommandQueue (1.21.9+).
 * require = 0: if the method cannot be found the game still starts and the menu reports the hook as inactive.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    /** Set to true the first time the hook runs; shown in the Items tab. */
    public static volatile boolean hookActive = false;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), require = 0)
    private void epitaph$begin(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
                               float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                               @Coerce Object queue, int light, CallbackInfo ci) {
        hookActive = true;
        matrices.push(); // always pushed; popped at RETURN so the stack stays balanced
        EpitaphConfig c = EpitaphConfig.INSTANCE;
        if (!c.masterEnabled || !c.heldItemsEnabled || item == null || item.isEmpty()) return;

        ItemCategory cat = ItemCategory.classify(Registries.ITEM.getId(item.getItem()).toString(), c);
        double s = cat.scale(c);
        double ox = c.offsetX, oy = c.offsetY, oz = c.offsetZ;
        if (cat == ItemCategory.SHIELD) { ox += c.shieldOffsetX; oy += c.shieldOffsetY; oz += c.shieldOffsetZ; }

        matrices.translate(ox, oy, oz);
        if (Math.abs(s - 1.0) > 1.0E-4) {
            // Scale about roughly where the hand holds the item, so it grows/shrinks in place
            // instead of drifting toward the screen centre.
            Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            double side = arm == Arm.RIGHT ? 1.0 : -1.0;
            double px = 0.56 * side, py = -0.52, pz = -0.72;
            matrices.translate(px, py, pz);
            matrices.scale((float) s, (float) s, (float) s);
            matrices.translate(-px, -py, -pz);
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"), require = 0)
    private void epitaph$end(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
                             float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                             @Coerce Object queue, int light, CallbackInfo ci) {
        matrices.pop();
    }
}
