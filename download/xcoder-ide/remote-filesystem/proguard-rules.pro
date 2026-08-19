# XCoder IDE — Remote Filesystem ProGuard Rules

# Apache Commons Net (FTP)
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# JSch (SFTP)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.xcoder.remote.model.**$$serializer { *; }
-keepclassmembers class com.xcoder.remote.model.** { ** Companion; }
-keepclasseswithmembers class com.xcoder.remote.model.** { ** Companion; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
