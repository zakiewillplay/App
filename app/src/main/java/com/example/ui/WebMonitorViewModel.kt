package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WebMonitorApplication
import com.example.data.AlertLog
import com.example.data.MonitoredPage
import com.example.data.WebCheckEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebMonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WebMonitorApplication).repository

    // Reactive lists from database
    val allPages: StateFlow<List<MonitoredPage>> = repository.allPages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allAlerts: StateFlow<List<AlertLog>> = repository.allAlerts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dynamic loading/checking flags mapped by page ID; status of active fetch actions
    private val _checkingPagesState = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val checkingPagesState: StateFlow<Map<Int, Boolean>> = _checkingPagesState.asStateFlow()

    private val _isCheckingAll = MutableStateFlow(false)
    val isCheckingAll: StateFlow<Boolean> = _isCheckingAll.asStateFlow()

    fun addPage(url: String, name: String, keyword: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val formattedUrl = url.trim()
            val pageName = name.ifBlank {
                try {
                    val cleaned = formattedUrl.substringAfter("://").substringBefore("/")
                    cleaned.ifEmpty { "My Web Page" }
                } catch (e: Exception) {
                    "My Web Page"
                }
            }

            val newPage = MonitoredPage(
                url = formattedUrl,
                name = pageName,
                keyword = keyword?.trim()?.ifBlank { null },
                isActive = true
            )
            val newId = repository.insertPage(newPage).toInt()
            
            // Immediately run an initial check to fetch current content
            triggerForceCheckWithId(newId)
        }
    }

    fun deletePage(pageId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _checkingPagesState.value = _checkingPagesState.value - pageId
            repository.deletePageById(pageId)
        }
    }

    fun togglePageActiveStatus(page: MonitoredPage) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = page.copy(isActive = !page.isActive)
            repository.updatePage(updated)
        }
    }

    fun checkPageNow(page: MonitoredPage) {
        viewModelScope.launch {
            _checkingPagesState.value = _checkingPagesState.value + (page.id to true)
            try {
                withContext(Dispatchers.IO) {
                    WebCheckEngine.checkPage(getApplication(), repository, page)
                }
            } catch (e: Exception) {
                Log.e("WebMonitorViewModel", "Manual check error for page id ${page.id}", e)
            } finally {
                _checkingPagesState.value = _checkingPagesState.value - page.id
            }
        }
    }

    fun checkAllPagesNow() {
        viewModelScope.launch {
            _isCheckingAll.value = true
            withContext(Dispatchers.IO) {
                val activePages = repository.getActivePages()
                activePages.forEach { page ->
                    // Set loading status for individual page
                    _checkingPagesState.value = _checkingPagesState.value + (page.id to true)
                    try {
                        WebCheckEngine.checkPage(getApplication(), repository, page)
                    } catch (e: Exception) {
                        Log.e("WebMonitorViewModel", "Force check all error for ${page.id}", e)
                    } finally {
                        _checkingPagesState.value = _checkingPagesState.value - page.id
                    }
                }
            }
            _isCheckingAll.value = false
        }
    }

    fun clearAllAlertHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllAlerts()
        }
    }

    private fun triggerForceCheckWithId(pageId: Int) {
        viewModelScope.launch {
            _checkingPagesState.value = _checkingPagesState.value + (pageId to true)
            try {
                withContext(Dispatchers.IO) {
                    val page = repository.getPageById(pageId)
                    if (page != null) {
                        WebCheckEngine.checkPage(getApplication(), repository, page)
                    }
                }
            } catch (e: Exception) {
                Log.e("WebMonitorViewModel", "Error running initial check for page ID: $pageId", e)
            } finally {
                _checkingPagesState.value = _checkingPagesState.value - pageId
            }
        }
    }
}
