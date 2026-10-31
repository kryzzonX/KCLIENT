package fi.kclient.mc;

import fi.kclient.Game;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class YarnGame implements Game {
    private static final Item[] TRACKED = {Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.SPLASH_POTION, Items.ARROW};
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Override public int fps() { return mc.getCurrentFps(); }

    @Override public int ping() {
        if (mc.getNetworkHandler() == null || mc.player == null) return 0;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? 0 : e.getLatency();
    }

    @Override public double x() { return mc.player == null ? 0 : mc.player.getX(); }
    @Override public double y() { return mc.player == null ? 0 : mc.player.getY(); }
    @Override public double z() { return mc.player == null ? 0 : mc.player.getZ(); }
    @Override public String facing() { return mc.player == null ? "" : mc.player.getHorizontalFacing().asString(); }
    @Override public int hurtTime() { return mc.player == null ? 0 : mc.player.hurtTime; }

    @Override public boolean keyDown(int k) {
        return switch (k) {
            case 0 -> mc.options.forwardKey.isPressed();
            case 1 -> mc.options.leftKey.isPressed();
            case 2 -> mc.options.backKey.isPressed();
            case 3 -> mc.options.rightKey.isPressed();
            case 4 -> mc.options.attackKey.isPressed();
            default -> mc.options.useKey.isPressed();
        };
    }

    /** Lukee zoom-näppäimen myös suoraan, jotta toisen modin samaan näppäimeen tekemä sidonta ei estä zoomia. */
    @Override public boolean zoomKeyDown() {
        if (mc.currentScreen != null) return false;
        KeyBinding kb = KClient.zoomKey;
        if (kb.isPressed()) return true;
        try {
            InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(kb);
            if (key.getCategory() == InputUtil.Type.KEYSYM) {
                return InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getCode());
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override public List<String[]> effects() {
        List<String[]> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            String name = e.getEffectType().value().getName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
            int sec = e.getDuration() / 20;
            out.add(new String[]{name, e.isInfinite() ? "--:--" : String.format("%d:%02d", sec / 60, sec % 60)});
        }
        return out;
    }

    @Override public Object[] armor() {
        Object[] a = new Object[4];
        if (mc.player == null) return a;
        for (int i = 0; i < 4; i++) {
            ItemStack st = mc.player.getEquippedStack(ARMOR[i]);
            a[i] = st.isEmpty() ? null : st;
        }
        return a;
    }

    @Override public List<Object[]> items() {
        List<Object[]> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (Item it : TRACKED) {
            int n = mc.player.getInventory().count(it);
            if (n > 0) out.add(new Object[]{new ItemStack(it), n});
        }
        return out;
    }

    @Override public void tickSprint(boolean on) {
        ClientPlayerEntity p = mc.player;
        if (on && p != null && mc.options.forwardKey.isPressed() && !p.isSneaking()
            && !p.horizontalCollision && p.getHungerManager().getFoodLevel() > 6) {
            p.setSprinting(true);
        }
    }

    @Override public int getFov() { return mc.options.getFov().getValue(); }
    @Override public void setFov(int fov) { mc.options.getFov().setValue(fov); }
}
