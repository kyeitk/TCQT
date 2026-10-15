package com.owo233.tcqt.features.menu.settingmecards

import android.util.AtomicFile
import com.owo233.tcqt.core.env.HookEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class WeatherCity(val name: String, val latitude: Double, val longitude: Double) {
    companion object {
        fun parse(raw: String): WeatherCity {
            val values = raw.split(',').map(String::trim)
            require(values.size == 3 && values[0].isNotEmpty()) { "请设置城市名,纬度,经度" }
            val lat = values[1].toDouble()
            val lon = values[2].toDouble()
            require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
            return WeatherCity(values[0], lat, lon)
        }
    }
}

@Serializable
internal data class CardWeatherSnapshot(
    val citySetting: String,
    val temperature: Double,
    val apparent: Double,
    val humidity: Int,
    val wind: Double,
    val code: Int,
    val high: Double,
    val low: Double,
    val updatedAt: Long,
)

internal object CardWeather {
    private val json = Json { ignoreUnknownKeys = true }
    private val file get() = AtomicFile(File(HookEnv.moduleDataPath, "settingme-cards/weather.json"))

    suspend fun cached(city: String): CardWeatherSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            json.decodeFromString(CardWeatherSnapshot.serializer(),
                file.openRead().bufferedReader().use { it.readText() }).takeIf { it.citySetting == city }
        }.getOrNull()
    }

    // Open-Meteo documented current and daily variables; no QQ credentials or location APIs.
    suspend fun refresh(raw: String): CardWeatherSnapshot = withContext(Dispatchers.IO) {
        val city = WeatherCity.parse(raw)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
        val connection = URL(url).openConnection() as HttpURLConnection
        val payload = try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            require(connection.responseCode == 200) { "天气服务暂不可用" }
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
        val current = payload.getJSONObject("current")
        val daily = payload.getJSONObject("daily")
        val result = CardWeatherSnapshot(raw,
            current.getDouble("temperature_2m"), current.getDouble("apparent_temperature"),
            current.getInt("relative_humidity_2m"), current.getDouble("wind_speed_10m"),
            current.getInt("weather_code"), daily.getJSONArray("temperature_2m_max").getDouble(0),
            daily.getJSONArray("temperature_2m_min").getDouble(0), System.currentTimeMillis())
        val atomic = file
        atomic.baseFile.parentFile?.mkdirs()
        val stream = atomic.startWrite()
        try {
            stream.write(json.encodeToString(CardWeatherSnapshot.serializer(), result).toByteArray())
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
        result
    }

    fun description(code: Int): String = when (code) {
        0 -> "晴"; 1, 2 -> "多云"; 3 -> "阴"; 45, 48 -> "雾"
        in 51..57 -> "毛毛雨"; in 61..67 -> "雨"; in 71..77 -> "雪"
        in 80..82 -> "阵雨"; 85, 86 -> "阵雪"; in 95..99 -> "雷雨"; else -> "天气"
    }
}
