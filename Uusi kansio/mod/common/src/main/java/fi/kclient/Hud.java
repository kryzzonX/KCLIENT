package fi.kclient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** HUD-moduulit. Vain tietoa ja mukavuutta, ei huijauksia. */
public final class Hud {
    public static int combo = 0;
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
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

    /** Piirtää tietopaneelin kohtaan y ja palauttaa seuraavan paneelin y-koordinaatin. */
    private static int panel(Gfx g, List<String[]> list, int y) {
        if (list.isEmpty()) return y;
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
        return y + ph + 4;
    }

    public static void render(Gfx g, Game game) {
        float s = (float) KConfig.num("hudScale");
        int sw = g.width(), sh = g.height();

        List<String[]> lines = new ArrayList<>();
        if (KConfig.on("fps")) lines.add(new String[]{"FPS", String.valueOf(game.fps())});
        if (KConfig.on("cps")) lines.add(new String[]{"CPS", cps(LEFT) + " | " + cps(RIGHT)});
        if (KConfig.on("ping")) lines.add(new String[]{"Ping", game.ping() + " ms"});
        if (KConfig.on("combo")) lines.add(new String[]{"Combo", String.valueOf(combo)});
        if (KConfig.on("coords")) lines.add(new String[]{"XYZ", String.format("%.0f / %.0f / %.0f", game.x(), game.y(), game.z())});
        if (KConfig.on("direction")) {
            String f = game.facing();
            lines.add(new String[]{KConfig.fi() ? "Suunta" : "Facing", f.isEmpty() ? f : f.substring(0, 1).toUpperCase() + f.substring(1)});
        }
        if (KConfig.on("clock")) lines.add(new String[]{KConfig.fi() ? "Aika" : "Time", LocalTime.now().format(TIME)});

        List<String[]> pots = KConfig.on("potions") ? game.effects() : new ArrayList<>();

        int wmax = Math.max(width(g, lines), width(g, pots));
        if (wmax > 0) {
            float x0 = KConfig.on("hudRight") ? sw - wmax * s - 6 : 6;
            g.push();
            g.translate(x0, 6f);
            g.scale(s);
            int y = panel(g, lines, 0);
            panel(g, pots, y);
            g.pop();
        }
        if (KConfig.on("armor")) armor(g, game, sw, sh);
        if (KConfig.on("items")) items(g, game, sh);
        if (KConfig.on("keys")) keys(g, game, s, sh);
    }

    private static void armor(Gfx g, Game game, int sw, int sh) {
        Object[] a = game.armor();
        boolean any = false;
        for (Object o : a) if (o != null) any = true;
        if (!any) return;
        int x = sw - 26, y = sh / 2 - 38;
        Ui.rect(g, x - 4, y - 3, 24, 4 * 18 + 4, 0x99150F22);
        g.fill(x - 4, y, x - 2, y + 4 * 18 - 3, Ui.ACC);
        for (int i = 0; i < a.length; i++) if (a[i] != null) g.item(a[i], x, y + i * 18);
    }

    private static void items(Gfx g, Game game, int sh) {
        List<Object[]> it = game.items();
        if (it.isEmpty()) return;
        int x = 6, y = sh / 2 - it.size() * 9;
        Ui.rect(g, x - 3, y - 3, 44, it.size() * 18 + 4, 0x99150F22);
        g.fill(x - 3, y, x - 1, y + it.size() * 18 - 3, Ui.ACC);
        for (int i = 0; i < it.size(); i++) {
            g.item(it.get(i)[0], x + 1, y + i * 18);
            g.text("x" + it.get(i)[1], x + 20, y + i * 18 + 4, 0xFFFFFFFF, true);
        }
    }

    private static void keys(Gfx g, Game game, float s, int sh) {
        g.push();
        g.translate(6f, sh - 62 * s - 6);
        g.scale(s);
        key(g, "W", 22, 0, 20, 20, game.keyDown(0));
        key(g, "A", 0, 22, 20, 20, game.keyDown(1));
        key(g, "S", 22, 22, 20, 20, game.keyDown(2));
        key(g, "D", 44, 22, 20, 20, game.keyDown(3));
        key(g, "LMB", 0, 44, 31, 18, game.keyDown(4));
        key(g, "RMB", 33, 44, 31, 18, game.keyDown(5));
        g.pop();
    }

    private static void key(Gfx g, String t, int x, int y, int w, int h, boolean down) {
        Ui.rect(g, x, y, w, h, down ? 0xDDA855F7 : 0x99150F22);
        g.text(t, x + (w - g.textWidth(t)) / 2, y + (h - 8) / 2, down ? 0xFFFFFFFF : 0xFFA99DC4, true);
    }
}
