package com.docwallet.vault.crypto

interface KeyManager {
    fun getMasterKeyForSession(): ByteArray?
    fun lock()
    fun isPasswordSet(): Boolean
    fun isFirstLaunch(): Boolean
}
