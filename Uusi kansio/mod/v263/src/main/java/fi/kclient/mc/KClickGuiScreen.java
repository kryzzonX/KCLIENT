package fi.kclient.mc;

import fi.kclient.ClickGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class KClickGuiScreen extends Screen {
    private final ClickGui gui = new ClickGui();

    public KClickGuiScreen() {
        super(Component.literal("K Client"));
        gui.onClose = this::onClose;
    }

    @Override protected void init() { gui.init(this.width, this.height); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        gui.render(new MojGfx(graphics), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return gui.click(event.x(), event.y(), event.button());
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return gui.drag(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { gui.release(); return super.mouseReleased(event); }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) { gui.scroll(scrollY); return true; }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (KClient.menuKey.matches(event)) { onClose(); return true; }
        return super.keyPressed(event);
    }
}
