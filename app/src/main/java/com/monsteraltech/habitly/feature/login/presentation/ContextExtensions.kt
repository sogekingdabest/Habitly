package com.monsteraltech.habitly.feature.login.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("No se pudo encontrar la Activity para el CredentialManager")
}
