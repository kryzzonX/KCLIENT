package fi.kclient.mc;

import fi.kclient.ClickGui;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Minecraft 1.21.9 - 1.21.11. Hiiren paikka otetaan viimeisestä piirrosta, joten tapahtumien koordinaatteja ei tarvita. */
public class KClickGuiScreen extends Screen {
    private final ClickGui gui = new ClickGui();
    private double lastX, lastY;

    public KClickGuiScreen() {
        super(Text.literal("K Client"));
        gui.onClose = this::close;
    }

    @Override protected void init() { gui.init(width, height); }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        lastX = mx;
        lastY = my;
        gui.render(new YarnGfx(ctx), mx, my);
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) { return gui.click(lastX, lastY, 0); }
    @Override public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        return gui.drag(lastX, lastY) || super.mouseDragged(click, offsetX, offsetY);
    }
    @Override public boolean mouseReleased(Click click) { gui.release(); return super.mouseReleased(click); }
    @Override public boolean mouseScrolled(double mx, double my, double h, double v) { gui.scroll(v); return true; }
}
