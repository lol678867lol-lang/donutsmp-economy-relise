DonutMod - dein Projekt (Mojang-Mappings, 1.21.11) mit den neuen Funktionen

NEU
- Geld-Anzeige rechts in der Mitte: Donutsmp (oben) / Geld / Economy (unten)
- Zahlen ueberall als k/m/b/t/q/qi (3587 -> 3.5k), in /settings ein- und ausschaltbar
- /settings: Kurze Zahlen, Geld-Anzeige, Money Name Tags (Geld unter dem Namen)
- /opsettings (nur OP): Schalter fuer Bots, Fake-Basen und Mod-Items (gleich wie /bot, /fakebase und /mod on|off)
- /mod on|off (nur OP): Items anderer Mods verkaufbar / in /ah und Orders
- Preise aus Rezepten (Zutaten + 10 %, nie billiger als das Ausgangsmaterial), auch fuer Mod-Items
  -> beim ersten Start entsteht config/donutmod-prices-v8.json (die alte v7-Datei wird nicht mehr benutzt)
- /bot (nur OP):  /bot on  /bot off  /bot create order <item> <preis> [menge]  /bot create ah <item> <preis> [menge]
    off = alle Bot-Angebote und Bot-Orders weg, Bots kaufen/liefern/fuellen nicht mehr; on = wieder an
    Reihenfolge: Item, Preis, Menge. Preise mit k/m/b/t/q/qi (z.B. 1.5k); Item ohne Namensraum (kelp, brass_ingot); Order-Preis = pro Stueck, AH-Preis = fuers ganze Angebot
- /fakebase (nur OP):  /fakebase on|off  /fakebase spawn stash|farm|bigstash
    off = es entstehen keine neuen Fake-Basen (bestehende bleiben); spawn baut eine Basis unter dir (Overworld)
    bigstash = Stash mit vollen Kisten und Riesen-Spawnerstapel (1000-2000)
- Easter Egg: Schild "WerIstDas88" bei X=8888, Z=8888 (Overworld)

GEAENDERT: DonutMod.java, Gui.java, OrderBuilder.java, PriceFile.java, Market.java, Bases.java, AuctionHouse.java, Orders.java
NEU:       Settings.java, RecipeGraph.java, RecipeLoader.java, Easter.java
Alles andere ist unveraendert aus deiner ZIP (inkl. build.gradle, fabric.mod.json, donutmod.accesswidener).

HOCHLADEN
1. ZIP entpacken, alle Dateien in dein GitHub-Repository hochladen (vorhandene ersetzen).
2. .github/workflows/build.yml: Upload-Pfad ist jetzt build/libs/*.jar (nimmt jede Jar).
3. Der Ordner "nur-falls-fehlt" enthaelt gradle.properties und settings.gradle - nur noetig, wenn sie in deinem Repository fehlen
   (diese Dateien dann ins Hauptverzeichnis legen, nicht im Ordner lassen).
