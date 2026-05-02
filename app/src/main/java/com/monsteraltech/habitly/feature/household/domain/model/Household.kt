package com.monsteraltech.habitly.feature.household.domain.model

data class Household(
    var id: String = "",
    var name: String = "",
    var inviteCode: String = "",
    var members: List<String> = emptyList(),
    var customStores: List<String> = emptyList()
)
