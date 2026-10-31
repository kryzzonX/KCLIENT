package fi.kclient.mc;

import fi.kclient.Game;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class MojGame implements Game {
    private static final Item[] TRACKED = {Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.SPLASH_POTION, Items.ARROW};
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final Minecraft mc = Minecraft.getInstance();
    private int frames, fps;
    private long last = System.currentTimeMillis();

    /** Kutsutaan joka ruudulla, jotta FPS voidaan laskea ilman pelin omia kutsuja. */
    public void frame() {
        frames++;
        long n = System.currentTimeMillis();
        if (n - last >= 1000) { fps = frames; frames = 0; last = n; }
    }

    @Override public int fps() { return fps; }

    @Override public int ping() {
        if (mc.getConnection() == null || mc.player == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    @Override public double x() { return mc.player == null ? 0 : mc.player.getX(); }
    @Override public double y() { return mc.player == null ? 0 : mc.player.getY(); }
    @Override public double z() { return mc.player == null ? 0 : mc.player.getZ(); }
    @Override public String facing() { return mc.player == null ? "" : mc.player.getDirection().getName(); }
    @Override public int hurtTime() { return mc.player == null ? 0 : mc.player.hurtTime; }

    @Override public boolean keyDown(int k) {
        return switch (k) {
            case 0 -> mc.options.keyUp.isDown();
            case 1 -> mc.options.keyLeft.isDown();
            case 2 -> mc.options.keyDown.isDown();
            case 3 -> mc.options.keyRight.isDown();
            case 4 -> mc.options.keyAttack.isDown();
            default -> mc.options.keyUse.isDown();
        };
    }

    @Override public boolean zoomKeyDown() { return KClient.zoomKey.isDown(); }

    @Override public List<String[]> effects() {
        List<String[]> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            String name = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
            int sec = e.getDuration() / 20;
            out.add(new String[]{name, e.isInfiniteDuration() ? "--:--" : String.format("%d:%02d", sec / 60, sec % 60)});
        }
        return out;
    }

    @Override public Object[] armor() {
        Object[] a = new Object[4];
        if (mc.player == null) return a;
        for (int i = 0; i < 4; i++) {
            ItemStack st = mc.player.getItemBySlot(ARMOR[i]);
            a[i] = st.isEmpty() ? null : st;
        }
        return a;
    }

    @Override public List<Object[]> items() {
        List<Object[]> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (Item it : TRACKED) {
            int n = mc.player.getInventory().countItem(it);
            if (n > 0) out.add(new Object[]{new ItemStack(it), n});
        }
        return out;
    }

    @Override public void tickSprint(boolean on) {
        LocalPlayer p = mc.player;
        if (on && p != null && mc.options.keyUp.isDown() && !p.isShiftKeyDown()
            && !p.horizontalCollision && p.getFoodData().getFoodLevel() > 6) {
            p.setSprinting(true);
        }
    }

    @Override public int getFov() { return mc.options.fov().get(); }
    @Override public void setFov(int fov) { mc.options.fov().set(fov); }
}
