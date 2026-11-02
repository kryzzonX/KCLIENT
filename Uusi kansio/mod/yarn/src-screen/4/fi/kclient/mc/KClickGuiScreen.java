package fi.kclient.mc;

import fi.kclient.ClickGui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class KClickGuiScreen extends Screen {
    private final ClickGui gui = new ClickGui();

    public KClickGuiScreen() {
        super(Text.literal("K Client"));
        gui.onClose = this::close;
    }

    @Override protected void init() { gui.init(width, height); }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        gui.render(new YarnGfx(ctx), mx, my);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) { return gui.click(mx, my, button); }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return gui.drag(mx, my) || super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) { gui.release(); return super.mouseReleased(mx, my, button); }
    @Override public boolean mouseScrolled(double mx, double my, double h, double v) { gui.scroll(v); return true; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KClient.menuKey.matchesKey(keyCode, scanCode)) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
