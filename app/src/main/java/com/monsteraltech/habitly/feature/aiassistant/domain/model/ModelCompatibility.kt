package com.monsteraltech.habitly.feature.aiassistant.domain.model

/**
 * How well a model fits the current device, decided on RAM.
 *
 * This exists because running out of memory while loading a model is **not** a catchable exception:
 * the engine lives in native code and the kernel kills the whole process (or the runtime aborts),
 * so the app vanishes without passing through any `catch`. The only defence is never to try.
 */
enum class ModelCompatibility {
    /** The device has room to spare: load it with no warnings. */
    Supported,

    /** Tight on RAM: it will probably work, but may die with other apps open. */
    Tight,

    /** Out of reach — not even downloadable. Spending GB on a model that cannot start is a
     *  double punishment. */
    Unsupported;

    val canUse: Boolean get() = this != Unsupported
}

/**
 * Decides the fit by comparing device RAM against the model's thresholds.
 *
 * [deviceRamBytes] must already be rounded to its commercial tier (see `DeviceMemoryProbe`): the
 * thresholds are written in the GB on the phone's spec sheet, not the smaller figure the kernel
 * reports.
 *
 * A [deviceRamBytes] of 0 — the system had no answer — yields [ModelCompatibility.Tight]: a failed
 * reading neither blocks the user nor waves them silently through.
 */
fun AiModelConfig.compatibilityWith(deviceRamBytes: Long): ModelCompatibility = when {
    deviceRamBytes <= 0L -> ModelCompatibility.Tight
    deviceRamBytes < minRamBytes -> ModelCompatibility.Unsupported
    deviceRamBytes < recommendedRamBytes -> ModelCompatibility.Tight
    else -> ModelCompatibility.Supported
}
