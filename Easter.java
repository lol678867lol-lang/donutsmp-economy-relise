package de.donutmod.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/** Easter Egg: Ein Schild mit "WerIstDas88" bei X=8888, Z=8888 (Overworld). Erscheint, sobald ein Spieler in der Naehe ist. */
final class Easter {
    private Easter() {}
    static final int X = 8888, Z = 8888;

    static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Level lv = p.level();
            if (!(lv instanceof ServerLevel w) || !w.dimension().equals(Level.OVERWORLD)) continue;
            if (p.distanceToSqr(X, p.getY(), Z) > 64 * 64) continue;
            int y = w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, X, Z);
            BlockPos pos = new BlockPos(X, y, Z);
            if (w.getBlockState(pos).is(Blocks.OAK_SIGN)) return;
            w.setBlock(pos.below(), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            w.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState(), 3);
            if (w.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                sign.setText(sign.getFrontText().setMessage(0, Component.literal("WerIstDas88")), true);
                sign.setChanged();
                w.sendBlockUpdated(pos, w.getBlockState(pos), w.getBlockState(pos), 3);
            }
            return;
        }
    }
}
