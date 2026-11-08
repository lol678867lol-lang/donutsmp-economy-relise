package de.donutmod.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.donutmod.core.RecipeGraph;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Liest alle Rezepte (Vanilla und andere Mods) aus den Datenpaketen und fuettert den RecipeGraph. */
final class RecipeLoader {
    private RecipeLoader() {}

    /** Haeufige Tags -> ein typisches Item (Tags selbst aufzuloesen waere versionsabhaengig). */
    static final Map<String, String> TAGS = new HashMap<>();
    static {
        String[][] t = {
                {"planks", "oak_planks"}, {"logs", "oak_log"}, {"logs_that_burn", "oak_log"}, {"oak_logs", "oak_log"},
                {"wooden_slabs", "oak_slab"}, {"wooden_stairs", "oak_stairs"}, {"wooden_fences", "oak_fence"},
                {"wooden_doors", "oak_door"}, {"wooden_trapdoors", "oak_trapdoor"}, {"wooden_buttons", "oak_button"},
                {"wooden_pressure_plates", "oak_pressure_plate"}, {"wool", "white_wool"}, {"sand", "sand"},
                {"stone_crafting_materials", "cobblestone"}, {"stone_tool_materials", "cobblestone"},
                {"coals", "coal"}, {"soul_fire_base_blocks", "soul_sand"}, {"fences", "oak_fence"},
                {"boats", "oak_boat"}, {"signs", "oak_sign"}, {"banners", "white_banner"}, {"beds", "white_bed"},
                {"terracotta", "terracotta"}, {"candles", "candle"}, {"fishes", "cod"}, {"eggs", "egg"}
        };
        for (String[] e : t) TAGS.put("minecraft:" + e[0], "minecraft:" + e[1]);
    }

    static void load(RecipeGraph g) {
        try {
            var found = DonutMod.INSTANCE.srv.getResourceManager().listResources("recipe", id -> id.getPath().endsWith(".json"));
            for (var e : found.entrySet()) {
                try (BufferedReader r = e.getValue().openAsReader()) {
                    parse(JsonParser.parseReader(r).getAsJsonObject(), g);
                } catch (Exception ignored) { /* unbekanntes oder kaputtes Rezept ueberspringen */ }
            }
        } catch (Exception ex) { System.err.println("[DonutMod] Rezepte lesen fehlgeschlagen: " + ex); }
        System.out.println("[DonutMod] Rezepte fuer Preise: " + g.size() + " Items");
    }

    private static void parse(JsonObject o, RecipeGraph g) {
        if (!o.has("type")) return;
        String type = o.get("type").getAsString();
        String t = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        JsonElement res = o.has("result") ? o.get("result") : o.get("results");
        if (res != null && res.isJsonArray()) res = res.getAsJsonArray().isEmpty() ? null : res.getAsJsonArray().get(0);
        if (res == null) return;
        String out; int outCount = 1;
        if (res.isJsonPrimitive()) out = res.getAsString();
        else {
            JsonObject ro = res.getAsJsonObject();
            JsonElement idEl = ro.has("id") ? ro.get("id") : ro.get("item");
            if (idEl == null) return;
            out = idEl.getAsString();
            if (ro.has("count")) outCount = ro.get("count").getAsInt();
        }
        if (!out.contains(":")) out = "minecraft:" + out;
        List<RecipeGraph.Ing> ings = new ArrayList<>();
        switch (t) {
            case "crafting_shaped" -> {
                Map<Character, Integer> counts = new HashMap<>();
                for (JsonElement row : o.getAsJsonArray("pattern"))
                    for (char c : row.getAsString().toCharArray()) if (c != ' ') counts.merge(c, 1, Integer::sum);
                JsonObject key = o.getAsJsonObject("key");
                for (var k : counts.entrySet()) {
                    List<String> opts = options(key.get(String.valueOf(k.getKey())));
                    if (opts.isEmpty()) return;
                    ings.add(new RecipeGraph.Ing(opts, k.getValue()));
                }
            }
            case "crafting_shapeless" -> {
                for (JsonElement el : o.getAsJsonArray("ingredients")) {
                    List<String> opts = options(el);
                    if (opts.isEmpty()) return;
                    ings.add(new RecipeGraph.Ing(opts, 1));
                }
            }
            case "smelting", "blasting", "smoking", "campfire_cooking" -> {
                List<String> opts = options(o.get("ingredient"));
                if (opts.isEmpty()) return;
                ings.add(new RecipeGraph.Ing(opts, 1));
            }
            case "smithing_transform" -> {
                for (String f : new String[]{"template", "base", "addition"}) {
                    List<String> opts = options(o.get(f));
                    if (opts.isEmpty()) return;
                    ings.add(new RecipeGraph.Ing(opts, 1));
                }
            }
            default -> {
                // Unbekannter (Mod-)Rezepttyp: generisch versuchen, "ingredients"/"ingredient" zu lesen
                JsonElement all = o.has("ingredients") ? o.get("ingredients") : o.get("ingredient");
                if (all == null) return;
                if (all.isJsonArray()) {
                    for (JsonElement el : all.getAsJsonArray()) {
                        List<String> opts = options(el);
                        if (opts.isEmpty()) return;
                        ings.add(new RecipeGraph.Ing(opts, 1));
                    }
                } else {
                    List<String> opts = options(all);
                    if (opts.isEmpty()) return;
                    ings.add(new RecipeGraph.Ing(opts, 1));
                }
                if (ings.isEmpty()) return;
            }
        }
        g.add(new RecipeGraph.Recipe(out, outCount, ings));
    }

    /** Zutat als Liste moeglicher Item-IDs. */
    private static List<String> options(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el == null) return out;
        if (el.isJsonPrimitive()) add(out, el.getAsString());
        else if (el.isJsonObject()) {                       // altes/Mod-Format: {"item": "..."} oder {"tag": "..."}
            JsonObject jo = el.getAsJsonObject();
            if (jo.has("item")) add(out, jo.get("item").getAsString());
            else if (jo.has("id")) add(out, jo.get("id").getAsString());
            else if (jo.has("tag")) add(out, "#" + jo.get("tag").getAsString());
        } else if (el.isJsonArray()) for (JsonElement x : el.getAsJsonArray()) out.addAll(options(x));
        return out;
    }

    private static void add(List<String> out, String s) {
        if (!s.startsWith("#")) { out.add(s.contains(":") ? s : "minecraft:" + s); return; }
        String tag = s.substring(1);
        if (!tag.contains(":")) tag = "minecraft:" + tag;
        String rep = TAGS.get(tag);
        if (rep != null) out.add(rep);   // unbekannte Tags: keine Option -> Rezept wird uebersprungen
    }
}
