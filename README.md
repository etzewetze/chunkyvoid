# ChunkyVoid

Eigenständiges Minecraft-Server-Plugin mit Void-Generator, pausierbarer Chunk-Vorabgenerierung und unsichtbarer quadratischer Border. Es enthält **keinen Code und keine JAR von Chunky oder VoidWorldGenerator**; beides muss nicht zusätzlich installiert werden. Bereits gespeicherte Chunks werden durch einen Generatorwechsel nicht gelöscht.

## Kompatibilität und Build

- Java 17 Bytecode, Spigot/Paper/Purpur ab Minecraft 1.19 und Folia ab dessen erster Veröffentlichung (1.19.4). Der Server braucht je nach Version Java 17 (1.19), Java 21 (1.20–1.21.11) oder Java 25 (26.1+).
- Die CI kompiliert und testet gegen die Spigot-APIs 1.19 und 26.3. Papier-, Purpur- und Folia-spezifische Scheduler- und Chunk-APIs werden zur Laufzeit erkannt.
- `mvn verify` erstellt `target/chunkyvoid-0.1.0.jar`. Die JAR steht nach einem erfolgreichen Build als Workflow-Artefakt und auf der [Releases-Seite](https://github.com/etzewetze/chunkyvoid/releases) bereit. Ein neuer Release wird beim Merge einer neuen Projektversion nach `main` erstellt.
- Serverlaufzeit und Verhalten mit jeder einzelnen Version müssen zusätzlich auf einem Testserver geprüft werden; ein API-Build ersetzt diesen Test nicht.

## Installation: vollständig leere Welt

1. Den Server stoppen, eine Sicherung der Welt erstellen und die JAR in `plugins/` kopieren.
2. In `bukkit.yml` unter dem vorhandenen Schlüssel `worlds:` den tatsächlichen Weltnamen eintragen (hier `world`):

   ```yaml
   worlds:
     world:
       generator: ChunkyVoid
   ```

3. Bei einer **neuen** Welt den Server starten. Bei einer bereits vorhandenen Welt bleiben vorhandene Chunks unverändert; nur neu erzeugte Chunks sind leer. Eine wirklich vollständig leere Welt muss daher neu angelegt oder nach Sicherung von alten Chunk-Dateien bereinigt werden.
4. In der leeren Welt einen sicheren Spawn mit einer Plattform um (0, 80, 0) bauen oder einen vorhandenen sicheren Spawn verwenden. Der Generator legt zwar eine feste Spawnposition fest, baut aber absichtlich keine Blöcke automatisch; ohne Plattform fällt ein Spieler in die Leere.
5. `/chunkyvoid border set 500 world` setzt eine unsichtbare Border um (0, 0). Mit Mittelpunkt (0, 0) sind Blockkoordinaten −500 bis +500 erlaubt; beim Versuch, Block 501 zu betreten, wird ein sicherer Platz um Block 490 gesucht.

Eine vollständig leere Welt muss nicht groß vorab generiert werden. Die normale Serverspeicherung sichert erzeugte Chunks; Vorabgenerierung hält die Welt nicht dauerhaft im RAM.

## Normales Gelände innen, Void außen

1. Zuerst **ohne** den `generator: ChunkyVoid`-Eintrag eine normale Welt anlegen bzw. laden.
2. `/chunkyvoid border set 500 world`, dann `/chunkyvoid pregen start world` ausführen. Fortschritt mit `/chunkyvoid pregen status` prüfen und bis `done` warten. Der Auftrag deckt alle Chunks ab, die die quadratische Border schneiden.
3. Server sauber stoppen und Welt sichern. Jetzt den Generator-Eintrag aus dem obigen Beispiel in `bukkit.yml` setzen und den Server neu starten. Vorhandene Chunks behalten ihr Gelände, neu erzeugte sind leer.
4. Wenn **vorher schon Gelände außerhalb** der Grenze gespeichert wurde, bleibt es dort erhalten. Diese Chunks nach einer Sicherung separat entfernen. Terrain-Features am Rand können vor dem Wechsel auch Nachbarchunks erzeugen; den Rand auf einem Testserver prüfen.

Den Generator-Eintrag und die Plugin-JAR bei jedem Serverstart beibehalten. Für eine Welt mit vielen Gelände-Chunks kann [Chunky](https://github.com/pop4959/Chunky) als eigenständige Alternative zur eingebauten, bewusst auf eine Anfrage pro Tick begrenzten Vorabgenerierung verwendet werden. ChunkyVoid führt keine Chunky-Befehle aus und benötigt Chunky nicht.

## Befehle

Alle Befehle benötigen OP oder `chunkyvoid.admin`. In der Konsole muss der Weltname angegeben werden, wo `[Welt]` steht.

| Befehl | Wirkung |
| --- | --- |
| `/chunkyvoid border set <Radius> [Welt]` | Border einschalten; Radius ist der Abstand vom Mittelpunkt in Blöcken, kein Durchmesser. |
| `/chunkyvoid border center <X> <Z> [Welt]` | Mittelpunkt speichern. |
| `/chunkyvoid border off [Welt]` | Border ausschalten. |
| `/chunkyvoid border info [Welt]` | Border anzeigen. |
| `/chunkyvoid pregen start [Welt]` | Chunks innerhalb der aktiven Border generieren. |
| `/chunkyvoid pregen pause`, `resume`, `cancel`, `status` | Auftrag verwalten. `cancel` löscht keine Chunks. |
| `/chunkyvoid reload` | `config.yml` neu laden. |

`plugins/ChunkyVoid/config.yml` enthält pro Welt `center-x`, `center-z`, `radius`, `setback` (Standard: 10) und `search-radius` (Standard: 3). Der Weltenname muss exakt stimmen. Ein Serverneustart hält Vorabgenerierungsaufträge an; `resume` setzt sie fort. Nur ein Auftrag kann gleichzeitig laufen. Der Fortschritt liegt in `plugins/ChunkyVoid/pregen.properties` und wird regelmäßig und beim normalen Stop gespeichert. War der Auftrag beim letzten Start noch als laufend gespeichert (etwa nach einem Absturz), prüft `resume` sicherheitshalber wieder alle Chunks ab dem Anfang. Nach einer sauberen Pause oder einem normalen Stop wird der Cursor weiterverwendet.

Die Border ist eine Plugin-Regel und zeigt keine sichtbare Vanilla-Wand. Überschreitet ein Spieler sie, wird die Bewegung abgebrochen und der Titel **„Du kannst hier nicht weiter.“** angezeigt. Die Rückkehr sucht in einem kleinen Bereich zehn Blöcke innerhalb der Grenze nach einem festen, begehbaren Block mit zwei freien Blöcken darüber. Ohne geeigneten Block dort wird eine **sichere Plattform am Weltspawn** als Rückfall gesucht. **Ist auch dort kein fester Block vorhanden, erfolgt aus Sicherheitsgründen kein Teleport**; die Bewegung über die Grenze bleibt gesperrt. Baue daher mindestens eine Spawnplattform oder an gewünschten Grenzabschnitten begehbaren Boden. Der Suchradius ist in der Konfiguration bis 8 einstellbar. Teleports aus anderen Welten direkt außerhalb der Grenze werden abgebrochen.

## Mitentwicklung

Projektinhaber: etzewetze. Plugin-Implementierung und Dokumentation: **OpenAI Codex (KI-Assistenz, Mitentwicklung)**. Dies bezeichnet die Mitarbeit am Code und behauptet keine eigene GitHub-Person oder menschliche Identität.
