/*
 * MainActivity.kt
 *
 * Hosts the GravityFluxView plus a simple HUD and control buttons:
 * Bend Gravity, Undo, Reset, Next. On launch it selects the daily chamber via
 * GravityFluxEngine.dailyIndex(DAY_OF_YEAR) and shows the current daily streak.
 */

package com.iamconanpeter.gravityflux.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.iamconanpeter.gravityflux.R
import com.iamconanpeter.gravityflux.audio.FluxAudioManager
import com.iamconanpeter.gravityflux.audio.FluxEvent
import com.iamconanpeter.gravityflux.data.FluxProgressManager
import com.iamconanpeter.gravityflux.engine.GameState
import com.iamconanpeter.gravityflux.engine.GravityFluxEngine
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var fluxView: GravityFluxView
    private lateinit var movesText: TextView
    private lateinit var parText: TextView
    private lateinit var starsText: TextView
    private lateinit var statusText: TextView
    private lateinit var overlayText: TextView

    private lateinit var audio: FluxAudioManager
    private lateinit var progress: FluxProgressManager

    private lateinit var engine: GravityFluxEngine
    private var campaignIndex: Int = 0
    private var isDaily: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = FluxAudioManager(this)
        progress = FluxProgressManager(this)

        buildLayout()
        loadDaily()
    }

    // ---- Layout (programmatic, no XML layout file required) ------------

    private fun buildLayout() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#05080F"))
        }

        fluxView = GravityFluxView(this)

        // HUD at the top.
        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8))
        }
        movesText = makeLabel("Moves: 0")
        parText = makeLabel("Par: 0")
        starsText = makeLabel("Stars: -")
        statusText = makeLabel("Daily streak: 0")
        hud.addView(movesText)
        hud.addView(parText)
        hud.addView(starsText)
        hud.addView(statusText)

        // Controls at the bottom.
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8))
        }
        val bend = makeButton(R.string.bend_gravity) { onBendGravity() }
        val undo = makeButton(R.string.undo) { onUndo() }
        val reset = makeButton(R.string.reset) { onReset() }
        val next = makeButton(R.string.next) { onNext() }
        controls.addView(bend)
        controls.addView(undo)
        controls.addView(reset)
        controls.addView(next)

        val frame = FrameLayout(this).apply {
            addView(fluxView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.CENTER })
        }

        overlayText = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = android.view.View.GONE
        }
        frame.addView(overlayText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val vbox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        vbox.addView(hud, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        vbox.addView(frame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))
        vbox.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(vbox)
        setContentView(root)
    }

    private fun makeLabel(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#9FB3D1"))
            setPadding(dp(8))
        }

    private fun makeButton(textRes: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            this.setText(textRes)
            textSize = 13f
            setOnClickListener { onClick() }
            setPadding(dp(8))
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---- Game flow -----------------------------------------------------

    private fun loadDaily() {
        isDaily = true
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        campaignIndex = GravityFluxEngine.dailyIndex(dayOfYear)
        engine = GravityFluxEngine(GravityFluxEngine.CAMPAIGN[campaignIndex])
        fluxView.setEngine(engine)
        statusText.text = "Daily streak: ${progress.getStreak()}"
        overlayText.visibility = android.view.View.GONE
        refreshHud()
    }

    private fun loadCampaign(index: Int) {
        isDaily = false
        campaignIndex = index.coerceIn(0, GravityFluxEngine.CAMPAIGN.lastIndex)
        engine = GravityFluxEngine(GravityFluxEngine.CAMPAIGN[campaignIndex])
        fluxView.setEngine(engine)
        overlayText.visibility = android.view.View.GONE
        refreshHud()
    }

    private fun onBendGravity() {
        if (engine.state != GameState.PLAYING) return
        val fromRow = engine.orbRow
        val fromCol = engine.orbCol
        engine.rotateAndSlide()
        audio.play(FluxEvent.ROTATE)
        fluxView.animateSlide(fromRow, fromCol, engine.orbRow, engine.orbCol) {
            when (engine.state) {
                GameState.WON -> {
                    audio.play(FluxEvent.WIN)
                    onWin()
                }
                GameState.FAILED -> {
                    audio.play(FluxEvent.HAZARD)
                    onFail()
                }
                else -> {
                    // Still sliding across multiple cells? The engine already
                    // settled; just play a collect chime if nodes were gained.
                    if (engine.collectedNodes.isNotEmpty()) audio.play(FluxEvent.COLLECT)
                }
            }
            refreshHud()
        }
        refreshHud()
    }

    private fun onUndo() {
        engine.undo()
        fluxView.render()
        overlayText.visibility = android.view.View.GONE
        refreshHud()
    }

    private fun onReset() {
        engine.reset()
        fluxView.render()
        overlayText.visibility = android.view.View.GONE
        refreshHud()
    }

    private fun onNext() {
        // After a win, advance to the next campaign chamber (cycle around).
        loadCampaign((campaignIndex + 1) % GravityFluxEngine.CAMPAIGN.size)
    }

    private fun onWin() {
        val stars = engine.stars()
        if (isDaily) {
            progress.bumpStreak()
            statusText.text = "Daily streak: ${progress.getStreak()}"
        }
        progress.setBestStars(progressKey(), stars)
        overlayText.text = getString(R.string.won_template, stars)
        overlayText.visibility = android.view.View.VISIBLE
        refreshHud()
    }

    private fun onFail() {
        overlayText.text = getString(R.string.failed)
        overlayText.visibility = android.view.View.VISIBLE
        refreshHud()
    }

    private fun progressKey(): Int = if (isDaily) 1000 + campaignIndex else campaignIndex

    private fun refreshHud() {
        movesText.text = getString(R.string.moves_template, engine.moves)
        parText.text = getString(R.string.par_template, engine.chamberView.par)
        starsText.text = if (engine.state == GameState.WON)
            getString(R.string.stars_template, engine.stars())
        else
            getString(R.string.stars_template, engine.stars())
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
    }
}
