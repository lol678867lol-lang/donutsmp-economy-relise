package de.donutmod.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.donutmod.core.PriceBook;
import de.donutmod.core.RecipeGraph;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Jedes Item im Spiel bekommt einen eigenen Preis. Beim ersten Start wird
 * config/donutmod-prices.json mit ALLEN Items erzeugt (bekannte Preise + Schaetzung).
 * Die Datei kann man von Hand aendern. Zum Neu-Erzeugen: Datei loeschen.
 */
public class PriceFile {
    /** Alle Basis-Preise werden damit multipliziert (DonutSMP-artiges Preisniveau). Zum Anpassen hier aendern. */
    static final long MULT = 48;
    /** Premium-Items mit festen Preisen (nicht multipliziert). */
    static final Map<String, Long> HIGH = Map.ofEntries(
            Map.entry("minecraft:mace", 2_500_000L), Map.entry("minecraft:elytra", 1_500_000L),
            Map.entry("minecraft:totem_of_undying", 800_000L), Map.entry("minecraft:heavy_core", 4_000_000L), Map.entry("minecraft:dragon_head", 6_000_000L),
            Map.entry("minecraft:nether_star", 1_500_000L), Map.entry("minecraft:beacon", 2_500_000L),
            Map.entry("minecraft:enchanted_golden_apple", 1_000_000L), Map.entry("minecraft:trident", 600_000L),
            Map.entry("minecraft:dragon_egg", 5_000_000L),
            Map.entry("minecraft:netherite_upgrade_smithing_template", 400_000L));

    /** Preisliste PRO WELT (im Weltordner), damit jede Welt eigene Preise hat. */
    static Path file() { return DonutMod.worldDir().resolve("donutmod-prices-v8.json"); }

    public static void loadInto(PriceBook prices) {
        try {
            if (!Files.exists(file())) generate();
            Map<String, Long> m = new Gson().fromJson(Files.readString(file()), new TypeToken<Map<String, Long>>() {}.getType());
            if (m != null) m.forEach((id, price) -> prices.setBase(id, price));
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Preisliste fehlgeschlagen: " + e); }
    }

    /** Speichert mehrere geaenderte Basispreise auf einmal. */
    static synchronized void setAll(Map<String, Long> changes) {
        try {
            Map<String, Long> m = new TreeMap<>();
            if (Files.exists(file())) {
                Map<String, Long> old = new Gson().fromJson(Files.readString(file()), new TypeToken<Map<String, Long>>() {}.getType());
                if (old != null) m.putAll(old);
            }
            m.putAll(changes);
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(m));
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Preise speichern fehlgeschlagen: " + e); }
    }

    /** Speichert einen geaenderten Basispreis in der Preisdatei (bleibt nach dem Neustart erhalten). */
    static synchronized void set(String id, long base) {
        try {
            Map<String, Long> m = new TreeMap<>();
            if (Files.exists(file())) {
                Map<String, Long> old = new Gson().fromJson(Files.readString(file()), new TypeToken<Map<String, Long>>() {}.getType());
                if (old != null) m.putAll(old);
            }
            m.put(id, base);
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(m));
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Preis speichern fehlgeschlagen: " + e); }
    }

    /** Berechnet die komplette Standard-Preisliste (Rezept-Preise, Schaetzungen, feste Preise) - auch fuer Items anderer Mods. */
    static Map<String, Long> compute() {
        Map<String, Long> known = new HashMap<>();
        for (Object[] r : DonutMod.KNOWN) known.put("minecraft:" + r[0], ((Number) r[1]).longValue());
        Map<String, Long> old = new TreeMap<>();     // Fixpreise + Schaetzung (wie bisher)
        Map<String, Long> fixed = new HashMap<>();   // Items mit festem Preis: werden nicht aus Rezepten berechnet
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (id.equals("minecraft:air")) continue;
            Long k = known.get(id);
            Long hi = HIGH.get(id);
            long price = hi != null ? hi : (k != null ? k : estimate(item, id)) * MULT;
            old.put(id, price);
            if (hi != null || k != null) fixed.put(id, price);
        }
        // Rezept-Preise: Summe der Zutaten + 10 %, nie billiger als das Ausgangsmaterial (auch fuer Items anderer Mods)
        RecipeGraph g = new RecipeGraph(fixed::get, id -> old.getOrDefault(id, 100L));
        RecipeLoader.load(g);
        Map<String, Long> out = new TreeMap<>();
        for (String id : old.keySet())
            out.put(id, !fixed.containsKey(id) && g.has(id) ? g.price(id) : old.get(id));
        return out;
    }

    static void generate() throws java.io.IOException {
        Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(compute()));
    }

    /**
     * Items, die in der Preisliste fehlen (z.B. weil eine Mod nach dem Erstellen der Welt dazugekommen ist), bekommen ihren
     * Standardpreis. Sonst haetten sie nur den Platzhalterpreis. Rueckgabe: Anzahl ergaenzter Items.
     */
    static int fillMissing(PriceBook prices) {
        try {
            boolean any = false;
            for (Item item : BuiltInRegistries.ITEM) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                if (!id.equals("minecraft:air") && prices.baseOf(id) == null) { any = true; break; }
            }
            if (!any) return 0;
            Map<String, Long> all = compute();
            Map<String, Long> add = new TreeMap<>();
            for (var e : all.entrySet()) if (prices.baseOf(e.getKey()) == null) add.put(e.getKey(), e.getValue());
            add.forEach(prices::setBase);
            if (!add.isEmpty()) setAll(add);
            return add.size();
        } catch (Exception e) {
            System.err.println("[Donutsmp Economy] Fehlende Preise ergaenzen fehlgeschlagen: " + e);
            return 0;
        }
    }

    static final String[][] MAT = {{"netherite", "1800"}, {"diamond", "500"}, {"golden", "120"}, {"gold", "120"},
            {"iron", "80"}, {"chainmail", "60"}, {"copper", "30"}, {"stone", "8"}, {"wooden", "5"}, {"leather", "15"}, {"turtle", "200"}};
    static final String[][] SHAPE = {{"sword", "2"}, {"pickaxe", "3"}, {"axe", "3"}, {"shovel", "1"}, {"hoe", "2"},
            {"helmet", "5"}, {"chestplate", "8"}, {"leggings", "7"}, {"boots", "4"}, {"horse_armor", "6"}, {"spear", "2"}};
    static final Object[][] WORDS = {
            {"spawn_egg", 5000}, {"smithing_template", 8000}, {"music_disc", 3000}, {"banner_pattern", 1500},
            {"pottery_sherd", 700}, {"_head", 2500}, {"skull", 2500}, {"shulker_box", 3500}, {"_ore", 50},
            {"raw_", 40}, {"nugget", 5}, {"stripped_", 6}, {"_log", 6}, {"_wood", 6}, {"planks", 2},
            {"_stairs", 3}, {"_slab", 2}, {"_wall", 3}, {"_fence", 4}, {"_door", 8}, {"trapdoor", 8},
            {"_sign", 6}, {"chest_boat", 25}, {"_boat", 20}, {"_wool", 6}, {"carpet", 3},
            {"concrete_powder", 5}, {"concrete", 6}, {"glazed_terracotta", 10}, {"terracotta", 5},
            {"stained_glass", 6}, {"glass", 4}, {"_bed", 40}, {"_dye", 6}, {"candle", 12}, {"coral", 15},
            {"sapling", 8}, {"seeds", 3}, {"leaves", 1}, {"_button", 3}, {"pressure_plate", 5},
            {"potion", 150}, {"_banner", 40}, {"minecart", 60}, {"rail", 12}, {"_bricks", 5}, {"brick", 4},
            {"copper", 10}};

    static long estimate(Item item, String id) {
        String p = id.substring(id.indexOf(':') + 1);
        double v = base(item, p);
        double jitter = 0.85 + (Math.abs(p.hashCode() % 31)) / 100.0; // 0.85 .. 1.15, fest pro Item
        return Math.max(1, Math.round(v * jitter));
    }

    static double base(Item item, String p) {
        for (String[] m : MAT) {
            if (!p.contains(m[0])) continue;
            for (String[] s : SHAPE) if (p.contains(s[0])) return Double.parseDouble(m[1]) * Double.parseDouble(s[1]);
        }
        for (Object[] w : WORDS) if (p.contains((String) w[0])) return ((Number) w[1]).doubleValue();
        ItemStack d = item.getDefaultInstance();
        double[] rarity = {3, 40, 400, 4000};
        double r = rarity[Math.min(d.getRarity().ordinal(), 3)];
        int ms = d.getMaxStackSize();
        double f = ms >= 64 ? 1 : ms >= 16 ? 2 : 3;
        return r * f + Math.max(0, d.getMaxDamage()) * 1;
    }
}
