package de.donutmod.core;

/** Rechnen ohne Ueberlauf: bei zu grossen Zahlen bleibt das Ergebnis am Maximum (Long.MAX_VALUE) stehen und wird nie negativ. */
public final class Sat {
    private Sat() {}

    public static long add(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;   // Ueberlauf
        return r;
    }

    public static long mul(long a, long b) {
        long hi = Math.multiplyHigh(a, b), lo = a * b;
        if ((hi == 0 && lo >= 0) || (hi == -1 && lo < 0)) return lo;                 // passt in ein long
        return (a < 0) == (b < 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
}
