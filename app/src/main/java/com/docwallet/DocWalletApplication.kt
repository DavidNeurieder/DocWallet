package com.docwallet

import android.app.Application
import com.docwallet.data.db.DocWalletDatabase
import com.docwallet.data.db.DocumentDao
import com.docwallet.data.encryption.EncryptionManager

class DocWalletApplication : Application() {
    lateinit var encryptionManager: EncryptionManager
        private set

    val documentDao: DocumentDao by lazy {
        val passphrase = encryptionManager.getMasterKeyForSession()
            ?: throw IllegalStateException("Master key not available for database access")
        val db = DocWalletDatabase.create(this, passphrase)
        db.documentDao()
    }

    override fun onCreate() {
        super.onCreate()
        encryptionManager = EncryptionManager(this)
    }
}
