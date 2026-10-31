package fi.kclient.mc;

import com.mojang.blaze3d.platform.InputConstants;
import fi.kclient.Crosshair;
import fi.kclient.Hud;
import fi.kclient.KConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class KClient implements ClientModInitializer {
    public static KeyMapping menuKey;
    public static KeyMapping zoomKey;
    private static final MojGame GAME = new MojGame();
    private static Method hudMethod;

    @Override
    public void onInitializeClient() {
        KConfig.load();
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("kclient", "main"));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.kclient.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, cat));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.kclient.zoom", InputConstants.Type.KEYSYM, InputConstants.KEY_C, cat));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) open(client, new KClickGuiScreen());
            Hud.tick(GAME);
        });

        // HUD-elementti ei piirry, kun pelin HUD on piilotettu.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("kclient", "hud"),
            (graphics, tracker) -> {
                GAME.frame();
                Hud.render(new MojGfx(graphics), GAME);
            });

        // Oma tähtäin korvaa pelin tähtäimen vain, kun se on päällä.
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, tracker) -> {
            if (KConfig.on("crosshair")) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options.getCameraType().isFirstPerson()) {
                    Crosshair.draw(new MojGfx(graphics), mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2);
                }
            } else {
                callOriginal(original, graphics, tracker);
            }
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player == Minecraft.getInstance().player && entity instanceof LivingEntity le && le.hurtTime == 0) Hud.combo++;
            return InteractionResult.PASS;
        });
    }

    /** HudElementin ainoa metodi haetaan nimellä, jotta sen mahdollinen uudelleennimeäminen ei riko käännöstä. */
    private static void callOriginal(Object original, Object graphics, Object tracker) {
        try {
            if (hudMethod == null) {
                for (Method m : HudElement.class.getMethods()) {
                    if (Modifier.isAbstract(m.getModifiers())) { hudMethod = m; break; }
                }
            }
            if (hudMethod != null) hudMethod.invoke(original, graphics, tracker);
        } catch (Exception ignored) {}
    }

    /** Näytön avaus haetaan nimellä, koska metodi on nimetty eri tavalla eri 26.x-versioissa. */
    private static void open(Minecraft mc, Screen screen) {
        for (String name : new String[]{"setScreen", "setScreenAndShow"}) {
            try {
                Method m = Minecraft.class.getMethod(name, Screen.class);
                m.invoke(mc, screen);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                return;
            }
        }
    }
}
