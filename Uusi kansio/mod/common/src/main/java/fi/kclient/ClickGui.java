package fi.kclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** K Client Click GUI. Piirto ja hiiri/näppäinlogiikka ovat täällä, joten sama koodi toimii kaikilla versioilla. */
public final class ClickGui {
    private static final int SB = 104, RH = 38, HEAD = 54;

    private static final class Row {
        String id, name, desc;
        int type; // 0 = kytkin, 1 = liukusäädin, 2 = painike, 3 = valinta
        double min, max;
        String[] opts;
    }

    public Runnable onClose = () -> {};

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();
    private final float[] tabAnim = new float[4];
    private int tab = 0, x0, y0, pw, ph, sw, sh;
    private double scroll = 0;
    private Row dragging = null;
    private long last = System.nanoTime();
    private final long opened = System.nanoTime();

    public void init(int w, int h) {
        sw = w;
        sh = h;
        pw = Math.min(430, w - 16);
        ph = Math.min(272, h - 16);
        x0 = (w - pw) / 2;
        y0 = (h - ph) / 2;
        build();
    }

    private static Row row(String id, int type, String name, String desc, double min, double max) {
        Row r = new Row();
        r.id = id; r.type = type; r.name = name; r.desc = desc; r.min = min; r.max = max;
        return r;
    }

    private static Row cycle(String id, String name, String[] opts) {
        Row r = row(id, 3, name, "", 0, opts.length - 1);
        r.opts = opts;
        return r;
    }

    private void mods(String cat) {
        for (KConfig.Mod m : KConfig.MODS) if (m.cat().equals(cat)) rows.add(row(m.id(), 0, m.name(), m.desc(), 0, 0));
    }

    private void build() {
        rows.clear();
        scroll = 0;
        boolean fi = KConfig.fi();
        if (tab == 0) {
            mods("hud");
        } else if (tab == 1) {
            mods("pvp");
        } else if (tab == 2) {
            rows.add(row("crosshair", 0, fi ? "Oma tähtäin" : "Custom crosshair",
                fi ? "Korvaa pelin tähtäimen" : "Replaces the vanilla crosshair", 0, 0));
            rows.add(cycle("xStyle", fi ? "Tyyli" : "Style",
                fi ? new String[]{"Risti", "Piste", "Rengas", "T-risti"} : new String[]{"Cross", "Dot", "Ring", "T-cross"}));
            rows.add(cycle("xColor", fi ? "Väri" : "Color",
                fi ? new String[]{"Violetti", "Valkoinen", "Vihreä", "Punainen", "Syaani", "Keltainen", "Pinkki"}
                   : new String[]{"Purple", "White", "Green", "Red", "Cyan", "Yellow", "Pink"}));
            rows.add(row("xSize", 1, fi ? "Koko" : "Size", "", 1, 12));
            rows.add(row("xGap", 1, fi ? "Väli" : "Gap", "", 0, 8));
            rows.add(row("xThick", 1, fi ? "Paksuus" : "Thickness", "", 1, 4));
            rows.add(row("xOutline", 0, fi ? "Ääriviiva" : "Outline",
                fi ? "Musta reuna näkyvyyden parantamiseksi" : "Black edge for visibility", 0, 0));
            rows.add(row("xDot", 0, fi ? "Keskipiste" : "Center dot", fi ? "Piste tähtäimen keskellä" : "A dot in the middle", 0, 0));
        } else {
            rows.add(row("hudScale", 1, fi ? "HUD-koko" : "HUD size", "", 0.6, 1.6));
            rows.add(row("zoomFov", 1, fi ? "Zoomin FOV" : "Zoom FOV", "", 30, 60));
            rows.add(row("hudBg", 0, fi ? "HUD-tausta" : "HUD background",
                fi ? "Tumma tausta tiedoille" : "Dark panel behind the info", 0, 0));
            rows.add(row("hudRight", 0, fi ? "HUD oikealla" : "HUD on the right",
                fi ? "Siirtää tiedot oikeaan reunaan" : "Moves the info to the right edge", 0, 0));
            rows.add(row("lang", 2, fi ? "Kieli: Suomi" : "Language: English", fi ? "Vaihda kieli" : "Switch language", 0, 0));
            rows.add(row("reset", 2, fi ? "Nollaa asetukset" : "Reset settings",
                fi ? "Palauttaa kaikki oletusarvoihin" : "Restores every default", 0, 0));
        }
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int trackX() { return x0 + pw - 134; }

    private String valueText(Row r) {
        double v = KConfig.num(r.id);
        if (r.type == 3) return r.opts[Math.floorMod((int) v, r.opts.length)];
        if (r.id.equals("hudScale")) return String.format("%.1fx", v);
        if (r.id.equals("zoomFov")) return ((int) v) + " deg";
        return String.valueOf((int) v);
    }

    private static void icon(Gfx g, int kind, int x, int y, int c) {
        if (kind == 0) {
            g.fill(x, y + 1, x + 10, y + 3, c);
            g.fill(x, y + 4, x + 7, y + 6, c);
            g.fill(x, y + 7, x + 10, y + 9, c);
        } else if (kind == 1) {
            for (int i = 0; i < 10; i++) {
                g.fill(x + i, y + i, x + i + 2, y + i + 1, c);
                g.fill(x + 8 - i, y + i, x + 10 - i, y + i + 1, c);
            }
        } else if (kind == 2) {
            g.fill(x + 4, y, x + 6, y + 3, c);
            g.fill(x + 4, y + 7, x + 6, y + 10, c);
            g.fill(x, y + 4, x + 3, y + 6, c);
            g.fill(x + 7, y + 4, x + 10, y + 6, c);
        } else {
            g.fill(x, y + 1, x + 10, y + 2, c);
            g.fill(x + 6, y, x + 8, y + 3, c);
            g.fill(x, y + 5, x + 10, y + 6, c);
            g.fill(x + 2, y + 4, x + 4, y + 7, c);
            g.fill(x, y + 9, x + 10, y + 10, c);
            g.fill(x + 5, y + 8, x + 7, y + 11, c);
        }
    }

    public void render(Gfx g, int mx, int my) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        float t = Math.min(1f, (now - opened) / 1e9f / 0.16f);
        float e = 1f - (1f - t) * (1f - t);
        boolean fi = KConfig.fi();

        g.fill(0, 0, sw, sh, Ui.alpha(0x070410, (int) (0x99 * e)));
        g.push();
        float sc = 0.94f + 0.06f * e, cx0 = x0 + pw / 2f, cy0 = y0 + ph / 2f;
        g.translate(cx0, cy0);
        g.scale(sc);
        g.translate(-cx0, -cy0);

        Ui.shadow(g, x0, y0, pw, ph);
        Ui.card(g, x0, y0, pw, ph, Ui.BG, Ui.LINE);
        g.fill(x0 + 1, y0 + 2, x0 + SB, y0 + ph - 2, Ui.PANEL);

        // logo ja nimi
        Ui.rect(g, x0 + 10, y0 + 10, 24, 24, Ui.ACC);
        g.push();
        g.translate(x0 + 22 - g.textWidth("K") * 0.75f, y0 + 15);
        g.scale(1.5f);
        g.textBold("K", 0, 0, 0xFFFFFFFF);
        g.pop();
        g.textBold("K CLIENT", x0 + 40, y0 + 12, Ui.TX);
        g.text("Click GUI", x0 + 40, y0 + 24, Ui.ACC, false);

        // välilehdet
        String[] names = fi ? new String[]{"HUD", "PvP", "Tähtäin", "Asetukset"} : new String[]{"HUD", "PvP", "Crosshair", "Settings"};
        for (int i = 0; i < names.length; i++) {
            int ty = y0 + 48 + i * 22;
            boolean sel = i == tab, hov = in(mx, my, x0 + 6, ty, SB - 12, 18);
            tabAnim[i] += ((sel ? 1f : hov ? 0.5f : 0f) - tabAnim[i]) * Math.min(1f, dt * 14f);
            if (tabAnim[i] > 0.02f) Ui.rect(g, x0 + 6, ty, SB - 12, 18, Ui.lerp(Ui.PANEL, Ui.PANEL2, tabAnim[i]));
            if (sel) g.fill(x0 + 6, ty + 4, x0 + 8, ty + 14, Ui.ACC);
            icon(g, i, x0 + 14, ty + 4, sel ? Ui.ACC : Ui.TX2);
            g.text(names[i], x0 + 30, ty + 5, sel ? Ui.TX : Ui.TX2, false);
        }
        Ui.card(g, x0 + 10, y0 + ph - 28, SB - 20, 14, Ui.PANEL2, Ui.LINE);
        g.text("RSHIFT", x0 + 10 + (SB - 20 - g.textWidth("RSHIFT")) / 2, y0 + ph - 25, Ui.TX2, false);
        g.text("v1.2", x0 + 12, y0 + ph - 11, 0xFF6B5B8C, false);

        // otsikko
        int cx = x0 + SB + 14, cw = pw - SB - 28;
        g.push();
        g.translate(cx, y0 + 11);
        g.scale(1.3f);
        g.textBold(names[tab], 0, 0, Ui.TX);
        g.pop();
        String sub;
        if (tab < 2) {
            int on = 0;
            for (Row r : rows) if (KConfig.on(r.id)) on++;
            sub = on + "/" + rows.size() + (fi ? " päällä" : " enabled");
        } else if (tab == 2) {
            sub = fi ? "Esikatselu oikealla" : "Live preview on the right";
        } else {
            sub = fi ? "Säädä clientin asetuksia" : "Tweak the client";
        }
        g.text(sub, cx, y0 + 27, Ui.TX2, false);
        g.fill(cx, y0 + HEAD - 6, cx + cw, y0 + HEAD - 5, Ui.LINE);
        g.text("x", x0 + pw - 17, y0 + 10, in(mx, my, x0 + pw - 20, y0 + 8, 12, 12) ? Ui.TX : Ui.TX2, false);

        if (tab == 2) {
            int bx = x0 + pw - 74, by = y0 + 6;
            Ui.card(g, bx, by, 44, 40, Ui.PANEL, Ui.LINE);
            g.scissor(bx + 1, by + 1, bx + 43, by + 39);
            Crosshair.draw(g, bx + 22, by + 20);
            g.noScissor();
        }

        // rivit
        int top = y0 + HEAD, bottom = y0 + ph - 8;
        g.scissor(x0 + SB, top, x0 + pw - 2, bottom);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = top + 2 + i * RH - (int) scroll;
            if (ry + RH < top || ry > bottom) continue;
            boolean hov = in(mx, my, cx, ry, cw, RH - 4) && my >= top && my <= bottom;
            boolean on = r.type == 0 && KConfig.on(r.id);
            float a = anim.getOrDefault(r.id, on ? 1f : 0f);
            a += ((on ? 1f : 0f) - a) * Math.min(1f, dt * 14f);
            anim.put(r.id, a);
            float hv = anim.getOrDefault("h" + r.id, 0f);
            hv += ((hov ? 1f : 0f) - hv) * Math.min(1f, dt * 16f);
            anim.put("h" + r.id, hv);

            int fill = Ui.lerp(Ui.lerp(Ui.PANEL, 0xFF1A1030, a), Ui.PANEL2, hv * 0.7f);
            int border = Ui.lerp(Ui.lerp(Ui.LINE, Ui.ACC2, a * 0.8f), Ui.ACC2, hv * 0.6f);
            Ui.card(g, cx, ry, cw, RH - 4, fill, border);
            if (a > 0.05f) g.fill(cx + 1, ry + 6, cx + 3, ry + RH - 10, Ui.lerp(Ui.PANEL, Ui.ACC, a));
            g.text(r.name, cx + 12, ry + 7, Ui.TX, false);
            g.text((r.type == 1 || r.type == 3) ? valueText(r) : r.desc, cx + 12, ry + 19, Ui.TX2, false);

            int rx = cx + cw - 10;
            if (r.type == 0) {
                Ui.rect(g, rx - 28, ry + 10, 28, 14, Ui.lerp(Ui.LINE, Ui.ACC, a));
                Ui.rect(g, (int) (rx - 26 + a * 14), ry + 12, 10, 10, 0xFFF1ECFA);
            } else if (r.type == 1) {
                double f = (KConfig.num(r.id) - r.min) / (r.max - r.min);
                int tx = rx - 110, fw = Math.max(3, (int) (110 * f));
                Ui.rect(g, tx, ry + 15, 110, 4, Ui.LINE);
                Ui.rect(g, tx, ry + 15, fw, 4, Ui.ACC);
                Ui.rect(g, tx + fw - 3, ry + 11, 7, 12, 0xFFF1ECFA);
            } else if (r.type == 3) {
                if (r.id.equals("xColor")) {
                    int ci = Math.floorMod((int) KConfig.num("xColor"), Crosshair.COLORS.length);
                    Ui.card(g, rx - 18, ry + 9, 16, 16, Crosshair.COLORS[ci], Ui.LINE);
                } else {
                    g.text("<  >", rx - 22, ry + 13, Ui.ACC, false);
                }
            } else {
                g.text(">", rx - 6, ry + 13, Ui.ACC, false);
            }
        }
        g.noScissor();

        // vierityspalkki
        int content = rows.size() * RH, view = bottom - top;
        if (content > view) {
            int th = Math.max(14, view * view / content);
            int tyy = top + 2 + (int) ((view - th - 4) * (scroll / Math.max(1, content - view)));
            g.fill(x0 + pw - 6, top + 2, x0 + pw - 4, bottom - 2, Ui.LINE);
            g.fill(x0 + pw - 6, tyy, x0 + pw - 4, tyy + th, Ui.ACC);
        }
        g.pop();
    }

    private void setSlider(Row r, double mx) {
        double f = Math.max(0, Math.min(1, (mx - trackX()) / 110.0));
        double v = r.min + f * (r.max - r.min);
        v = r.id.equals("hudScale") ? Math.round(v * 10) / 10.0 : Math.round(v);
        KConfig.setNum(r.id, v);
    }

    public boolean click(double mx, double my, int button) {
        if (button != 0) return true;
        if (in(mx, my, x0 + pw - 20, y0 + 8, 12, 12)) { onClose.run(); return true; }
        for (int i = 0; i < 4; i++) {
            if (in(mx, my, x0 + 6, y0 + 48 + i * 22, SB - 12, 18)) { tab = i; build(); return true; }
        }
        int top = y0 + HEAD, bottom = y0 + ph - 8, cx = x0 + SB + 14, cw = pw - SB - 28;
        if (my < top || my > bottom) return true;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = top + 2 + i * RH - (int) scroll;
            if (!in(mx, my, cx, ry, cw, RH - 4)) continue;
            if (r.type == 0) {
                KConfig.toggle(r.id);
            } else if (r.type == 1) {
                if (mx >= trackX() - 6) { dragging = r; setSlider(r, mx); }
            } else if (r.type == 3) {
                KConfig.setNum(r.id, (Math.floorMod((int) KConfig.num(r.id), r.opts.length) + 1) % r.opts.length);
                KConfig.save();
            } else if (r.id.equals("lang")) {
                KConfig.toggleLang();
                build();
            } else if (r.id.equals("reset")) {
                KConfig.reset();
                build();
            }
            return true;
        }
        return true;
    }

    public boolean drag(double mx, double my) {
        if (dragging == null) return false;
        setSlider(dragging, mx);
        return true;
    }

    public void release() {
        if (dragging != null) { dragging = null; KConfig.save(); }
    }

    public void scroll(double v) {
        double max = Math.max(0, rows.size() * RH - (ph - HEAD - 8));
        scroll = Math.max(0, Math.min(max, scroll - v * 14));
    }
}
