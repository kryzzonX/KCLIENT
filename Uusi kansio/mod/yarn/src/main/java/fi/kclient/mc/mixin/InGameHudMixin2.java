package fi.kclient.mc.mixin;

import fi.kclient.Crosshair;
import fi.kclient.KConfig;
import fi.kclient.mc.YarnGfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sama kuin InGameHudMixin, mutta versioille, joissa renderCrosshair ottaa vain DrawContextin. Vain toinen näistä sopii kulloiseenkin versioon. */
@Mixin(InGameHud.class)
public class InGameHudMixin2 {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void kclient$crosshairOld(DrawContext context, CallbackInfo ci) {
        if (!KConfig.on("crosshair")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.getPerspective().isFirstPerson()) {
            Crosshair.draw(new YarnGfx(context), mc.getWindow().getScaledWidth() / 2, mc.getWindow().getScaledHeight() / 2);
        }
        ci.cancel();
    }
}
