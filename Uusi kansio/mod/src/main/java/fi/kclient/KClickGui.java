package fi.kclient;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** K Client Click GUI. Avautuu Right Shiftillä. */
public class KClickGui extends Screen {
    private static final int SB = 100, RH = 36, HEAD = 50;

    private static final class Row {
        String id, name, desc;
        int type; // 0 = kytkin, 1 = liukusäädin, 2 = painike, 3 = valinta
        double min, max;
        String[] opts;
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();
    private int tab = 0, x0, y0, pw, ph;
    private double scroll = 0;
    private Row dragging = null;
    private long last = System.nanoTime();

    public KClickGui() { super(Text.literal("K Client")); }

    @Override
    protected void init() {
        pw = Math.min(400, width - 16);
        ph = Math.min(250, height - 16);
        x0 = (width - pw) / 2;
        y0 = (height - ph) / 2;
        build();
    }

    @Override
    public boolean shouldPause() { return false; }

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
            rows.add(row("xOutline", 0, fi ? "Ääriviiva" : "Outline", fi ? "Musta reuna näkyvyyden parantamiseksi" : "Black edge for visibility", 0, 0));
            rows.add(row("xDot", 0, fi ? "Keskipiste" : "Center dot", fi ? "Piste tähtäimen keskellä" : "A dot in the middle", 0, 0));
        } else {
            rows.add(row("hudScale", 1, fi ? "HUD-koko" : "HUD size", "", 0.6, 1.6));
            rows.add(row("zoomFov", 1, fi ? "Zoomin FOV" : "Zoom FOV", "", 10, 50));
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

    private int trackX() { return x0 + pw - 12 - 10 - 110; }

    private String valueText(Row r) {
        double v = KConfig.num(r.id);
        if (r.type == 3) return r.opts[Math.floorMod((int) v, r.opts.length)];
        if (r.id.equals("hudScale")) return String.format("%.1fx", v);
        if (r.id.equals("zoomFov")) return ((int) v) + " deg";
        return String.valueOf((int) v);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        TextRenderer tr = textRenderer;
        boolean fi = KConfig.fi();
        MatrixStack m = ctx.getMatrices();

        ctx.fill(0, 0, width, height, 0x99070410);
        Ui.rect(ctx, x0 - 1, y0 - 1, pw + 2, ph + 2, Ui.LINE);
        Ui.rect(ctx, x0, y0, pw, ph, Ui.BG);
        ctx.fill(x0, y0 + 1, x0 + SB, y0 + ph - 1, Ui.PANEL);

        // logo
        Ui.rect(ctx, x0 + 10, y0 + 10, 24, 24, Ui.ACC);
        m.push();
        m.translate(x0 + 22 - tr.getWidth("K") * 0.75f, y0 + 15, 0f);
        m.scale(1.5f, 1.5f, 1f);
        ctx.drawText(tr, Text.literal("K").formatted(Formatting.BOLD), 0, 0, 0xFFFFFFFF, false);
        m.pop();
        ctx.drawText(tr, Text.literal("K CLIENT").formatted(Formatting.BOLD), x0 + 40, y0 + 12, Ui.TX, false);
        ctx.drawText(tr, "Click GUI", x0 + 40, y0 + 24, Ui.ACC, false);

        // välilehdet
        String[] names = fi ? new String[]{"HUD", "PvP", "Tähtäin", "Asetukset"} : new String[]{"HUD", "PvP", "Crosshair", "Settings"};
        for (int i = 0; i < names.length; i++) {
            int ty = y0 + 46 + i * 22;
            boolean sel = i == tab, hov = in(mx, my, x0 + 6, ty, SB - 12, 18);
            if (sel || hov) Ui.rect(ctx, x0 + 6, ty, SB - 12, 18, sel ? Ui.PANEL2 : 0xFF181127);
            if (sel) ctx.fill(x0 + 6, ty + 3, x0 + 8, ty + 15, Ui.ACC);
            ctx.drawText(tr, names[i], x0 + 16, ty + 5, sel ? Ui.TX : Ui.TX2, false);
        }
        ctx.drawText(tr, "Right Shift", x0 + 10, y0 + ph - 20, Ui.TX2, false);
        ctx.drawText(tr, "v1.1", x0 + 10, y0 + ph - 11, 0xFF6B5B8C, false);

        // otsikko
        int cx = x0 + SB + 12, cw = pw - SB - 24;
        m.push();
        m.translate(cx, y0 + 11, 0f);
        m.scale(1.3f, 1.3f, 1f);
        ctx.drawText(tr, Text.literal(names[tab]).formatted(Formatting.BOLD), 0, 0, Ui.TX, false);
        m.pop();
        ctx.drawText(tr, "x", x0 + pw - 17, y0 + 10, in(mx, my, x0 + pw - 20, y0 + 8, 12, 12) ? Ui.TX : Ui.TX2, false);
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
        ctx.drawText(tr, sub, cx, y0 + 27, Ui.TX2, false);

        // tähtäimen esikatselu
        if (tab == 2) {
            int bx = x0 + pw - 74, by = y0 + 4;
            Ui.rect(ctx, bx, by, 44, 44, Ui.PANEL);
            ctx.enableScissor(bx, by, bx + 44, by + 44);
            Crosshair.draw(ctx, bx + 22, by + 22);
            ctx.disableScissor();
        }

        // rivit
        int top = y0 + HEAD, bottom = y0 + ph - 8;
        ctx.enableScissor(x0 + SB, top, x0 + pw - 4, bottom);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = top + 2 + i * RH - (int) scroll;
            if (ry + RH < top || ry > bottom) continue;
            boolean hov = in(mx, my, cx, ry, cw, RH - 4) && my >= top && my <= bottom;
            boolean on = r.type == 0 && KConfig.on(r.id);
            Ui.rect(ctx, cx, ry, cw, RH - 4, hov ? Ui.PANEL2 : Ui.PANEL);
            if (on) ctx.fill(cx, ry + 3, cx + 2, ry + RH - 7, Ui.ACC);
            ctx.drawText(tr, r.name, cx + 10, ry + 7, Ui.TX, false);
            ctx.drawText(tr, (r.type == 1 || r.type == 3) ? valueText(r) : r.desc, cx + 10, ry + 19, Ui.TX2, false);
            int rx = cx + cw - 10;
            if (r.type == 0) {
                float t = on ? 1f : 0f, a = anim.getOrDefault(r.id, t);
                a += (t - a) * Math.min(1f, dt * 14f);
                anim.put(r.id, a);
                Ui.rect(ctx, rx - 26, ry + 11, 26, 12, Ui.lerp(Ui.LINE, Ui.ACC, a));
                Ui.rect(ctx, (int) (rx - 24 + a * 14), ry + 13, 8, 8, 0xFFF1ECFA);
            } else if (r.type == 1) {
                double f = (KConfig.num(r.id) - r.min) / (r.max - r.min);
                int tx = rx - 110, fw = Math.max(3, (int) (110 * f));
                Ui.rect(ctx, tx, ry + 15, 110, 4, Ui.LINE);
                Ui.rect(ctx, tx, ry + 15, fw, 4, Ui.ACC);
                Ui.rect(ctx, tx + fw - 3, ry + 12, 6, 10, 0xFFF1ECFA);
            } else if (r.type == 3) {
                if (r.id.equals("xColor")) {
                    int ci = Math.floorMod((int) KConfig.num("xColor"), Crosshair.COLORS.length);
                    Ui.rect(ctx, rx - 16, ry + 10, 14, 14, Crosshair.COLORS[ci]);
                } else {
                    ctx.drawText(tr, ">", rx - 6, ry + 14, Ui.ACC, false);
                }
            } else {
                ctx.drawText(tr, ">", rx - 6, ry + 14, Ui.ACC, false);
            }
        }
        ctx.disableScissor();
    }

    private void setSlider(Row r, double mx) {
        double f = Math.max(0, Math.min(1, (mx - trackX()) / 110.0));
        double v = r.min + f * (r.max - r.min);
        v = r.id.equals("hudScale") ? Math.round(v * 10) / 10.0 : Math.round(v);
        KConfig.setNum(r.id, v);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (in(mx, my, x0 + pw - 20, y0 + 8, 12, 12)) { close(); return true; }
        for (int i = 0; i < 4; i++) {
            if (in(mx, my, x0 + 6, y0 + 46 + i * 22, SB - 12, 18)) { tab = i; build(); return true; }
        }
        int top = y0 + HEAD, bottom = y0 + ph - 8, cx = x0 + SB + 12, cw = pw - SB - 24;
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

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) { setSlider(dragging, mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) { dragging = null; KConfig.save(); }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        double max = Math.max(0, rows.size() * RH - (ph - HEAD - 8));
        scroll = Math.max(0, Math.min(max, scroll - v * 14));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KClient.menuKey.matchesKey(keyCode, scanCode)) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
