# Military Aircraft Radar - MAR

Android-App, die den aktuellen Standort verwendet und militärisch registrierte Flugzeuge live über ADSB.lol überwacht. Treffer lassen sich sichtbar in Flightradar24 öffnen.

## Verhalten
- Foreground-Service mit dauerhafter Statusbenachrichtigung
- Live-Abfrage alle 10 Sekunden
- Einstellbarer Radius von 10 bis 300 km
- Meldung nur bei militärischem Datenbank-Flag und neuem Eintritt in den Radius
- Wählbarer Tracker beim Antippen: Flightradar24 oder ADS-B Exchange
- OLED-, Hell- und System-Theme
- Deutsch, Englisch oder Systemsprache
- Luftfahrt- oder metrische Einheiten
- Live-Flugzeugliste und animierte Detailansichten
- Animiertes Radar nur bei aktiver Überwachung
- Animierter App-Start
- Keine Zugangsdaten erforderlich
- Kein Scraping von Flightradar24 oder ADS-B Exchange

## Bauen
1. Projekt in Android Studio öffnen.
2. Android SDK 35 installieren.
3. `Build > Build APK(s)` ausführen.
4. Debug-APK liegt anschließend unter `app/build/outputs/apk/debug/app-debug.apk`.

## Einschränkungen
ADS-B/MLAT ist nicht vollständig und kann je nach Empfängerabdeckung sowie abgeschalteten oder verdeckten Transpondern Flugzeuge fehlen lassen. Android kann den Dienst bei aggressiver Akkuoptimierung beenden; deshalb die App bei Bedarf von der Akkuoptimierung ausnehmen.
