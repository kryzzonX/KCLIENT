package fi.kclient;

/** Piirtopinta. Jokaisella Minecraft-versiolla on oma toteutus, joten yhteinen koodi ei tunne Minecraftia. */
public interface Gfx {
    int width();
    int height();
    void fill(int x1, int y1, int x2, int y2, int argb);
    void text(String s, int x, int y, int argb, boolean shadow);
    void textBold(String s, int x, int y, int argb);
    int textWidth(String s);
    void push();
    void pop();
    void translate(float x, float y);
    void scale(float s);
    void scissor(int x1, int y1, int x2, int y2);
    void noScissor();
    /** Piirtää esineen (16x16) ja sen kestävyyspalkin. */
    void item(Object stack, int x, int y);
}
