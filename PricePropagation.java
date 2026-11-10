package de.donutmod.fabric;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.donutmod.core.RecipeGraph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Preis eines Items aendern und alles, was daraus gecraftet wird, mitziehen (z.B. Diamant teurer -> Diamant-Schwert,
 * -Ruestung, ... teurer). Vorhandene Bot-Angebote und Bot-Orders dieser Items werden sofort neu bepreist.
 * Feste Preise (KNOWN/HIGH) und selbst gesetzte Preise bleiben unveraendert.
 */
final class PricePropagation {
    private PricePropagation() {}

    private static RecipeGraph graph;
    private static Set<String> recomputing = Set.of();       // Items, die gerade neu berechnet werden
    private static final Set<String> EDITED = new HashSet<>();   // vom Admin selbst gesetzte Preise (bleiben fest)
    private static Set<String> knownIds;
    private static boolean loaded = false;

    /** Loescht die Liste der selbst gesetzten Preise (beim Zuruecksetzen aller Preise). */
    static void deleteEdited() {
        try { Files.deleteIfExists(editedFile()); } catch (Exception ignored) {}
        EDITED.clear();
    }

    /** Beim Start einer Welt: Zustand der vorherigen Welt vergessen. */
    static void reset() {
        graph = null;
        recomputing = Set.of();
        EDITED.clear();
        loaded = false;
    }

    private static Path editedFile() { return PriceFile.file().resolveSibling("donutmod-prices-edited.json"); }

    private static void loadEdited() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(editedFile())) {
                List<String> l = new Gson().fromJson(Files.readString(editedFile()), new TypeToken<List<String>>() {}.getType());
                if (l != null) EDITED.addAll(l);
            }
        } catch (Exception ignored) {}
    }

    private static void saveEdited() {
        try { Files.writeString(editedFile(), new Gson().toJson(EDITED)); } catch (Exception ignored) {}
    }

    private static boolean fixed(String id) {
        if (knownIds == null) {
            knownIds = new HashSet<>();
            for (Object[] r : DonutMod.KNOWN) knownIds.add("minecraft:" + r[0]);
        }
        return knownIds.contains(id) || PriceFile.HIGH.containsKey(id) || EDITED.contains(id) || id.startsWith("donutmod:");
    }

    private static RecipeGraph graph(DonutMod m) {
        if (graph == null) {
            graph = new RecipeGraph(
                    id -> recomputing.contains(id) ? null : m.prices.baseOf(id),
                    id -> { Long b = m.prices.baseOf(id); return b != null ? b : m.prices.defaultBase; });
            RecipeLoader.load(graph);
        }
        return graph;
    }

    /** Setzt den Basispreis von id, zieht abhaengige Items mit und bepreist den Markt neu. Rueckgabe: Anzahl mitgezogener Items. */
    static int set(DonutMod m, String id, long newBase) {
        loadEdited();
        RecipeGraph g = graph(m);
        Set<String> deps = new HashSet<>(g.dependentsOf(id, PricePropagation::fixed));
        deps.removeIf(PricePropagation::fixed);

        Map<String, Long> oldUnit = new HashMap<>();   // Verkaufspreis je Stueck vorher
        oldUnit.put(id, m.prices.unitPrice(id, Map.of()));
        for (String d : deps) oldUnit.put(d, m.prices.unitPrice(d, Map.of()));

        m.prices.setBase(id, newBase);
        EDITED.add(id);
        Map<String, Long> changed = new HashMap<>();
        changed.put(id, newBase);
        recomputing = deps;
        g.clearMemo();
        try {
            for (String d : deps) {
                if (!g.has(d)) continue;
                long nb = g.price(d);
                Long old = m.prices.baseOf(d);
                if (old == null || nb != old) changed.put(d, nb);
            }
        } finally { recomputing = Set.of(); g.clearMemo(); }
        for (var e : changed.entrySet()) m.prices.setBase(e.getKey(), e.getValue());
        PriceFile.setAll(changed);
        saveEdited();

        // Markt: vorhandene Bot-Angebote und Bot-Orders dieser Items sofort neu bepreisen
        Map<String, Double> ratios = new HashMap<>();
        for (var e : oldUnit.entrySet()) {
            long o = e.getValue(), n = m.prices.unitPrice(e.getKey(), Map.of());
            if (o > 0 && n != o) ratios.put(e.getKey(), (double) n / o);
        }
        m.ah.rescaleBotsFor(ratios);
        m.orders.rescaleBotsFor(ratios);
        if (m.market != null) { m.market.ahCache.clear(); m.market.ahCacheTime.clear(); m.market.ordersCache = null; }
        return Math.max(0, changed.size() - 1);
    }
}
