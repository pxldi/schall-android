# kotlinx.serialization reaches the generated serializers through the
# companion, which R8 would otherwise strip from a class nothing else names.
-keepclassmembers class io.github.pxldi.schall.data.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.pxldi.schall.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.pxldi.schall.data.**$$serializer { *; }
