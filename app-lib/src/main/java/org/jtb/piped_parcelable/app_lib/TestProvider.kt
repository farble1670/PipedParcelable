package org.jtb.piped_parcelable.app_lib

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jtb.piped_parcelable.PipedParcelable
import kotlin.random.Random

class TestProvider : ContentProvider() {
    private companion object {
        private const val TAG = "TestProvider"
        private const val METHOD_START = "start"
        private const val METHOD_STOP = "stop"
        private const val METHOD_RECEIVE = "receive"
        private const val MAX_SIZE = 10_000_000 // 10MB
        private const val DELAY_MS = 1000L

        @Volatile
        private var isStarted = false

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "test_provider_channel"
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var testJob: Job? = null

    private val authority: String by lazy {
        context?.packageName?.let { "$it.provider" } ?: throw IllegalStateException("No package name")
    }

    private val targetAuthority: String by lazy {
        if (app == "app1") {
            "org.jtb.piped_parcelable.app2.provider"
        } else {
            "org.jtb.piped_parcelable.app1.provider"
        }
    }

    private val tag: String
        get() = authority?.split(".")?.takeLast(2)?.joinToString(".") ?: "unknown.provider"
    private val app: String
        get() = authority?.split(".")?.dropLast(1)?.lastOrNull() ?: "unknown"


    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Test Provider",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running parcel tests"
            }
            val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(context!!, CHANNEL_ID)
            .setContentTitle("Test Provider Running")
            .setContentText("Running parcel tests")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Start foreground service through a bound service
        val intent = Intent(context, TestService::class.java)
        context?.startForegroundService(intent)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        extras?.classLoader = PipedParcelable::class.java.classLoader

        return when (method) {
            METHOD_START -> {
                if (!isStarted) {
                    isStarted = true
                    context?.startForegroundService(Intent(context, TestService::class.java))
                    startTesting()
                    Log.i(tag, "Testing started")
                }
                Bundle()
            }
            METHOD_STOP -> {
                if (isStarted) {
                    isStarted = false
                    testJob?.cancel()
                    context?.stopService(Intent(context, TestService::class.java))
                    Log.i(tag, "Testing stopped")
                }
                Bundle()
            }
            METHOD_RECEIVE -> {
                try {
                    val start = SystemClock.elapsedRealtime()
                    val piped = extras?.getParcelable<PipedParcelable<TestParcelable>>("data")
                    val data = piped?.value
                    Log.i(tag, "Received ${data?.content?.length ?: 0} bytes from $app in ${SystemClock.elapsedRealtime() - start}ms")
                } catch (e: Exception) {
                    Log.e(tag, "Error receiving data", e)
                }
                Bundle()
            }
            else -> super.call(method, arg, extras)
        }
    }

    private fun startTesting() {
        testJob = scope.launch {
            while (isStarted) {
                try {
                    val size = Random.nextInt(MAX_SIZE)
                    val data = TestParcelable(RandomString.generate(size))
                    val piped = PipedParcelable(data)

                    val start = SystemClock.elapsedRealtime()
                    context?.contentResolver?.call(
                        Uri.parse("content://$targetAuthority/test"),
                        METHOD_RECEIVE,
                        null,
                        Bundle().apply { putParcelable("data", piped) }
                    )

                    Log.i(tag, "Sent ${data.content.length} bytes to $app in ${SystemClock.elapsedRealtime() - start}ms")
                } catch (e: Exception) {
                    Log.e(tag, "Error sending data", e)
                }

                delay(DELAY_MS)
            }
        }
    }
}