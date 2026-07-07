package com.docwallet.vault.model

data class VaultCollection(
    val id: String,
    val name: String,
    val icon: String,
    val sortOrder: Int,
    val parentId: String?,
)
