package br.com.taparelli.bigword

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import org.json.JSONArray
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class Palette(val label: String, val foreground: Int, val background: Int) {
    constructor(label: String, foreground: String, background: String) :
        this(label, Color.parseColor(foreground), Color.parseColor(background))
}

internal val palettes = listOf(
    Palette("STANDARD", "#FF5722", "#26626D"),
    Palette("CLEAN", "#000000", "#FFFFFF"),
    Palette("TURQUOISE", "#1ABC9C", "#5D4037"),
    Palette("SUNNY", "#F1C40F", "#3498DB"),
    Palette("CARROT", "#27DF60", "#E67E22"),
    Palette("CHERRY", "#C2185B", "#ECF0F1"),
    Palette("AMETHYST", "#FFFFFF", "#9B59B6"),
    Palette("EMERALD", "#FFFFFF", "#2ECC71"),
    Palette("ALIZARIN", "#FFFFFF", "#E74C3C"),
    Palette("ASPHALT", "#95A5A6", "#34495E"),
    Palette("OMELET", "#576377", "#FFC107"),
    Palette("MATRIX", "#00FF00", "#000000"),
)

internal data class FontChoice(val label: String, val family: String) {
    fun load(context: Context): Typeface =
        Typeface.createFromAsset(context.assets, "fonts/$family.ttf")
}

internal val fonts = listOf(
    FontChoice("ANTON", "Anton-Regular"),
    FontChoice("BANGERS", "Bangers-Regular"),
    FontChoice("BEBAS", "BebasNeue-Regular"),
    FontChoice("JETBRAINS", "JetBrainsMono-Regular"),
    FontChoice("MERRIWEATHER", "Merriweather_36pt-Regular"),
    FontChoice("MONTSERRAT", "Montserrat-Regular"),
    FontChoice("OSWALD", "Oswald-Regular"),
    FontChoice("PACIFICO", "Pacifico-Regular"),
    FontChoice("PLAYFAIR", "PlayfairDisplay-Regular"),
    FontChoice("ROBOTO SLAB", "RobotoSlab-Regular"),
)

internal enum class Mode(val labelRes: Int) {
    FIT(R.string.fit_screen), CAROUSEL(R.string.carousel), WORDS(R.string.word_by_word)
}

private const val speedLimit = 0.75
private val fadeDurations = listOf(1600L, 1000L, 600L, 300L)

internal fun fadeDurationMillis(speed: Int, mode: Mode): Long {
    val position = speed * speedLimit / 25
    val lower = position.toInt().coerceAtMost(fadeDurations.lastIndex - 1)
    val fraction = position - lower
    val start = fadeDurations[lower]
    val end = if (mode == Mode.WORDS && lower + 1 == fadeDurations.lastIndex) {
        200L
    } else {
        fadeDurations[lower + 1]
    }
    return (start + (end - start) * fraction).roundToLong()
}

internal fun carouselDpPerSecond(speed: Int): Double =
    90.0 * (2600.0 / 90.0).pow(speed * speedLimit / 100)

internal class Settings(context: Context) {
    private val defaultMessage = context.getString(R.string.default_message)
    private val preferences = context.getSharedPreferences("bigword", Context.MODE_PRIVATE)

    init {
        if (!preferences.getBoolean("legacyImported", false)) importLegacy(context)
    }

    var message: String
        get() = preferences.getString("inputText", defaultMessage) ?: defaultMessage
        set(value) { preferences.edit().putString("inputText", value).apply() }

    var mode: Mode
        get() = Mode.entries.getOrElse(preferences.getInt("mode", 0)) { Mode.FIT }
        set(value) { preferences.edit().putInt("mode", value.ordinal).apply() }

    var blink: Boolean
        get() = preferences.getBoolean("blink", false)
        set(value) { preferences.edit().putBoolean("blink", value).apply() }

    var speed: Int
        get() {
            // Earlier versions stored a preset or a percentage on the wider range.
            val legacySpeed = preferences.getInt("speed", 0).coerceIn(0, 2)
            val previousProgress = preferences.getInt("speedProgress", (legacySpeed + 1) * 25)
            val adjusted = (previousProgress / speedLimit).roundToInt()
            return preferences.getInt("limitedSpeedProgress", adjusted).coerceIn(0, 100)
        }
        set(value) { preferences.edit().putInt("limitedSpeedProgress", value).apply() }

    var paletteIndex: Int
        get() = preferences.getInt("fontStyle", 0).coerceIn(palettes.indices)
        set(value) { preferences.edit().putInt("fontStyle", value).apply() }

    var fontIndex: Int
        get() = preferences.getInt("fontIndex", 0).coerceIn(fonts.indices)
        set(value) { preferences.edit().putInt("fontIndex", value).apply() }

    var favorites: List<String>
        get() = parseFavorites(preferences.getString("favorites", null))
        set(value) { preferences.edit().putString("favorites", JSONArray(value).toString()).apply() }

    private fun importLegacy(context: Context) {
        val file = context.getDatabasePath("RKStorage")
        val values = mutableMapOf<String, String>()
        if (file.isFile) {
            try {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.query("catalystLocalStorage", arrayOf("key", "value"), null, null, null, null, null)
                        .use { rows ->
                            while (rows.moveToNext()) values[rows.getString(0)] = rows.getString(1)
                        }
                }
            } catch (error: SQLiteException) {
                Log.w("BigWord", "Could not read v3 settings", error)
                return
            }
        }

        val edit = preferences.edit()
        fun importInt(key: String, index: Int?) {
            if (index != null && !preferences.contains(key)) edit.putInt(key, index)
        }
        fun importString(key: String, value: String?) {
            if (value != null && !preferences.contains(key)) edit.putString(key, value)
        }
        importString("inputText", values["inputText"])
        importString("favorites", values["favorites"]?.let { JSONArray(parseFavorites(it)).toString() })
        importInt("mode", values["mode"]?.toIntOrNull()?.takeIf { it in Mode.entries.indices })
        importInt("speed", values["speed"]?.toIntOrNull()?.takeIf { it in 0..2 })
        importInt("fontStyle", values["fontStyle"]?.toIntOrNull()?.takeIf { it in palettes.indices })
        val familyIndex = fonts.indexOfFirst { it.family == values["fontFamily"]?.trim() }
        importInt("fontIndex", values["fontIndex"]?.toIntOrNull()?.takeIf { it in fonts.indices }
            ?: familyIndex.takeIf { it >= 0 })
        if (!preferences.contains("blink")) {
            values["blink"]?.toBooleanStrictOrNull()?.let { edit.putBoolean("blink", it) }
        }
        edit.putBoolean("legacyImported", true).apply()
    }

    private fun parseFavorites(value: String?): List<String> {
        if (value == null) return emptyList()
        val array = runCatching { JSONArray(value) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.opt(it) as? String }.distinct()
    }
}
