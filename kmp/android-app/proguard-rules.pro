# FlutLink keeps its own model + network classes; keep them whole.
-keep class com.flutcloud.flutlink.data.dto.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Tink (via androidx.security:security-crypto) references
# errorprone annotations that are compile-only.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# kotlinx.serialization keeps serializer classes generated at compile time.
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
    static **$* *;
}
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.flutcloud.flutlink.**$$serializer { *; }
-keepclassmembers class com.flutcloud.flutlink.** {
    *** Companion;
}
-keepclasseswithmembers class com.flutcloud.flutlink.** {
    kotlinx.serialization.KSerializer serializer(...);
}