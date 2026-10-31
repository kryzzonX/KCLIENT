package fi.kclient.mc;

import fi.kclient.Gfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class YarnGfx implements Gfx {
    private final DrawContext c;
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final TextRenderer tr = mc.textRenderer;

    public YarnGfx(DrawContext c) { this.c = c; }

    @Override public int width() { return mc.getWindow().getScaledWidth(); }
    @Override public int height() { return mc.getWindow().getScaledHeight(); }
    @Override public void fill(int x1, int y1, int x2, int y2, int argb) { c.fill(x1, y1, x2, y2, argb); }
    @Override public void text(String s, int x, int y, int argb, boolean shadow) { c.drawText(tr, s, x, y, argb, shadow); }
    @Override public void textBold(String s, int x, int y, int argb) {
        c.drawText(tr, Text.literal(s).formatted(Formatting.BOLD), x, y, argb, false);
    }
    @Override public int textWidth(String s) { return tr.getWidth(s); }
    @Override public void push() { c.getMatrices().push(); }
    @Override public void pop() { c.getMatrices().pop(); }
    @Override public void translate(float x, float y) { c.getMatrices().translate(x, y, 0f); }
    @Override public void scale(float s) { c.getMatrices().scale(s, s, 1f); }
    @Override public void scissor(int x1, int y1, int x2, int y2) { c.enableScissor(x1, y1, x2, y2); }
    @Override public void noScissor() { c.disableScissor(); }
    @Override public void item(Object stack, int x, int y) {
        ItemStack st = (ItemStack) stack;
        c.drawItem(st, x, y);
        c.drawStackOverlay(tr, st, x, y);
    }
}
