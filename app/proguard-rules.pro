-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Room genera implementaciones por reflexion en tiempo de compilacion; basta con
# conservar las entidades para que los nombres de columna no se ofusquen.
-keep class mx.ollin.actividades.data.db.** { *; }
-keep class mx.ollin.actividades.domain.model.** { *; }

# SQLCipher llama a estas clases desde JNI; ofuscarlas rompe la carga nativa.
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }

# El lector/escritor XLSX usa el SAX de la plataforma.
-dontwarn org.xmlpull.v1.**
