# AGENTS.md — Reglas de Kribo Android

Este documento es vinculante para cualquier agente o persona que toque el repositorio.
Si una tarea entra en conflicto con estas reglas, se detiene y se consulta al CEO.

## 1. UI: contenedores obligatorios

- **Toda pantalla** debe construirse dentro de `AppScreen` (`ui/components/AppScreen.kt`).
- **Todo modal o diálogo** debe construirse dentro de `AppModal` (`ui/components/AppModal.kt`).
- Prohibido usar `Scaffold`, `Dialog`, `ModalBottomSheet` o `AlertDialog` directamente en pantallas.
- `AppScreen` y `AppModal` ya aplican `statusBarsPadding()`, `navigationBarsPadding()` e `imePadding()`.
  No duplicar esos paddings dentro del contenido.
- Todo pie con CTA debe ir en el parámetro `footer` de `AppScreen` (margen seguro >= 16.dp).
- Área táctil mínima: **48.dp** en cualquier elemento interactivo.
- Iconos: solo vectores Material (`androidx.compose.material.icons`). **Cero emojis como iconos.**
- Texto de botones: acción explícita, nunca "OK" genérico.

## 2. Paleta y tema

- Cuatro temas oficiales: **Obsidiana** (default), **Papel Cálido**, **AMOLED Negro** y **Claro Puro**, implementados solo en `ui/theme/Theme.kt` y persistidos en `UiPreferences`.
- Prohibido hardcodear colores de tema en pantallas; las tarjetas Bento pueden tintar el borde con el color de la carpeta.

| Rol | Oscuro | Claro |
|---|---|---|
| Fondo obsidiana | `#0D1117` | `#F6F8FA` |
| Superficie | `#161B22` | `#FFFFFF` |
| Texto | `#FFFFFF` | `#0D1117` |
| Acento cian eléctrico | `#00D8FF` | `#007A99` |

- Implementación única en `ui/theme/Color.kt` y `ui/theme/Theme.kt`.
- Prohibido hardcodear colores en pantallas; usar `MaterialTheme.colorScheme`.

## 3. Arranque y rendimiento

- SplashScreen API nativa (`androidx.core:core-splashscreen`).
- Transición `<= 350 ms` (`windowSplashScreenAnimationDuration = 250`).
- Prohibido añadir `delay()`, `Thread.sleep()` o pantallas de carga artificiales al abrir.
- El motor PP-OCRv5 (`ocr/OcrSubsystem.kt`) se carga de forma diferida solo al escanear: prohibido tocar ONNX, modelos o ML Kit en `Application.onCreate`/`MainActivity.onCreate`.
- Presupuesto de arranque en frío: **< 250 ms** (objetivo 120-160 ms).

## 4. Idioma

- `res/values/strings.xml` = inglés (base) y `res/values-es/strings.xml` = español.
- Paridad 100% obligatoria. `StringParityTest` falla el build si falta una clave o un placeholder.
- Prohibido hardcodear texto visible en Kotlin.
- Selector real en Ajustes: Automático / Español / English con
  `AppCompatDelegate.setApplicationLocales` + `res/xml/locales_config.xml`.

## 5. Datos

- Room v2 (`FolderEntity`, `NoteEntity`, `AttachmentEntity`) con `exportSchema = true`; los esquemas viven versionados en `app/schemas/`.
- Los bloques del editor se serializan con kotlinx.serialization en `blocksJson`; nunca romper la compatibilidad hacia atrás.
- Toda migración lleva su test (`Migration1To2Test`).
- Toda migración debe ser explícita y revisable. Prohibido `fallbackToDestructiveMigration`.
- El repositorio (`NoteRepository`) es la única puerta de acceso a la base de datos.

## 6. Monetización

- Google Play Billing `billing-ktx` **8.x**. Prohibido 7.x.
- Los precios se leen **solo** de `ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`.
  Prohibido escribir precios en strings, assets o UI.
- En cada `onResume`: `queryPurchasesAsync` + `acknowledgePurchase` si corresponde.

## 7. Seguridad (base del protocolo de 6 capas)

- `android:allowBackup="false"`, `fullBackupContent="false"`.
- `network_security_config.xml` con `cleartextTrafficPermitted="false"`.
- `AppSignatureVerifier` se ejecuta **solo en release** (`BuildConfig.DEBUG == false`).
  - Huella maestra BLACK EYE incluida.
  - Huecos preparados para las 3 huellas de Play App Signing (clásica activa, ML-DSA-65, clásica anterior).
- Prohibido incluir API keys en `BuildConfig`, `strings.xml` o `assets/`.
- Prohibido commitear `*.jks`, `*.keystore`, `*.p12`, `keystore.properties`, `keystore.env`, `local.properties`, `.env`.

## 8. Build y verificación

```bash
./gradlew testDebugUnitTest --console=plain -q
./gradlew installRelease --console=plain -q
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | head -c 1500
adb logcat -d -t 50 '*:E' AndroidRuntime:E
```

- `ANDROID_HOME` local: `/opt/homebrew/share/android-commandlinetools` (ver `local.properties`, no versionado).
- Firma release: variables de CI (`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS` / `ANDROID_KEYSTORE_ALIAS`) con fallback a `~/.blackeye-keys/android/keystore.env`.

## 9. Git

- Commits pequeños y descriptivos (`feat:`, `fix:`, `chore:`, `test:`).
- No reescribir historia publicada.
- No tocar `main` con cambios sin tests en verde.
