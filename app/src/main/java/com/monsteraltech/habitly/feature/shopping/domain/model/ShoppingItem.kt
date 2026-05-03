package com.monsteraltech.habitly.feature.shopping.domain.model

import com.google.firebase.firestore.PropertyName

data class ShoppingItem(
    var id: String = "",
    var name: String = "",
    @get:PropertyName("isChecked")
    @set:PropertyName("isChecked")
    var isChecked: Boolean = false,
    var store: String = "Cualquiera",
    var authorId: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var quantity: Int = 1,
    var unit: String = "unidad"
)
