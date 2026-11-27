# Kribo release shrinking rules — BLACK EYE standard.
# Log.d / Log.v calls are stripped from release builds.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int println(...);
}

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod

# Room: entities, DAOs and generated implementations are accessed reflectively.
-keep class com.blackeye.kribo.data.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Kotlin metadata and coroutines internals.
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Google Play Billing.
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Google Identity / Drive authorization.
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class com.google.android.gms.auth.api.signin.** { *; }
-dontwarn com.google.android.gms.auth.api.identity.**
-dontwarn com.google.android.gms.auth.api.signin.**

# Kotlin serialization: block JSON models and generated serializers.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-keep class com.blackeye.kribo.data.blocks.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.blackeye.kribo.data.blocks.** {
    *** Companion;
}

# ONNX Runtime + PP-OCRv5 native bridge.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
# Moonshine Voice (MIT) streaming speech-to-text native bridge.
-keep class ai.moonshine.voice.** { *; }
-dontwarn ai.moonshine.voice.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# ML Kit Document Scanner + bundled Text Recognition fallback.
-keep class com.google.mlkit.vision.documentscanner.** { *; }
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_document_scanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_document_scanner.**
-dontwarn com.google.android.gms.internal.mlkit_vision_text_common.**

# Keep the local signature allow-list even if reflection is used by CI tooling.
-keep class com.blackeye.kribo.security.AppSignatureVerifier { *; }

# Drive sync + local Wi-Fi payloads: JSON serializers and WorkManager worker.
-keep class com.blackeye.kribo.drive.** { *; }
-keep class com.blackeye.kribo.transfer.** { *; }
-keep class com.blackeye.kribo.drive.**$$serializer { *; }
-keep class com.blackeye.kribo.transfer.**$$serializer { *; }
-keepclassmembers class com.blackeye.kribo.drive.** { *** Companion; }
-keepclassmembers class com.blackeye.kribo.transfer.** { *** Companion; }
-keep class com.blackeye.kribo.drive.DriveSyncWorker { *; }

# UVDoc / book pipeline classes are instantiated dynamically in the scan path.
-keep class com.blackeye.kribo.scan.** { *; }
