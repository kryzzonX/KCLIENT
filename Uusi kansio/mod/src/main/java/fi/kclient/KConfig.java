package fi.kclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KConfig {
    public record Mod(String id, String cat, String fi, String en, String dfi, String den, boolean def) {
        public String name() { return KConfig.fi() ? fi : en; }
        public String desc() { return KConfig.fi() ? dfi : den; }
    }

    public static final List<Mod> MODS = List.of(
        new Mod("fps", "hud", "FPS-laskuri", "FPS counter", "Näyttää ruudunpäivityksen", "Shows your frame rate", true),
        new Mod("cps", "hud", "CPS-laskuri", "CPS counter", "Klikkaukset sekunnissa (vasen | oikea)", "Clicks per second (left | right)", true),
        new Mod("ping", "hud", "Ping", "Ping", "Viive palvelimelle", "Latency to the server", true),
        new Mod("combo", "hud", "Combo-laskuri", "Combo counter", "Peräkkäiset osumat ilman osumaa itseesi", "Consecutive hits without being hit", false),
        new Mod("coords", "hud", "Koordinaatit", "Coordinates", "XYZ-sijaintisi", "Your XYZ position", false),
        new Mod("direction", "hud", "Suunta", "Direction", "Ilmansuunta, johon katsot", "The direction you face", false),
        new Mod("clock", "hud", "Kello", "Clock", "Oikea kellonaika", "Real-world time", false),
        new Mod("armor", "hud", "Armor HUD", "Armor HUD", "Panssarit ja kestävyys", "Armor pieces and durability", true),
        new Mod("potions", "hud", "Potion HUD", "Potion HUD", "Aktiiviset efektit ja kesto", "Active effects and time left", true),
        new Mod("items", "hud", "Item-laskuri", "Item counter", "Enderhelmet, omenat, totemit, potionit, nuolet", "Pearls, apples, totems, potions, arrows", false),
        new Mod("keys", "hud", "Näppäimet", "Keystrokes", "WASD ja hiiren napit", "WASD and mouse buttons", true),
        new Mod("sprint", "pvp", "Toggle sprint", "Toggle sprint", "Juoksee automaattisesti eteenpäin", "Sprints automatically while moving", true),
        new Mod("zoom", "pvp", "Zoom (C)", "Zoom (C)", "Pidä C-näppäintä zoomataksesi", "Hold C to zoom in", true)
    );

    private static final Map<String, Boolean> DEF_BOOL = Map.of(
        "hudBg", true, "hudRight", false, "crosshair", false, "xOutline", true, "xDot", false);
    private static final Map<String, Double> DEF_NUM = Map.of(
        "hudScale", 1.0, "zoomFov", 30.0, "xSize", 5.0, "xGap", 3.0, "xThick", 1.0, "xStyle", 0.0, "xColor", 0.0);

    private static class Data {
        Map<String, Boolean> on = new HashMap<>();
        Map<String, Double> num = new HashMap<>();
        String lang = "fi";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kclient.json");
    private static Data d = new Data();

    public static void load() {
        try {
            if (Files.exists(FILE)) d = GSON.fromJson(Files.readString(FILE), Data.class);
        } catch (Exception e) {
            d = new Data();
        }
        if (d == null) d = new Data();
        if (d.on == null) d.on = new HashMap<>();
        if (d.num == null) d.num = new HashMap<>();
        if (d.lang == null) d.lang = "fi";
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(d)); } catch (IOException ignored) {}
    }

    public static boolean on(String id) {
        Boolean b = d.on.get(id);
        if (b != null) return b;
        for (Mod m : MODS) if (m.id().equals(id)) return m.def();
        return DEF_BOOL.getOrDefault(id, false);
    }
    public static void toggle(String id) { d.on.put(id, !on(id)); save(); }
    public static double num(String id) { return d.num.getOrDefault(id, DEF_NUM.getOrDefault(id, 0.0)); }
    public static void setNum(String id, double v) { d.num.put(id, v); }
    public static boolean fi() { return "fi".equals(d.lang); }
    public static void toggleLang() { d.lang = fi() ? "en" : "fi"; save(); }
    public static void reset() { d.on.clear(); d.num.clear(); save(); }
}
