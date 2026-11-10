package de.donutmod.fabric;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Einstellungen: /settings (pro Spieler: Geld-Anzeige, Money Name Tags; global: kurze Zahlen) und /mod (Mod-Items an/aus). */
public final class Settings {
    private Settings() {}

    /** Kurze Zahlen (k/m/b/t/q/qi) statt voller Zahl, z.B. 3587 -> 3.5k. */
    static boolean shortNumbers = true;
    /** Items anderer Mods verkaufbar und in /ah und Orders. */
    static boolean modItems = true;
    /** /bot on|off: Bots fuellen /ah und Orders und kaufen/liefern. */
    static boolean bots = true;
    /** /fakebase on|off: automatische Fake-Basen (Stashes/Farmen) unter der Erde. */
    static boolean fakeBases = true;
    /** Idle-Screen "Made by WerIstDas88" nach 1 Minute ohne Bewegung. */
    static boolean idleScreen = true;
    /** /bot tick <n>: Bots kaufen und liefern n-mal schneller (1 = normal). */
    static int botTick = 1;
    /** /spawner tick <n>: Spawner sammeln n-mal schneller (1 = normal). */
    static int spawnerTick = 1;
    static final int MAX_TICK = 999_999;
    /** /opsettings: Preise aller Items in Prozent (100 = normal) und zusaetzlich nur die Bot-Order-Preise. */
    /** Boden-Hoehe (y) der neuen Fake-Basen: zufaellig zwischen baseYMin und baseYMax. */
    static int baseYMin = -46, baseYMax = -20;
    static final int BASE_Y_LOW = -60, BASE_Y_HIGH = 300;
    private static void setBaseYNoSave() {
        int a = Math.max(BASE_Y_LOW, Math.min(BASE_Y_HIGH, Math.min(baseYMin, baseYMax)));
        int b = Math.max(BASE_Y_LOW, Math.min(BASE_Y_HIGH, Math.max(baseYMin, baseYMax)));
        baseYMin = a; baseYMax = b;
    }
    static void setBaseY(int min, int max) {
        int a = Math.max(BASE_Y_LOW, Math.min(BASE_Y_HIGH, Math.min(min, max)));
        int b = Math.max(BASE_Y_LOW, Math.min(BASE_Y_HIGH, Math.max(min, max)));
        baseYMin = a; baseYMax = b; save();
    }
    static int priceFactor = 100;
    static int orderFactor = 100;
    static int clampPct(int v) { return Math.max(1, Math.min(100_000, v)); }
    /** Pro Spieler: [0] = Geld-Anzeige (Sidebar), [1] = Money Name Tags (Geld unter dem Namen). */
    private static final Map<UUID, boolean[]> PLAYERS = new HashMap<>();

    private static boolean[] of(UUID u) { return PLAYERS.computeIfAbsent(u, k -> new boolean[]{true, true}); }

    static boolean hud(UUID u) { return of(u)[0]; }
    static boolean tags(UUID u) { return of(u)[1]; }
    static void setHud(UUID u, boolean v) { of(u)[0] = v; save(); }
    static void setTags(UUID u, boolean v) { of(u)[1] = v; save(); }
    static void setShort(boolean v) { shortNumbers = v; save(); }
    static void setMods(boolean v) { modItems = v; save(); }
    static void setBots(boolean v) { bots = v; save(); }
    static void setFakeBases(boolean v) { fakeBases = v; save(); }
    static void setIdle(boolean v) { idleScreen = v; save(); }
    static void setBotTick(int v) { botTick = Math.max(1, Math.min(MAX_TICK, v)); save(); }
    static void setFactors(int price, int order) { priceFactor = clampPct(price); orderFactor = clampPct(order); save(); }
    static void setSpawnerTick(int v) { spawnerTick = Math.max(1, Math.min(MAX_TICK, v)); save(); }

    static java.nio.file.Path file() { return DonutMod.worldDir().resolve("donutmod-settings.json"); }

    static void load() {
        PLAYERS.clear();
        shortNumbers = true;
        modItems = true;
        bots = true;
        fakeBases = true;
        idleScreen = true;
        botTick = 1;
        spawnerTick = 1;
        priceFactor = 100;
        orderFactor = 100;
        baseYMin = -46;
        baseYMax = -20;
        try {
            if (!Files.exists(file())) return;
            JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            if (o.has("shortNumbers")) shortNumbers = o.get("shortNumbers").getAsBoolean();
            if (o.has("modItems")) modItems = o.get("modItems").getAsBoolean();
            if (o.has("bots")) bots = o.get("bots").getAsBoolean();
            if (o.has("fakeBases")) fakeBases = o.get("fakeBases").getAsBoolean();
            if (o.has("idleScreen")) idleScreen = o.get("idleScreen").getAsBoolean();
            if (o.has("botTick")) botTick = Math.max(1, Math.min(MAX_TICK, o.get("botTick").getAsInt()));
            if (o.has("baseYMin") && o.has("baseYMax")) { baseYMin = o.get("baseYMin").getAsInt(); baseYMax = o.get("baseYMax").getAsInt(); setBaseYNoSave(); }
            if (o.has("priceFactor")) priceFactor = clampPct(o.get("priceFactor").getAsInt());
            if (o.has("orderFactor")) orderFactor = clampPct(o.get("orderFactor").getAsInt());
            if (o.has("spawnerTick")) spawnerTick = Math.max(1, Math.min(MAX_TICK, o.get("spawnerTick").getAsInt()));
            if (o.has("players"))
                for (var e : o.getAsJsonObject("players").entrySet()) {
                    JsonObject v = e.getValue().getAsJsonObject();
                    PLAYERS.put(UUID.fromString(e.getKey()), new boolean[]{
                            !v.has("hud") || v.get("hud").getAsBoolean(), !v.has("tags") || v.get("tags").getAsBoolean()});
                }
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Einstellungen laden fehlgeschlagen: " + e); }
    }

    static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("shortNumbers", shortNumbers);
            o.addProperty("modItems", modItems);
            o.addProperty("bots", bots);
            o.addProperty("fakeBases", fakeBases);
            o.addProperty("idleScreen", idleScreen);
            o.addProperty("botTick", botTick);
            o.addProperty("spawnerTick", spawnerTick);
            o.addProperty("baseYMin", baseYMin);
            o.addProperty("baseYMax", baseYMax);
            o.addProperty("priceFactor", priceFactor);
            o.addProperty("orderFactor", orderFactor);
            JsonObject pl = new JsonObject();
            for (var e : PLAYERS.entrySet()) {
                JsonObject v = new JsonObject();
                v.addProperty("hud", e.getValue()[0]);
                v.addProperty("tags", e.getValue()[1]);
                pl.add(e.getKey().toString(), v);
            }
            o.add("players", pl);
            DonutMod.writeSafe(file(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Einstellungen speichern fehlgeschlagen: " + e); }
    }
}
