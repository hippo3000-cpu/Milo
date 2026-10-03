# Milo Alive

Interaktiver Android-Prototyp für **Milo**.

## Funktionen
- 14 Milo-Zustände als lokale Sprites
- zufälliges Blinzeln und spontane Idle-Aktionen
- Inaktivitätsverhalten: Sitzen → Hinlegen → Schlafen
- Tippen, Doppeltippen, Gedrückthalten und Finger-Following
- Herz-Partikel, Haptik und deutsche Text-to-Speech-Ausgaben
- Demo-Reaktionen auf Sync-Erfolg, Offline und Fehler
- interne Werte für Freude, Energie und Neugier

## APK automatisch bauen
GitHub Actions baut bei jedem Push auf `main` sowie manuell über **Actions → Build Android APK → Run workflow** eine Debug-APK.

Nach erfolgreichem Lauf liegt sie unter **Actions → Build Android APK → Artifacts → MiloAlive-debug-apk**.

## Lokal starten
1. Repository in Android Studio öffnen.
2. Android SDK 35 installieren/synchronisieren lassen.
3. App auf Gerät oder Emulator starten.

Das Projekt verwendet bewusst nur Android-Plattform-APIs. Ein späterer Schritt kann Milo durch ein echtes Rive-/Spine-Rig noch flüssiger und lebendiger machen.
