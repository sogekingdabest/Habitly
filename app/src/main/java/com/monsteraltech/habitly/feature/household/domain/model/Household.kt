package com.monsteraltech.habitly.feature.household.domain.model

/**
 * Datos públicos de un miembro, duplicados dentro del documento de la casa.
 *
 * Existe para poder cerrar la lectura de `/users/{uid}` a su propio dueño: antes, resolver
 * el nombre de un compañero de casa exigía leer su perfil, lo que obligaba a dejar TODOS
 * los perfiles legibles por cualquier usuario autenticado. Con la copia aquí, los nombres
 * salen del documento de la casa —que ya está cargado— y de paso se ahorra una lectura de
 * Firestore por miembro y por pantalla.
 *
 * Solo debe contener lo que los demás miembros necesitan ver. Nada de correo ni de
 * identificadores de otros servicios.
 */
data class MemberProfile(
    var displayName: String = "",
    var nickname: String = ""
)

data class Household(
    var id: String = "",
    var name: String = "",
    var inviteCode: String = "",
    /**
     * Cuándo caduca [inviteCode] (epoch ms). Duplicado del documento de `invite_codes`
     * —que solo puede leer quien conoce el código— para que la pantalla de la casa pueda
     * avisar a los miembros. 0 = código antiguo sin caducidad, que ya no se resuelve y hay
     * que regenerar.
     */
    var inviteCodeExpiresAt: Long = 0,
    /**
     * Quién manda en la casa: el único que puede borrarla o expulsar a otros. Antes
     * cualquier miembro podía borrar la casa entera con su historial, lo que en una app
     * de convivencia es un problema de convivencia y no solo técnico.
     *
     * Vacío en las casas creadas antes de este campo; [ownerOrFallback] resuelve ese caso.
     */
    var ownerId: String = "",
    var members: List<String> = emptyList(),
    var customStores: List<String> = emptyList(),
    /**
     * Perfil público de cada miembro, indexado por uid. Se mantiene al día en los puntos
     * donde cambia la pertenencia (crear, unirse, salir, expulsar) y al editar el nickname.
     *
     * Puede venir incompleto en casas creadas antes de que existiera este campo: cada
     * usuario rellena su propia entrada al abrir la app (ver `SyncOwnMemberProfileUseCase`).
     * Por eso quien lo consuma debe tolerar que falte un uid.
     */
    var memberProfiles: Map<String, MemberProfile> = emptyMap()
) {
    /**
     * Propietario efectivo. En las casas creadas antes de que existiera [ownerId] el campo
     * llega vacío, y ahí el criterio es "el primer miembro", que por construcción es quien
     * la creó (createHousehold arranca members con solo el creador y las altas posteriores
     * usan arrayUnion, que añade al final).
     *
     * Devuelve cadena vacía en una casa sin miembros, que no debería existir.
     *
     * `@get:Exclude` es obligatorio: el mapeador de Firestore serializa TODO getter público,
     * así que sin él cada `set(household)` escribía un campo `ownerOrFallback` derivado en el
     * documento, y cada lectura avisaba en el log ("No setter/field for ownerOrFallback").
     * Basura duplicada en la nube y ruido que tapa avisos de verdad.
     */
    @get:com.google.firebase.firestore.Exclude
    val ownerOrFallback: String
        get() = ownerId.ifBlank { members.firstOrNull().orEmpty() }

    fun isOwner(userId: String): Boolean =
        userId.isNotBlank() && userId == ownerOrFallback
}
