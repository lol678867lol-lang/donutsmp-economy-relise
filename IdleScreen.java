package de.donutmod.fabric;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Idle-Screen: Wer 1 Minute nichts macht, sieht "Made by WerIstDas88"; sobald er sich bewegt, verschwindet es. */
final class IdleScreen {
    private IdleScreen() {}

    static final int IDLE_TICKS = 60 * 20;   // 1 Minute

    private static final class St {
        double x, y, z;
        float yaw, pitch;
        long since;
        boolean shown;
    }

    private static final Map<UUID, St> STATE = new HashMap<>();

    /** Mehrmals pro Sekunde aufrufen (Bewegung wird so schnell erkannt). */
    static void tick(MinecraftServer server, long tick) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID u = p.getUUID();
            online.add(u);
            St s = STATE.get(u);
            if (s == null) { s = new St(); s.since = tick; remember(s, p); STATE.put(u, s); }
            if (!Settings.idleScreen) {             // in /opsettings ausgeschaltet
                if (s.shown) { s.shown = false; hide(p); }
                remember(s, p);
                s.since = tick;
                continue;
            }
            boolean menuOpen = p.containerMenu != p.inventoryMenu;   // /sell, /ah, /order ... gelten als "etwas machen"
            boolean moved = menuOpen
                    || Math.abs(p.getX() - s.x) > 0.01 || Math.abs(p.getY() - s.y) > 0.01 || Math.abs(p.getZ() - s.z) > 0.01
                    || Math.abs(p.getYRot() - s.yaw) > 0.5f || Math.abs(p.getXRot() - s.pitch) > 0.5f;
            if (moved) {
                remember(s, p);
                s.since = tick;
                if (s.shown) { s.shown = false; hide(p); }
            } else if (!s.shown && tick - s.since >= IDLE_TICKS) {
                s.shown = true;
                show(p);
            }
        }
        STATE.keySet().retainAll(online);
    }

    private static void remember(St s, ServerPlayer p) {
        s.x = p.getX(); s.y = p.getY(); s.z = p.getZ();
        s.yaw = p.getYRot(); s.pitch = p.getXRot();
    }

    private static void show(ServerPlayer p) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(20, 1_000_000, 10));   // bleibt, bis hide() es entfernt
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6§lMade by §e§lWerIstDas88")));
    }

    private static void hide(ServerPlayer p) {
        p.connection.send(new ClientboundClearTitlesPacket(true));
    }

    /** Beim Verlassen aufraeumen. */
    static void forget(UUID u) { STATE.remove(u); }
}
