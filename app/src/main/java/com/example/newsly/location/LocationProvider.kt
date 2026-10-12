package com.example.newsly.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class UserLocation(
    val city: String?,
    val countryCode: String,
    val countryName: String,
    /** True when we only know the country (no permission, or no fix). */
    val isApproximate: Boolean,
)

/**
 * Resolves the user's city and country. Only ever keeps the place name;
 * raw coordinates never leave this class.
 *
 * Fallback order: device location + Geocoder → SIM/network country → device locale.
 */
class LocationProvider(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun currentLocation(): UserLocation {
        if (hasPermission()) {
            runCatching { fromDevice() }.getOrNull()?.let { return it }
        }
        return fromRegion()
    }

    @SuppressLint("MissingPermission") // Checked by hasPermission() before calling.
    private suspend fun fromDevice(): UserLocation? {
        val location = withTimeoutOrNull(10_000) {
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                .await()
        } ?: fused.lastLocation.await() ?: return null

        if (!Geocoder.isPresent()) return null
        val address = reverseGeocode(location.latitude, location.longitude) ?: return null
        val cc = address.countryCode ?: return null
        return UserLocation(
            city = address.locality ?: address.subAdminArea ?: address.adminArea,
            countryCode = cc,
            countryName = address.countryName ?: countryName(cc),
            isApproximate = false,
        )
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): Address? {
        val geocoder = Geocoder(context, Locale.ENGLISH) // English names match English news queries.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        cont.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        cont.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }

    private fun fromRegion(): UserLocation {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val cc = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().country.takeIf { it.isNotBlank() }
            ?: "US"
        return UserLocation(city = null, countryCode = cc.uppercase(), countryName = countryName(cc), isApproximate = true)
    }

    private fun countryName(cc: String): String =
        Locale("", cc.uppercase()).getDisplayCountry(Locale.ENGLISH).ifBlank { cc.uppercase() }
}
