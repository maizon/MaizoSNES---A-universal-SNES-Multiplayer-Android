# Preserve JNI methods and the bridge class in minified release builds.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.maizon.maizosnes.NativeBridge { *; }
