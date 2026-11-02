package fi.kclient.mc;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Zoom-näppäin luetaan myös suoraan, jotta toisen modin samaan näppäimeen tekemä sidonta ei estä sitä. */
final class KeyPoll {
    private KeyPoll() {}

    static boolean down(KeyBinding kb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return false;
        if (kb.isPressed()) return true;
        try {
            InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(kb);
            if (key.getCategory() == InputUtil.Type.KEYSYM) {
                return InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getCode());
            }
        } catch (Exception ignored) {}
        return false;
    }
}
