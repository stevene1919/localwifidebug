package com.enuff.steven.localwifidebug

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class WiFiDebugTileService : TileService() {

    private val TAG = "WiFiDebugTile"
    private val SERVICE_TYPE = "_adb-tls-connect._tcp."
    private val WEBHOOK_URL = "http://192.168.50.200:8123/api/webhook/ccwgt_port"
    private val client = OkHttpClient()
    private lateinit var nsdManager: NsdManager

    override fun onCreate() {
        super.onCreate()
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        val isEnabled = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
        qsTile?.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isEnabled = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
        val newState = if (isEnabled) 0 else 1

        try {
            Settings.Global.putInt(contentResolver, "adb_wifi_enabled", newState)
            updateTile()
            
            if (newState == 1) {
                startDiscovery()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied. Grant WRITE_SECURE_SETTINGS via ADB.", e)
        }
    }

    private fun startDiscovery() {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("adb-tls-connect")) {
                    val l = this
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(p0: NsdServiceInfo?, p1: Int) {}

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            reportToHomeAssistant(serviceInfo.port)
                            try {
                                nsdManager.stopServiceDiscovery(l)
                            } catch (e: Exception) {}
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(p0: String?, p1: Int) {}
            override fun onStopDiscoveryFailed(p0: String?, p1: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun reportToHomeAssistant(port: Int) {
        val json = JSONObject().apply {
            put("port", port)
            put("device_id", "ccwgt")
        }
        
        val request = Request.Builder()
            .url(WEBHOOK_URL)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to report to HA", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                Log.d(TAG, "Reported port $port to HA: ${response.code}")
                response.close()
            }
        })
    }
}
