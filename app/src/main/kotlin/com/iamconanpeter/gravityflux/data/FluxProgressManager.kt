/*
 * FluxProgressManager.kt
 *
 * Persistence layer for Gravity Flux. Stores:
 *  - best star rating per campaign/daily level (SharedPreferences)
 *  - daily-solve tracking with a streak-safe consecutive-day counter
 *
 * The streak logic is deliberately simple and correct:
 *  - solving "today" for the first time increments the streak
 *  - solving "today" again keeps the streak unchanged (no break, no double-count)
 *  - if the last solved day was not yesterday, the streak resets to 1
 */

package com.iamconanpeter.gravityflux.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FluxProgressManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ---- Best stars per level ------------------------------------------

    /** Returns the best (highest) star rating recorded for a level (0 if none). */
    fun getBestStars(level: Int): Int = prefs.getInt(keyStars(level), 0)

    /**
     * Records a star rating for a level. Only overwrites the stored value when
     * the new result is strictly better.
     */
    fun setBestStars(level: Int, stars: Int) {
        val key = keyStars(level)
        val current = prefs.getInt(key, 0)
        if (stars > current) {
            prefs.edit().putInt(key, stars).apply()
        }
    }

    // ---- Daily solve tracking ------------------------------------------

    /** True if the given date key has been solved. */
    fun getDailySolved(dateKey: String): Boolean = prefs.getBoolean(keyDaily(dateKey), false)

    /** Marks the given date key as solved. */
    fun markDailySolved(dateKey: String) {
        prefs.edit().putBoolean(keyDaily(dateKey), true).apply()
    }

    // ---- Streak --------------------------------------------------------

    /**
     * Current consecutive-day solve streak.
     */
    fun getStreak(): Int = prefs.getInt(KEY_STREAK, 0)

    /**
     * Streak-safe bump. Call once when the player completes today's daily.
     * - If today was already solved: keep current streak (no change).
     * - If the last solved day was yesterday: streak += 1.
     * - Otherwise (gap or first ever): streak resets to 1.
     */
    fun bumpStreak() {
        val todayKey = todayKey()
        if (getDailySolved(todayKey)) {
            // Already counted today; do not alter the streak.
            return
        }

        val last = prefs.getString(KEY_LAST_DAILY_DATE, null)
        val streak = if (last != null && last == yesterdayKey()) {
            getStreak() + 1
        } else {
            1
        }

        prefs.edit()
            .putInt(KEY_STREAK, streak)
            .putString(KEY_LAST_DAILY_DATE, todayKey)
            .apply()
        markDailySolved(todayKey)
    }

    // ---- Date helpers --------------------------------------------------

    /** YYYY-MM-DD for today in local time. */
    fun todayKey(): String = dateKeyFor(Calendar.getInstance())

    /** YYYY-MM-DD for yesterday in local time. */
    fun yesterdayKey(): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return dateKeyFor(cal)
    }

    private fun dateKeyFor(cal: Calendar): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(cal.time)
    }

    // ---- Key helpers ---------------------------------------------------

    private fun keyStars(level: Int) = "stars_$level"
    private fun keyDaily(dateKey: String) = "daily_solved_$dateKey"

    companion object {
        private const val PREF_NAME = "gravity_flux"
        private const val KEY_STREAK = "daily_streak"
        private const val KEY_LAST_DAILY_DATE = "last_daily_date"
    }
}
