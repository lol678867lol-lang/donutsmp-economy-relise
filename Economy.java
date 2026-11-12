package de.donutmod.core;

import java.math.BigInteger;
import java.util.*;

/** Kontostaende ohne Obergrenze (BigInteger): Geld kann unendlich hoch werden und geht nie ins Minus durch Ueberlauf. */
public class Economy {
    private static final BigInteger LMAX = BigInteger.valueOf(Long.MAX_VALUE), LMIN = BigInteger.valueOf(Long.MIN_VALUE);
    private final Map<UUID, BigInteger> balances = new HashMap<>();

    /** Voller Kontostand (beliebig gross). */
    public BigInteger getBig(UUID p) { return balances.getOrDefault(p, BigInteger.ZERO); }

    /** Kontostand, begrenzt auf long - fuer Vergleiche und Rechnungen mit normalen Betraegen. */
    public long get(UUID p) {
        BigInteger b = getBig(p);
        return b.compareTo(LMAX) > 0 ? Long.MAX_VALUE : b.compareTo(LMIN) < 0 ? Long.MIN_VALUE : b.longValue();
    }

    public void add(UUID p, long amount) { addBig(p, BigInteger.valueOf(amount)); }
    public void addBig(UUID p, BigInteger amount) { balances.merge(p, amount, BigInteger::add); }

    public boolean take(UUID p, long amount) { return takeBig(p, BigInteger.valueOf(amount)); }
    public boolean takeBig(UUID p, BigInteger amount) {
        if (getBig(p).compareTo(amount) < 0) return false;
        addBig(p, amount.negate());
        return true;
    }

    public Map<UUID, BigInteger> snapshotBig() { return new HashMap<>(balances); }

    public Map<UUID, Long> snapshot() {
        Map<UUID, Long> m = new HashMap<>();
        for (UUID u : balances.keySet()) m.put(u, get(u));
        return m;
    }

    public void loadBig(Map<UUID, BigInteger> data) {
        balances.clear();
        for (var e : data.entrySet()) balances.put(e.getKey(), e.getValue().signum() < 0 ? LMAX : e.getValue());   // alte Ueberlauf-Staende reparieren
    }

    public void load(Map<UUID, Long> data) {
        Map<UUID, BigInteger> m = new HashMap<>();
        for (var e : data.entrySet()) m.put(e.getKey(), BigInteger.valueOf(e.getValue()));
        loadBig(m);
    }
}
