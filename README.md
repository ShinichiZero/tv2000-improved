# TV 2000 – FireStick App

App nativa Android TV per guardare **TV 2000** (Canale 28 DTT) sul FireStick 2
e qualsiasi dispositivo Android TV. Usa ExoPlayer (Media3) con un buffer
ottimizzato per bassa RAM.

---

## Come costruire l'APK (senza Android Studio)

### Metodo 1 — GitHub Actions (consigliato, gratis, 0 installazioni)

1. Crea un account su [github.com](https://github.com) (gratis)
2. Crea un **nuovo repository pubblico**
3. Carica tutti i file di questa cartella nel repository
4. Vai su **Actions → Build TV2000 APK → Run workflow**
5. Aspetta ~3 minuti, poi scarica l'APK da **Artifacts**

### Metodo 2 — Android Studio (locale)

1. Installa [Android Studio](https://developer.android.com/studio)
2. Apri questa cartella come progetto
3. Clicca `Build → Build Bundle(s)/APK(s) → Build APK(s)`
4. L'APK si trova in `app/build/outputs/apk/debug/app-debug.apk`

---

## Come installare sul FireStick

### Via ADB (cavo + PC)

```bash
# 1. Abilita ADB sul FireStick:
#    Impostazioni → My Fire TV → Opzioni sviluppatore → Debugging ADB ON

# 2. Trova l'IP del FireStick:
#    Impostazioni → My Fire TV → About → Network

# 3. Collega via ADB (stesso WiFi):
adb connect <IP_FIRESTICK>:5555
adb install app-debug.apk
```

### Via Downloader (senza PC)

1. Installa **Downloader** dall'Amazon App Store sul FireStick
2. Carica l'APK su Google Drive o Dropbox e copia il link diretto
3. Apri Downloader, incolla il link e installa

---

## Controlli telecomando FireStick

| Tasto            | Azione                        |
|------------------|-------------------------------|
| **OK / Select**  | Mostra/attiva controlli       |
| **◄ ►**          | Naviga tra i pulsanti         |
| **▲ ▼**          | Mostra overlay                |
| **Back**         | Chiudi overlay / esci         |
| **Play/Pause**   | Play / Pausa stream           |

---

## Note tecniche

- **ExoPlayer Media3 1.3.1** per HLS nativo
- **Buffer massimo 25 s** (default ExoPlayer: ~50 s) → meno RAM usata
- **Auto-reload ogni 30 minuti** per prevenire freeze su dispositivi low-RAM
- **Retry automatico** fino a 5 volte con backoff esponenziale
- **Qualità ciclica**: Auto → SD 480p → HD 720p
- Stream URL: `https://hls-live-tv2000.akamaized.net/hls/live/2028510/tv2000/master.m3u8`
