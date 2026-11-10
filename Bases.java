package de.donutmod.fabric;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Zufaellige unterirdische Basen (Farmen und Stashes) tief unter y = -8, ca. alle 1500-2000 Bloecke.
 * Entstehen, sobald ein Spieler in die Naehe kommt. Eigene Bauplaene (keine Schematics aus dem Internet).
 */
public final class Bases {
    private Bases() {}

    static final int SPACING = 550, JITTER = 50;   // Abstand zwischen Nachbarn: 500 bis 600 Bloecke
    static final Set<String> DONE = new HashSet<>();
    static final Gson GSON = new Gson();

    static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    static final BlockState FLOOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    static final class Farm { String dim; int x, y, z; }
    static final List<Farm> FARMS = new ArrayList<>();
    static Path farmFile() { return DonutMod.worldDir().resolve("donutmod-farms.json"); }

    static void addItem(ChestBlockEntity c, ItemStack st) {
        for (int i = 0; i < 27 && !st.isEmpty(); i++) {
            ItemStack cur = c.getItem(i);
            if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, st) && cur.getCount() < cur.getMaxStackSize()) {
                int add = Math.min(st.getCount(), cur.getMaxStackSize() - cur.getCount());
                cur.grow(add);
                st.shrink(add);
            }
        }
        for (int i = 0; i < 27 && !st.isEmpty(); i++)
            if (c.getItem(i).isEmpty()) { c.setItem(i, st.copy()); st.setCount(0); }
    }

    /** Jede Minute: die Sammelkisten der Redstone-Farmen fuellen sich mit Ernte (nur wo der Chunk geladen ist). */
    static void produce(MinecraftServer server) {
        Item[] crops = {Items.WHEAT, Items.CARROT, Items.POTATO};
        for (Farm f : FARMS) {
            ServerLevel lvl = null;
            for (ServerLevel l : server.getAllLevels()) if (l.dimension().toString().equals(f.dim)) { lvl = l; break; }
            if (lvl == null || !lvl.hasChunk(f.x >> 4, f.z >> 4)) continue;
            BlockEntity be = lvl.getBlockEntity(new BlockPos(f.x, f.y, f.z));
            if (!(be instanceof ChestBlockEntity c)) continue;
            for (Item it : crops) addItem(c, new ItemStack(it, 6 + DonutMod.RND.nextInt(15)));
            c.setChanged();
        }
    }

    static Path file() { return DonutMod.worldDir().resolve("donutmod-bases.json"); }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int t = server.getTickCount();
            if (t % 40 == 0) scan(server);
            if (t % 1200 == 0) produce(server);
            if (t % 6000 == 0) save();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> load());
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> {
            var gm = d.getRoot().getChild("gamemode");
            java.util.function.Predicate<CommandSourceStack> isOp = gm != null ? gm.getRequirement() : (src -> false);
            // /fakebase on|off und /fakebase spawn stash|farm|bigstash (nur OP)
            d.register(Commands.literal("fakebase").requires(isOp)
                    .executes(c -> DonutMod.msg(c, "§eFake-Basen: " + (Settings.fakeBases ? "§aAN" : "§cAUS")))
                    .then(Commands.literal("on").executes(c -> { Settings.setFakeBases(true); return DonutMod.msg(c, "§aFake-Basen: AN (entstehen wieder automatisch)"); }))
                    .then(Commands.literal("off").executes(c -> { Settings.setFakeBases(false); return DonutMod.msg(c, "§cFake-Basen: AUS (es entstehen keine neuen, bestehende bleiben)"); }))
                    .then(Commands.literal("height")
                            .executes(c -> DonutMod.msg(c, "§eBasen-Höhe (Boden): y " + Settings.baseYMin + " bis " + Settings.baseYMax))
                            .then(Commands.argument("von", com.mojang.brigadier.arguments.IntegerArgumentType.integer(Settings.BASE_Y_LOW, Settings.BASE_Y_HIGH))
                                    .then(Commands.argument("bis", com.mojang.brigadier.arguments.IntegerArgumentType.integer(Settings.BASE_Y_LOW, Settings.BASE_Y_HIGH))
                                            .executes(c -> {
                                                Settings.setBaseY(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "von"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "bis"));
                                                return DonutMod.msg(c, "§aBasen-Höhe: y " + Settings.baseYMin + " bis " + Settings.baseYMax);
                                            }))))
                    .then(Commands.literal("spawn")
                            .then(typeNode("stash", 1)).then(typeNode("farm", 2))
                            .then(typeNode("bigstash", 3)).then(typeNode("base", 4))));
            d.register(Commands.literal("nearbase").requires(isOp).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                int ci = Math.floorDiv((int) p.getX(), SPACING), cj = Math.floorDiv((int) p.getZ(), SPACING);
                StringBuilder sb = new StringBuilder("§6Basen in der Nähe (unter y=-8):");
                for (int di = -1; di <= 1; di++)
                    for (int dj = -1; dj <= 1; dj++) {
                        int[] pos = cellPos(((ServerLevel) p.level()).getSeed(), ci + di, cj + dj);
                        sb.append("\n§e").append(pos[0]).append(" ").append(pos[1]).append(DONE.contains((ci + di) + "," + (cj + dj)) ? " §7(schon gebaut)" : " §a(noch nicht erzeugt)");
                    }
                return DonutMod.msg(c, sb.toString());
            }));
        });
    }

    static void save() {
        try {
            DonutMod.writeSafe(file(), GSON.toJson(new ArrayList<>(DONE)));
            DonutMod.writeSafe(farmFile(), GSON.toJson(new ArrayList<>(FARMS)));
        }
        catch (Exception e) { System.err.println("[Donutsmp Economy] Basen speichern fehlgeschlagen: " + e); }
    }

    static void load() {
        DONE.clear();
        FARMS.clear();
        try {
            if (Files.exists(farmFile())) { Farm[] fa = GSON.fromJson(Files.readString(farmFile()), Farm[].class); if (fa != null) Collections.addAll(FARMS, fa); }
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Farmen laden fehlgeschlagen: " + e); }
        try {
            if (!Files.exists(file())) return;
            String[] arr = GSON.fromJson(Files.readString(file()), String[].class);
            DONE.clear();
            if (arr != null) Collections.addAll(DONE, arr);
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Basen laden fehlgeschlagen: " + e); }
    }

    static Random cellRandom(long seed, int i, int j) { return new Random(seed * 31L + i * 341873128712L + j * 132897987541L); }

    static int[] cellPos(long seed, int i, int j) {
        Random r = cellRandom(seed, i, j);
        return new int[]{i * SPACING + r.nextInt(JITTER + 1), j * SPACING + r.nextInt(JITTER + 1)};
    }

    static boolean chunksLoaded(ServerLevel level, int bx, int bz) {
        for (int cx = (bx >> 4) - 1; cx <= (bx >> 4) + 2; cx++)
            for (int cz = (bz >> 4) - 1; cz <= (bz >> 4) + 2; cz++)
                if (!level.hasChunk(cx, cz)) return false;
        return true;
    }

    static void scan(MinecraftServer server) {
        if (!Settings.fakeBases) return;   // /fakebase off
        DonutMod m = DonutMod.INSTANCE;
        if (m == null || m.enchList.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Level lv = p.level();
            if (!(lv instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) continue;
            int ci = Math.floorDiv((int) p.getX(), SPACING), cj = Math.floorDiv((int) p.getZ(), SPACING);
            for (int di = -1; di <= 1; di++)
                for (int dj = -1; dj <= 1; dj++) {
                    int i = ci + di, j = cj + dj;
                    String key = i + "," + j;
                    if (DONE.contains(key)) continue;
                    int[] pos = cellPos(level.getSeed(), i, j);
                    double dx = pos[0] - p.getX(), dz = pos[1] - p.getZ();
                    if (dx * dx + dz * dz > 90.0 * 90.0 || !chunksLoaded(level, pos[0], pos[1])) continue;
                    DONE.add(key);
                    try { generate(m, level, cellRandom(level.getSeed() + 7, i, j), pos[0], pos[1]); }
                    catch (Exception e) { System.err.println("[Donutsmp Economy] Basis-Bau fehlgeschlagen: " + e); }
                }
        }
    }

    // ---------- Bauen ----------
    static void fill(ServerLevel l, int x1, int y1, int z1, int x2, int y2, int z2, BlockState st) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) l.setBlock(new BlockPos(x, y, z), st, 2);
    }

    static boolean hasFluid(ServerLevel l, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    if (!l.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) return true;
        return false;
    }

    static void generate(DonutMod m, ServerLevel level, Random r, int bx, int bz) { generate(m, level, r, bx, bz, 0, 0, null); }

    static boolean FORCE_BIG = false;
    static boolean CUR_NETH = true;    // Stufe der Basis, die gerade gebaut wird (Netherite oder Diamant, nie gemischt)
    static boolean LAST_NETH = true;   // Stufe der zuletzt fertig gebauten Basis

    /**
     * Baut eine Basis. mode: 0 = zufaellig, 1 = stash, 2 = farm, 3 = bigstash, 4 = base (zufaellig Stash oder Farm).
     * tier: 0 = zufaellig, 1 = netherite, 2 = diamant. Rueckgabe: Boden-y, oder Integer.MIN_VALUE wenn es nicht ging.
     */
    static int generate(DonutMod m, ServerLevel level, Random r, int bx, int bz, int mode, int tier, Integer forceY) {
        boolean farm, big;
        if (mode == 0) { farm = r.nextInt(100) < 40; big = !farm && r.nextInt(100) < 15; }
        else if (mode == 4) { farm = r.nextInt(100) < 40; big = false; }
        else { farm = mode == 2; big = mode == 3; }
        boolean neth = tier == 1 || (tier == 0 && r.nextBoolean());
        int sx = farm ? 25 : (big ? 45 : 21), sz = farm ? 21 : 15;   // Big-Stash: breiterer Raum = mehr Kisten
        FORCE_BIG = big && mode == 3;
        CUR_NETH = neth;
        try {
            for (int attempt = 0; attempt < 16; attempt++) {
                if (forceY != null && attempt > 0) break;   // feste Hoehe: nur ein Versuch
                // Boden-Hoehe: feste y-Angabe oder zufaellig im Bereich aus /fakebase height bzw. /opsettings (Standard -46 bis -20)
                int y0 = forceY != null ? Math.max(Settings.BASE_Y_LOW, Math.min(Settings.BASE_Y_HIGH, forceY))
                        : Settings.baseYMin + r.nextInt(Settings.baseYMax - Settings.baseYMin + 1);
                // keine Fluessigkeit (Wasser/Lava) im Bereich inkl. 2 Bloecken Rand
                if (hasFluid(level, bx - 2, y0 - 2, bz - 2, bx + sx + 1, y0 + 7, bz + sz + 1)) continue;
                if (farm) buildFarm(m, level, r, bx, y0, bz, sx, sz);
                else buildStash(m, level, r, bx, y0, bz, sx, sz, big, neth);
                LAST_NETH = neth;
                return y0;
            }
        } finally { FORCE_BIG = false; }
        return Integer.MIN_VALUE;
    }

    static int spawnHere(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, int mode, int tier, Integer cx, Integer cz, Integer cy) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Level lv = p.level();
        if (!(lv instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return DonutMod.msg(c, "§cNur in der Overworld.");
        DonutMod m = DonutMod.INSTANCE;
        if (m == null || m.enchList.isEmpty()) return DonutMod.msg(c, "§cNoch nicht bereit, versuch es gleich nochmal.");
        int bx = cx != null ? cx : (int) p.getX() + 2, bz = cz != null ? cz : (int) p.getZ() + 2;   // ohne Koordinaten: neben dir
        int y = generate(m, level, new Random(), bx, bz, mode, tier, cy);
        String name = mode == 1 ? "Stash" : mode == 2 ? "Farm" : mode == 3 ? "Big-Stash" : "Base";
        if (y == Integer.MIN_VALUE) return DonutMod.msg(c, "§cHier ging es nicht (Wasser oder Lava im Weg). Geh ein Stück weiter.");
        return DonutMod.msg(c, "§aFake-" + name + (LAST_NETH ? " (Netherite)" : " (Diamant)") + " gebaut: X=" + bx + " Z=" + bz + ", Boden y=" + y + (cx != null ? "." : " (unter dir)."));
    }

    /** /fakebase spawn <typ> [netherite|dia] [x z [y]] - ohne Koordinaten wird neben dir gebaut, ohne y zufaellig im Hoehenbereich. */
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> typeNode(String name, int mode) {
        return Commands.literal(name)
                .executes(c -> spawnHere(c, mode, 0, null, null, null))
                .then(coords(mode, 0))
                .then(tierNode("netherite", mode, 1))
                .then(tierNode("dia", mode, 2))
                .then(tierNode("diamond", mode, 2));
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> tierNode(String lit, int mode, int tier) {
        return Commands.literal(lit)
                .executes(c -> spawnHere(c, mode, tier, null, null, null))
                .then(coords(mode, tier));
    }

    /** x, z (und optional y = Boden-Hoehe): absolute Blockkoordinaten, Ecke der Basis. */
    static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> coords(int mode, int tier) {
        return Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-29_999_000, 29_999_000))
                .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-29_999_000, 29_999_000))
                        .executes(c -> spawnHere(c, mode, tier,
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x"),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "z"), null))
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer(Settings.BASE_Y_LOW, Settings.BASE_Y_HIGH))
                                .executes(c -> spawnHere(c, mode, tier,
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "z"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "y")))));
    }

    static void room(ServerLevel level, int x0, int y0, int z0, int sx, int sz) {
        fill(level, x0 - 1, y0, z0 - 1, x0 + sx, y0 + 5, z0 + sz, WALL);         // Huelle
        fill(level, x0, y0 + 1, z0, x0 + sx - 1, y0 + 4, z0 + sz - 1, AIR);       // Innenraum
        fill(level, x0, y0, z0, x0 + sx - 1, y0, z0 + sz - 1, FLOOR);              // Boden
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        for (int[] c : new int[][]{{0, 0}, {sx - 1, 0}, {0, sz - 1}, {sx - 1, sz - 1}})
            level.setBlock(new BlockPos(x0 + c[0], y0 + 1, z0 + c[1]), lantern, 2);
    }

    static BlockPos placeSingle(ServerLevel level, BlockPos p, Direction facing) {
        level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        return p;
    }

    static List<BlockPos> placeDouble(ServerLevel level, BlockPos left, Direction facing) {
        BlockPos right = left.relative(facing.getClockWise());
        level.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.LEFT), 2);
        level.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.RIGHT), 2);
        return List.of(left, right);
    }

    /** Normale Basen: 1-20 Spawner gestapelt; in ca. 15 % der Basen ein Riesen-Stapel mit 1000-2000 Spawnern. */
    static int stackFor(Random r) { if (FORCE_BIG) return 1000 + r.nextInt(1001); return r.nextInt(100) < 15 ? 1000 + r.nextInt(1001) : 1 + r.nextInt(20); }

    static void spawner(ServerLevel level, BlockPos p, String type, Random r) {
        level.setBlock(p, Blocks.SPAWNER.defaultBlockState(), 2);
        Spawners.registerAt(level, p, type, stackFor(r));
    }

    /** Stash: Kisten an den Waenden, Loot nach Sorte sortiert (eine Sorte pro Kiste), nur Netherite ODER nur Diamant. */
    static void buildStash(DonutMod m, ServerLevel level, Random r, int x0, int y0, int z0, int sx, int sz, boolean big, boolean neth) {
        room(level, x0, y0, z0, sx, sz);
        // Saeulen als Deko
        for (int x = x0 + 4; x < x0 + sx - 2; x += 4)
            for (int z : new int[]{z0 + 5, z0 + 9}) fill(level, x, y0 + 1, z, x, y0 + 4, z, WALL);
        // Kisten: erst die ganze Nordwand, dann die Suedwand (so liegen zusammengehoerige Kisten nebeneinander)
        int per = (sx - 1) / 2;   // 10 Kisten pro Wand im Stash, 22 im Big-Stash (44 Kisten)
        List<BlockPos> chests = new ArrayList<>();
        for (int i = 0; i < per; i++) chests.add(placeSingle(level, new BlockPos(x0 + 1 + i * 2, y0 + 1, z0), Direction.SOUTH));
        for (int i = 0; i < per; i++) chests.add(placeSingle(level, new BlockPos(x0 + 1 + i * 2, y0 + 1, z0 + sz - 1), Direction.NORTH));
        String[] types = {"skeleton", "zombified_piglin", "iron_golem", "creeper", "zombie", "spider"};
        int spawners = 2 + r.nextInt(3);   // 2 bis 4 Spawner
        for (int i = 0; i < spawners; i++)
            spawner(level, new BlockPos(x0 + sx - 2, y0 + 1, z0 + 3 + i * 3), types[r.nextInt(types.length)], r);
        sortedLoot(m, level, r, chests, neth, big);
    }

    /** Grosse Redstone-Farm: 4 Reihen mit Kolben, Observern und Leitungen, Hopper-Baender mit Kisten, Spawner und Loot hinten. */
    static void buildFarm(DonutMod m, ServerLevel level, Random r, int x0, int y0, int z0, int sx, int sz) {
        room(level, x0, y0, z0, sx, sz);
        BlockState[] crops = {((CropBlock) Blocks.WHEAT).getStateForAge(7), ((CropBlock) Blocks.CARROTS).getStateForAge(7),
                ((CropBlock) Blocks.POTATOES).getStateForAge(7)};
        List<BlockPos> collectors = new ArrayList<>();
        for (int pair = 0; pair < 2; pair++) {
            int base = z0 + 1 + pair * 10;
            for (int strip = 0; strip < 2; strip++) {
                int a = base + strip * 5;
                BlockState crop = crops[r.nextInt(crops.length)];
                for (int x = x0 + 1; x <= x0 + sx - 2; x++) {
                    level.setBlock(new BlockPos(x, y0 + 1, a), Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH), 2);
                    level.setBlock(new BlockPos(x, y0, a + 1), Blocks.FARMLAND.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, y0 + 1, a + 1), crop, 2);
                    level.setBlock(new BlockPos(x, y0 + 1, a + 2), Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH), 2);
                    level.setBlock(new BlockPos(x, y0 + 1, a + 3), Blocks.REDSTONE_WIRE.defaultBlockState(), 2);
                }
                level.setBlock(new BlockPos(x0, y0 + 1, a + 3), Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + sx - 1, y0 + 1, a + 3), Blocks.REDSTONE_LAMP.defaultBlockState(), 2);
            }
            // Sammelband: Hopper-Reihe fuehrt zu einer Kiste
            for (int x = x0 + 1; x <= x0 + sx - 3; x++)
                level.setBlock(new BlockPos(x, y0 + 1, base + 4), Blocks.HOPPER.defaultBlockState().setValue(BlockStateProperties.FACING_HOPPER, Direction.EAST), 2);
            BlockPos col = placeSingle(level, new BlockPos(x0 + sx - 2, y0 + 1, base + 4), Direction.WEST);
            collectors.add(col);
            Farm farm = new Farm();
            farm.dim = level.dimension().toString(); farm.x = col.getX(); farm.y = col.getY(); farm.z = col.getZ();
            FARMS.add(farm);
        }
        for (int x = x0 + 2; x < x0 + sx - 1; x += 4)
            for (int z : new int[]{z0 + 3, z0 + 8, z0 + 13, z0 + 18})
                level.setBlock(new BlockPos(x, y0 + 5, z), Blocks.GLOWSTONE.defaultBlockState(), 2);
        distribute(level, collectors, List.of(new ItemStack(Items.WHEAT, 32 + r.nextInt(33)), new ItemStack(Items.CARROT, 32 + r.nextInt(33)),
                new ItemStack(Items.POTATO, 32 + r.nextInt(33)), new ItemStack(Items.BONE_MEAL, 16 + r.nextInt(49))), r);
        // Spawner (teils Riesen-Stapel) und Loot an der Rueckwand
        String[] types = {"zombie", "skeleton", "spider", "creeper"};
        for (int x : new int[]{x0 + 1, x0 + 5, x0 + sx - 6, x0 + sx - 2})
            spawner(level, new BlockPos(x, y0 + 1, z0 + sz - 1), types[r.nextInt(types.length)], r);
        List<BlockPos> chests = new ArrayList<>();
        for (int x : new int[]{x0 + 8, x0 + 10, x0 + 12, x0 + 14, x0 + 16})
            chests.add(placeSingle(level, new BlockPos(x, y0 + 1, z0 + sz - 1), Direction.NORTH));
        sortedLoot(m, level, r, chests, CUR_NETH, false);   // sortiert, nur eine Stufe
    }

    // ---------- Sortierter Loot ----------
    static List<ItemStack> many(int n, Supplier<ItemStack> f) {
        List<ItemStack> g = new ArrayList<>();
        for (int i = 0; i < n; i++) { ItemStack s = f.get(); if (!s.isEmpty()) g.add(s); }
        return g;
    }

    /** total Stueck als Stacks (jeweils bis zur maximalen Stapelgroesse). */
    static List<ItemStack> stacks(net.minecraft.world.item.Item it, int total) {
        List<ItemStack> g = new ArrayList<>();
        int max = Math.max(1, new ItemStack(it).getMaxStackSize());
        while (total > 0) { int n = Math.min(max, total); g.add(new ItemStack(it, n)); total -= n; }
        return g;
    }

    /** 5 bis 50 Stueck. */
    static int n550(Random r) { return 5 + r.nextInt(46); }

    /** 5 bis 50 Stueck oder ein voller Stack (50:50); Items, die nicht stapelbar sind, immer 5 bis 50. */
    static int amt(Random r, int max) { return max <= 1 || r.nextBoolean() ? n550(r) : max; }

    /**
     * Fuellt die Kisten sortiert: jede Sorte bekommt eigene Kisten (Helme, Brustplatten, Hosen, Schuhe, Schwerter, Aexte,
     * Spitzhacken, ...), jedes Teil 5 bis 50 mal. Nur Netherite ODER nur Diamant. OP-Gaps: Diamant 1-5, Netherite 20-640.
     */
    static void sortedLoot(DonutMod m, ServerLevel level, Random r, List<BlockPos> chests, boolean neth, boolean big) {
        String mat = neth ? "netherite" : "diamond";
        List<List<ItemStack>> g = new ArrayList<>();
        for (String part : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            boolean legs = part.equals("leggings") || part.equals("boots");
            g.add(many(n550(r), () -> legs && r.nextInt(4) == 0
                    ? gear(m, mat + "_" + part, "blast_protection", 4, "unbreaking", 3, "mending", 1)
                    : gear(m, mat + "_" + part, "protection", 4, "unbreaking", 3, "mending", 1)));
        }
        g.add(many(n550(r), () -> gear(m, mat + "_sword", "sharpness", 5, "unbreaking", 3, "fire_aspect", 2, "mending", 1)));
        g.add(many(n550(r), () -> gear(m, mat + "_axe", "sharpness", 5, "efficiency", 5, "unbreaking", 3, "mending", 1)));
        g.add(many(n550(r), () -> gear(m, mat + "_pickaxe", "efficiency", 5, "silk_touch", 1, "unbreaking", 3, "mending", 1)));
        g.add(stacks(Items.ENCHANTED_GOLDEN_APPLE, neth ? 20 + r.nextInt(621) : 1 + r.nextInt(5)));   // OP-Gaps
        g.add(many(n550(r), () -> gear(m, mat + "_spear", "sharpness", 5, "lunge", 3, "unbreaking", 3, "fire_aspect", 2, "mending", 1)));
        g.add(many(n550(r), () -> gear(m, "mace", "wind_burst", 3, "density", 5, "unbreaking", 3, "mending", 1)));
        g.add(many(n550(r), () -> gear(m, "shield", "unbreaking", 3, "mending", 1)));
        if (neth) g.add(many(n550(r), () -> gear(m, "elytra", "unbreaking", 3, "mending", 1)));
        g.add(stacks(Items.TOTEM_OF_UNDYING, n550(r)));
        g.add(stacks(Items.GOLDEN_APPLE, amt(r, 64)));
        g.add(stacks(Items.ENDER_PEARL, amt(r, 16)));
        // nur hilfreiche Traenke - keine Gift-, Schadens- oder Schwaeche-Traenke
        g.add(many(n550(r), () -> potion(Potions.STRONG_STRENGTH)));
        g.add(many(n550(r), () -> potion(Potions.STRONG_SWIFTNESS)));
        g.add(many(n550(r), () -> potion(Potions.LONG_FIRE_RESISTANCE)));
        g.add(stacks(Items.END_CRYSTAL, amt(r, 64)));
        g.add(stacks(Items.EXPERIENCE_BOTTLE, amt(r, 64)));
        g.add(stacks(Items.OBSIDIAN, amt(r, 64)));
        g.add(stacks(Items.COBWEB, amt(r, 64)));
        g.add(stacks(Items.RESPAWN_ANCHOR, amt(r, 64)));
        g.add(stacks(Items.GLOWSTONE, amt(r, 64)));
        g.add(many(1 + r.nextInt(3), () -> rockets(r)));
        fillSorted(level, chests, g);
    }

    /** Jede Gruppe kommt in eigene Kisten (der Reihe nach); passt etwas nicht mehr, wird es weggelassen. */
    static void fillSorted(ServerLevel level, List<BlockPos> chests, List<List<ItemStack>> groups) {
        int ci = 0;
        for (List<ItemStack> grp : groups) {
            int i = 0;
            while (i < grp.size() && ci < chests.size()) {
                BlockEntity be = level.getBlockEntity(chests.get(ci));
                ci++;
                if (!(be instanceof ChestBlockEntity c)) continue;
                for (int slot = 0; slot < 27 && i < grp.size(); slot++) c.setItem(slot, grp.get(i++));
                c.setChanged();
            }
            if (ci >= chests.size()) break;
        }
    }

    // ---------- Bot-Orders fuer Basen-Loot ----------
    record LootOrder(String id, Map<String, Integer> ench, int amount) {}

    static Map<String, Integer> ench(Object... kv) {
        Map<String, Integer> e = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) e.put("minecraft:" + kv[i], (Integer) kv[i + 1]);
        return e;
    }

    /** Zufaellige Order auf Dinge, die in Basen liegen (Netherite-/Diamant-Ruestung und -Waffen, Gaps, Totems ...). */
    static LootOrder lootOrder(Random r) {
        String mat = r.nextBoolean() ? "netherite" : "diamond";
        switch (r.nextInt(14)) {
            case 0 -> { return new LootOrder("minecraft:" + mat + "_helmet", ench("protection", 4, "unbreaking", 3, "mending", 1), 1 + r.nextInt(8)); }
            case 1 -> { return new LootOrder("minecraft:" + mat + "_chestplate", ench("protection", 4, "unbreaking", 3, "mending", 1), 1 + r.nextInt(8)); }
            case 2 -> { return new LootOrder("minecraft:" + mat + "_leggings", ench(r.nextInt(4) == 0 ? "blast_protection" : "protection", 4, "unbreaking", 3, "mending", 1), 1 + r.nextInt(8)); }
            case 3 -> { return new LootOrder("minecraft:" + mat + "_boots", ench(r.nextInt(4) == 0 ? "blast_protection" : "protection", 4, "unbreaking", 3, "mending", 1), 1 + r.nextInt(8)); }
            case 4 -> { return new LootOrder("minecraft:" + mat + "_sword", ench("sharpness", 5, "unbreaking", 3, "fire_aspect", 2, "mending", 1), 1 + r.nextInt(6)); }
            case 5 -> { return new LootOrder("minecraft:" + mat + "_axe", ench("sharpness", 5, "efficiency", 5, "unbreaking", 3, "mending", 1), 1 + r.nextInt(6)); }
            case 6 -> { return new LootOrder("minecraft:" + mat + "_pickaxe", ench("efficiency", 5, "silk_touch", 1, "unbreaking", 3, "mending", 1), 1 + r.nextInt(6)); }
            case 7 -> { return new LootOrder("minecraft:enchanted_golden_apple", Map.of(), 8 + r.nextInt(121)); }
            case 8 -> { return new LootOrder("minecraft:golden_apple", Map.of(), 16 + r.nextInt(241)); }
            case 9 -> { return new LootOrder("minecraft:totem_of_undying", Map.of(), 4 + r.nextInt(61)); }
            case 10 -> { return new LootOrder("minecraft:ender_pearl", Map.of(), 16 + r.nextInt(241)); }
            case 11 -> { return new LootOrder("minecraft:obsidian", Map.of(), 64 + r.nextInt(449)); }
            case 12 -> { return new LootOrder("minecraft:end_crystal", Map.of(), 16 + r.nextInt(113)); }
            default -> { return new LootOrder("minecraft:experience_bottle", Map.of(), 64 + r.nextInt(449)); }
        }
    }

    // ---------- Loot ----------
    static int pickLot(Random r) {
        int v = r.nextInt(100);   // Lot 1 und 2 oefter als Lot 3 und 4
        return v < 35 ? 1 : v < 70 ? 2 : v < 85 ? 3 : 4;
    }

    static void distribute(ServerLevel level, List<BlockPos> chests, List<ItemStack> items, Random r) {
        List<ChestBlockEntity> bes = new ArrayList<>();
        for (BlockPos p : chests) { BlockEntity be = level.getBlockEntity(p); if (be instanceof ChestBlockEntity c) bes.add(c); }
        if (bes.isEmpty()) return;
        List<ItemStack> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, r);
        for (ItemStack st : shuffled)
            for (int tries = 0; tries < 60; tries++) {
                ChestBlockEntity c = bes.get(r.nextInt(bes.size()));
                int slot = r.nextInt(27);
                if (c.getItem(slot).isEmpty()) { c.setItem(slot, st); break; }
            }
        for (ChestBlockEntity c : bes) c.setChanged();
    }

    /** Seltene Stashes: alle Kisten komplett voll. */
    static void fillRest(ServerLevel level, List<BlockPos> chests, Random r) {
        for (BlockPos p : chests) {
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof ChestBlockEntity c)) continue;
            for (int slot = 0; slot < 27; slot++)
                if (c.getItem(slot).isEmpty()) {
                    int k = r.nextInt(6);
                    c.setItem(slot, switch (k) {
                        case 0 -> new ItemStack(Items.ENDER_PEARL, 8 + r.nextInt(9));
                        case 1 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3 + r.nextInt(10));
                        case 2 -> new ItemStack(Items.GOLDEN_APPLE, 8 + r.nextInt(25));
                        case 3 -> new ItemStack(Items.TOTEM_OF_UNDYING);
                        case 4 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 16 + r.nextInt(49));
                        default -> new ItemStack(Items.OBSIDIAN, 16 + r.nextInt(49));
                    });
                }
            c.setChanged();
        }
    }

    static ItemStack gear(DonutMod m, String id, Object... kv) {
        Map<String, Integer> e = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) e.put("minecraft:" + kv[i], (Integer) kv[i + 1]);
        return m.buildStack("minecraft:" + id, e, 1);
    }

    static void addN(List<ItemStack> out, Random r, int lo, int hi, Supplier<ItemStack> f) {
        int n = lo + r.nextInt(hi - lo + 1);
        for (int i = 0; i < n; i++) { ItemStack s = f.get(); if (!s.isEmpty()) out.add(s); }
    }

    static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> p) {
        ItemStack s = new ItemStack(Items.POTION);
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(p));
        return s;
    }

    static ItemStack rockets(Random r) {
        ItemStack s = new ItemStack(Items.FIREWORK_ROCKET, 16 + r.nextInt(17));
        s.set(DataComponents.FIREWORKS, new Fireworks(3, List.of()));
        return s;
    }

    static void stack(List<ItemStack> out, Random r, net.minecraft.world.item.Item it, int lo, int hi) {
        out.add(new ItemStack(it, lo + r.nextInt(hi - lo + 1)));
    }

    static List<ItemStack> loot(DonutMod m, int lot, Random r) {
        List<ItemStack> out = new ArrayList<>();
        boolean neth = lot >= 3;
        String mat = neth ? "netherite" : "diamond";
        // Ruestung: 3 bis 12 Teile
        int pieces = 3 + r.nextInt(10);
        for (int i = 0; i < pieces; i++) {
            String part = new String[]{"helmet", "chestplate", "leggings", "boots"}[r.nextInt(4)];
            boolean blast = (lot == 2 || lot == 4) && (part.equals("leggings") || part.equals("boots"));
            out.add(gear(m, mat + "_" + part, blast ? "blast_protection" : "protection", 4, "unbreaking", 3, "mending", 1));
        }
        switch (lot) {
            case 1 -> {
                addN(out, r, 1, 4, () -> gear(m, "mace", "wind_burst", 3, "density", 5, "unbreaking", 3, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "diamond_spear", "sharpness", 5, "unbreaking", 3, "lunge", 3, "fire_aspect", 2, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "diamond_sword", "sharpness", 5, "unbreaking", 3, "fire_aspect", 2, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "diamond_pickaxe", "efficiency", 5, "silk_touch", 1, "unbreaking", 3, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "diamond_axe", "sharpness", 5, "efficiency", 5, "unbreaking", 3, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "shield", "unbreaking", 3, "mending", 1));
                addN(out, r, 3, 12, () -> potion(Potions.STRONG_SWIFTNESS));
                addN(out, r, 3, 12, () -> potion(Potions.STRONG_STRENGTH));
                addN(out, r, 3, 12, () -> potion(Potions.LONG_FIRE_RESISTANCE));
                stack(out, r, Items.TOTEM_OF_UNDYING, 1, 1); addN(out, r, 2, 11, () -> new ItemStack(Items.TOTEM_OF_UNDYING));
                stack(out, r, Items.GOLDEN_APPLE, 3, 12); stack(out, r, Items.ENCHANTED_GOLDEN_APPLE, 3, 12);
                stack(out, r, Items.ENDER_PEARL, 3, 12); stack(out, r, Items.COBWEB, 3, 12);
            }
            case 2 -> {
                addN(out, r, 1, 4, () -> gear(m, "diamond_sword", "sharpness", 5, "unbreaking", 3, "knockback", 1, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "diamond_axe", "sharpness", 5, "efficiency", 5, "mending", 1, "unbreaking", 3));
                addN(out, r, 1, 4, () -> gear(m, "diamond_pickaxe", "efficiency", 5, "mending", 1, "silk_touch", 1, "unbreaking", 3));
                addN(out, r, 1, 4, () -> gear(m, "shield", "mending", 1, "unbreaking", 3));
                addN(out, r, 3, 12, () -> new ItemStack(Items.TOTEM_OF_UNDYING));
                stack(out, r, Items.GOLDEN_APPLE, 3, 12); stack(out, r, Items.ENCHANTED_GOLDEN_APPLE, 3, 12);
                stack(out, r, Items.ENDER_PEARL, 3, 12); stack(out, r, Items.END_CRYSTAL, 3, 12);
                stack(out, r, Items.OBSIDIAN, 8, 32); stack(out, r, Items.RESPAWN_ANCHOR, 3, 12);
                stack(out, r, Items.GLOWSTONE, 8, 32); stack(out, r, Items.EXPERIENCE_BOTTLE, 8, 32);
            }
            case 3 -> {
                addN(out, r, 1, 3, () -> gear(m, "elytra", "unbreaking", 3, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "mace", "wind_burst", 3, "density", 5, "unbreaking", 3, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "netherite_spear", "sharpness", 5, "lunge", 3, "unbreaking", 3, "fire_aspect", 2, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "netherite_sword", "sharpness", 5, "unbreaking", 3, "fire_aspect", 2, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "netherite_axe", "sharpness", 5, "unbreaking", 3, "efficiency", 5, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "netherite_pickaxe", "efficiency", 5, "silk_touch", 1, "unbreaking", 3, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "shield", "unbreaking", 3, "mending", 1));
                stack(out, r, Items.GOLDEN_APPLE, 3, 12); stack(out, r, Items.ENCHANTED_GOLDEN_APPLE, 3, 12);
                stack(out, r, Items.ENDER_PEARL, 3, 12); stack(out, r, Items.COBWEB, 3, 12);
                addN(out, r, 3, 12, () -> potion(Potions.STRONG_SWIFTNESS));
                addN(out, r, 3, 12, () -> potion(Potions.STRONG_STRENGTH));
                addN(out, r, 3, 12, () -> potion(Potions.LONG_FIRE_RESISTANCE));
                addN(out, r, 3, 12, () -> new ItemStack(Items.TOTEM_OF_UNDYING));
                out.add(rockets(r));
            }
            default -> {
                addN(out, r, 1, 4, () -> gear(m, "netherite_sword", "sharpness", 5, "unbreaking", 3, "knockback", 1, "fire_aspect", 2, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "netherite_axe", "sharpness", 5, "unbreaking", 3, "efficiency", 5, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "netherite_pickaxe", "efficiency", 5, "silk_touch", 1, "unbreaking", 3, "mending", 1));
                addN(out, r, 1, 4, () -> gear(m, "shield", "mending", 1, "unbreaking", 3));
                addN(out, r, 3, 12, () -> new ItemStack(Items.TOTEM_OF_UNDYING));
                stack(out, r, Items.GOLDEN_APPLE, 3, 12); stack(out, r, Items.ENCHANTED_GOLDEN_APPLE, 3, 12);
                stack(out, r, Items.ENDER_PEARL, 3, 12);
                addN(out, r, 1, 3, () -> gear(m, "elytra", "unbreaking", 3, "mending", 1));
                out.add(rockets(r));
            }
        }
        return out;
    }
}
