package de.donutmod.fabric;

import de.donutmod.core.Stack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/** Wer die Sell Axe in der Hand hat und auf eine Kiste/Fass schaut, sieht in der Action-Bar, wie viel der Inhalt wert ist. */
final class ChestPreview {
    private ChestPreview() {}

    /** Alle 5 Ticks aufrufen. */
    static void tick(MinecraftServer server) {
        DonutMod m = DonutMod.INSTANCE;
        if (m == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!"donutmod_sell_axe".equals(Custom.key(p.getMainHandItem()))) continue;
            Level lv = p.level();
            if (!(lv instanceof ServerLevel level)) continue;
            HitResult hit = p.pick(6.0, 0.0f, false);
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!(state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL))) continue;
            List<Stack> list = contents(level, pos, state);
            String text = list.isEmpty() ? "§7Kiste: leer" : "§6Kiste: §a$" + DonutMod.fmt(m.sell.preview(list)) + " §7(Sell Axe: Abbauen verkauft)";
            p.connection.send(new ClientboundSetActionBarTextPacket(Lang.lit(text)));
        }
    }

    /** Inhalt der Kiste (bei Doppelkisten beide Haelften), ohne etwas zu veraendern. */
    static List<Stack> contents(ServerLevel level, BlockPos pos, BlockState state) {
        List<Container> parts = new ArrayList<>();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c) parts.add(c);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockEntity ob = level.getBlockEntity(pos.relative(ChestBlock.getConnectedDirection(state)));
            if (ob instanceof Container oc) parts.add(oc);
        }
        List<Stack> list = new ArrayList<>();
        for (Container c : parts)
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) DonutMod.addStacks(s, list);
            }
        return list;
    }
}
