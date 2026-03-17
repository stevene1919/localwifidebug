package com.enuff.localwifidebug

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.enuff.localwifidebug.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "WiFiDebugReporter"
    // Project home page
    private val GITHUB_URL = "https://github.com/stevene1919/localwifidebug"
    private val SERVICE_TYPE = "_adb-tls-connect._tcp."
    private val WEBHOOK_URL = BuildConfig.WEBHOOK_URL
    private val CHANNEL_ID = "wifi_debug_status"
    
    private lateinit var nsdManager: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        createNotificationChannel()

        binding.versionText.text = "Version ${BuildConfig.VERSION_NAME}"

        // Auto-run logic
        lifecycleScope.launch {
            autoRun()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "WiFi Debug Status"
            val descriptionText = "Notifications for WiFi Debug port reporting"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }

    private var wasAlreadyEnabled = false

    private suspend fun autoRun() {
        try {
            wasAlreadyEnabled = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
            if (!wasAlreadyEnabled) {
                Log.d(TAG, "Enabling WiFi Debugging...")
                Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
                delay(3000) // Give it a bit more time to start
            } else {
                Log.d(TAG, "WiFi Debugging already enabled.")
            }
            
            withContext(Dispatchers.Main) {
                binding.statusText.text = "WiFi Debug Status: ENABLED"
                Toast.makeText(this@MainActivity, "Searching for debug port...", Toast.LENGTH_SHORT).show()
            }
            
            startDiscovery()
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied. Need WRITE_SECURE_SETTINGS.", e)
            showNotification("WiFi Debug Error", "Permission denied. Grant WRITE_SECURE_SETTINGS via ADB.")
            finish()
        }
    }

    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("adb-tls-connect")) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val port = serviceInfo.port
                            Log.d(TAG, "Resolved Port: $port")
                            
                            reportToHomeAssistant(port)
                            stopDiscovery()
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: $errorCode")
                stopDiscovery()
                finish()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {}
        }
        discoveryListener = null
    }

    private fun reportToHomeAssistant(port: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("port", port)
                    put("device_id", BuildConfig.DEVICE_ID)
                }
                
                val request = Request.Builder()
                    .url(WEBHOOK_URL)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "HA Response: ${response.code}")
                    withContext(Dispatchers.Main) {
                        val statusMsg = if (wasAlreadyEnabled) "Already on. Port $port sent." else "Successfully enabled. Port $port sent."
                        if (response.isSuccessful) {
                            showNotification("WiFi Debug Sync", statusMsg)
                            Toast.makeText(this@MainActivity, "Success! $statusMsg", Toast.LENGTH_LONG).show()
                        } else {
                            showNotification("WiFi Debug Error", "HA Error ${response.code}. Port was $port")
                        }
                        delay(1500)
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reporting to HA", e)
                withContext(Dispatchers.Main) {
                    showNotification("WiFi Debug Error", "Error: ${e.message}")
                    delay(1000)
                    finish()
                }
            }
        }
    }
}
