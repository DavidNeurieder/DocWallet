package com.docwallet.ui.tag

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docwallet.DocWalletApplication
import com.docwallet.data.model.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class TagViewModel(application: Application) : AndroidViewModel(application) {
    private val tagDao = (application as DocWalletApplication).tagDao

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val tags: StateFlow<List<Tag>> = tagDao.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nameInput = MutableStateFlow("")
    val selectedColor = MutableStateFlow(randomColor())
    val showDialog = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            tagDao.getAllTags().collect {
                _isLoading.value = false
            }
        }
    }

    fun createTag(name: String, color: Long) {
        viewModelScope.launch {
            tagDao.insert(Tag(name = name, color = color))
        }
    }

    fun deleteTag(id: String) {
        viewModelScope.launch {
            tagDao.deleteById(id)
        }
    }

    fun randomColor(): Long {
        val colors = listOf(
            0xFFF44336L, 0xFFFF9800L, 0xFFFFEB3BL, 0xFF4CAF50L,
            0xFF009688L, 0xFF2196F3L, 0xFF9C27B0L, 0xFFE91E63L,
            0xFF795548L, 0xFF9E9E9EL,
        )
        return colors[Random.nextInt(colors.size)]
    }
}
