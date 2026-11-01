package fi.kclient.mc;

import fi.kclient.Hud;
import fi.kclient.KConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public class KClient implements ClientModInitializer {
    public static KeyBinding menuKey;
    public static KeyBinding zoomKey;
    private static final YarnGame GAME = new YarnGame();

    @Override
    public void onInitializeClient() {
        KConfig.load();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.kclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.kclient"));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.kclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.kclient"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new KClickGuiScreen());
            }
            Hud.tick(GAME);
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
            Hud.render(new YarnGfx(ctx), GAME);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && entity instanceof LivingEntity le && le.hurtTime == 0) Hud.combo++;
            return ActionResult.PASS;
        });
    }
}
