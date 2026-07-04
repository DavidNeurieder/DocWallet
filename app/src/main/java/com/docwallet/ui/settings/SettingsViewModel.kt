package com.docwallet.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwallet.DocWalletApplication
import com.docwallet.data.encryption.EncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DocWalletApplication
    private val encryptionManager: EncryptionManager = app.encryptionManager

    val isPasswordSet: Boolean
        get() = encryptionManager.isPasswordSet()

    val currentPassword = MutableStateFlow("")
    val newPassword = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")

    val exportBackupPassword = MutableStateFlow("")
    val exportBackupPasswordConfirm = MutableStateFlow("")
    val importBackupPassword = MutableStateFlow("")

    val showExportPasswordDialog = MutableStateFlow(false)
    val showImportPasswordDialog = MutableStateFlow(false)
    val pendingImportUri = MutableStateFlow<Uri?>(null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setPassword() {
        val pwd = newPassword.value
        val confirm = confirmPassword.value

        when {
            pwd.length < 6 -> {
                _message.value = "Password must be at least 6 characters"
                return
            }
            pwd != confirm -> {
                _message.value = "Passwords do not match"
                return
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            if (encryptionManager.setPassword(pwd)) {
                _message.value = "Password set successfully"
                clearFields()
            } else {
                _message.value = "Failed to set password"
            }
        }
    }

    fun changePassword() {
        val old = currentPassword.value
        val new = newPassword.value
        val confirm = confirmPassword.value

        when {
            old.length < 6 || new.length < 6 -> {
                _message.value = "Password must be at least 6 characters"
                return
            }
            new != confirm -> {
                _message.value = "New passwords do not match"
                return
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            if (encryptionManager.changePassword(old, new)) {
                _message.value = "Password changed successfully"
                clearFields()
            } else {
                _message.value = "Failed to change password. Check your current password."
            }
        }
    }

    fun disablePassword() {
        val pwd = currentPassword.value
        if (pwd.isBlank()) {
            _message.value = "Enter your current password to disable"
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            if (!encryptionManager.verifyPassword(pwd)) {
                _message.value = "Wrong password"
                return@launch
            }
            if (encryptionManager.disablePassword()) {
                _message.value = "Password disabled. Using device-level encryption."
                clearFields()
            } else {
                _message.value = "Failed to disable password"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun onExportConfirmed(uri: Uri) {
        val password = exportBackupPassword.value
        val confirm = exportBackupPasswordConfirm.value

        when {
            password.length < 6 -> {
                _message.value = "Backup password must be at least 6 characters"
                return
            }
            password != confirm -> {
                _message.value = "Backup passwords do not match"
                return
            }
        }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                app.backupManager.exportBackupToUri(uri, password)
            }
            if (success) {
                exportBackupPassword.value = ""
                exportBackupPasswordConfirm.value = ""
            }
            _message.value = if (success) "Backup exported successfully" else "Backup export failed"
        }
        showExportPasswordDialog.value = false
    }

    fun onImportConfirmed() {
        val password = importBackupPassword.value
        val uri = pendingImportUri.value ?: return

        if (password.isBlank()) {
            _message.value = "Enter the backup password"
            return
        }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                val ok = app.backupManager.importBackupFromUri(uri, password)
                if (ok) app.reopenDatabase()
                ok
            }
            if (success) {
                importBackupPassword.value = ""
                pendingImportUri.value = null
            }
            _message.value = if (success) "Backup imported successfully" else "Backup import failed"
        }
        showImportPasswordDialog.value = false
    }

    fun cancelExport() {
        showExportPasswordDialog.value = false
        exportBackupPassword.value = ""
        exportBackupPasswordConfirm.value = ""
    }

    fun cancelImport() {
        showImportPasswordDialog.value = false
        importBackupPassword.value = ""
        pendingImportUri.value = null
    }

    private fun clearFields() {
        currentPassword.value = ""
        newPassword.value = ""
        confirmPassword.value = ""
    }
}
