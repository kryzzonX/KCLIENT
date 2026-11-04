package fi.kclient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** HUD-moduulit. Vain tietoa ja mukavuutta, ei huijauksia. Jokaisen elementin paikkaa voi siirtää Paikka-välilehdellä. */
public final class Hud {
    /** HUD-elementin rajat ruudulla (GUI-koordinaatit). */
    public record Box(String id, int x, int y, int w, int h) {}

    public static int combo = 0;
    /** Asetetaan, kun paikkaeditori on auki: oikea HUD piiloutuu, koska editori piirtää esikatselun. */
    public static volatile long editUntil = 0L;

    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static List<String[]> cacheInfo, cachePots;
    private static int cacheRev = -1;
    private static long cacheAt, armorAt, itemsAt;
    private static Object[] armorCache;
    private static List<Object[]> itemsCache;
    private static boolean lastL, lastR, zooming;
    private static int oldFov;

    private Hud() {}

    public static void tick(Game g) {
        boolean l = g.keyDown(4), r = g.keyDown(5);
        long now = System.currentTimeMillis();
        if (l && !lastL) LEFT.add(now);
        if (r && !lastR) RIGHT.add(now);
        lastL = l;
        lastR = r;
        if (g.hurtTime() > 0) combo = 0;

        g.tickSprint(KConfig.on("sprint"));

        boolean z = KConfig.on("zoom") && g.zoomKeyDown();
        if (z) {
            if (!zooming) { oldFov = g.getFov(); zooming = true; }
            g.setFov(Math.max(30, Math.min(110, (int) KConfig.num("zoomFov"))));
        } else if (zooming) {
            g.setFov(oldFov);
            zooming = false;
        }
    }

    private static int cps(ArrayDeque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    private static int width(Gfx g, List<String[]> list) {
        int w = 0;
        for (String[] l : list) w = Math.max(w, g.textWidth(l[0] + ": " + l[1]));
        return list.isEmpty() ? 0 : w + 12;
    }

    private static int ceil(float v) { return (int) Math.ceil(v); }

    /** Elementin paikka: tallennettu paikka (osuus vapaasta tilasta) tai oletus. Pysyy aina ruudulla. */
    private static int[] place(String id, int defX, int defY, int w, int h, int sw, int sh) {
        int fw = Math.max(1, sw - w), fh = Math.max(1, sh - h);
        int x = KConfig.hasNum("p_" + id + "x") ? (int) Math.round(KConfig.num("p_" + id + "x") * fw) : defX;
        int y = KConfig.hasNum("p_" + id + "y") ? (int) Math.round(KConfig.num("p_" + id + "y") * fh) : defY;
        return new int[]{Math.max(0, Math.min(sw - w, x)), Math.max(0, Math.min(sh - h, y))};
    }

    private static void panel(Gfx g, List<String[]> list, int y) {
        int pw = width(g, list), ph = list.size() * 11 + 6;
        if (KConfig.on("hudBg")) {
            Ui.rect(g, 0, y, pw, ph, 0x99150F22);
            g.fill(0, y + 2, 2, y + ph - 2, Ui.ACC);
        }
        int ty = y + 4;
        for (String[] l : list) {
            String lab = l[0] + ": ";
            g.text(lab, 7, ty, 0xFFC084FC, true);
            g.text(l[1], 7 + g.textWidth(lab), ty, 0xFFFFFFFF, true);
            ty += 11;
        }
    }

    private static void drawPanel(Gfx g, List<String[]> list, int x, int y, float s) {
        g.push();
        g.translate(x, y);
        g.scale(s);
        panel(g, list, 0);
        g.pop();
    }

    private static List<String[]> buildInfo(Game game, boolean preview) {
        List<String[]> info = new ArrayList<>();
        if (KConfig.on("fps")) info.add(new String[]{"FPS", preview ? "144" : String.valueOf(game.fps())});
        if (KConfig.on("cps")) info.add(new String[]{"CPS", preview ? "6 | 0" : cps(LEFT) + " | " + cps(RIGHT)});
        if (KConfig.on("ping")) info.add(new String[]{"Ping", (preview ? 24 : game.ping()) + " ms"});
        if (KConfig.on("combo")) info.add(new String[]{"Combo", preview ? "3" : String.valueOf(combo)});
        if (KConfig.on("coords")) info.add(new String[]{"XYZ", preview ? "120 / 64 / -80" : String.format("%.0f / %.0f / %.0f", game.x(), game.y(), game.z())});
        if (KConfig.on("direction")) {
            String f = preview ? "north" : game.facing();
            info.add(new String[]{KConfig.fi() ? "Suunta" : "Facing", f.isEmpty() ? f : f.substring(0, 1).toUpperCase() + f.substring(1)});
        }
        if (KConfig.on("clock")) info.add(new String[]{KConfig.fi() ? "Aika" : "Time", LocalTime.now().format(TIME)});
        return info;
    }

    private static List<String[]> buildPots(Game game, boolean preview) {
        List<String[]> pots = new ArrayList<>();
        if (KConfig.on("potions")) {
            if (preview) {
                pots.add(new String[]{"Speed 2", "1:30"});
                pots.add(new String[]{"Strength", "0:45"});
            } else {
                pots.addAll(game.effects());
            }
        }
        return pots;
    }

    public static void render(Gfx g, Game game) { render(g, game, false); }

    /** Piirtää HUDin. preview = true: näyttää esimerkkisisällön (game voi olla null). Palauttaa elementtien rajat. */
    public static List<Box> render(Gfx g, Game game, boolean preview) {
        List<Box> out = new ArrayList<>();
        if (!preview && System.nanoTime() < editUntil) return out;
        float s = (float) KConfig.num("hudScale");
        int sw = g.width(), sh = g.height();
        boolean right = KConfig.on("hudRight");

        List<String[]> info, pots;
        long nowMs = System.currentTimeMillis();
        if (!preview && cacheInfo != null && cacheRev == KConfig.rev && nowMs - cacheAt < 100) {
            info = cacheInfo;
            pots = cachePots;
        } else {
            info = buildInfo(game, preview);
            pots = buildPots(game, preview);
            if (!preview) { cacheInfo = info; cachePots = pots; cacheRev = KConfig.rev; cacheAt = nowMs; }
        }

        int ih = info.isEmpty() ? 0 : info.size() * 11 + 6;
        int ibw = ceil(width(g, info) * s), ibh = ceil(ih * s);
        int nextY = ih == 0 ? 6 : 6 + ibh + Math.round(4 * s);
        if (ih > 0) {
            int[] p = place("info", right ? sw - ibw - 6 : 6, 6, ibw, ibh, sw, sh);
            drawPanel(g, info, p[0], p[1], s);
            out.add(new Box("info", p[0], p[1], ibw, ibh));
        }
        int ph = pots.isEmpty() ? 0 : pots.size() * 11 + 6;
        int pbw = ceil(width(g, pots) * s), pbh = ceil(ph * s);
        if (ph > 0) {
            int[] p = place("pots", right ? sw - pbw - 6 : 6, nextY, pbw, pbh, sw, sh);
            drawPanel(g, pots, p[0], p[1], s);
            out.add(new Box("pots", p[0], p[1], pbw, pbh));
        }
        if (KConfig.on("armor")) armor(g, game, preview, s, sw, sh, out);
        if (KConfig.on("items")) items(g, game, preview, s, sw, sh, out);
        if (KConfig.on("keys")) keys(g, game, preview, s, sw, sh, out);
        return out;
    }

    private static void armor(Gfx g, Game game, boolean preview, float s, int sw, int sh, List<Box> out) {
        Object[] a;
        if (preview) {
            a = new Object[4];
        } else {
            long n = System.currentTimeMillis();
            if (armorCache == null || n - armorAt >= 100) { armorCache = game.armor(); armorAt = n; }
            a = armorCache;
        }
        boolean any = preview;
        if (!preview) for (Object o : a) if (o != null) any = true;
        if (!any) return;
        int bw = ceil(24 * s), bh = ceil(76 * s);
        int[] p = place("armor", sw - bw - 6, sh / 2 - bh / 2, bw, bh, sw, sh);
        g.push();
        g.translate(p[0], p[1]);
        g.scale(s);
        Ui.rect(g, 0, 0, 24, 76, 0x99150F22);
        g.fill(0, 3, 2, 73, Ui.ACC);
        for (int i = 0; i < 4; i++) {
            if (a[i] != null) g.item(a[i], 4, 3 + i * 18);
            else if (preview) g.fill(4, 3 + i * 18, 20, 19 + i * 18, 0xFF6B5B8C);
        }
        g.pop();
        out.add(new Box("armor", p[0], p[1], bw, bh));
    }

    private static void items(Gfx g, Game game, boolean preview, float s, int sw, int sh, List<Box> out) {
        List<Object[]> it;
        if (preview) {
            it = new ArrayList<>();
            it.add(new Object[]{null, 16});
            it.add(new Object[]{null, 3});
        } else {
            long n = System.currentTimeMillis();
            if (itemsCache == null || n - itemsAt >= 100) { itemsCache = game.items(); itemsAt = n; }
            it = itemsCache;
        }
        if (it.isEmpty()) return;
        int h = it.size() * 18 + 4;
        int bw = ceil(44 * s), bh = ceil(h * s);
        int[] p = place("items", 6, sh / 2 - bh / 2, bw, bh, sw, sh);
        g.push();
        g.translate(p[0], p[1]);
        g.scale(s);
        Ui.rect(g, 0, 0, 44, h, 0x99150F22);
        g.fill(0, 3, 2, h - 3, Ui.ACC);
        for (int i = 0; i < it.size(); i++) {
            Object stack = it.get(i)[0];
            if (stack != null) g.item(stack, 4, 3 + i * 18);
            else g.fill(4, 3 + i * 18, 20, 19 + i * 18, 0xFF6B5B8C);
            g.text("x" + it.get(i)[1], 23, 7 + i * 18, 0xFFFFFFFF, true);
        }
        g.pop();
        out.add(new Box("items", p[0], p[1], bw, bh));
    }

    private static void keys(Gfx g, Game game, boolean preview, float s, int sw, int sh, List<Box> out) {
        int bw = ceil(64 * s), bh = ceil(62 * s);
        int[] p = place("keys", 6, sh - bh - 6, bw, bh, sw, sh);
        g.push();
        g.translate(p[0], p[1]);
        g.scale(s);
        key(g, "W", 22, 0, 20, 20, !preview && game.keyDown(0));
        key(g, "A", 0, 22, 20, 20, !preview && game.keyDown(1));
        key(g, "S", 22, 22, 20, 20, !preview && game.keyDown(2));
        key(g, "D", 44, 22, 20, 20, !preview && game.keyDown(3));
        key(g, "LMB", 0, 44, 31, 18, !preview && game.keyDown(4));
        key(g, "RMB", 33, 44, 31, 18, !preview && game.keyDown(5));
        g.pop();
        out.add(new Box("keys", p[0], p[1], bw, bh));
    }

    private static void key(Gfx g, String t, int x, int y, int w, int h, boolean down) {
        Ui.rect(g, x, y, w, h, down ? 0xDDA855F7 : 0x99150F22);
        g.text(t, x + (w - g.textWidth(t)) / 2, y + (h - 8) / 2, down ? 0xFFFFFFFF : 0xFFA99DC4, true);
    }
}
