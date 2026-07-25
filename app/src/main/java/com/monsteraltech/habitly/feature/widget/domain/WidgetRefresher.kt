package com.monsteraltech.habitly.feature.widget.domain

/**
 * Avisa al widget de pantalla de inicio de que los datos han cambiado.
 *
 * Es una interfaz para que las capas de datos no dependan de Glance ni arrastren un
 * `Context` a los tests: en las pruebas basta con no bindear nada.
 */
interface WidgetRefresher {
    /**
     * Repinta todas las instancias del widget. No bloquea: se lanza en segundo plano y los
     * fallos se tragan, porque refrescar el widget nunca debe tumbar la escritura que lo
     * provocó.
     */
    fun refresh()
}
