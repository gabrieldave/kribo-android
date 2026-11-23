# Kribo release shrinking rules — BLACK EYE standard.
# Log.d / Log.v calls are stripped from release builds.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
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
-keepclasseswithmembernames class * {
    native <methods>;
}

# ML Kit Document Scanner (unbundled, loaded through Play services).
-keep class com.google.mlkit.vision.documentscanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_document_scanner.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_document_scanner.**

# Keep the local signature allow-list even if reflection is used by CI tooling.
-keep class com.blackeye.kribo.security.AppSignatureVerifier { *; }

# Drive sync: WorkManager instantiates the worker by name; serializers are reflective.
-keep class com.blackeye.kribo.drive.DriveSyncWorker { *; }
-keep class com.blackeye.kribo.drive.**$$serializer { *; }
-keepclassmembers class com.blackeye.kribo.drive.** { *** Companion; }
