# R8 optimeringsregler for TilsynsApp

# Bevar linjenumre til debugging af crash-logs
-keepattributes SourceFile,LineNumberTable

# GSON REGLER: Meget vigtigt!
# R8 må ikke omdøbe felter i dine dataklasser, da JSON-nøglerne så ikke vil matche.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.aak.tilsynsapp.TilsynItem { *; }
-keep class com.aak.tilsynsapp.TilsynRow { *; }
-keep class com.aak.tilsynsapp.InspectionRecord { *; }

# Hvis du har andre dataklasser brugt til JSON, så tilføj dem her eller brug wildcard:
# -keep class com.aak.tilsynsapp.models.** { *; }

# ROOM REGLER: Sikrer at databasen virker efter optimering
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OSMDROID (Kortet)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# OKHTTP & GSON (Netværk)
-dontwarn com.google.gson.**
-dontwarn com.squareup.okhttp3.**
