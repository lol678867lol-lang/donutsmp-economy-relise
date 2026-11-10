package de.donutmod.core;

import java.util.*;

public class Economy {
    private final Map<UUID, Long> balances = new HashMap<>();
    public long get(UUID p) { return balances.getOrDefault(p, 0L); }
    public void add(UUID p, long amount) { balances.merge(p, amount, Sat::add); }   // saturiert: bleibt am Maximum, geht nie ins Minus
    public boolean take(UUID p, long amount) {
        if (get(p) < amount) return false;
        add(p, -amount);
        return true;
    }
    public Map<UUID, Long> snapshot() { return new HashMap<>(balances); }
    public void load(Map<UUID, Long> data) {
        balances.clear();
        for (var e : data.entrySet()) balances.put(e.getKey(), e.getValue() < 0 ? Long.MAX_VALUE : e.getValue());   // alte Ueberlauf-Staende reparieren
    }
}
