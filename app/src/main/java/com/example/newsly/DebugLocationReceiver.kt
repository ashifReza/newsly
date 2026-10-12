package com.example.newsly

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.example.newsly.location.LocationProvider
import com.example.newsly.location.UserLocation

/**
 * Debug builds only: pretend to be somewhere else, to test Near you without moving.
 *
 *   adb shell am broadcast -a com.example.newsly.DEBUG_SET_LOCATION --es city Paris --es country_code FR --es country France
 *   adb shell am broadcast -a com.example.newsly.DEBUG_SET_LOCATION --ez clear true
 *
 * Then leave the app and come back; it picks up the new place on return.
 */
class DebugLocationReceiver(private val provider: LocationProvider) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("clear", false)) {
            provider.debugOverride = null
            Log.i(TAG, "Pretend location cleared")
            return
        }
        val cc = intent.getStringExtra("country_code") ?: return
        provider.debugOverride = UserLocation(
            city = intent.getStringExtra("city"),
            countryCode = cc,
            countryName = intent.getStringExtra("country") ?: cc,
            isApproximate = false,
        )
        Log.i(TAG, "Pretending to be in ${provider.debugOverride}")
    }

    companion object {
        private const val TAG = "NewslyDebug"
        private const val ACTION = "com.example.newsly.DEBUG_SET_LOCATION"

        /** Registers the receiver in debuggable builds; does nothing in release builds. */
        fun registerIfDebuggable(context: Context, provider: LocationProvider) {
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
            val filter = IntentFilter(ACTION)
            val receiver = DebugLocationReceiver(provider)
            // Exported so `adb shell am broadcast` can reach it; only ever registered in debug builds.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }
    }
}
