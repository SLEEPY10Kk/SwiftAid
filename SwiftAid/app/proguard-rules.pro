# Keep Firebase, Google Identity, and Ktor serialization metadata used at runtime.
-keepattributes Signature,*Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Native crash detector JNI entry points.
-keep class com.example.swiftaid.NativeCrashBridge { *; }
-keep class com.example.swiftaid.NativeCrashBridge$CrashCallback { *; }
