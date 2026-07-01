package com.docwallet

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.docwallet.data.db.CollectionDao
import com.docwallet.data.db.DocWalletDatabase
import com.docwallet.data.db.DocumentDao
import com.docwallet.data.db.TagDao
import com.docwallet.data.encryption.EncryptionManager
import com.docwallet.data.encryption.FileEncryptor
import com.docwallet.data.import.DocumentImporter
import com.docwallet.domain.BackupManager
import java.util.concurrent.atomic.AtomicInteger


class DocWalletApplication : Application() {
    lateinit var encryptionManager: EncryptionManager
        private set

    private val database: DocWalletDatabase by lazy {
        val passphrase = encryptionManager.getMasterKeyForSession()
            ?: throw IllegalStateException("Master key not available for database access")
        DocWalletDatabase.create(this, passphrase)
    }

    val documentDao: DocumentDao by lazy { database.documentDao() }
    val collectionDao: CollectionDao by lazy { database.collectionDao() }
    val tagDao: TagDao by lazy { database.tagDao() }

    val fileEncryptor: FileEncryptor by lazy { FileEncryptor() }

    val documentImporter: DocumentImporter by lazy {
        DocumentImporter(this, documentDao, fileEncryptor, encryptionManager)
    }

    val backupManager: BackupManager by lazy {
        BackupManager(this, encryptionManager)
    }

    override fun onCreate() {
        super.onCreate()
        encryptionManager = EncryptionManager(this)
        registerActivityLifecycleCallbacks(ActivityLifecycleLockCallbacks(encryptionManager))
    }
}

private class ActivityLifecycleLockCallbacks(
    private val encryptionManager: EncryptionManager
) : Application.ActivityLifecycleCallbacks {
    private val activityCount = AtomicInteger(0)

    override fun onActivityStarted(activity: Activity) {
        activityCount.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        if (activityCount.decrementAndGet() <= 0) {
            encryptionManager.lock()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
