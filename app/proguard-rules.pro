-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Room genera implementaciones por reflexion en tiempo de compilacion; basta con
# conservar las entidades para que los nombres de columna no se ofusquen.
-keep class com.carlosalbertoxw.ollin.actividades.data.db.** { *; }
-keep class com.carlosalbertoxw.ollin.actividades.domain.model.** { *; }

# Estos enums no se guardan como numero sino como su nombre, y se releen con
# valueOf(). Si R8 los renombra, lo guardado deja de reconocerse y se cae al
# valor por omision sin avisar: la seleccion de pestanas del usuario se pierde,
# y en el caso del bloqueo la app se abriria sin pedir la llave.
-keepclassmembers enum com.carlosalbertoxw.ollin.actividades.data.excel.EsquemaExportacion { *; }
-keepclassmembers enum com.carlosalbertoxw.ollin.actividades.data.excel.HojaExportable { *; }
-keepclassmembers enum com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo { *; }

# SQLCipher llama a estas clases desde JNI; ofuscarlas rompe la carga nativa.
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }

# El lector/escritor XLSX usa el SAX de la plataforma.
-dontwarn org.xmlpull.v1.**
