package com.crickscorer.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayDeque
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private data class BallRecord(
        val runs: Int,
        val legalBall: Boolean,
        val wicket: Boolean,
        val label: String
    )

    private var totalRuns = 0
    private var wickets = 0
    private var legalBalls = 0
    private var innings = 1
    private var target = 0
    private var battingTeam = "TEAM A"
    private var bowlingTeam = "TEAM B"
    private val history = ArrayDeque<BallRecord>()

    private lateinit var scoreText: TextView
    private lateinit var oversText: TextView
    private lateinit var runRateText: TextView
    private lateinit var statusText: TextView
    private lateinit var targetText: TextView
    private lateinit var historyText: TextView
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            totalRuns = savedInstanceState.getInt("runs")
            wickets = savedInstanceState.getInt("wickets")
            legalBalls = savedInstanceState.getInt("balls")
            innings = savedInstanceState.getInt("innings")
            target = savedInstanceState.getInt("target")
            battingTeam = savedInstanceState.getString("batting", "TEAM A") ?: "TEAM A"
            bowlingTeam = savedInstanceState.getString("bowling", "TEAM B") ?: "TEAM B"
        }

        buildScreen()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("runs", totalRuns)
        outState.putInt("wickets", wickets)
        outState.putInt("balls", legalBalls)
        outState.putInt("innings", innings)
        outState.putInt("target", target)
        outState.putString("batting", battingTeam)
        outState.putString("bowling", bowlingTeam)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun panel(color: Int, radius: Int = 18): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.rgb(9, 17, 31))

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(root)

        val header = TextView(this).apply {
            text = "CRICKET SCORER"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(header)

        val subtitle = TextView(this).apply {
            text = "LIVE MATCH • SIMPLE SCOREBOARD"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(143, 163, 190))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(subtitle)

        val scoreCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = panel(Color.rgb(20, 39, 65), 22)
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }
        root.addView(scoreCard, matchWidth())

        val teamText = TextView(this).apply {
            text = battingTeam
            textSize = 18f
            setTextColor(Color.rgb(170, 205, 255))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        scoreCard.addView(teamText)
        scoreText = TextView(this).apply {
            text = "0/0"
            textSize = 48f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(2))
        }
        scoreCard.addView(scoreText)

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        scoreCard.addView(stats, matchWidth())
        oversText = statView("OVERS  0.0")
        runRateText = statView("RUN RATE  0.00")
        stats.addView(oversText, LinearLayout.LayoutParams(0, dp(42), 1f))
        stats.addView(runRateText, LinearLayout.LayoutParams(0, dp(42), 1f))

        targetText = TextView(this).apply {
            text = "1ST INNINGS"
            textSize = 13f
            setTextColor(Color.rgb(255, 205, 110))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        scoreCard.addView(targetText)

        statusText = TextView(this).apply {
            text = "Match in progress"
            textSize = 13f
            setTextColor(Color.rgb(175, 190, 210))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(statusText)

        sectionTitle("ADD RUNS")
        val runsRow = row()
        listOf(0, 1, 2, 3, 4, 6).forEach { value ->
            runsRow.addView(actionButton(value.toString(), Color.rgb(27, 112, 83)) {
                addBall(value, true, false, "$value run${if (value == 1) "" else "s"}")
            }, equalWeight())
        }
        root.addView(runsRow)

        sectionTitle("EXTRAS")
        val extrasRow = row()
        extrasRow.addView(actionButton("WIDE +1", Color.rgb(44, 91, 150)) {
            addBall(1, false, false, "Wide +1")
        }, equalWeight())
        extrasRow.addView(actionButton("NO BALL +1", Color.rgb(44, 91, 150)) {
            addBall(1, false, false, "No ball +1")
        }, equalWeight())
        root.addView(extrasRow)

        val extrasRow2 = row()
        extrasRow2.addView(actionButton("BYE", Color.rgb(44, 91, 150)) {
            addBall(1, true, false, "Bye 1")
        }, equalWeight())
        extrasRow2.addView(actionButton("LEG BYE", Color.rgb(44, 91, 150)) {
            addBall(1, true, false, "Leg bye 1")
        }, equalWeight())
        root.addView(extrasRow2)

        sectionTitle("WICKET & CONTROLS")
        val controlRow = row()
        controlRow.addView(actionButton("WICKET", Color.rgb(176, 55, 61)) {
            if (wickets < 10) addBall(0, true, true, "Wicket")
        }, equalWeight())
        controlRow.addView(actionButton("UNDO", Color.rgb(90, 101, 120)) {
            undo()
        }, equalWeight())
        root.addView(controlRow)

        val controlRow2 = row()
        controlRow2.addView(actionButton("END INNINGS", Color.rgb(175, 111, 34)) {
            endInnings()
        }, equalWeight())
        controlRow2.addView(actionButton("RESET MATCH", Color.rgb(111, 57, 122)) {
            resetMatch()
        }, equalWeight())
        root.addView(controlRow2)

        sectionTitle("RECENT BALLS")
        historyText = TextView(this).apply {
            text = "No balls recorded yet"
            textSize = 14f
            setTextColor(Color.WHITE)
            background = panel(Color.rgb(20, 31, 49), 14)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        root.addView(historyText, matchWidth())

        val note = TextView(this).apply {
            text = "Note: Wide and no-ball add one run and do not count as a legal delivery. Use Undo to reverse the latest scoring action."
            textSize = 11f
            setTextColor(Color.rgb(135, 153, 178))
            setPadding(dp(4), dp(16), dp(4), 0)
        }
        root.addView(note)
        setContentView(scroll)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun equalWeight() = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
        setMargins(dp(3), dp(3), dp(3), dp(3))
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun statView(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun sectionTitle(value: String) {
        val title = TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(Color.rgb(143, 179, 222))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(18), 0, dp(6))
        }
        root.addView(title)
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = panel(color, 12)
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { action(); refresh() }
        }
    }

    private fun addBall(runs: Int, legal: Boolean, wicket: Boolean, label: String) {
        history.addLast(BallRecord(runs, legal, wicket, label))
        totalRuns += runs
        if (legal) legalBalls++
        if (wicket && wickets < 10) wickets++
        refresh()
    }

    private fun undo() {
        if (history.isEmpty()) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }
        val last = history.removeLast()
        totalRuns = (totalRuns - last.runs).coerceAtLeast(0)
        if (last.legalBall) legalBalls = (legalBalls - 1).coerceAtLeast(0)
        if (last.wicket) wickets = (wickets - 1).coerceAtLeast(0)
        refresh()
    }

    private fun endInnings() {
        if (innings == 1) {
            target = totalRuns + 1
            val oldBatting = battingTeam
            battingTeam = bowlingTeam
            bowlingTeam = oldBatting
            innings = 2
            totalRuns = 0
            wickets = 0
            legalBalls = 0
            history.clear()
            refresh()
            Toast.makeText(this, "2nd innings started. Target: $target", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Match completed. Use Reset Match for a new match.", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetMatch() {
        totalRuns = 0
        wickets = 0
        legalBalls = 0
        innings = 1
        target = 0
        battingTeam = "TEAM A"
        bowlingTeam = "TEAM B"
        history.clear()
        refresh()
        Toast.makeText(this, "Match reset", Toast.LENGTH_SHORT).show()
    }

    private fun refresh() {
        if (!::scoreText.isInitialized) return
        scoreText.text = "$totalRuns/$wickets"
        val overs = "${legalBalls / 6}.${legalBalls % 6}"
        oversText.text = "OVERS  $overs"
        val rate = if (legalBalls == 0) 0.0 else totalRuns * 6.0 / legalBalls
        runRateText.text = "RUN RATE  ${"%.2f".format(rate)}"

        val teamLabel = root.getChildAt(2) as? LinearLayout
        val teamText = teamLabel?.getChildAt(0) as? TextView
        teamText?.text = battingTeam

        targetText.text = if (innings == 1) {
            "1ST INNINGS"
        } else {
            "2ND INNINGS • TARGET $target • NEED ${(target - totalRuns).coerceAtLeast(0)}"
        }

        statusText.text = when {
            wickets >= 10 -> "All out"
            innings == 2 && totalRuns >= target -> "Target reached — innings complete"
            else -> "Match in progress"
        }

        val recent = history.toList().takeLast(12).asReversed()
        historyText.text = if (recent.isEmpty()) {
            "No balls recorded yet"
        } else {
            recent.joinToString("     ") { it.label }
        }
    }
}