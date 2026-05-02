package com.monsteraltech.habitly.feature.household.domain.model

data class UserProfile(
    var id: String = "",
    var displayName: String = "",
    var nickname: String = "",
    var activeHouseholdId: String = ""
)
