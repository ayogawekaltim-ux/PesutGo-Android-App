package com.pesutgo.app.location

import android.content.Context
import android.location.Location
import org.json.JSONObject

/**
 * Small local bridge between the native foreground location service and the WebView.
 * No server/API is involved here. The website remains responsible for any server-side
 * processing it already performs.
 */
object LocationStore {
    private const val PREFS = "pesutgo_native_location"
    private const val LAT = "lat"
    private const val LNG = "lng"
    private const val ACC = "accuracy"
    private const val SPEED = "speed"
    private const val BEARING = "bearing"
    private const val TIME = "timestamp"

    fun save(context: Context, location: Location) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LAT, location.latitude.toString())
            .putString(LNG, location.longitude.toString())
            .putString(ACC, location.accuracy.toString())
            .putString(SPEED, location.speed.toString())
            .putString(BEARING, location.bearing.toString())
            .putLong(TIME, location.time)
            .apply()
    }

    fun readJson(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = prefs.getString(LAT, null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(LNG, null)?.toDoubleOrNull() ?: return null
        val accuracy = prefs.getString(ACC, "0")?.toFloatOrNull() ?: 0f
        val speed = prefs.getString(SPEED, "0")?.toFloatOrNull() ?: 0f
        val bearing = prefs.getString(BEARING, "0")?.toFloatOrNull() ?: 0f
        val timestamp = prefs.getLong(TIME, 0L)

        return JSONObject().apply {
            put("latitude", lat)
            put("longitude", lng)
            put("accuracy", accuracy)
            put("speed", speed)
            put("heading", bearing)
            put("timestamp", timestamp)
        }.toString()
    }
}
