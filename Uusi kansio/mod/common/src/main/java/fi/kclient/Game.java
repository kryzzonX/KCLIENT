package fi.kclient;

import java.util.List;

/** Pelin tila. Jokaisella Minecraft-versiolla on oma toteutus. */
public interface Game {
    int fps();
    int ping();
    double x();
    double y();
    double z();
    String facing();
    int hurtTime();
    /** 0 = W, 1 = A, 2 = S, 3 = D, 4 = vasen hiiri, 5 = oikea hiiri. */
    boolean keyDown(int key);
    boolean zoomKeyDown();
    /** Aktiiviset efektit: {nimi, kesto}. */
    List<String[]> effects();
    /** Pää, rinta, jalat, jalkineet. Null, jos paikka on tyhjä. */
    Object[] armor();
    /** Seurattavat esineet, joita on repussa: {esine, määrä (Integer)}. */
    List<Object[]> items();
    void tickSprint(boolean on);
    int getFov();
    void setFov(int fov);
}
