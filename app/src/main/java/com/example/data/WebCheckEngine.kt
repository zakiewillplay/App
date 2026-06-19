package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object WebCheckEngine {
    private const val TAG = "WebCheckEngine"
    private const val NOTIFICATION_CHANNEL_ID = "web_monitor_alerts"
    
    // Configured with standard timeouts so it doesn't block indefinitely
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Standard MD5 washer to produce unique hashes of cleaned texts.
     */
    fun getMd5Hash(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    /**
     * Strips HTML scripts, script bodies, stylesheet styles, CSS blocks and other marks.
     * Leaves clean visible paragraphs, sentences, and readable words.
     */
    fun extractVisibleText(html: String): String {
        return try {
            // 1. Remove script blocks
            var text = html.replace(Regex("(?s)<script.*?>.*?</script>"), "")
            // 2. Remove style blocks
            text = text.replace(Regex("(?s)<style.*?>.*?</style>"), "")
            // 3. Strip all HTML brackets
            text = text.replace(Regex("<[^>]*>"), "")
            // 4. Clean entities
            text = text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
            // 5. Build cleaned, trim-normalized text lines
            text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        } catch (e: Exception) {
            html
        }
    }

    /**
     * Performs a fetch and check logic for a page.
     * Returns true if there was an alert/notification triggered.
     */
    suspend fun checkPage(
        context: Context,
        repository: PageRepository,
        page: MonitoredPage
    ): Boolean {
        Log.d(TAG, "Starting URL check for: ${page.name} (${page.url})")
        
        // Setup raw request URL with protocol if missing
        var targetUrl = page.url.trim()
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "https://$targetUrl"
        }

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) WebMonitor/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "HTTP Error: ${response.code}"
                repository.updatePage(
                    page.copy(
                        status = errorMsg,
                        lastChecked = System.currentTimeMillis()
                    )
                )
                return false
            }

            val htmlBody = response.body?.string() ?: ""
            val visibleText = extractVisibleText(htmlBody)
            val currentHash = getMd5Hash(visibleText)
            val currentTime = System.currentTimeMillis()

            var triggerNotification = false
            var notificationText = ""
            var changeLogDescription = ""

            val isFirstCheck = (page.lastChecked == 0L)

            if (!page.keyword.isNullOrBlank()) {
                val keyword = page.keyword.trim()
                val isPresentNow = visibleText.contains(keyword, ignoreCase = true)

                if (isFirstCheck) {
                    val statusText = if (isPresentNow) "Keyword '$keyword' present" else "Keyword '$keyword' absent"
                    repository.updatePage(
                        page.copy(
                            lastContentHash = currentHash,
                            lastText = visibleText,
                            lastChecked = currentTime,
                            status = statusText
                        )
                    )
                } else {
                    val wasPresentPreviously = page.lastText?.contains(keyword, ignoreCase = true) == true
                    if (wasPresentPreviously != isPresentNow) {
                        triggerNotification = true
                        if (isPresentNow) {
                            notificationText = "Keyword '$keyword' is now PRESENT on ${page.name}!"
                            changeLogDescription = "Keyword '$keyword' appeared on the webpage."
                        } else {
                            notificationText = "Keyword '$keyword' is now ABSENT from ${page.name}!"
                            changeLogDescription = "Keyword '$keyword' disappeared from the webpage."
                        }
                        
                        repository.insertAlert(
                            AlertLog(
                                pageId = page.id,
                                pageName = page.name,
                                timestamp = currentTime,
                                changeDescription = changeLogDescription
                            )
                        )
                    }

                    val statusText = if (isPresentNow) "Keyword '$keyword' present" else "Keyword '$keyword' absent"
                    repository.updatePage(
                        page.copy(
                            lastContentHash = currentHash,
                            lastText = visibleText,
                            lastChecked = currentTime,
                            status = statusText
                        )
                    )
                }
            } else {
                // Tracking general page content modifications
                if (isFirstCheck) {
                    repository.updatePage(
                        page.copy(
                            lastContentHash = currentHash,
                            lastText = visibleText,
                            lastChecked = currentTime,
                            status = "First check successful"
                        )
                    )
                } else {
                    val didContentChange = page.lastContentHash != currentHash
                    if (didContentChange) {
                        triggerNotification = true
                        notificationText = "Updates detected on ${page.name}!"
                        changeLogDescription = "Webpage layout or text content changed."

                        repository.insertAlert(
                            AlertLog(
                                pageId = page.id,
                                pageName = page.name,
                                timestamp = currentTime,
                                changeDescription = changeLogDescription
                            )
                        )
                    }

                    repository.updatePage(
                        page.copy(
                            lastContentHash = currentHash,
                            lastText = visibleText,
                            lastChecked = currentTime,
                            status = if (didContentChange) "Changes detected" else "No changes detected"
                        )
                    )
                }
            }

            if (triggerNotification) {
                Log.d(TAG, "Triggering Alert Notification: $notificationText")
                sendLocalNotification(context, page.id, page.name, notificationText)
            }

            return triggerNotification

        } catch (e: Exception) {
            Log.e(TAG, "Error checking page: ${page.name}", e)
            repository.updatePage(
                page.copy(
                    status = "Error: ${e.localizedMessage ?: "Unknown connection error"}",
                    lastChecked = System.currentTimeMillis()
                )
            )
            return false
        }
    }

    /**
     * Creates and triggers a device notification channel and alerts the user.
     */
    private fun sendLocalNotification(context: Context, pageId: Int, pageTitle: String, text: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Web Monitor Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when monitored webpages have status changes or keyword triggers."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_PAGE_ID", pageId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            pageId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder) // standard default icon
            .setContentTitle("Web Page Alert: $pageTitle")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notificationManager.notify(pageId, builder.build())
    }
}
