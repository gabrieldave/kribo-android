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

# Keep the local signature allow-list even if reflection is used by CI tooling.
-keep class com.blackeye.kribo.security.AppSignatureVerifier { *; }
