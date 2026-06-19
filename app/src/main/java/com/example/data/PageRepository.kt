package com.example.data

import kotlinx.coroutines.flow.Flow

class PageRepository(
    private val pageDao: PageDao,
    private val alertDao: AlertDao
) {
    val allPages: Flow<List<MonitoredPage>> = pageDao.getAllPagesFlow()
    val allAlerts: Flow<List<AlertLog>> = alertDao.getAllAlertsFlow()

    fun getAlertsForPage(pageId: Int): Flow<List<AlertLog>> {
        return alertDao.getAlertsForPageFlow(pageId)
    }

    suspend fun getPageById(id: Int): MonitoredPage? {
        return pageDao.getPageById(id)
    }

    suspend fun getAllPages(): List<MonitoredPage> {
        return pageDao.getAllPages()
    }

    suspend fun getActivePages(): List<MonitoredPage> {
        return pageDao.getActivePages()
    }

    suspend fun insertPage(page: MonitoredPage): Long {
        return pageDao.insertPage(page)
    }

    suspend fun updatePage(page: MonitoredPage) {
        pageDao.updatePage(page)
    }

    suspend fun deletePageById(id: Int) {
        // Cascade delete on alert_logs is handled by Room automatically
        pageDao.deletePageById(id)
    }

    suspend fun insertAlert(alert: AlertLog): Long {
        return alertDao.insertAlert(alert)
    }

    suspend fun deleteAlertById(id: Int) {
        alertDao.deleteAlertById(id)
    }

    suspend fun deleteAlertsForPage(pageId: Int) {
        alertDao.deleteAlertsForPage(pageId)
    }

    suspend fun clearAllAlerts() {
        alertDao.clearAllAlerts()
    }
}
