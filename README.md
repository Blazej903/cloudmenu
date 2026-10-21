# Cloud Menu (Fabric 1.21.11)

Czyste menu z chmurką w lewym górnym rogu, otwierane klawiszem **P**.

## Co jest w środku
- **Gracze** – lista graczy z serwera (głowa, nick, ping), klik kopiuje nick
- **Moduły** – Fullbright, Auto sprint, HUD (FPS / ping / koordynaty)
- **Wygląd** – kolor motywu (gotowe kolory + suwaki RGB), przezroczystość, jasny/ciemny motyw
- **Klawisze** – zmiana klawiszy w menu (menu, zoom, fullbright, auto sprint)
- Zoom: przytrzymaj **C**

Ustawienia zapisują się w `.minecraft/config/cloudmenu.json`.

## Budowanie
1. Zainstaluj JDK 21 i otwórz folder w IntelliJ IDEA (Gradle sam się zaimportuje).
2. W terminalu w folderze projektu:
   - Windows: `gradlew.bat build`
   - Linux/macOS: `./gradlew build`
3. Gotowy plik: `build/libs/cloudmenu-1.0.0.jar`
4. Wrzuć go do folderu `mods` profilu w Feather Client (razem z Fabric API).

Jeśli Gradle napisze, że jakiejś wersji nie ma (Yarn, Loader, Fabric API), podmień ją
w `gradle.properties` na aktualną z https://fabricmc.net/develop/template/
