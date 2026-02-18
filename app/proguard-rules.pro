-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
**[] $VALUES;
public *;
}

-keep class com.google.firebase.auth.** { *; }

-keepclassmembers class com.example.ourmemories.Models.** {}

-keep class com.example.ourmemories.Models.* { *; }

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
<init>();
}


-keep class androidx.viewbinding.** { }
-keep class com.example.ourmemories.databinding.* { *; }


-keep class com.facebook.shimmer.** { }
-keep class nl.dionsegijn.konfetti.* { *; }

-keepattributes SourceFile,LineNumberTable
-keepattributes Annotation
-keepattributes Signature
-keepattributes EnclosingMethod