package de.donutmod.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.context.CommandContext;
import de.donutmod.core.*;
import de.donutmod.core.Stack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.nio.file.*;
import java.util.*;

/** Fabric-Anbindung (Mojang-Mappings, MC 1.21.11) an den versionsunabhaengigen Core. */
public class DonutMod implements ModInitializer {
    static final Random RND = new Random();
    static final List<String> META_POOL = List.of("minecraft:kelp", "minecraft:diamond", "minecraft:iron_ingot",
            "minecraft:gold_ingot", "minecraft:sea_lantern", "minecraft:cactus", "minecraft:bone_meal");

    PriceBook prices = new PriceBook();
    MetaManager meta = new MetaManager();
    Economy eco = new Economy();
    Orders orders = new Orders(prices, meta, eco);
    AuctionHouse ah = new AuctionHouse(prices, meta, eco);
    SellService sell = new SellService(prices, orders, eco);

    /** Ordner der aktuellen Welt: jede Welt hat ihr eigenes Geld, ihre eigenen Orders, Homes, Spawner usw. */
    /** Sicher speichern: erst in eine Temp-Datei, dann umbenennen; die alte Version bleibt als .bak erhalten. */
    static void writeSafe(Path file, String content) throws java.io.IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content);
        if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    static Path worldDir() {
        try {
            Path d = INSTANCE.srv.getWorldPath(LevelResource.ROOT).resolve("donutmod");
            Files.createDirectories(d);
            return d;
        } catch (Exception e) { return FabricLoader.getInstance().getConfigDir(); }
    }

    /** Setzt alles zurueck, bevor die Daten der neuen Welt geladen werden. */
    void resetState() {
        prices = new PriceBook();
        meta = new MetaManager();
        eco = new Economy();
        orders = new Orders(prices, meta, eco);
        ah = new AuctionHouse(prices, meta, eco);
        sell = new SellService(prices, orders, eco);
        ahItems.clear(); pending.clear(); names.clear(); shulkerOn.clear(); boards.clear(); headBoards.clear(); capCache.clear();
        for (Object[] row : KNOWN) prices.setBase("minecraft:" + row[0], ((Number) row[1]).longValue());
        prices.sellFactor = 6.0 / PriceFile.MULT;
        meta.roll(META_POOL, 2, RND);
    }
    /** Echte ItemStacks zu den AH-Angeboten (Core kennt nur Stack-Beschreibungen). */
    final Map<Integer, ItemStack> ahItems = new HashMap<>();
    /** Pro Spieler eigenes Sidebar-Scoreboard (rechts, mittig) mit dem eigenen Kontostand. */
    final Map<UUID, Objective> boards = new HashMap<>();
    /** Geld ueber den Koepfen: BELOW_NAME-Anzeige je Betrachter. */
    final Map<UUID, Objective> headBoards = new HashMap<>();
    static final Gson GSON = new Gson();
    static Path saveFile() { return worldDir().resolve("donutmod-balances.json"); }

    static final Object[][] KNOWN = {
            {"kelp",12},{"dried_kelp",14},{"coal_block",110},{"blaze_powder",30},{"stick",4},{"deepslate_emerald_ore",6000},{"emerald_ore",5000},{"deepslate_diamond_ore",3500},{"diamond_ore",3000},
            {"deepslate_gold_ore",350},{"gold_ore",300},{"deepslate_lapis_ore",220},{"lapis_ore",200},{"deepslate_iron_ore",130},{"iron_ore",120},
            {"deepslate_redstone_ore",110},{"redstone_ore",100},{"deepslate_copper_ore",65},{"copper_ore",60},{"deepslate_coal_ore",45},{"coal_ore",40},
            {"nether_quartz_ore",150},{"nether_gold_ore",150},{"sculk_catalyst",3000},{"sculk_shrieker",3000},{"heart_of_the_sea",8000},
            {"nautilus_shell",3000},{"conduit",6000},{"recovery_compass",4000},{"echo_shard",3000},{"sniffer_egg",4000},
            {"trial_key",5000},{"ominous_trial_key",8000},{"wither_skeleton_skull",5000},{"turtle_scute",200},{"phantom_membrane",300},
            {"dried_kelp_block",120},{"sea_lantern",300},{"cactus",40},{"bone_meal",30},{"bone",60},
            {"string",90},{"spider_eye",70},{"rotten_flesh",20},{"gunpowder",120},{"ender_pearl",400},{"blaze_rod",60},
            {"coal",12},{"charcoal",10},{"iron_ingot",80},{"gold_ingot",120},{"copper_ingot",30},{"diamond",500},
            {"emerald",350},{"lapis_lazuli",60},{"redstone",40},{"quartz",70},{"amethyst_shard",90},
            {"netherite_scrap",1500},{"netherite_ingot",5000},{"ancient_debris",2500},{"iron_block",720},
            {"gold_block",1080},{"diamond_block",4500},{"emerald_block",3150},{"netherite_block",45000},
            {"cobblestone",5},{"stone",8},{"deepslate",8},{"obsidian",150},{"crying_obsidian",300},{"glowstone",110},
            {"oak_log",20},{"spruce_log",20},{"birch_log",20},{"sand",10},{"gravel",10},{"dirt",3},
            {"wheat",25},{"carrot",25},{"potato",25},{"melon_slice",15},{"pumpkin",40},{"sugar_cane",30},
            {"sweet_berries",20},{"golden_apple",900},{"enchanted_golden_apple",25000},{"cooked_beef",80},
            {"diamond_sword",2500},{"diamond_pickaxe",3000},{"diamond_axe",3000},{"diamond_shovel",1500},
            {"diamond_helmet",3500},{"diamond_chestplate",5600},{"diamond_leggings",5000},{"diamond_boots",2900},
            {"netherite_sword",9000},{"netherite_pickaxe",9500},{"netherite_axe",9500},{"netherite_shovel",8000},
            {"netherite_helmet",20000},{"netherite_chestplate",60000},{"netherite_leggings",50000},{"netherite_boots",15000},
            {"elytra",20000},{"totem_of_undying",15000},{"shulker_shell",1500},{"shulker_box",3500},
            {"nether_star",40000},{"beacon",60000},{"experience_bottle",300},{"enchanted_book",2000},
            {"trident",12000},{"mace",30000},{"heavy_core",20000},{"wind_charge",150},{"breeze_rod",600}
        };

    static DonutMod INSTANCE;

    @Override
    public void onInitialize() {
        INSTANCE = this;
        // WICHTIG: srv muss gesetzt sein, BEVOR die anderen Klassen beim Start ihre Daten laden (sonst falscher Ordner!)
        ServerLifecycleEvents.SERVER_STARTING.register(server -> srv = server);
        for (Object[] row : KNOWN) prices.setBase("minecraft:" + row[0], ((Number) row[1]).longValue());
        prices.sellFactor = 6.0 / PriceFile.MULT;   // /sell zahlt 6x, Markt (AH) 48x des Originalpreises
        meta.roll(META_POOL, 2, RND);

        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> registerCommands(d));
        Tools.register();
        Spawners.register();
        Hoppers.register();
        Homes.register();
        Rtp.register();
        Bases.register();
        Tpa.register();
        EnderStorage.register();

        // Kill-Belohnung: der Moerder bekommt 10 % vom Geld des Opfers
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer victim) || !(source.getEntity() instanceof ServerPlayer killer) || killer == victim) return;
            long amt = eco.get(victim.getUUID()) / 10;
            if (amt <= 0) return;
            eco.take(victim.getUUID(), amt);
            eco.add(killer.getUUID(), amt);
            srv.getPlayerList().broadcastSystemMessage(Lang.lit("§c" + killer.getName().getString() + " §7hat §c"
                    + victim.getName().getString() + " §7getötet und §a$" + fmtShort(amt) + " §7(10 %) erhalten."), false);
            ping(killer, SoundEvents.PLAYER_LEVELUP, 1.2f);
        });
        OrderBuilder.register(this);
        PriceEditor.register(this);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> { srv = server; resetState(); Tpa.BY_TARGET.clear(); Hoppers.TRACKED.clear(); OrderBuilder.AWAIT.clear(); loadBalances(); loadNames(); Settings.load(); PricePropagation.reset(); Easter.reset(); applyFactorsNoRescale(); initEnchants(); PriceFile.loadInto(prices); Custom.registerPrices(prices); initItems(); loadMarket(); market = new Market(this); market.init(); seedCustom(); market.ensureCustomOrders(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveBalances());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer jp = handler.getPlayer();
            names.put(jp.getUUID(), jp.getName().getString());
            createBoard(jp);
            if (market != null && market.reduced)
                Gui.say(jp, "§eMarkt: wegen wenig Arbeitsspeicher nur " + fmt(market.minAh) + " Angebote und " + fmt(market.minOrders)
                        + " Orders. Gib Minecraft im Launcher mehr RAM, dann wird er groesser.");
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { IdleScreen.forget(handler.getPlayer().getUUID()); boards.remove(handler.getPlayer().getUUID()); headBoards.remove(handler.getPlayer().getUUID()); });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int t = server.getTickCount();
            if (market != null) market.tick(t);
            if (t % 20 == 0) Easter.tick(server);           // Easter Egg: steigt jede Sekunde 1 Block
            if (t % 5 == 0) IdleScreen.tick(server, t);     // 1 Min nichts gemacht -> "Made by WerIstDas88"
            if (t % 5 == 0) ChestPreview.tick(server);       // Sell Axe: Kistenwert anzeigen
            if (t % 20 == 0) Lang.update(server);           // Sprache folgt Minecraft
            if (t % 20 == 0) updateBoards(server);          // jede Sekunde Anzeige aktualisieren
            if (t % 6000 == 0) saveBalances();              // alle 5 Min speichern
            if (t % 1200 == 0) botCycle(server);          // jede Minute
            if (Settings.botTick > 1) {                       // /bot tick <n>: im Schnitt genau n Handelsrunden pro Minute
                botTradeAcc += Settings.botTick / 1200.0;
                int r = (int) botTradeAcc;                    // bei grossem Tempo mehrere Runden pro Tick, gebuendelt
                if (r >= 1) { botTradeAcc -= r; botTrade(server, r); }
            } else botTradeAcc = 0;
            if (t % 36000 == 0) {                           // alle 30 Min neue Meta
                meta.roll(META_POOL, 2, RND);
            }
        });
    }

    // ---- Speichern ----
    void saveBalances() {
        saveNames();
        saveMarket();
        try {
            Map<String, Long> out = new HashMap<>();
            eco.snapshot().forEach((u, v) -> out.put(u.toString(), v));
            writeSafe(saveFile(), GSON.toJson(out));
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Speichern fehlgeschlagen: " + e); }
    }

    void loadBalances() {
        try {
            if (!Files.exists(saveFile())) return;
            Map<String, Long> in = GSON.fromJson(Files.readString(saveFile()), new TypeToken<Map<String, Long>>() {}.getType());
            Map<UUID, Long> data = new HashMap<>();
            if (in != null) in.forEach((k, v) -> data.put(UUID.fromString(k), v));
            eco.load(data);
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Laden fehlgeschlagen: " + e); }
    }

    // ---- Scoreboard (Sidebar rechts, mittig) ----
    void createBoard(ServerPlayer p) {
        Objective obj = new Scoreboard().addObjective("donutmod_money", ObjectiveCriteria.DUMMY,
                Lang.lit("§9§lDonutsmp"), ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        boards.put(p.getUUID(), obj);
        p.connection.send(new ClientboundSetObjectivePacket(obj, 0));                 // 0 = erstellen
        if (Settings.hud(p.getUUID())) p.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, obj));
        sendMoney(p, obj);
        Objective head = new Scoreboard().addObjective("donutmod_head", ObjectiveCriteria.DUMMY, Lang.lit(" "),
                ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        headBoards.put(p.getUUID(), head);
        p.connection.send(new ClientboundSetObjectivePacket(head, 0));
        if (Settings.tags(p.getUUID())) p.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.BELOW_NAME, head));
        sendHeads(p, head, srv.getPlayerList().getPlayers());
    }

    /** Zeigt dem Betrachter das Geld aller Spieler unter/ueber deren Namen. */
    void sendHeads(ServerPlayer viewer, Objective head, List<ServerPlayer> all) {
        for (ServerPlayer t : all)
            viewer.connection.send(new ClientboundSetScorePacket(t.getScoreboardName(), head.getName(), 0, Optional.empty(),
                    Optional.of(new FixedFormat(Lang.lit("§a$" + fmt(eco.get(t.getUUID())))))));
    }

    void sendMoney(ServerPlayer p, Objective obj) {
        p.connection.send(new ClientboundSetScorePacket("money", obj.getName(), 1,
                Optional.of(Lang.lit("§eGeld: §a$" + fmt(eco.get(p.getUUID())))), Optional.of(BlankFormat.INSTANCE)));
        p.connection.send(new ClientboundSetScorePacket("info", obj.getName(), 0,
                Optional.of(Lang.lit("§9Economy")), Optional.of(BlankFormat.INSTANCE)));
    }

    /** Nach einer Aenderung in /settings: Geld-Anzeige und Name Tags fuer diesen Spieler ein-/ausblenden. */
    void applyBoards(ServerPlayer p) {
        Objective obj = boards.get(p.getUUID()), head = headBoards.get(p.getUUID());
        if (obj == null || head == null) { createBoard(p); return; }
        boolean hud = Settings.hud(p.getUUID()), tags = Settings.tags(p.getUUID());
        p.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, hud ? obj : null));
        p.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.BELOW_NAME, tags ? head : null));
        if (hud) sendMoney(p, obj);
        if (tags) sendHeads(p, head, srv.getPlayerList().getPlayers());
    }

    void updateBoards(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Objective obj = boards.get(p.getUUID());
            if (obj == null) createBoard(p); else if (Settings.hud(p.getUUID())) sendMoney(p, obj);
            Objective h = headBoards.get(p.getUUID());
            if (h != null && Settings.tags(p.getUUID())) sendHeads(p, h, server.getPlayerList().getPlayers());
        }
    }

    // ---- Bot-Logik ----
    static final long REQ_AH = 1_000_000L, REQ_ORDERS = 500_000L;   // Wunsch; wird bei wenig RAM automatisch verkleinert
    Market market;
    static final double BOT_ORDER_FILL = 0.01;   // Chance pro Minute, dass ein Bot-Order teilweise gefuellt wird
    static final Map<String, Item> ITEM_BY_ID = new HashMap<>();
    final List<Item> pool = new ArrayList<>();
    static final String[] BAD = {"spawn_egg", "command_block", "structure_", "barrier", "light", "debug_stick", "jigsaw",
            "knowledge_book", "bedrock", "spawner", "test_", "trial_spawner", "vault", "farmland", "dirt_path",
            "frogspawn", "budding_amethyst", "infested", "reinforced_deepslate", "petrified", "end_portal", "player_head",
            "splash_potion", "lingering_potion", "tipped_arrow"};   // keine Gift-/Schadens-Traenke (Effekt laesst sich nicht erkennen)

    static final double BASE_MULT = 1.25;   // eingebauter Preisaufschlag; /opsettings-Faktor kommt obendrauf

    /** Nach dem Laden: Faktoren aus /opsettings uebernehmen (ohne vorhandene Markt-Preise anzufassen, die sind gespeichert). */
    void applyFactorsNoRescale() {
        prices.priceMultiplier = BASE_MULT * Settings.priceFactor / 100.0;
        orders.priceFactor = Settings.orderFactor / 100.0;
    }

    /** /opsettings: neue Prozentwerte setzen und vorhandene Bot-Angebote/-Orders im Markt um das Verhaeltnis neu/alt skalieren. */
    void changeFactors(int newPrice, int newOrder) {
        int oldP = Settings.priceFactor, oldO = Settings.orderFactor;
        Settings.setFactors(newPrice, newOrder);
        applyFactorsNoRescale();
        double rp = (double) Settings.priceFactor / oldP;
        double ro = rp * ((double) Settings.orderFactor / oldO);
        if (rp != 1.0) ah.rescaleBots(rp);
        if (ro != 1.0) orders.rescaleBots(ro);
        if (market != null) { market.ahCache.clear(); market.ahCacheTime.clear(); market.ordersCache = null; }
    }

    /** Items anderer Mods (nicht minecraft:/donutmod:). */
    static boolean isMod(String id) { return !(id.startsWith("minecraft:") || id.startsWith("donutmod:")); }

    /** Darf dieses Item gehandelt werden? (/mod on|off, /opsettings) */
    static boolean allowed(String id) { return Settings.modItems || !isMod(id); }

    /** /mod on|off: bei "aus" verschwinden Mod-Items aus Bot-Angeboten/-Orders, aus /sell und aus den Menues. */
    void setModItems(boolean on) {
        Settings.setMods(on);
        if (!on) removeBots(DonutMod::isMod);
        initItems();
    }

    /** Entfernt alle Bot-Angebote und Bot-Orders, deren Item das Filter erfuellt (ein Durchlauf, schnell auch bei Millionen). */
    void removeBots(java.util.function.Predicate<String> idFilter) {
        for (AuctionHouse.Listing l : ah.removeBotsIf(x -> idFilter.test(x.stack.id()))) {
            ahItems.remove(l.id);
            if (market != null) market.ahRemoved(l);
        }
        for (Orders.Order o : orders.removeBotsIf(x -> idFilter.test(x.itemId)))
            if (market != null) market.ordersRemoved(o);
        if (market != null) { market.ahCache.clear(); market.ahCacheTime.clear(); market.ordersCache = null; }
    }

    void initItems() {
        ITEM_BY_ID.clear();
        pool.clear();
        for (Item it : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(it).toString();
            ITEM_BY_ID.put(id, it);
            String p = id.substring(id.indexOf(':') + 1);
            if (p.equals("air")) continue;
            boolean bad = false;
            for (String b : BAD) if (p.contains(b)) { bad = true; break; }
            if (!bad && allowed(id)) pool.add(it);
        }
    }

    final Map<Item, String> idCache = new HashMap<>();
    static final Map<Item, String> NAME_CACHE = new HashMap<>();
    final Map<String, Integer> capCache = new HashMap<>();
    final List<Holder.Reference<Enchantment>> enchList = new ArrayList<>();
    final Set<UUID> shulkerOn = new HashSet<>();
    /** Spielernamen (UUID -> Name), damit man /ah <Name> und /orders <Name> nutzen kann, auch fuer Offline-Spieler. */
    final Map<UUID, String> names = new HashMap<>();

    static Path marketFile() { return worldDir().resolve("donutmod-market.json"); }

    static final class SavedListing { String seller; long price; String item; }
    static final class SavedOrder { String owner; String itemId; Map<String, Integer> ench; int remaining; long ppi; }
    static final class SavedMarket {
        List<SavedListing> ah = new ArrayList<>();
        List<SavedOrder> orders = new ArrayList<>();
        Map<String, List<String>> storage = new HashMap<>();
    }

    String encodeStack(ItemStack st) {
        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, srv.registryAccess());
            return ItemStack.CODEC.encodeStart(ops, st).result().map(Tag::toString).orElse(null);
        } catch (Exception e) { return null; }
    }

    ItemStack decodeStack(String s) {
        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, srv.registryAccess());
            CompoundTag tag = TagParser.parseCompoundFully(s);
            return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    /** Speichert Spieler-AH-Angebote, Spieler-Orders und das Lager (Bot-Eintraege entstehen beim Start neu). */
    void saveMarket() {
        if (srv == null) return;
        try {
            SavedMarket sm = new SavedMarket();
            for (AuctionHouse.Listing l : ah.all()) {
                if (l.seller == null) continue;
                ItemStack st = ahItems.get(l.id);
                String enc = st == null ? null : encodeStack(st);
                if (enc == null) continue;
                SavedListing x = new SavedListing();
                x.seller = l.seller.toString(); x.price = l.price; x.item = enc;
                sm.ah.add(x);
            }
            for (Orders.Order o : orders.all()) {
                if (o.owner == null) continue;
                SavedOrder x = new SavedOrder();
                x.owner = o.owner.toString(); x.itemId = o.itemId; x.ench = new HashMap<>(o.enchants);
                x.remaining = o.remaining; x.ppi = o.pricePerItem;
                sm.orders.add(x);
            }
            for (Map.Entry<UUID, List<ItemStack>> e : pending.entrySet()) {
                List<String> l = new ArrayList<>();
                for (ItemStack s : e.getValue()) { String enc = encodeStack(s); if (enc != null) l.add(enc); }
                if (!l.isEmpty()) sm.storage.put(e.getKey().toString(), l);
            }
            writeSafe(marketFile(), GSON.toJson(sm));
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Markt speichern fehlgeschlagen: " + e); }
    }

    void loadMarket() {
        try {
            if (!Files.exists(marketFile())) return;
            SavedMarket sm = GSON.fromJson(Files.readString(marketFile()), SavedMarket.class);
            if (sm == null) return;
            for (SavedListing x : sm.ah) {
                ItemStack st = decodeStack(x.item);
                if (st.isEmpty()) continue;
                AuctionHouse.Listing l = ah.list(UUID.fromString(x.seller), toStack(st), x.price);
                ahItems.put(l.id, st);
            }
            for (SavedOrder x : sm.orders)
                orders.restore(UUID.fromString(x.owner), x.itemId, x.ench == null ? new HashMap<>() : x.ench, x.remaining, x.ppi);
            for (Map.Entry<String, List<String>> e : sm.storage.entrySet()) {
                List<ItemStack> l = new ArrayList<>();
                for (String enc : e.getValue()) { ItemStack st = decodeStack(enc); if (!st.isEmpty()) l.add(st); }
                if (!l.isEmpty()) pending.put(UUID.fromString(e.getKey()), l);
            }
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Markt laden fehlgeschlagen: " + e); }
    }

    static Path namesFile() { return worldDir().resolve("donutmod-names.json"); }

    String playerName(UUID u) { return u == null ? "Server" : names.getOrDefault(u, "Spieler"); }

    UUID uuidOfName(String n) {
        if (n == null || n.isBlank()) return null;
        for (Map.Entry<UUID, String> e : names.entrySet()) if (e.getValue().equalsIgnoreCase(n.trim())) return e.getKey();
        return null;
    }

    void saveNames() {
        try {
            Map<String, String> out = new HashMap<>();
            names.forEach((u, n) -> out.put(u.toString(), n));
            writeSafe(namesFile(), GSON.toJson(out));
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Namen speichern fehlgeschlagen: " + e); }
    }

    void loadNames() {
        try {
            if (!Files.exists(namesFile())) return;
            Map<String, String> in = GSON.fromJson(Files.readString(namesFile()), new TypeToken<Map<String, String>>() {}.getType());
            if (in != null) in.forEach((k, v) -> names.put(UUID.fromString(k), v));
        } catch (Exception e) { System.err.println("[Donutsmp Economy] Namen laden fehlgeschlagen: " + e); }
    }

    String idOf(Item it) { return idCache.computeIfAbsent(it, k -> BuiltInRegistries.ITEM.getKey(k).toString()); }

    static String idOfStatic(ItemStack st) {
        String c = Custom.idOf(st);
        return c != null ? c : BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    static String nameOf(Item it) {
        return NAME_CACHE.computeIfAbsent(it, k -> k.getDefaultInstance().getHoverName().getString().toLowerCase(Locale.ROOT));
    }

    static boolean isShulker(ItemStack st) { return !st.isEmpty() && idOfStatic(st).endsWith("shulker_box"); }

    /** Ton nur fuer diesen Spieler (andere hoeren z.B. den Kauf-Ton im Auktionshaus nicht). */
    static void ping(ServerPlayer p, SoundEvent s, float pitch) { p.playNotifySound(s, SoundSource.PLAYERS, 0.8f, pitch); }

    /** Wie oft ein Item gleichzeitig vorkommen darf: teure/seltene Items nur 1-60 mal (Drachenei: 1x). */
    int cap(String id) {
        return capCache.computeIfAbsent(id, k -> {
            long pr = prices.unitPrice(k, Map.of());
            return pr >= 1_000_000 ? 1 : pr >= 300_000 ? 3 : pr >= 100_000 ? 8 : pr >= 30_000 ? 60 : Integer.MAX_VALUE;
        });
    }

    void initEnchants() {
        enchList.clear();
        if (srv == null) return;
        srv.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(enchList::add);
        // Jede Verzauberung ist ca. 10k (Stufe 1) bis 40k (Maximalstufe) wert, inkl. 25 % Aufschlag
        for (Holder.Reference<Enchantment> h : enchList) {
            int max = Math.max(1, h.value().getMaxLevel());
            prices.setEnchantValue(h.getRegisteredName(), Math.max(8_000L, Math.min(32_000L, 32_000L / max)));
        }
    }

    /** Item mit zufaelligen, passenden Verzauberungen (Buecher: 1-2). null, wenn nicht verzauberbar. */
    ItemStack randomEnchanted(Item it) {
        if (enchList.isEmpty()) return null;
        ItemStack st = new ItemStack(it);
        boolean book = it == Items.ENCHANTED_BOOK;
        if (!book && !st.isEnchantable()) return null;
        List<Holder.Reference<Enchantment>> ok = new ArrayList<>();
        for (Holder.Reference<Enchantment> h : enchList) if (book || h.value().isSupportedItem(st)) ok.add(h);
        if (ok.isEmpty()) return null;
        Collections.shuffle(ok, RND);
        int n = Math.min(ok.size(), 1 + RND.nextInt(book ? 2 : 4));
        for (int i = 0; i < n; i++) {
            Holder.Reference<Enchantment> h = ok.get(i);
            int max = Math.max(1, h.value().getMaxLevel());
            int lvl = RND.nextInt(3) == 0 ? max : 1 + RND.nextInt(max);
            st.enchant(h, lvl);
        }
        return st;
    }

    /** ItemStack aus Item-Id, Verzauberungen und Menge (fuer Anzeige und Lieferungen). */
    ItemStack buildStack(String itemId, Map<String, Integer> ench, int count) {
        ItemStack custom = Custom.make(itemId);
        if (custom != null) {
            custom.setCount(Math.max(1, count));
            for (Map.Entry<String, Integer> e : ench.entrySet())
                for (Holder.Reference<Enchantment> h : enchList)
                    if (h.getRegisteredName().equals(e.getKey())) { custom.enchant(h, e.getValue()); break; }
            return custom;
        }
        Item it = ITEM_BY_ID.get(itemId);
        if (it == null) return ItemStack.EMPTY;
        ItemStack st = new ItemStack(it, Math.max(1, count));
        for (Map.Entry<String, Integer> e : ench.entrySet())
            for (Holder.Reference<Enchantment> h : enchList)
                if (h.getRegisteredName().equals(e.getKey())) { st.enchant(h, e.getValue()); break; }
        return st;
    }

    /** Immer mindestens 2 Angebote pro Shard-Werkzeug und Spawner (15-30 Mio. bzw. Spawner-Preise). */
    void seedCustom() {
        if (!Settings.bots) return;
        Map<String, Integer> have = new HashMap<>();
        for (ItemStack s : ahItems.values()) { String cid = Custom.idOf(s); if (cid != null) have.merge(cid, 1, Integer::sum); }
        for (Map.Entry<String, Long> e : Custom.BASE.entrySet()) {
            for (int i = have.getOrDefault(e.getKey(), 0); i < 2; i++) {
                ItemStack st = Custom.make(e.getKey());
                if (st == null) continue;
                long price = Math.round(e.getValue() * (0.85 + RND.nextDouble() * 0.3));
                AuctionHouse.Listing l = ah.list(null, toStack(st), price);
                ahItems.put(l.id, st);
            }
        }
    }

    void botCycle(MinecraftServer server) {
        if (!Settings.bots) { prices.decay(); return; }   // /bot off: keine Bot-Kaeufe und keine Lieferungen
        if (Settings.botTick <= 1) botTrade(server, 1);   // bei /bot tick > 1 laufen Kaeufe/Lieferungen oefter (siehe Server-Tick)
        prices.decay();
        if (market != null) market.popDecay();
        seedCustom();
        if (market != null) market.ensureCustomOrders();
    }

    private double botTradeAcc = 0;

    /** Bots kaufen Spieler-Angebote und liefern Spieler-Orders. Laeuft 1x pro Minute, bei /bot tick <n> n-mal pro Minute. */
    void botTrade(MinecraftServer server, int rounds) {
        if (!Settings.bots) return;
        for (AuctionHouse.Listing l : ah.botBuyPlayerListings(RND, rounds)) {
            ItemStack st = ahItems.remove(l.id);
            if (market != null) market.ahRemoved(l);
            if (l.seller != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(l.seller);
                if (p != null) {
                    p.sendSystemMessage(Lang.lit("§a[AH] Dein Angebot #" + l.id
                            + (st != null ? " (" + st.getHoverName().getString() + ")" : "") + " wurde fuer $" + fmt(l.price) + " verkauft."));
                    ping(p, SoundEvents.PLAYER_LEVELUP, 1.0f);
                }
            }
        }
        // Spieler-Orders: Bots liefern langsam, in kleinen Portionen
        List<Orders.Order> mine = new ArrayList<>();
        for (Orders.Order o : orders.all()) if (o.owner != null) mine.add(o);
        for (Orders.Order o : mine) {
            ItemStack probe = buildStack(o.itemId, o.enchants, 1);
            if (probe.isEmpty()) continue;
            double ratio = o.pricePerItem / Math.max(1.0, (double) prices.sellUnitPrice(o.itemId, o.enchants));
            double chance = Math.min(0.8, 0.05 * Math.pow(ratio, 3));
            // Bei mehreren Runden auf einmal: erwartete Anzahl Lieferungen k = Runden * Chance (zufaellig auf-/abgerundet)
            int k = rounds <= 1 ? (RND.nextDouble() < chance ? 1 : 0) : (int) Math.floor(rounds * chance + RND.nextDouble());
            if (k >= 1) {
                // billige, leicht zu bekommende Items werden in riesigen Mengen geliefert (bis 500k/min), teure in kleinen
                long sellP = Math.max(1, prices.sellUnitPrice(o.itemId, o.enchants));
                long capPerMin = Math.max(100L, Math.min(500_000L, 200_000_000L / sellP));
                double share = rounds <= 1 ? (0.01 + RND.nextDouble() * 0.02) : 1.0 - Math.pow(0.98, k);   // k Lieferungen mit je ~2 % vom Rest
                int n = (int) Math.max(1, Math.min(capPerMin * k, Math.ceil(o.remaining * share)));
                int d = orders.botDeliver(o, n);
                if (d > 0) addPending(o.owner, buildStack(o.itemId, o.enchants, d));
            }
        }
    }

    // ---- Gelieferte Order-Items (zum Abholen) ----
    final Map<UUID, List<ItemStack>> pending = new HashMap<>();
    MinecraftServer srv;

    void addPending(UUID owner, ItemStack st) {
        List<ItemStack> l = pending.computeIfAbsent(owner, k -> new ArrayList<>());
        int max = Math.max(1, st.getMaxStackSize());
        int left = st.getCount();
        while (left > 0) { int n = Math.min(left, max); l.add(st.copyWithCount(n)); left -= n; }
        if (srv != null) {
            ServerPlayer p = srv.getPlayerList().getPlayer(owner);
            if (p != null) p.sendSystemMessage(Lang.lit("§a[Orders] Lieferung: " + st.getCount() + "x "
                    + st.getHoverName().getString() + " - hole sie mit /orders claim ab."));
            if (p != null) ping(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f);
        }
    }

    int pendingCount(UUID u) {
        int n = 0;
        for (ItemStack s : pending.getOrDefault(u, List.of())) n += s.getCount();
        return n;
    }

    void claim(ServerPlayer p) { Gui.openStorage(this, p); }

    /** Beim Beenden: offene Spieler-Orders zurueckzahlen, damit kein Geld verloren geht. */
    void refundOrders() {
        for (Orders.Order o : new ArrayList<>(orders.all())) if (o.owner != null) orders.cancel(o);
    }

    // ---- Umrechnung MC <-> Core ----
    static Stack toStack(ItemStack st) {
        String id = idOfStatic(st);
        Map<String, Integer> ench = new HashMap<>();
        for (ItemEnchantments ie : new ItemEnchantments[]{st.get(DataComponents.ENCHANTMENTS), st.get(DataComponents.STORED_ENCHANTMENTS)})
            if (ie != null)
                for (Object2IntMap.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> e : ie.entrySet())
                    ench.merge(e.getKey().getRegisteredName(), e.getIntValue(), Math::max);
        return new Stack(id, st.getCount(), ench);
    }

    /** Fuegt den Stack und - bei Shulkern - auch seinen Inhalt zur Verkaufsliste hinzu. */
    static void addStacks(ItemStack st, List<Stack> out) {
        out.add(toStack(st));
        if (isShulker(st)) {
            ItemContainerContents cc = st.get(DataComponents.CONTAINER);
            if (cc != null) for (ItemStack in : cc.nonEmptyItems()) if (!in.isEmpty()) out.add(toStack(in));
        }
    }

    /** Betrag mit k/m/b/t/q/qi-Endung, z.B. 500, 1.5k, 2m. Rueckgabe -1 bei Fehler. */
    static long parseAmount(String s) {
        try {
            s = s.trim().toLowerCase(Locale.ROOT).replace(",", ".");
            double mult = 1;
            boolean two = s.endsWith("qi");
            if (two) mult = 1e18; else if (s.endsWith("q")) mult = 1e15;
            else if (s.endsWith("k")) mult = 1e3; else if (s.endsWith("m")) mult = 1e6;
            else if (s.endsWith("b")) mult = 1e9; else if (s.endsWith("t")) mult = 1e12;
            if (mult != 1) s = s.substring(0, s.length() - (two ? 2 : 1));
            double v = Double.parseDouble(s) * mult;
            if (v < 1 || v > 9.2e18) return -1;
            return (long) v;
        } catch (Exception e) { return -1; }
    }

    /** Kurzform: 3587 -> 3.5k (abgeschnitten, nicht gerundet); k, m, b, t, q, qi. */
    static String fmtShort(long n) {
        long a = Math.abs(n);
        String sign = n < 0 ? "-" : "";
        long unit; String suf;
        if (a >= 1_000_000_000_000_000_000L) { unit = 1_000_000_000_000_000_000L; suf = "qi"; }
        else if (a >= 1_000_000_000_000_000L) { unit = 1_000_000_000_000_000L; suf = "q"; }
        else if (a >= 1_000_000_000_000L) { unit = 1_000_000_000_000L; suf = "t"; }
        else if (a >= 1_000_000_000L) { unit = 1_000_000_000L; suf = "b"; }
        else if (a >= 1_000_000L) { unit = 1_000_000L; suf = "m"; }
        else if (a >= 1_000L) { unit = 1_000L; suf = "k"; }
        else return String.valueOf(n);
        long tenths = a / (unit / 10);
        return sign + (tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "." + (tenths % 10)) + suf;
    }

    /** Volle Zahl mit Tausenderpunkten (3.587). */
    static String fmtFull(long n) { return String.format(Locale.GERMANY, "%,d", n); }

    /** Ueberall genutzt: je nach /settings kurz (3.5k) oder voll (3.587). */
    static String fmt(long n) { return Settings.shortNumbers ? fmtShort(n) : fmtFull(n); }

    // ---- Commands ----
    void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("sell")
                .executes(c -> { Gui.openSell(this, c.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("hand").executes(c -> doSell(c, false)))
                .then(Commands.literal("all").executes(c -> doSell(c, true)))
                .then(Commands.literal("preview").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack h = p.getMainHandItem();
                    List<Stack> pv = new ArrayList<>();
                    if (!h.isEmpty()) addStacks(h, pv);
                    long v = pv.isEmpty() ? 0 : sell.preview(pv);
                    return msg(c, "§eVorschau: §a$" + fmt(v));
                })));

        d.register(Commands.literal("pay")
                .then(Commands.argument("spieler", EntityArgument.player())
                        .then(Commands.argument("betrag", StringArgumentType.word()).executes(c -> {
                            ServerPlayer from = c.getSource().getPlayerOrException();
                            ServerPlayer to = EntityArgument.getPlayer(c, "spieler");
                            long amt = parseAmount(StringArgumentType.getString(c, "betrag"));
                            if (amt <= 0) return msg(c, "§cUngültiger Betrag. Beispiele: 500, 1.5k, 2m");
                            if (from.getUUID().equals(to.getUUID())) return msg(c, "§cDu kannst dir nicht selbst Geld senden.");
                            if (!eco.take(from.getUUID(), amt))
                                return msg(c, "§cNicht genug Geld. Du hast $" + fmt(eco.get(from.getUUID())) + ".");
                            eco.add(to.getUUID(), amt);
                            to.sendSystemMessage(Lang.lit("§a" + from.getName().getString() + " hat dir $" + fmt(amt) + " gesendet."));
                            return msg(c, "§aDu hast $" + fmt(amt) + " an " + to.getName().getString() + " gesendet.");
                        }))));

        // OP-Check wie bei Vanilla-Befehlen (nutzt die Anforderung von /gamemode, funktioniert in jeder Version)
        com.mojang.brigadier.tree.CommandNode<CommandSourceStack> gm = d.getRoot().getChild("gamemode");
        java.util.function.Predicate<CommandSourceStack> isOp = gm != null ? gm.getRequirement() : (src -> false);
        // /money <Spieler> add|set|remove|weniger <Betrag>   (nur OP)
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> moneyCmd = Commands.literal("money").requires(isOp);
        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> who = Commands.argument("spieler", StringArgumentType.word())
                .suggests((ctx, sb) -> SharedSuggestionProvider.suggest(names.values(), sb));
        for (String op : new String[]{"add", "set", "remove", "weniger"}) {
            String kind = op.equals("weniger") ? "remove" : op;
            who.then(Commands.literal(op).then(Commands.argument("betrag", StringArgumentType.word()).executes(c -> money(c, kind))));
        }
        d.register(moneyCmd.then(who));

        // /settings (jeder), /opsettings und /mod on|off (nur OP)
        d.register(Commands.literal("settings").executes(c -> { Gui.openSettings(this, c.getSource().getPlayerOrException()); return 1; }));
        d.register(Commands.literal("opsettings").requires(isOp).executes(c -> { Gui.openOpSettings(this, c.getSource().getPlayerOrException()); return 1; }));
        // /bot: on | off | speed <n> | create order <item> <preis> [menge] | create ah <item> <preis> [menge]   (nur OP)
        d.register(Commands.literal("bot").requires(isOp)
                .executes(c -> msg(c, "§eBots: " + (Settings.bots ? "§aAN" : "§cAUS") + " §7| Angebote: " + fmt(ah.size()) + ", Orders: " + fmt(orders.size()) + " §7| Tempo: " + Settings.botTick + "x"))
                .then(Commands.literal("on").executes(c -> { Settings.setBots(true); return msg(c, "§aBots: AN (Angebote und Orders füllen sich wieder)"); }))
                .then(Commands.literal("off").executes(c -> {
                    Settings.setBots(false);
                    removeBots(id -> true);
                    return msg(c, "§cBots: AUS (alle Bot-Angebote und Bot-Orders entfernt)");
                }))
                .then(Commands.literal("speed")
                        .executes(c -> msg(c, "§eBot-Tempo: §f" + Settings.botTick + "x"))
                        .then(Commands.argument("tempo", IntegerArgumentType.integer(1, Settings.MAX_TICK)).executes(c -> {
                            int n = IntegerArgumentType.getInteger(c, "tempo");
                            Settings.setBotTick(n);
                            return msg(c, "§aBot-Tempo: §f" + n + "x §a(Bots kaufen und liefern " + n + "-mal schneller)");
                        })))
                .then(Commands.literal("create")
                        .then(Commands.literal("order").then(Commands.argument("item", StringArgumentType.word())
                                .suggests((ctx, sb) -> SharedSuggestionProvider.suggest(itemPaths(), sb))
                                .then(Commands.argument("preis", StringArgumentType.word())
                                        .executes(c -> botCreateOrder(c, 0))
                                        .then(Commands.argument("menge", IntegerArgumentType.integer(1))
                                                .executes(c -> botCreateOrder(c, IntegerArgumentType.getInteger(c, "menge")))))))
                        .then(Commands.literal("ah").then(Commands.argument("item", StringArgumentType.word())
                                .suggests((ctx, sb) -> SharedSuggestionProvider.suggest(itemPaths(), sb))
                                .then(Commands.argument("preis", StringArgumentType.word())
                                        .executes(c -> botCreateAh(c, 1))
                                        .then(Commands.argument("menge", IntegerArgumentType.integer(1))
                                                .executes(c -> botCreateAh(c, IntegerArgumentType.getInteger(c, "menge")))))))));

        // /spawner speed <n>: Spawner sammeln n-mal schneller (nur OP)
        d.register(Commands.literal("spawner").requires(isOp)
                .then(Commands.literal("speed")
                        .executes(c -> msg(c, "§eSpawner-Tempo: §f" + Settings.spawnerTick + "x"))
                        .then(Commands.argument("tempo", IntegerArgumentType.integer(1, Settings.MAX_TICK)).executes(c -> {
                            int n = IntegerArgumentType.getInteger(c, "tempo");
                            Settings.setSpawnerTick(n);
                            return msg(c, "§aSpawner-Tempo: §f" + n + "x §a(Spawner sammeln " + n + "-mal schneller)");
                        }))));

        d.register(Commands.literal("mod").requires(isOp)
                .executes(c -> msg(c, "§eMod-Items: " + (Settings.modItems ? "§aAN" : "§cAUS")))
                .then(Commands.literal("on").executes(c -> { setModItems(true); return msg(c, "§aMod-Items: AN (verkaufbar, in /ah und Orders)"); }))
                .then(Commands.literal("off").executes(c -> { setModItems(false); return msg(c, "§cMod-Items: AUS (nicht verkaufbar, nicht in /ah und Orders)"); })));

        // /worth [Item]  zeigt den Wert
        d.register(Commands.literal("worth")
                .executes(c -> worth(c, null))
                .then(Commands.argument("item", StringArgumentType.greedyString())
                        .executes(c -> worth(c, StringArgumentType.getString(c, "item")))));

        d.register(Commands.literal("bal")
                .executes(c -> {
                    long b = eco.get(c.getSource().getPlayerOrException().getUUID());
                    return msg(c, "§eKontostand: §a$" + fmt(b) + " §7(" + fmtFull(b) + ")");
                })
                .then(Commands.argument("spieler", StringArgumentType.word())
                        .suggests((ctx, sb) -> SharedSuggestionProvider.suggest(names.values(), sb))
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "spieler");
                            UUID u = uuidOfName(n);
                            if (u == null) return msg(c, "§cSpieler unbekannt: " + n);
                            return msg(c, "§e" + names.getOrDefault(u, n) + " hat §a$" + fmt(eco.get(u)) + " §7($" + fmtFull(eco.get(u)) + ")");
                        })));

        d.register(Commands.literal("meta").executes(c -> {
            StringBuilder sb = new StringBuilder("§6Aktuelle Metas:");
            meta.current().forEach((id, b) -> sb.append("\n§e").append(id).append(" §7(+").append(Math.round(b * 100)).append("%)"));
            return msg(c, sb.toString());
        }));

        d.register(ordersCmd("orders"));
        d.register(ordersCmd("order"));

        d.register(Commands.literal("ah")
                .executes(c -> { Gui.openAh(this, c.getSource().getPlayerOrException(), ""); return 1; })
                .then(Commands.argument("suche", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b)).executes(c -> {
                    Gui.openAh(this, c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "suche"));
                    return 1;
                }))
                .then(Commands.literal("sell").then(Commands.argument("preis", StringArgumentType.word())
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack h = p.getMainHandItem();
                            if (h.isEmpty()) return msg(c, "§cDu hast nichts in der Hand.");
                            if (!allowed(idOfStatic(h))) return msg(c, "§cItems anderer Mods sind ausgeschaltet (/mod on).");
                            long price = parseAmount(StringArgumentType.getString(c, "preis"));
                            if (price <= 0) return msg(c, "§cUngültiger Preis. Beispiele: 500, 1.5k, 2m, 3b, 1t");
                            ItemStack copy = h.copy();
                            AuctionHouse.Listing l = ah.list(p.getUUID(), toStack(copy), price);
                            ahItems.put(l.id, copy);
                            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            return msg(c, "§aAngebot #" + l.id + " eingestellt fuer $" + fmt(l.price));
                        })))
                .then(Commands.literal("buy").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int id = IntegerArgumentType.getInteger(c, "id");
                            AuctionHouse.Listing l = ah.all().stream().filter(x -> x.id == id).findFirst().orElse(null);
                            if (l == null) return msg(c, "§cAngebot nicht gefunden.");
                            if (!ah.buy(p.getUUID(), l)) return msg(c, "§cNicht genug Geld.");
                            ItemStack st = ahItems.remove(id);
                            if (st != null) p.getInventory().placeItemBackInInventory(st);
                            return msg(c, "§aGekauft fuer $" + fmt(l.price));
                        }))));
    }

    LiteralArgumentBuilder<CommandSourceStack> ordersCmd(String name) {
        return Commands.literal(name)
                .executes(c -> { Gui.openOrders(this, c.getSource().getPlayerOrException(), ""); return 1; })
                .then(Commands.literal("claim").executes(c -> { claim(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("create").then(Commands.argument("menge", StringArgumentType.word())
                        .then(Commands.argument("preis", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack h = p.getMainHandItem();
                            if (h.isEmpty()) return msg(c, "§cNimm das gewünschte Item in die Hand.");
                            long am = parseAmount(StringArgumentType.getString(c, "menge"));
                            long ppi = parseAmount(StringArgumentType.getString(c, "preis"));
                            if (am <= 0 || am > 1_000_000_000L) return msg(c, "§cUngültige Menge (1 bis 1b). Beispiele: 64, 100k, 2m");
                            if (ppi <= 0) return msg(c, "§cUngültiger Preis. Beispiele: 500, 1.5k, 2m, 3b, 1t");
                            if (ppi > Long.MAX_VALUE / am) return msg(c, "§cGesamtpreis zu hoch.");
                            int amount = (int) am;
                            String id = toStack(h).id();
                            Orders.Order o = orders.playerOrder(p.getUUID(), id, toStack(h).enchants(), amount, ppi);
                            if (o == null) return msg(c, "§cNicht genug Geld. Du brauchst $" + fmt(Sat.mul(ppi, amount)) + ".");
                            return msg(c, "§aOrder #" + o.id + " erstellt: " + amount + "x " + h.getHoverName().getString()
                                    + " für je $" + fmt(ppi) + " (bezahlt: $" + fmt(ppi * amount) + ")");
                        }))))
                .then(Commands.argument("suche", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b)).executes(c -> {
                    Gui.openOrders(this, c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "suche"));
                    return 1;
                }));
    }

    int money(CommandContext<CommandSourceStack> c, String op) {
        String n = StringArgumentType.getString(c, "spieler");
        UUID u = uuidOfName(n);
        if (u == null) return msg(c, "§cSpieler unbekannt (muss schon einmal beigetreten sein): " + n);
        String raw = StringArgumentType.getString(c, "betrag");
        long amt = raw.equals("0") ? 0 : parseAmount(raw);
        if (amt < 0 || (amt == 0 && !op.equals("set"))) return msg(c, "§cUngültiger Betrag. Beispiele: 500, 1.5k, 2m, 3b, 1t");
        long cur = eco.get(u);
        switch (op) {
            case "add" -> eco.add(u, amt);
            case "remove" -> eco.take(u, Math.min(amt, cur));
            default -> { if (amt >= cur) eco.add(u, amt - cur); else eco.take(u, cur - amt); }
        }
        ServerPlayer on = srv == null ? null : srv.getPlayerList().getPlayer(u);
        if (on != null) on.sendSystemMessage(Lang.lit("§eDein Kontostand: §a$" + fmt(eco.get(u))));
        return msg(c, "§aKontostand von " + names.getOrDefault(u, n) + ": $" + fmt(eco.get(u)));
    }

    int worth(CommandContext<CommandSourceStack> c, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<Stack> list = new ArrayList<>();
        String label;
        if (name == null || name.isBlank() || name.equalsIgnoreCase("item") || name.equalsIgnoreCase("hand")) {
            ItemStack h = p.getMainHandItem();
            if (h.isEmpty()) return msg(c, "§cNimm ein Item in die Hand oder nutze /worth <Name>, z.B. /worth diamond.");
            addStacks(h, list);
            label = h.getHoverName().getString() + " x" + h.getCount();
        } else {
            String q = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            Item found = null;
            for (Item it : pool) { String id = idOf(it); if (id.substring(id.indexOf(':') + 1).equals(q)) { found = it; break; } }
            if (found == null) for (Item it : pool) if (idOf(it).contains(q)) { found = it; break; }
            if (found == null) return msg(c, "§cKein Item gefunden: " + name);
            list.add(new Stack(idOf(found), 1, Map.of()));
            label = new ItemStack(found).getHoverName().getString() + " x1";
        }
        long sell = 0, market = 0;
        for (Stack s : list) { sell += prices.sellStackPrice(s); market += prices.stackPrice(s); }
        double f = prices.factorOf(list.get(0).id());
        return msg(c, "§6Wert von §f" + label + "\n§7Sell (/sell): §a$" + fmt(sell) + "\n§7AH-Markt: §6$" + fmt(market)
                + "\n§7Nachfrage-Faktor: §f" + String.format(Locale.ROOT, "%.2f", f) + "x");
    }

    int doSell(CommandContext<CommandSourceStack> c, boolean all) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<Stack> stacks = new ArrayList<>();
        if (all) {
            // nur Inventar + Hotbar (Slots 0-35), keine Ruestung und keine Zweithand
            var inv = p.getInventory();
            for (int i = 0; i < 36; i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty() && allowed(idOfStatic(st))) { addStacks(st, stacks); inv.setItem(i, ItemStack.EMPTY); }
            }
        } else {
            ItemStack h = p.getMainHandItem();
            if (!h.isEmpty() && !allowed(idOfStatic(h))) return msg(c, "§cItems anderer Mods sind ausgeschaltet (/mod on).");
            if (!h.isEmpty()) { addStacks(h, stacks); p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); }
        }
        if (stacks.isEmpty()) return msg(c, "§cNichts zu verkaufen.");
        long pay = sell.sell(p.getUUID(), stacks).payout();
        ping(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f);
        return msg(c, "§aVerkauft für $" + fmt(pay) + " §7| Kontostand: $" + fmt(eco.get(p.getUUID())));
    }

    /** "kelp" -> "minecraft:kelp"; Items anderer Mods auch ohne Namensraum (z.B. "brass_ingot"). null = unbekannt. */
    static String resolveItem(String in) {
        String s = in.toLowerCase(Locale.ROOT).trim();
        if (ITEM_BY_ID.containsKey(s)) return s;
        if (ITEM_BY_ID.containsKey("minecraft:" + s)) return "minecraft:" + s;
        if (ITEM_BY_ID.containsKey("donutmod:" + s)) return "donutmod:" + s;
        for (String id : ITEM_BY_ID.keySet()) if (id.endsWith(":" + s)) return id;
        return null;
    }

    static List<String> itemPaths() {
        List<String> l = new ArrayList<>();
        for (String id : ITEM_BY_ID.keySet()) if (!id.equals("minecraft:air")) l.add(id.substring(id.indexOf(':') + 1));
        Collections.sort(l);
        return l;
    }

    /** /bot create order <item> <preis> [menge]: Preis pro Stueck. */
    int botCreateOrder(CommandContext<CommandSourceStack> c, int menge) {
        if (!Settings.bots) return msg(c, "§cBots sind aus (/bot on).");
        long price = parseAmount(StringArgumentType.getString(c, "preis"));
        if (price <= 0) return msg(c, "§cUngültiger Preis. Beispiele: 500, 1.5k, 2m, 3b");
        String id = resolveItem(StringArgumentType.getString(c, "item"));
        if (id == null) return msg(c, "§cItem unbekannt.");
        if (!allowed(id)) return msg(c, "§cItems anderer Mods sind ausgeschaltet (/mod on).");
        int amount = menge > 0 ? menge : Math.max(1, ITEM_BY_ID.get(id).getDefaultInstance().getMaxStackSize());
        Orders.Order o = orders.restore(null, id, Market.NONE, amount, price);
        if (market != null) { market.orderCount.merge(id, 1, Integer::sum); market.ordersCache = null; }
        return msg(c, "§aBot-Order #" + o.id + ": §f" + amount + "x " + id + " §afür $" + fmt(price) + " pro Stück");
    }

    /** /bot create ah <item> <preis> [menge]: Preis fuer das ganze Angebot. */
    int botCreateAh(CommandContext<CommandSourceStack> c, int menge) {
        if (!Settings.bots) return msg(c, "§cBots sind aus (/bot on).");
        String id = resolveItem(StringArgumentType.getString(c, "item"));
        if (id == null) return msg(c, "§cItem unbekannt.");
        if (!allowed(id)) return msg(c, "§cItems anderer Mods sind ausgeschaltet (/mod on).");
        long price = parseAmount(StringArgumentType.getString(c, "preis"));
        if (price <= 0) return msg(c, "§cUngültiger Preis. Beispiele: 500, 1.5k, 2m, 3b");
        int max = Math.max(1, ITEM_BY_ID.get(id).getDefaultInstance().getMaxStackSize());
        int count = Math.min(Math.max(1, menge), max);
        AuctionHouse.Listing l = ah.list(null, new Stack(id, count, Market.NONE), price);
        if (market != null) { market.ahCount.merge(id, 1, Integer::sum); market.ahCache.clear(); market.ahCacheTime.clear(); }
        return msg(c, "§aBot-Angebot #" + l.id + ": §f" + count + "x " + id + " §afür $" + fmt(price));
    }

    static int msg(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Lang.lit(s), false);
        return 1;
    }
}
