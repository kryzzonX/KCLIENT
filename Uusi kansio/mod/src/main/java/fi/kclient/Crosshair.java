package fi.kclient;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Oma tähtäin: tyyli, väri, koko, väli, paksuus, ääriviiva ja keskipiste. */
public final class Crosshair {
    public static final int[] COLORS = {0xFFA855F7, 0xFFFFFFFF, 0xFF4ADE80, 0xFFF87171, 0xFF22D3EE, 0xFFFACC15, 0xFFF472B6};

    private Crosshair() {}

    public static void draw(DrawContext c, int cx, int cy) {
        int size = (int) KConfig.num("xSize"), gap = (int) KConfig.num("xGap");
        int t = Math.max(1, (int) KConfig.num("xThick")), style = (int) KConfig.num("xStyle");
        int col = COLORS[Math.floorMod((int) KConfig.num("xColor"), COLORS.length)];
        int h = t / 2;
        List<int[]> r = new ArrayList<>();

        if (style == 1) { // piste
            int s = Math.max(2, size / 2);
            r.add(new int[]{cx - s / 2, cy - s / 2, cx - s / 2 + s, cy - s / 2 + s});
        } else if (style == 2) { // rengas
            r.add(new int[]{cx - size, cy - size, cx + size + 1, cy - size + t});
            r.add(new int[]{cx - size, cy + size + 1 - t, cx + size + 1, cy + size + 1});
            r.add(new int[]{cx - size, cy - size, cx - size + t, cy + size + 1});
            r.add(new int[]{cx + size + 1 - t, cy - size, cx + size + 1, cy + size + 1});
        } else { // risti ja T-risti
            if (style != 3) r.add(new int[]{cx - h, cy - gap - size, cx - h + t, cy - gap});
            r.add(new int[]{cx - h, cy + gap + 1, cx - h + t, cy + gap + 1 + size});
            r.add(new int[]{cx - gap - size, cy - h, cx - gap, cy - h + t});
            r.add(new int[]{cx + gap + 1, cy - h, cx + gap + 1 + size, cy - h + t});
        }
        if (KConfig.on("xDot") && style != 1) r.add(new int[]{cx - h, cy - h, cx - h + t, cy - h + t});

        if (KConfig.on("xOutline")) {
            for (int[] a : r) c.fill(a[0] - 1, a[1] - 1, a[2] + 1, a[3] + 1, 0xFF000000);
        }
        for (int[] a : r) c.fill(a[0], a[1], a[2], a[3], col);
    }
}
