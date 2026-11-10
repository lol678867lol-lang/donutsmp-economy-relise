package de.donutmod.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Alle Preise zuruecksetzen": Standardpreise (aus den Rezepten), keine eigenen Aenderungen, Prozent-Regler auf 100 %
 * und die Preissenkung/-erhoehung durch zu viel Verkaufen/Kaufen weg. Vorhandene Bot-Angebote und Bot-Orders werden mitbepreist.
 * Zur Sicherheit muss man den Knopf zweimal innerhalb von 8 Sekunden klicken.
 */
final class PricesReset {
    private PricesReset() {}

    private static final Map<UUID, Long> ARMED = new HashMap<>();

    private static boolean armed(UUID u) {
        Long t = ARMED.get(u);
        return t != null && System.currentTimeMillis() - t < 8000;
    }

    /** Der Knopf (aendert Name, wenn er "scharf" ist). */
    static ItemStack button(UUID u) {
        if (armed(u))
            return Gui.named(Items.TNT, "§c§lNochmal klicken: ALLE Preise zurücksetzen", "§7Jetzt klicken bestätigt das Zurücksetzen.");
        return Gui.named(Items.TNT, "§cAlle Preise zurücksetzen", "§7Setzt alle Preise auf die Standardwerte zurück:",
                "§7eigene Änderungen, Prozent-Regler und die", "§7Preissenkung durch zu viel Verkaufen.", "", "§eKlick (2x zum Bestätigen)");
    }

    static void click(DonutMod m, ServerPlayer p) {
        UUID u = p.getUUID();
        if (armed(u)) {
            ARMED.remove(u);
            int n = resetAll(m);
            Gui.say(p, "§aAlle Preise zurückgesetzt. §7Markt neu bepreist (" + n + " Angebote/Orders).");
        } else {
            ARMED.put(u, System.currentTimeMillis());
            Gui.say(p, "§eNochmal klicken zum Bestätigen (8 Sekunden).");
        }
    }

    /** Rueckgabe: Anzahl neu bepreister Bot-Angebote und Bot-Orders. */
    static int resetAll(DonutMod m) {
        Set<String> ids = new HashSet<>(DonutMod.ITEM_BY_ID.keySet());
        ids.addAll(Custom.BASE.keySet());
        Map<String, Long> oldUnit = new HashMap<>();
        for (String id : ids) oldUnit.put(id, m.prices.unitPrice(id, Map.of()));

        m.prices.resetFactors();                                         // zu viel verkauft/gekauft -> wieder normal
        if (m.market != null) m.market.pop.replaceAll((k, v) -> 1.0);    // Beliebtheit der Items normal
        Settings.setFactors(100, 100);                                   // Prozent-Regler aus /opsettings
        m.applyFactorsNoRescale();
        PricePropagation.deleteEdited();                                 // eigene Preise vergessen
        PricePropagation.reset();
        try { Files.deleteIfExists(PriceFile.file()); } catch (Exception ignored) {}
        PriceFile.loadInto(m.prices);                                    // erzeugt die Standard-Preisliste neu und laedt sie
        Custom.registerPrices(m.prices);

        Map<String, Double> ratios = new HashMap<>();
        for (var e : oldUnit.entrySet()) {
            long o = e.getValue(), n = m.prices.unitPrice(e.getKey(), Map.of());
            if (o > 0 && n != o) ratios.put(e.getKey(), (double) n / o);
        }
        int changed = m.ah.rescaleBotsFor(ratios) + m.orders.rescaleBotsFor(ratios);
        if (m.market != null) { m.market.ahCache.clear(); m.market.ahCacheTime.clear(); m.market.ordersCache = null; }
        return changed;
    }
}
