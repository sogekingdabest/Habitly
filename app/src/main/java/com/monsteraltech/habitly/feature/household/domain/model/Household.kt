package com.monsteraltech.habitly.feature.household.domain.model

/**
 * Public data of a member, duplicated inside the household document.
 *
 * This exists so `/users/{uid}` can be locked down to its own owner. Resolving a housemate's name
 * used to require reading their profile, which forced every profile to stay readable by any
 * authenticated user. With the copy here, names come from the already-loaded household document
 * and one Firestore read per member per screen is saved.
 *
 * Must only hold what other members need to see — no email, no identifiers from other services.
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
     * When [inviteCode] expires (epoch ms). Duplicated from the `invite_codes` document — which
     * only someone who knows the code can read — so the household screen can warn its members.
     * 0 means a legacy code with no expiry, which no longer resolves and must be regenerated.
     */
    var inviteCodeExpiresAt: Long = 0,
    /**
     * Who owns the household: the only one who can delete it or remove others. Previously any
     * member could delete the whole household along with its history.
     *
     * Empty in households created before this field existed; [ownerOrFallback] covers that case.
     */
    var ownerId: String = "",
    var members: List<String> = emptyList(),
    var customStores: List<String> = emptyList(),
    /**
     * Proof submitted by each member when joining, keyed by uid.
     *
     * Firestore rules compare a newly-added value with the household's current invite code. This
     * prevents someone who merely knows the document id (for example, a former member) from adding
     * themselves again. Proofs are removed with membership and become harmless after code rotation.
     */
    var joinProofs: Map<String, String> = emptyMap(),
    /**
     * Public profile of each member, keyed by uid. Kept up to date wherever membership changes
     * (create, join, leave, remove) and when a nickname is edited.
     *
     * May be incomplete in households created before this field existed: each user fills in their
     * own entry on launch (see `SyncOwnMemberProfileUseCase`), so consumers must tolerate a
     * missing uid.
     */
    var memberProfiles: Map<String, MemberProfile> = emptyMap()
) {
    /**
     * Effective owner. In households created before [ownerId] existed the field arrives empty, and
     * the rule there is "the first member" — by construction whoever created it, since
     * createHousehold seeds members with the creator alone and later joins use arrayUnion, which
     * appends. Returns an empty string for a household with no members, which should not exist.
     *
     * `@get:Exclude` is mandatory: the Firestore mapper serialises **every** public getter, so
     * without it each `set(household)` wrote a derived `ownerOrFallback` field into the document
     * and every read logged "No setter/field for ownerOrFallback".
     */
    @get:com.google.firebase.firestore.Exclude
    val ownerOrFallback: String
        get() = ownerId.ifBlank { members.firstOrNull().orEmpty() }

    fun isOwner(userId: String): Boolean =
        userId.isNotBlank() && userId == ownerOrFallback
}
