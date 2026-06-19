package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("WebMonitorWorker", "Periodic Web Monitor worker triggered successfully.")
        
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = PageRepository(db.pageDao(), db.alertDao())
        
        try {
            val activePages = repository.getActivePages()
            Log.d("WebMonitorWorker", "Found ${activePages.size} active pages to verify.")
            
            for (page in activePages) {
                try {
                    WebCheckEngine.checkPage(applicationContext, repository, page)
                } catch (e: Exception) {
                    Log.e("WebMonitorWorker", "Failed checking page ID ${page.id} (${page.name})", e)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("WebMonitorWorker", "Detailed error in WebMonitorWorker execution", e)
            Result.retry()
        }
    }
}
