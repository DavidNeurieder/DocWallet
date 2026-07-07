package com.docwallet.vault.storage

interface Storage {
    fun save(id: String, data: ByteArray)
    fun load(id: String): ByteArray
    fun delete(id: String)
    fun exists(id: String): Boolean
}
