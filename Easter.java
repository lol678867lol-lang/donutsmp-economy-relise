package de.donutmod.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Easter Egg: Ein Schild "WerIstDas88" bei X=8888, Z=8888 (Overworld). Es erscheint, sobald ein Spieler in der Naehe ist,
 * und steigt dann jede Sekunde um 1 Block nach oben (mit Holzbrett darunter), bis es die Hoehengrenze erreicht.
 */
final class Easter {
    private Easter() {}
    static final int X = 8888, Z = 8888;
    static final int MAX_Y = 318;                       // hoechste Schild-Hoehe (Bauhoehe 320)
    private static int signY = Integer.MIN_VALUE;       // aktuelle Hoehe des Schilds (unbekannt = MIN_VALUE)

    /** Beim Start einer Welt: Schild-Hoehe vergessen (wird neu gesucht). */
    static void reset() { signY = Integer.MIN_VALUE; }

    /** Jede Sekunde aufrufen. Bewegt sich nur, solange ein Spieler (waagerecht) innerhalb von 64 Bloecken ist. */
    static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Level lv = p.level();
            if (!(lv instanceof ServerLevel w) || !w.dimension().equals(Level.OVERWORLD)) continue;
            double dx = p.getX() - X, dz = p.getZ() - Z;
            if (dx * dx + dz * dz > 64 * 64) continue;

            if (signY == Integer.MIN_VALUE) signY = findSign(w);          // nach Neustart: vorhandenes Schild wiederfinden
            if (signY == Integer.MIN_VALUE) signY = w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, X, Z);   // sonst auf dem Boden starten
            else if (signY < MAX_Y) {                                      // 1 Block hoch
                BlockPos old = new BlockPos(X, signY, Z);
                signY++;
                place(w, new BlockPos(X, signY, Z));                       // Brett kommt an die alte Schild-Stelle, neues Schild darueber
                w.setBlock(old.below(), Blocks.AIR.defaultBlockState(), 3);   // altes Brett unten entfernen
            }
            BlockPos pos = new BlockPos(X, signY, Z);
            if (!w.getBlockState(pos).is(Blocks.OAK_SIGN)) place(w, pos);  // falls jemand es abgebaut hat
            return;
        }
    }

    /** Sucht ein vorhandenes Schild in der Saeule (von oben nach unten). */
    private static int findSign(ServerLevel w) {
        for (int y = MAX_Y; y >= -60; y--)
            if (w.getBlockState(new BlockPos(X, y, Z)).is(Blocks.OAK_SIGN)) return y;
        return Integer.MIN_VALUE;
    }

    /** Setzt Holzbrett (unten) und Schild mit Text (oben). */
    private static void place(ServerLevel w, BlockPos pos) {
        w.setBlock(pos.below(), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        w.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState(), 3);
        if (w.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal("WerIstDas88")), true);
            sign.setChanged();
            BlockState st = w.getBlockState(pos);
            w.sendBlockUpdated(pos, st, st, 3);
        }
    }
}
