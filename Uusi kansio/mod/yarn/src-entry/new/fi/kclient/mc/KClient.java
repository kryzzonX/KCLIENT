package fi.kclient.mc;

import fi.kclient.Crosshair;
import fi.kclient.Hud;
import fi.kclient.KConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Minecraft 1.21.9 - 1.21.11: uusi näppäinkategoria ja HUD-rekisteri. */
public class KClient implements ClientModInitializer {
    public static KeyBinding menuKey;
    public static KeyBinding zoomKey;
    private static final YarnGame GAME = new YarnGame();
    private static Method hudMethod;

    @Override
    public void onInitializeClient() {
        KConfig.load();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("kclient", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.kclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.kclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, cat));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new KClickGuiScreen());
            }
            Hud.tick(GAME);
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("kclient", "hud"),
            (ctx, tickCounter) -> Hud.render(new YarnGfx(ctx), GAME));

        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (ctx, tickCounter) -> {
            if (KConfig.on("crosshair")) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.options.getPerspective().isFirstPerson()) {
                    Crosshair.draw(new YarnGfx(ctx), mc.getWindow().getScaledWidth() / 2, mc.getWindow().getScaledHeight() / 2);
                }
            } else {
                callOriginal(original, ctx, tickCounter);
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player == MinecraftClient.getInstance().player && entity instanceof LivingEntity le && le.hurtTime == 0) Hud.combo++;
            return ActionResult.PASS;
        });
    }

    /** HudElementin ainoa metodi kutsutaan nimestä riippumatta. */
    private static void callOriginal(Object original, Object ctx, Object tickCounter) {
        try {
            if (hudMethod == null) {
                for (Method m : HudElement.class.getMethods()) {
                    if (Modifier.isAbstract(m.getModifiers())) { hudMethod = m; break; }
                }
            }
            if (hudMethod != null) hudMethod.invoke(original, ctx, tickCounter);
        } catch (Exception ignored) {}
    }
}
