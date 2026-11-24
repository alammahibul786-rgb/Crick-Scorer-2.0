package com.crickscorer.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayDeque
import java.util.Locale

class MainActivity : Activity() {

    private data class Innings(
        var batting: String,
        var bowling: String,
        var runs: Int = 0,
        var wickets: Int = 0,
        var legalBalls: Int = 0,
        var wides: Int = 0,
        var noBalls: Int = 0,
        var byes: Int = 0,
        var legByes: Int = 0,
        var penalty: Int = 0,
        var declared: Boolean = false,
        var completed: Boolean = false
    ) {
        val ballsInOver: Int get() = legalBalls % 6
        val overs: String get() = "${legalBalls / 6}.${ballsInOver}"
        val extras: Int get() = wides + noBalls + byes + legByes + penalty
        val runRate: Double get() =
            if (legalBalls == 0) 0.0 else runs * 6.0 / legalBalls
    }

    private data class Snapshot(
        val innings: List<Innings>,
        val currentIndex: Int,
        val matchType: String,
        val oversLimit: Int,
        val teamA: String,
        val teamB: String
    )

    private val inningsList = mutableListOf<Innings>()
    private val undoStack = ArrayDeque<Snapshot>()
    private var currentIndex = 0
    private var matchType = "T20"
    private var oversLimit = 20
    private var teamA = "TEAM A"
    private var teamB = "TEAM B"

    private lateinit var root: LinearLayout
    private lateinit var scoreText: TextView
    private lateinit var detailsText: TextView
    private lateinit var statusText: TextView
    private lateinit var inningsText: TextView
    private lateinit var historyText: TextView

    private val current: Innings
        get() = inningsList[currentIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startNewMatch()
        buildScreen()
        showSetupDialog()
    }

    private fun startNewMatch() {
        inningsList.clear()
        undoStack.clear()
        currentIndex = 0
        inningsList.add(Innings(teamA, teamB))
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(Color.rgb(10, 20, 35))
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = text("🏏 CRICKET SCORER", 25, Color.WHITE, true)
        header.gravity = Gravity.CENTER
        root.addView(header)
        val sub = text("INTERNATIONAL EDITION", 12, Color.rgb(100, 220, 190), true)
        sub.gravity = Gravity.CENTER
        root.addView(sub)
        addGap(14)

        val setupButton = button("MATCH SETUP  •  $matchType", Color.rgb(32, 55, 78))
        setupButton.setOnClickListener { showSetupDialog() }
        root.addView(setupButton)

        inningsText = text("", 14, Color.rgb(170, 195, 220), true)
        root.addView(inningsText)
        scoreText = text("0/0", 48, Color.WHITE, true)
        scoreText.gravity = Gravity.CENTER
        root.addView(scoreText)
        detailsText = text("", 16, Color.rgb(190, 210, 230), false)
        detailsText.gravity = Gravity.CENTER
        root.addView(detailsText)
        statusText = text("", 14, Color.rgb(100, 220, 190), true)
        statusText.gravity = Gravity.CENTER
        root.addView(statusText)
        addGap(12)

        section("SCORE RUNS")
        val runRow = row()
        listOf(0, 1, 2, 3, 4, 6).forEach { value ->
            val b = button(value.toString(), if (value == 4 || value == 6)
                Color.rgb(0, 130, 105) else Color.rgb(35, 75, 110))
            b.textSize = 20f
            b.setOnClickListener { addRuns(value, true, "Runs $value") }
            runRow.addView(b, weightParams())
        }
        root.addView(runRow)

        section("EXTRAS")
        val extraRow1 = row()
        addAction(extraRow1, "Wide +1") { addRuns(1, false, "Wide", wide = 1) }
        addAction(extraRow1, "No-ball +1") { addRuns(1, false, "No-ball", noBall = 1) }
        root.addView(extraRow1)
        val extraRow2 = row()
        addAction(extraRow2, "Bye") { addRuns(1, true, "Bye", bye = 1) }
        addAction(extraRow2, "Leg-bye") { addRuns(1, true, "Leg-bye", legBye = 1) }
        root.addView(extraRow2)

        section("WICKET & MATCH")
        val matchRow1 = row()
        addAction(matchRow1, "WICKET", Color.rgb(170, 55, 65)) { addWicket() }
        addAction(matchRow1, "UNDO", Color.rgb(90, 90, 105)) { undo() }
        root.addView(matchRow1)
        val matchRow2 = row()
        addAction(matchRow2, "Next innings") { nextInnings() }
        addAction(matchRow2, "Declare (Test)") { declareInnings() }
        root.addView(matchRow2)

        section("INNINGS SUMMARY")
        historyText = text("", 14, Color.rgb(220, 230, 240), false)
        root.addView(historyText)

        val bottom = button("NEW MATCH", Color.rgb(120, 70, 35))
        bottom.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Start a new match?")
                .setMessage("The current match will be cleared from this screen.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("New match") { _, _ ->
                    startNewMatch()
                    refresh()
                    showSetupDialog()
                }.show()
        }
        root.addView(bottom)
        refresh()
    }

    private fun addRuns(
        runs: Int,
        legal: Boolean,
        label: String,
        wide: Int = 0,
        noBall: Int = 0,
        bye: Int = 0,
        legBye: Int = 0
    ) {
        if (!canScore()) return
        saveUndo()
        current.runs += runs
        current.wides += wide
        current.noBalls += noBall
        current.byes += bye
        current.legByes += legBye
        if (legal) current.legalBalls++
        checkInningsEnd()
        refresh(label)
    }

    private fun addWicket() {
        if (!canScore()) return
        if (current.wickets >= 10) return
        saveUndo()
        current.wickets++
        current.legalBalls++
        checkInningsEnd()
        refresh("Wicket!")
    }

    private fun canScore(): Boolean {
        if (current.completed) {
            toast("This innings is complete. Start the next innings.")
            return false
        }
        return true
    }

    private fun checkInningsEnd() {
        val oversFinished = matchType != "Test" && current.legalBalls >= oversLimit * 6
        if (current.wickets >= 10 || oversFinished) current.completed = true
        if (inningsList.size >= 2 && currentIndex > 0) {
            val first = inningsList[0]
            if (current.runs > first.runs) current.completed = true
        }
    }

    private fun nextInnings() {
        if (!current.completed && current.wickets < 10) {
            AlertDialog.Builder(this)
                .setTitle("End this innings?")
                .setMessage("The current innings is not complete. Continue to the next innings?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ -> createNextInnings() }
                .show()
        } else createNextInnings()
    }

    private fun createNextInnings() {
        val maxInnings = if (matchType == "Test") 4 else 2
        if (inningsList.size >= maxInnings) {
            toast("All innings for this match have been entered.")
            return
        }
        saveUndo()
        val batting = current.bowling
        val bowling = current.batting
        inningsList.add(Innings(batting, bowling))
        currentIndex = inningsList.lastIndex
        refresh("Innings ${currentIndex + 1} started")
    }

    private fun declareInnings() {
        if (matchType != "Test") {
            toast("Declaration is available in Test matches.")
            return
        }
        if (!canScore()) return
        saveUndo()
        current.declared = true
        current.completed = true
        refresh("Innings declared")
    }

    private fun saveUndo() {
        val copy = inningsList.map { it.copy() }
        undoStack.addLast(Snapshot(copy, currentIndex, matchType, oversLimit, teamA, teamB))
        if (undoStack.size > 100) undoStack.removeFirst()
    }

    private fun undo() {
        if (undoStack.isEmpty()) {
            toast("Nothing to undo")
            return
        }
        val state = undoStack.removeLast()
        inningsList.clear()
        inningsList.addAll(state.innings.map { it.copy() })
        currentIndex = state.currentIndex
        matchType = state.matchType
        oversLimit = state.oversLimit
        teamA = state.teamA
        teamB = state.teamB
        refresh("Last action undone")
    }

    private fun refresh(message: String = "") {
        if (!::scoreText.isInitialized) return
        val c = current
        scoreText.text = "${c.runs}/${c.wickets}"
        detailsText.text =
            "Overs ${c.overs}    •    Extras ${c.extras}    •    RR ${String.format(Locale.US, "%.2f", c.runRate)}"
        inningsText.text = "${matchType.uppercase(Locale.getDefault())}  |  INNINGS ${currentIndex + 1}  |  ${c.batting} batting"
        statusText.text = when {
            c.declared -> "DECLARED"
            c.completed -> "INNINGS COMPLETE"
            else -> message
        }
        val summary = StringBuilder()
        inningsList.forEachIndexed { index, inn ->
            summary.append("Innings ${index + 1}: ${inn.batting}  ${inn.runs}/${inn.wickets} (${inn.overs} ov)")
            if (inn.declared) summary.append("  declared")
            if (inn.completed) summary.append("  complete")
            summary.append("\n")
        }
        if (inningsList.size >= 2) {
            val first = inningsList[0]
            val second = inningsList[1]
            val target = first.runs + 1
            if (currentIndex == 1 && !second.completed) {
                summary.append("\nTarget: $target  •  Need ${maxOf(0, target - second.runs)} runs")
            }
            if (currentIndex >= 1 && second.completed) {
                summary.append("\n${if (second.runs >= target) second.batting + " won" else first.batting + " ahead"}")
            }
        }
        historyText.text = summary.toString()
    }

    private fun showSetupDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val a = EditText(this).apply { hint = "Batting team"; setText(teamA) }
        val b = EditText(this).apply { hint = "Bowling team"; setText(teamB) }
        layout.addView(a)
        layout.addView(b)
        val typeButton = Button(this).apply { text = "Match type: $matchType" }
        layout.addView(typeButton)
        typeButton.setOnClickListener {
            val types = arrayOf("T20", "ODI", "Test", "Custom")
            AlertDialog.Builder(this)
                .setTitle("Select match type")
                .setItems(types) { _, which ->
                    matchType = types[which]
                    oversLimit = when (matchType) {
                        "T20" -> 20
                        "ODI" -> 50
                        "Test" -> 0
                        else -> 20
                    }
                    typeButton.text = "Match type: $matchType"
                }.show()
        }
        val overs = EditText(this).apply {
            hint = "Overs per innings (Custom only)"
            inputType = 2
            setText(if (oversLimit > 0) oversLimit.toString() else "")
        }
        layout.addView(overs)
        AlertDialog.Builder(this)
            .setTitle("Match setup")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Start match") { _, _ ->
                teamA = a.text.toString().trim().ifEmpty { "TEAM A" }
                teamB = b.text.toString().trim().ifEmpty { "TEAM B" }
                if (teamA.equals(teamB, true)) teamB += " B"
                if (matchType == "Custom") {
                    oversLimit = overs.text.toString().toIntOrNull()?.coerceIn(1, 200) ?: 20
                }
                inningsList[0].batting = teamA
                inningsList[0].bowling = teamB
                refresh()
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun section(label: String) {
        addGap(14)
        root.addView(text(label, 13, Color.rgb(100, 220, 190), true))
        addGap(5)
    }

    private fun addAction(parent: LinearLayout, label: String, color: Int = Color.rgb(35, 75, 110), action: () -> Unit) {
        val b = button(label, color)
        b.setOnClickListener { action() }
        parent.addView(b, weightParams())
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
        setMargins(dp(3), dp(3), dp(3), dp(3))
    }

    private fun button(label: String, color: Int): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            minHeight = dp(48)
        }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }

    private fun addGap(height: Int) {
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}