# R8 rules for IPXtream TV.
#
# Everything under com.ipxtream.tv.data is read or written by Gson (Xtream API responses,
# saved favorites/history/accounts, catalog caches, downloads, GitHub releases). Gson maps
# JSON by field name, so these classes must keep their names, or every TV that updates
# would read its saved data as empty.
-keep class com.ipxtream.tv.data.** { *; }

# Gson: generic type information for TypeToken<List<...>> subclasses.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep line numbers so crash logs (Settings → About) stay readable.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Tink (EncryptedSharedPreferences) stores its keyset as protobuf-lite, which reads fields by
# name. Renaming them makes the saved login undecryptable after an update (seen in testing:
# the store was reset and the user signed out).
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { <fields>; }
# Tink references optional HTTP and annotation libraries the app doesn't use.
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
