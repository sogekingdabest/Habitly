package com.monsteraltech.habitly.feature.shopping.domain.model

data class ShoppingHistory(
    var id: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var items: List<ShoppingItem> = emptyList()
)
