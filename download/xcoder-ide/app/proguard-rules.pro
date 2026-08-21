# XCoder IDE — ProGuard Rules

# Keep Hilt classes
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Compose
-dontwarn androidx.compose.**

# Keep LSP4J
-keep class org.eclipse.lsp4j.** { *; }
-keep interface org.eclipse.lsp4j.** { *; }

# Keep sora-editor
-keep class io.github.rosemoe.sora.** { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Termux terminal classes
-keep class com.termux.terminal.** { *; }

# Keep model/data classes
-keep class com.xcoder.**.model.** { *; }
-keep class com.xcoder.**.data.** { *; }
