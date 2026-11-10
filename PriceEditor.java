package de.donutmod.fabric;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** /opsettings -> "Preise ändern": Liste aller Items mit Preis; Klick auf ein Item, neuen Preis in den Chat tippen. */
final class PriceEditor {
    private PriceEditor() {}

    /** Spieler, von denen die naechste Chat-Nachricht eine Eingabe ist (Preis oder Suchbegriff). */
    private static final Map<UUID, Consumer<String>> PROMPT = new HashMap<>();

    static void register(DonutMod m) {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            Consumer<String> c = PROMPT.remove(sender.getUUID());
            if (c == null) return true;
            String q = message.signedContent().trim();
            m.srv.execute(() -> c.accept(q));
            return false;   // Eingabe nicht als normale Chat-Nachricht zeigen
        });
    }

    static void ask(ServerPlayer p, String text, Consumer<String> then) {
        PROMPT.put(p.getUUID(), then);
        p.closeContainer();
        Gui.say(p, text);
    }

    static boolean cancel(String in) {
        String s = in.toLowerCase(Locale.ROOT);
        return s.equals("abbrechen") || s.equals("cancel");
    }

    /** Alle Item-IDs (Vanilla, Mods, eigene Items), optional nach Suchbegriff gefiltert. */
    private static List<String> ids(String filter) {
        List<String> all = new ArrayList<>();
        for (String id : DonutMod.ITEM_BY_ID.keySet()) if (!id.equals("minecraft:air")) all.add(id);
        for (String id : Custom.BASE.keySet()) if (!all.contains(id)) all.add(id);
        Collections.sort(all);
        if (filter.isEmpty()) return all;
        List<String> out = new ArrayList<>();
        for (String id : all) if (Gui.matches(filter, id, Map.of(), DonutMod.ITEM_BY_ID.get(id))) out.add(id);
        return out;
    }

    static void openList(DonutMod m, ServerPlayer p, String filter) {
        List<String> ids = ids(filter);
        Gui.ListGui<String> g = new Gui.ListGui<>(m, p, "Preise: " + (filter.isEmpty() ? "alle" : filter),
                () -> ids,
                id -> Gui.withLore(m.buildStack(id, Map.of(), 1),
                        "§7Sell-Preis: §a$" + DonutMod.fmt(m.prices.sellUnitPrice(id, Map.of())),
                        "§7AH-Preis: §6$" + DonutMod.fmt(m.prices.unitPrice(id, Map.of())),
                        "§eKlick: Sell-Preis ändern"),
                (id, pl) -> edit(m, pl, id, filter));
        g.extra(47, () -> Gui.named(Items.COMPASS, "§bSuche", "§7Klick: Item im Chat suchen"), () -> search(m, p));
        g.extra(50, () -> PricesReset.button(p.getUUID()), () -> PricesReset.click(m, p));
        g.extra(51, () -> Gui.named(Items.BARRIER, "§cZurück"), () -> Gui.openOpSettings(m, p));
        g.open();
    }

    private static void search(DonutMod m, ServerPlayer p) {
        ask(p, "§eTippe einen Suchbegriff in den Chat (z.B. diamond, netherite). 'abbrechen' bricht ab.", in -> {
            if (cancel(in)) { openList(m, p, ""); return; }
            String q = in.toLowerCase(Locale.ROOT);
            if (ids(q).isEmpty()) { Gui.say(p, "§cKein Item zu diesem Suchbegriff."); openList(m, p, ""); return; }
            openList(m, p, q);
        });
    }

    private static void edit(DonutMod m, ServerPlayer p, String id, String filter) {
        long cur = m.prices.sellUnitPrice(id, Map.of());
        ask(p, "§eNeuer Sell-Preis für §f" + id + " §e(aktuell $" + DonutMod.fmt(cur) + "). Tippe ihn in den Chat, z.B. 1.5k, 2m. 'abbrechen' bricht ab.", in -> {
            if (cancel(in)) { openList(m, p, filter); return; }
            long v = DonutMod.parseAmount(in);
            if (v <= 0) { Gui.say(p, "§cUngültiger Preis. Beispiele: 500, 1.5k, 2m, 3b"); openList(m, p, filter); return; }
            int n = setPrice(m, id, v);
            Gui.say(p, "§aSell-Preis von §f" + id + " §agesetzt auf $" + DonutMod.fmt(v) + " §7(AH: $" + DonutMod.fmt(m.prices.unitPrice(id, Map.of())) + ")"
                    + (n > 0 ? " §7(+" + n + " abhängige Items angepasst)" : ""));
            openList(m, p, filter);
        });
    }

    /** Prozentwert per Chat: "150" = 150 %, "+25" = aktuell + 25, "-30" = aktuell - 30. */
    static void askPercent(DonutMod m, ServerPlayer p, boolean all) {
        ask(p, "§eTippe den Prozentwert in den Chat (100 = normal, z.B. 150 = 50 % teurer, +25, -30). 'abbrechen' bricht ab.", in -> {
            if (!cancel(in)) {
                int cur = all ? Settings.priceFactor : Settings.orderFactor;
                int v = parsePercent(in, cur);
                if (v < 1 || v > 100_000) Gui.say(p, "§cUngültiger Wert (1 bis 100000 %).");
                else {
                    if (all) m.changeFactors(v, Settings.orderFactor); else m.changeFactors(Settings.priceFactor, v);
                    Gui.say(p, (all ? "§aAlle Preise: §f" : "§aOrder-Preise: §f") + v + " %");
                }
            }
            Gui.openOpSettings(m, p);
        });
    }

    /** Hoehenbereich der Fake-Basen per Chat: "von bis", z.B. "-40 -20". */
    static void askHeight(DonutMod m, ServerPlayer p) {
        ask(p, "§eTippe die Höhe (y) als \"von bis\" in den Chat, z.B. -40 -20 (erlaubt: -60 bis 300). 'abbrechen' bricht ab.", in -> {
            if (!cancel(in)) {
                String[] parts = in.trim().replace(",", " ").replace(" bis ", " ").trim().split("\\s+");
                try {
                    int a = Integer.parseInt(parts[0]), b = Integer.parseInt(parts.length > 1 ? parts[1] : parts[0]);
                    if (Math.min(a, b) < Settings.BASE_Y_LOW || Math.max(a, b) > Settings.BASE_Y_HIGH) throw new NumberFormatException();
                    Settings.setBaseY(a, b);
                    Gui.say(p, "§aBasen-Höhe: y " + Settings.baseYMin + " bis " + Settings.baseYMax);
                } catch (Exception e) { Gui.say(p, "§cUngültig. Beispiel: -40 -20 (zwischen -60 und 300)."); }
            }
            Gui.openOpSettings(m, p);
        });
    }

    /** -1 bei ungueltiger Eingabe. */
    static int parsePercent(String in, int current) {
        String s = in.trim().replace("%", "").replace(",", ".");
        try {
            double v = Double.parseDouble(s);
            return (int) Math.round(s.startsWith("+") || s.startsWith("-") ? current + v : v);
        } catch (NumberFormatException e) { return -1; }
    }

    /**
     * Setzt den SELL-Preis (das, was /sell pro Stueck zahlt). Der AH-Preis ergibt sich daraus (Sell-Preis / sellFactor).
     * Abhaengige Items ziehen mit. Rueckgabe: Anzahl mitgezogener Items.
     */
    static int setPrice(DonutMod m, String id, long sellPrice) {
        m.prices.resetFactor(id);   // Nachfrage-Abschlag/-Aufschlag weg, damit genau der eingetippte Sell-Preis gilt
        double perBase = Math.max(1e-9, m.prices.priceMultiplier * m.prices.sellFactor);
        long base = Math.max(1, Math.round(sellPrice / perBase));
        return PricePropagation.set(m, id, base);
    }
}
