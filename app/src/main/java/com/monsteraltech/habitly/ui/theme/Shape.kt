package com.monsteraltech.habitly.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de formas de Habitly.
 *
 * Más redondeada que la de Material 3 por defecto — lo que separa una app de producto
 * de una plantilla. Los componentes de Material la consumen sola: `Card` usa
 * [Shapes.medium], los diálogos y `BottomSheet` usan [Shapes.extraLarge], los chips
 * usan [Shapes.small].
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * La **firma** de Habitly: la esquina-hoja.
 *
 * Toda superficie-tarjeta recorta su esquina inferior-izquierda (como una hoja
 * plegada) mientras mantiene redondeadas las otras tres. Ese detalle, junto con la
 * sombra verde cálida, es lo que hace reconocible la app de un vistazo.
 *
 * @param radius radio de las tres esquinas redondeadas.
 * @param notch  radio (pequeño) de la esquina inferior-izquierda "plegada".
 */
fun leafCornerShape(radius: Dp = 24.dp, notch: Dp = 8.dp) = RoundedCornerShape(
    topStart = radius,
    topEnd = radius,
    bottomEnd = radius,
    bottomStart = notch,
)

/** Esquina-hoja grande para tarjetas destacadas (cabecera, compra). */
val LeafCornerLarge = leafCornerShape(28.dp, 8.dp)

/** Esquina-hoja estándar para filas de lista (rutinas, productos). */
val LeafCornerMedium = leafCornerShape(24.dp, 8.dp)
