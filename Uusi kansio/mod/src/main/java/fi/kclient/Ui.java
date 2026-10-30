package fi.kclient;

import net.minecraft.client.gui.DrawContext;

/** K Client -teema: sama violetti kuin launcherissa. */
public final class Ui {
    public static final int BG = 0xFF0C0814, PANEL = 0xFF150F22, PANEL2 = 0xFF1C1430, LINE = 0xFF2C2145;
    public static final int ACC = 0xFFA855F7, ACC2 = 0xFF7C3AED, TX = 0xFFF1ECFA, TX2 = 0xFFA99DC4;

    private Ui() {}

    /** Suorakulmio, jonka kulmat on pyöristetty yhdellä pikselillä. */
    public static void rect(DrawContext c, int x, int y, int w, int h, int col) {
        if (w <= 2 || h <= 2) { c.fill(x, y, x + w, y + h, col); return; }
        c.fill(x + 1, y, x + w - 1, y + h, col);
        c.fill(x, y + 1, x + w, y + h - 1, col);
    }

    public static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >> s) & 255, cb = (b >> s) & 255;
            r |= (((int) (ca + (cb - ca) * t)) & 255) << s;
        }
        return r;
    }
}
