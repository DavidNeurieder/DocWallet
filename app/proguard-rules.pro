# DocWallet ProGuard rules
-keepattributes *Annotation*

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Argon2Kt
-keep class com.lambdapioneer.argon2kt.** { *; }
-dontwarn com.lambdapioneer.argon2kt.**

# Apache Commons Compress
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# CommonMark
-keep class org.commonmark.** { *; }
-dontwarn org.commonmark.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
