package fi.kclient;

/** K Client -teema: sama violetti kuin launcherissa. */
public final class Ui {
    public static final int BG = 0xFF0C0814, PANEL = 0xFF150F22, PANEL2 = 0xFF1C1430, LINE = 0xFF2C2145;
    public static final int ACC = 0xFFA855F7, ACC2 = 0xFF7C3AED, TX = 0xFFF1ECFA, TX2 = 0xFFA99DC4;

    private Ui() {}

    /** Suorakulmio, jonka kulmat on pyöristetty kahdella pikselillä. */
    public static void rect(Gfx g, int x, int y, int w, int h, int col) {
        if (w <= 4 || h <= 4) { g.fill(x, y, x + w, y + h, col); return; }
        g.fill(x + 2, y, x + w - 2, y + h, col);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, col);
        g.fill(x, y + 2, x + w, y + h - 2, col);
    }

    /** Pyöristetty kortti reunaviivalla. */
    public static void card(Gfx g, int x, int y, int w, int h, int fill, int border) {
        rect(g, x, y, w, h, border);
        rect(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    public static int alpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >> s) & 255, cb = (b >> s) & 255;
            r |= (((int) (ca + (cb - ca) * t)) & 255) << s;
        }
        return r;
    }

    /** Pehmeä varjo ikkunan taakse. */
    public static void shadow(Gfx g, int x, int y, int w, int h) {
        for (int i = 4; i >= 1; i--) rect(g, x - i, y - i, w + 2 * i, h + 2 * i, alpha(0x000000, 10 * (5 - i)));
    }
}
