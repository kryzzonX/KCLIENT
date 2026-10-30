package fi.kclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** HUD-moduulit. Vain tietoa ja mukavuutta, ei huijauksia. */
public class Hud {
    public static int combo = 0;
    private static final ArrayDeque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Item[] TRACKED = {Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.SPLASH_POTION, Items.ARROW};
    private static boolean lastL, lastR, zooming;
    private static int oldFov;

    public static void tick(MinecraftClient mc) {
        boolean l = mc.options.attackKey.isPressed(), r = mc.options.useKey.isPressed();
        long now = System.currentTimeMillis();
        if (mc.currentScreen == null) {
            if (l && !lastL) left.add(now);
            if (r && !lastR) right.add(now);
        }
        lastL = l;
        lastR = r;

        ClientPlayerEntity p = mc.player;
        if (p != null && p.hurtTime > 0) combo = 0;
        if (p != null && KConfig.on("sprint") && p.input.movementForward > 0
            && !p.isSneaking() && !p.horizontalCollision && p.getHungerManager().getFoodLevel() > 6) {
            p.setSprinting(true);
        }

        if (KConfig.on("zoom") && KClient.zoomKey.isPressed() && mc.currentScreen == null) {
            if (!zooming) { oldFov = mc.options.getFov().getValue(); zooming = true; }
            mc.options.getFov().setValue((int) KConfig.num("zoomFov"));
        } else if (zooming) {
            mc.options.getFov().setValue(oldFov);
            zooming = false;
        }
    }

    private static int cps(ArrayDeque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    private static int width(TextRenderer tr, List<String[]> list) {
        int w = 0;
        for (String[] l : list) w = Math.max(w, tr.getWidth(l[0] + ": " + l[1]));
        return list.isEmpty() ? 0 : w + 12;
    }

    /** Piirtää tietopaneelin kohtaan y ja palauttaa seuraavan paneelin y-koordinaatin. */
    private static int panel(DrawContext ctx, TextRenderer tr, List<String[]> list, int y) {
        if (list.isEmpty()) return y;
        int pw = width(tr, list), ph = list.size() * 11 + 6;
        if (KConfig.on("hudBg")) {
            Ui.rect(ctx, 0, y, pw, ph, 0x99150F22);
            ctx.fill(0, y + 2, 2, y + ph - 2, Ui.ACC);
        }
        int ty = y + 4;
        for (String[] l : list) {
            String lab = l[0] + ": ";
            ctx.drawTextWithShadow(tr, lab, 7, ty, 0xFFC084FC);
            ctx.drawTextWithShadow(tr, l[1], 7 + tr.getWidth(lab), ty, 0xFFFFFFFF);
            ty += 11;
        }
        return y + ph + 4;
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        TextRenderer tr = mc.textRenderer;
        float s = (float) KConfig.num("hudScale");
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();

        List<String[]> lines = new ArrayList<>();
        if (KConfig.on("fps")) lines.add(new String[]{"FPS", String.valueOf(mc.getCurrentFps())});
        if (KConfig.on("cps")) lines.add(new String[]{"CPS", cps(left) + " | " + cps(right)});
        if (KConfig.on("ping")) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            lines.add(new String[]{"Ping", (e == null ? 0 : e.getLatency()) + " ms"});
        }
        if (KConfig.on("combo")) lines.add(new String[]{"Combo", String.valueOf(combo)});
        if (KConfig.on("coords")) lines.add(new String[]{"XYZ", String.format("%.0f / %.0f / %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ())});
        if (KConfig.on("direction")) {
            String f = mc.player.getHorizontalFacing().asString();
            lines.add(new String[]{KConfig.fi() ? "Suunta" : "Facing", f.substring(0, 1).toUpperCase() + f.substring(1)});
        }
        if (KConfig.on("clock")) lines.add(new String[]{KConfig.fi() ? "Aika" : "Time", LocalTime.now().format(TIME)});

        List<String[]> pots = new ArrayList<>();
        if (KConfig.on("potions")) {
            for (StatusEffectInstance e : mc.player.getStatusEffects()) {
                String name = e.getEffectType().value().getName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
                int sec = e.getDuration() / 20;
                pots.add(new String[]{name, e.isInfinite() ? "--:--" : String.format("%d:%02d", sec / 60, sec % 60)});
            }
        }

        int wmax = Math.max(width(tr, lines), width(tr, pots));
        if (wmax > 0) {
            float x0 = KConfig.on("hudRight") ? sw - wmax * s - 6 : 6;
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(x0, 6f, 0f);
            m.scale(s, s, 1f);
            int y = panel(ctx, tr, lines, 0);
            panel(ctx, tr, pots, y);
            m.pop();
        }
        if (KConfig.on("armor")) armor(ctx, tr, mc, sw, sh);
        if (KConfig.on("items")) items(ctx, tr, mc, sh);
        if (KConfig.on("keys")) keys(ctx, tr, mc, s, sh);
    }

    private static void armor(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int sw, int sh) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        boolean any = false;
        for (EquipmentSlot sl : slots) if (!mc.player.getEquippedStack(sl).isEmpty()) any = true;
        if (!any) return;
        int x = sw - 26, y = sh / 2 - 38;
        Ui.rect(ctx, x - 4, y - 3, 24, 4 * 18 + 4, 0x99150F22);
        ctx.fill(x - 4, y, x - 2, y + 4 * 18 - 3, Ui.ACC);
        for (int i = 0; i < 4; i++) {
            ItemStack st = mc.player.getEquippedStack(slots[i]);
            if (st.isEmpty()) continue;
            ctx.drawItem(st, x, y + i * 18);
            ctx.drawStackOverlay(tr, st, x, y + i * 18);
        }
    }

    private static void items(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int sh) {
        List<Item> shown = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Item it : TRACKED) {
            int n = mc.player.getInventory().count(it);
            if (n > 0) { shown.add(it); counts.add(n); }
        }
        if (shown.isEmpty()) return;
        int x = 6, y = sh / 2 - shown.size() * 9;
        Ui.rect(ctx, x - 3, y - 3, 44, shown.size() * 18 + 4, 0x99150F22);
        ctx.fill(x - 3, y, x - 1, y + shown.size() * 18 - 3, Ui.ACC);
        for (int i = 0; i < shown.size(); i++) {
            ctx.drawItem(new ItemStack(shown.get(i)), x + 1, y + i * 18);
            ctx.drawTextWithShadow(tr, "x" + counts.get(i), x + 20, y + i * 18 + 4, 0xFFFFFFFF);
        }
    }

    private static void keys(DrawContext ctx, TextRenderer tr, MinecraftClient mc, float s, int sh) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(6f, sh - 62 * s - 6, 0f);
        m.scale(s, s, 1f);
        key(ctx, tr, "W", 22, 0, 20, 20, mc.options.forwardKey.isPressed());
        key(ctx, tr, "A", 0, 22, 20, 20, mc.options.leftKey.isPressed());
        key(ctx, tr, "S", 22, 22, 20, 20, mc.options.backKey.isPressed());
        key(ctx, tr, "D", 44, 22, 20, 20, mc.options.rightKey.isPressed());
        key(ctx, tr, "LMB", 0, 44, 31, 18, mc.options.attackKey.isPressed());
        key(ctx, tr, "RMB", 33, 44, 31, 18, mc.options.useKey.isPressed());
        m.pop();
    }

    private static void key(DrawContext ctx, TextRenderer tr, String t, int x, int y, int w, int h, boolean down) {
        Ui.rect(ctx, x, y, w, h, down ? 0xDDA855F7 : 0x99150F22);
        ctx.drawCenteredTextWithShadow(tr, t, x + w / 2, y + (h - 8) / 2, down ? 0xFFFFFFFF : 0xFFA99DC4);
    }
}
