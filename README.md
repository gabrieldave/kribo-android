# Kribo — Android

Notas rápidas, escaneo OCR local y búsqueda. Privado por diseño, sin cuentas y sin rastreo.

Home ofrece lista compacta o cuadrícula Bento, cuatro temas (Obsidiana, Papel Cálido, AMOLED Negro y Claro Puro), captura rápida inferior y escaneo OCR en un toque.

- **Paquete:** `com.blackeye.kribo`
- **Stack:** Kotlin · Jetpack Compose · Material 3 · Room v2 · SplashScreen API · ML Kit Document Scanner + ONNX Runtime PP-OCRv5
- **SDK:** compileSdk 36 · targetSdk 36 · minSdk 26
- **Versión:** 1.0.2 (versionCode 3)
- **Billing:** Google Play Billing `billing-ktx` 8.3.0

## Requisitos

- JDK 21 (Temurin)
- Android SDK Platform 36 y Build Tools 36
- NDK 27.0.12077973 (por `debugSymbolLevel` en release)
- Dispositivo/emulador con API 26+

`local.properties` (no versionado):

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

## Comandos

```bash
./gradlew testDebugUnitTest --console=plain -q
./gradlew assembleDebug --console=plain
./gradlew installRelease --console=plain -q
./gradlew bundleRelease --console=plain
```

## Firma

La configuración release resuelve credenciales en este orden:

1. Variables de entorno de CI: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
   `ANDROID_KEY_ALIAS` (o `ANDROID_KEYSTORE_ALIAS`), `ANDROID_KEYSTORE_KEY_PASSWORD`.
2. `~/.blackeye-keys/android/keystore.env` en local.

Secretos de GitHub Actions requeridos por el workflow:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEYSTORE_ALIAS`

Ningún `.jks`, contraseña o `local.properties` se versiona.

## Estructura

```
app/src/main/java/com/blackeye/kribo/
├── billing/        BillingManager (formattedPrice, acknowledge en onResume)
├── data/           Room v2: Folder, Note, Attachment, bloque JSON, migración 1→2
├── importexport/   ENEX / Markdown / ZIP import, Markdown / PDF / backup export
├── ocr/            PP-OCRv5 lazy (ONNX), cuota cifrada, AttachmentStore
├── security/       AppSignatureVerifier (release-only) + SecurePrefsStore
├── ui/
│   ├── components/ AppScreen, AppModal (contenedores obligatorios)
│   ├── home/ note/ search/ scan/ settings/ theme/
│   └── KriboApp.kt
└── MainActivity.kt
app/schemas/        Esquemas Room versionados
```

## Reglas de contribución

Ver [`AGENTS.md`](AGENTS.md). Resumen: toda pantalla usa `AppScreen`, todo modal usa `AppModal`,
paridad estricta `values/` ↔ `values-es/`, precios solo desde Play Billing y seguridad release activa.
