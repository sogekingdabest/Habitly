package com.monsteraltech.habitly.feature.aiassistant.domain.model

/**
 * Encaje de un modelo en el dispositivo actual, decidido por RAM.
 *
 * Existe porque quedarse sin memoria cargando un modelo NO es una excepción que podamos
 * capturar: el motor vive en código nativo y el kernel mata el proceso entero (o el runtime
 * aborta), así que la app desaparece de golpe sin pasar por ningún `catch`. La única defensa
 * es no llegar a intentarlo.
 */
enum class ModelCompatibility {
    /** El dispositivo va sobrado: se carga sin avisos. */
    Supported,

    /** Justo de RAM: probablemente funcione, pero puede morir si hay otras apps abiertas. */
    Tight,

    /** Imposible: ni descargar. Descargar GB para un modelo que no arranca es doble castigo. */
    Unsupported;

    val canUse: Boolean get() = this != Unsupported
}

/**
 * Decide el encaje comparando la RAM del dispositivo con los umbrales del modelo.
 *
 * [deviceRamBytes] debe venir ya redondeado al escalón comercial (ver `DeviceMemoryProbe`):
 * los umbrales están expresados en los GB de la ficha del móvil, no en los que reporta el
 * kernel, que siempre son menos.
 *
 * Con [deviceRamBytes] a 0 (el sistema no supo responder) se devuelve [ModelCompatibility.Tight]:
 * ni bloqueamos por una lectura que falló ni damos vía libre en silencio.
 */
fun AiModelConfig.compatibilityWith(deviceRamBytes: Long): ModelCompatibility = when {
    deviceRamBytes <= 0L -> ModelCompatibility.Tight
    deviceRamBytes < minRamBytes -> ModelCompatibility.Unsupported
    deviceRamBytes < recommendedRamBytes -> ModelCompatibility.Tight
    else -> ModelCompatibility.Supported
}
