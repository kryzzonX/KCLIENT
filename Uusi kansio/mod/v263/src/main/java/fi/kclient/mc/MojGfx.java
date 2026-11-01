package fi.kclient.mc;

import fi.kclient.Gfx;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

public final class MojGfx implements Gfx {
    private static Method ITEM, DECOR;

    static {
        for (Method m : GuiGraphicsExtractor.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().equals("item") && p.length == 3 && p[0] == ItemStack.class) ITEM = m;
            if (m.getName().equals("itemDecorations") && p.length == 4 && p[0] == Font.class && p[1] == ItemStack.class) DECOR = m;
        }
    }

    private final GuiGraphicsExtractor g;
    private final Minecraft mc = Minecraft.getInstance();
    private final Font font = mc.font;

    public MojGfx(GuiGraphicsExtractor g) { this.g = g; }

    @Override public int width() { return mc.getWindow().getGuiScaledWidth(); }
    @Override public int height() { return mc.getWindow().getGuiScaledHeight(); }
    @Override public void fill(int x1, int y1, int x2, int y2, int argb) { g.fill(x1, y1, x2, y2, argb); }
    @Override public void text(String s, int x, int y, int argb, boolean shadow) { g.text(font, s, x, y, argb, shadow); }
    @Override public void textBold(String s, int x, int y, int argb) {
        g.text(font, Component.literal(s).withStyle(ChatFormatting.BOLD), x, y, argb, false);
    }
    @Override public int textWidth(String s) { return font.width(s); }
    @Override public void push() { g.pose().pushMatrix(); }
    @Override public void pop() { g.pose().popMatrix(); }
    @Override public void translate(float x, float y) { g.pose().translate(x, y); }
    @Override public void scale(float s) { g.pose().scale(s, s); }
    @Override public void scissor(int x1, int y1, int x2, int y2) { g.enableScissor(x1, y1, x2, y2); }
    @Override public void noScissor() { g.disableScissor(); }

    /** Esineen piirto haetaan nimellä, jotta pienet nimimuutokset eivät estä käännöstä. */
    @Override public void item(Object stack, int x, int y) {
        ItemStack st = (ItemStack) stack;
        try {
            if (ITEM != null) {
                ITEM.invoke(g, st, x, y);
                if (DECOR != null) DECOR.invoke(g, font, st, x, y);
                return;
            }
        } catch (Exception ignored) {}
        g.fill(x, y, x + 16, y + 16, 0xFF6B5B8C);
    }
}
