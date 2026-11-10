Donutsmp Economy - dein Projekt (Mojang-Mappings, 1.21.11) mit den neuen Funktionen

NEU
- Geld-Anzeige rechts in der Mitte: Donutsmp (oben) / Geld / Economy (unten)
- Zahlen ueberall als k/m/b/t/q/qi (3587 -> 3.5k), in /settings ein- und ausschaltbar
- /settings: Kurze Zahlen, Geld-Anzeige, Money Name Tags (Geld unter dem Namen)
- Einstellungen und Preise gelten PRO WELT: /opsettings, /settings, Preise (Preisliste, Prozent-Regler), Bot-Tempo, Basen-Hoehe usw.
  liegen im Weltordner (donutmod/donutmod-settings.json, donutmod/donutmod-prices-v8.json). Jede Welt hat ihre eigenen Werte.
- /opsettings (nur OP): Schalter fuer Bots, Fake-Basen, Mod-Items und Idle-Screen (gleich wie /bot, /fakebase und /mod on|off)
    "Preise aendern": Liste aller Items (Vanilla, Mods, eigene) mit Preis, Suche per Chat; Klick auf ein Item -> neuen Preis in den Chat
    tippen (z.B. 1.5k). Der Preis wird gespeichert (config/donutmod-prices-v8.json) und gilt sofort im Markt.
    Preis aendern: Klick auf ein Item in der Liste. Aendert sich z.B. Diamant, ziehen alle daraus gecrafteten Items (Schwert, Ruestung, ...)
    mit, und vorhandene Bot-Angebote und Bot-Orders dieser Items werden SOFORT neu bepreist. Feste Preise bleiben.
    Preise in Prozent: "Alle Preise" (alle Items inkl. Bot-Angebote/-Orders) und "Order-Preise" (nur Bot-Orders): -10 %, +10 %, eigener Wert, zuruecksetzen.
- Sell Axe: auf eine Kiste/ein Fass schauen -> Wert des Inhalts steht in der Action-Bar
- Auktionshaus: der Kauf-Ton ist nur fuer den Kaeufer zu hoeren (nicht fuer andere Spieler)
- Geld ueberlaeuft nie: bei extrem hohen Betraegen bleibt es am Maximum (9.2qi) stehen und geht nie ins Minus
- Orders erstellen: Preis und Menge lassen sich per Klick auf die Anzeige frei eintippen (nicht nur +10k)
- Bots machen mehr Orders auf Basen-Loot (Netherite/Diamant-Gear, Gaps, Totems ...)
- Sprache: folgt der Minecraft-Sprache. Deutsch (de_*) = deutsch, alles andere = englisch (vom ersten Spieler uebernommen)
- /mod on|off (nur OP): Items anderer Mods verkaufbar / in /ah und Orders
- Preise aus Rezepten (Zutaten + 10 %, nie billiger als das Ausgangsmaterial), auch fuer Mod-Items
  -> beim ersten Start entsteht config/donutmod-prices-v8.json (die alte v7-Datei wird nicht mehr benutzt)
- /bot (nur OP):  /bot on  /bot off  /bot create order <item> <preis> [menge]  /bot create ah <item> <preis> [menge]
    speed <n> (1-999999): Bots kaufen Angebote und liefern Orders n-mal schneller (z.B. /bot speed 999; /bot speed 1 = normal)
    off = alle Bot-Angebote und Bot-Orders weg, Bots kaufen/liefern/fuellen nicht mehr; on = wieder an
    Reihenfolge: Item, Preis, Menge. Preise mit k/m/b/t/q/qi (z.B. 1.5k); Item ohne Namensraum (kelp, brass_ingot); Order-Preis = pro Stueck, AH-Preis = fuers ganze Angebot
- /spawner speed <n> (nur OP, 1-999999): Spawner sammeln n-mal schneller (/spawner speed 1 = normal)
- /fakebase (nur OP):  /fakebase on|off  /fakebase height <von> <bis> (Boden-y der Basen, -60 bis 300; auch in /opsettings)  /fakebase spawn <stash|farm|bigstash|base> [netherite|dia] [x z [y]]
    x z = Koordinaten (Ecke der Basis, z.B. /fakebase spawn bigstash netherite 1000 -500); ohne Koordinaten wird neben dir gebaut.
    base = zufaellig Stash oder Farm. Jede Basis hat NUR Netherite ODER NUR Diamant (ohne Stufe zufaellig).
    Loot ist sortiert: eine Kiste pro Sorte (Helme, Brustplatten, Hosen, Schuhe, Schwerter, Aexte, Spitzhacken, ...), jedes Teil 5-50 mal,
    andere Items 5-50 oder ein Stack; OP-Gaps: Diamant-Stash 1-5, Netherite-Stash 20-640. Big-Stash: breiterer Raum, 44 Kisten (alle Sorten).
    Es gibt nur hilfreiche Traenke (kein Gift/Schaden/Schwaeche).
    off = es entstehen keine neuen Fake-Basen (bestehende bleiben); spawn baut eine Basis unter dir (Overworld)
    bigstash = Stash mit vollen Kisten und Riesen-Spawnerstapel (1000-2000)
- Idle-Screen: nach 1 Minute ohne Bewegung (und ohne offenes Menue) erscheint "Made by WerIstDas88"; bei Bewegung verschwindet es (in /opsettings abschaltbar)
- Easter Egg: Schild "WerIstDas88" bei X=8888, Z=8888 (Overworld); es steigt jede Sekunde 1 Block hoch (solange ein Spieler im Umkreis von 64 Bloecken ist) bis y=318

GEAENDERT: DonutMod.java, Settings.java, DonutModClient.java, EnderStorage.java, Gui.java, OrderBuilder.java, PriceFile.java, Market.java, Bases.java, AuctionHouse.java, Orders.java
NEU:       Settings.java, RecipeGraph.java, RecipeLoader.java, Easter.java, IdleScreen.java, Lang.java, PriceEditor.java, ChestPreview.java
Alles andere ist unveraendert aus deiner ZIP (inkl. build.gradle, fabric.mod.json, donutmod.accesswidener).

HOCHLADEN
1. ZIP entpacken, alle Dateien in dein GitHub-Repository hochladen (vorhandene ersetzen).
2. .github/workflows/build.yml: Upload-Pfad ist jetzt build/libs/*.jar (nimmt jede Jar).
3. Version: mod_version=1.0.3 in gradle.properties -> Jar heisst Donutsmp-Economy-1.0.3.jar (steht schon eine gradle.properties im Repository, dort mod_version=1.0.3 setzen).
4. Der Ordner "nur-falls-fehlt" enthaelt gradle.properties und settings.gradle - nur noetig, wenn sie in deinem Repository fehlen
   (diese Dateien dann ins Hauptverzeichnis legen, nicht im Ordner lassen).
