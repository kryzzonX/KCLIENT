package fi.kclient.mc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

final class KeyPoll {
    private KeyPoll() {}

    static boolean down(KeyBinding kb) {
        return MinecraftClient.getInstance().currentScreen == null && kb.isPressed();
    }
}
