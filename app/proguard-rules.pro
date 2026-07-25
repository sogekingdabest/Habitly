# ---------------------------------------------------------------------------------------------
# Reglas de R8 para Habitly.
#
# Criterio: reglas POR PAQUETE, no clase por clase. Los modelos y las tools que se añadan en el
# futuro quedan cubiertos sin tener que volver a este fichero, que es justo el tipo de paso que
# se olvida y solo se nota en producción.
#
# Cómo verificar un cambio aquí: `assembleRelease` que compila NO demuestra nada, R8 rompe en
# tiempo de ejecución. Instala el APK de release y recorre login, casa, compra, despensa,
# rutinas, asistente de IA (descarga + mensaje) y widget.
# ---------------------------------------------------------------------------------------------

# --- Modelos de datos -------------------------------------------------------------------------
# Firestore (toObject), Gson y Room deserializan por reflexión emparejando el NOMBRE del campo
# con el de la nube o el de la columna. Renombrarlos no da error de compilación: los campos
# llegan a null en silencio y la app se ve vacía.
-keep class com.monsteraltech.habitly.**.domain.model.** { *; }
-keep class com.monsteraltech.habitly.**.data.source.local.** { *; }
-keepclassmembers class com.monsteraltech.habitly.** {
    @com.google.firebase.firestore.PropertyName *;
}

# --- Room -------------------------------------------------------------------------------------
# Room genera <Database>_Impl y lo instancia con getDeclaredConstructor() sin argumentos. La regla
# de consumidor de room-runtime 2.6.1 es `-keep class * extends androidx.room.RoomDatabase` SIN
# bloque de miembros, y en R8 full mode —obligatorio desde AGP 8— eso conserva la clase pero NO su
# constructor por defecto. La primera víctima no es una base de datos nuestra sino la de
# WorkManager: `androidx.startup` la crea al arrancar el proceso y el APK de release moría con
# NoSuchMethodException androidx.work.impl.WorkDatabase_Impl.<init>[] antes de pintar la primera
# pantalla. Regla genérica a propósito: cubre también las nuestras y las de cualquier librería.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# --- Tool-calling del asistente de IA ---------------------------------------------------------
# litertlm resuelve las tools con kotlin-reflect: lee el nombre de la función, el nombre de CADA
# PARÁMETRO y las anotaciones @Tool/@ToolParam. Si R8 renombra `addRoutine(title, frequency)`,
# el esquema que ve el modelo deja de coincidir y la propuesta de rutinas cae en silencio a la
# vía de reserva por JSON. No hay excepción que lo delate.
-keep class com.monsteraltech.habitly.feature.aiassistant.data.tools.** { *; }

# --- Acciones del widget (Glance) -------------------------------------------------------------
# `actionRunCallback<T>()` no guarda una referencia a la clase: guarda su NOMBRE dentro del
# PendingIntent, y al pulsar la casilla Glance la reinstancia con Class.forName + constructor sin
# argumentos. Si R8 la renombra o se la come por "no usada", tachar desde el widget revienta con
# ClassNotFoundException en release y funciona perfectamente en debug.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}

# --- LiteRT-LM --------------------------------------------------------------------------------
# El AAR no trae reglas de consumidor (comprobado en litertlm-android-0.14.0.aar: no hay
# proguard.txt) y su capa JNI resuelve clases y métodos por nombre desde C++ —LiteRtLmJni,
# JniMessageCallback, JniInferenceCallback, NativeLibraryLoader—. Renombrarlas es
# NoSuchMethodError al cargar el modelo.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# kotlin-reflect entra como dependencia transitiva de litertlm y reconstruye las firmas Kotlin
# (incluidos los nombres de parámetro) leyendo @kotlin.Metadata. Sin la anotación no hay nombres.
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# --- Atributos --------------------------------------------------------------------------------
# Signature: sin él Gson resuelve List<T> como List<Object> y devuelve LinkedTreeMap.
# *Annotation*: comodín que cubre RuntimeVisible[Parameter]Annotations y AnnotationDefault, que
# es lo que leen kotlin-reflect y el mapeador de Firestore.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Trazas de crash legibles en Play Console (sube el mapping.txt junto al bundle).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
