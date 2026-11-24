package com.crickscorer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.Locale

class MainActivity : Activity() {
    private data class Team(val name: String, val players: MutableList<String> = mutableListOf())
    private data class Innings(
        var batting: String, var bowling: String, var runs: Int = 0, var wickets: Int = 0,
        var legalBalls: Int = 0, var wides: Int = 0, var noBalls: Int = 0,
        var byes: Int = 0, var legByes: Int = 0, var penalty: Int = 0,
        var declared: Boolean = false, var completed: Boolean = false
    ) {
        val overs get() = "${legalBalls / 6}.${legalBalls % 6}"
        val extras get() = wides + noBalls + byes + legByes + penalty
        val runRate get() = if (legalBalls == 0) 0.0 else runs * 6.0 / legalBalls
    }
    private data class Snapshot(
        val innings: List<Innings>, val index: Int, val type: String, val limit: Int,
        val teamA: String, val teamB: String
    )

    private val inningsList = mutableListOf<Innings>()
    private val undoStack = ArrayDeque<Snapshot>()
    private val teams = mutableListOf<Team>()
    private var currentIndex = 0
    private var matchType = "T20"
    private var oversLimit = 20
    private var teamA = "TEAM A"
    private var teamB = "TEAM B"
    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var scoreText: TextView
    private lateinit var detailsText: TextView
    private lateinit var statusText: TextView
    private lateinit var inningsText: TextView
    private lateinit var historyText: TextView
    private val current get() = inningsList[currentIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("cricket_scorer_data", MODE_PRIVATE)
        loadTeams()
        startNewMatch()
        buildScreen()
        showSetupDialog()
    }

    private fun loadTeams() {
        teams.clear()
        try {
            val arr = JSONArray(prefs.getString("teams_json", "[]"))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val players = mutableListOf<String>()
                val p = obj.optJSONArray("players") ?: JSONArray()
                for (j in 0 until p.length()) players.add(p.optString(j))
                teams.add(Team(obj.optString("name", "Team"), players))
            }
        } catch (_: Exception) { teams.clear() }
    }

    private fun saveTeams() {
        val arr = JSONArray()
        teams.forEach { team ->
            val p = JSONArray()
            team.players.forEach { p.put(it) }
            arr.put(JSONObject().put("name", team.name).put("players", p))
        }
        prefs.edit().putString("teams_json", arr.toString()).apply()
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

        text("🏏 CRICKET SCORER", 25, Color.WHITE, true).also {
            it.gravity = Gravity.CENTER; root.addView(it)
        }
        text("INTERNATIONAL EDITION", 12, Color.rgb(100, 220, 190), true).also {
            it.gravity = Gravity.CENTER; root.addView(it)
        }
        addGap(12)
        button("MATCH SETUP • $matchType", Color.rgb(32, 55, 78)).also {
            it.setOnClickListener { showSetupDialog() }; root.addView(it)
        }
        button("TEAMS & PLAYERS", Color.rgb(0, 105, 100)).also {
            it.setOnClickListener { showTeamManager() }; root.addView(it)
        }
        inningsText = text("", 14, Color.rgb(170, 195, 220), true); root.addView(inningsText)
        scoreText = text("0/0", 48, Color.WHITE, true).also {
            it.gravity = Gravity.CENTER; root.addView(it)
        }
        detailsText = text("", 16, Color.rgb(190, 210, 230), false).also {
            it.gravity = Gravity.CENTER; root.addView(it)
        }
        statusText = text("", 14, Color.rgb(100, 220, 190), true).also {
            it.gravity = Gravity.CENTER; root.addView(it)
        }
        addGap(12)
        section("SCORE RUNS")
        val runRow = row()
        listOf(0, 1, 2, 3, 4, 6).forEach { n ->
            val b = button(n.toString(), if (n == 4 || n == 6) Color.rgb(0,130,105) else Color.rgb(35,75,110))
            b.textSize = 20f
            b.setOnClickListener { addRuns(n, true, "Runs $n") }
            runRow.addView(b, weightParams())
        }
        root.addView(runRow)
        section("EXTRAS")
        row().also { r ->
            addAction(r, "Wide +1") { addRuns(1, false, "Wide", wide = 1) }
            addAction(r, "No-ball +1") { addRuns(1, false, "No-ball", noBall = 1) }
            root.addView(r)
        }
        row().also { r ->
            addAction(r, "Bye") { addRuns(1, true, "Bye", bye = 1) }
            addAction(r, "Leg-bye") { addRuns(1, true, "Leg-bye", legBye = 1) }
            root.addView(r)
        }
        section("WICKET & MATCH")
        row().also { r ->
            addAction(r, "WICKET", Color.rgb(170,55,65)) { addWicket() }
            addAction(r, "UNDO", Color.rgb(90,90,105)) { undo() }
            root.addView(r)
        }
        row().also { r ->
            addAction(r, "Next innings") { nextInnings() }
            addAction(r, "Declare (Test)") { declareInnings() }
            root.addView(r)
        }
        section("INNINGS SUMMARY")
        historyText = text("", 14, Color.rgb(220,230,240), false); root.addView(historyText)
        button("NEW MATCH", Color.rgb(120,70,35)).also { b ->
            b.setOnClickListener {
                AlertDialog.Builder(this).setTitle("Start a new match?")
                    .setMessage("The current match will be cleared from this screen.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("New match") { _, _ ->
                        startNewMatch(); refresh(); showSetupDialog()
                    }.show()
            }
            root.addView(b)
        }
        refresh()
    }

    // Team and player manager: data is stored locally on this phone.
    private fun showTeamManager() {
        AlertDialog.Builder(this).setTitle("Team & player management")
            .setItems(arrayOf("Add new team", "View / manage saved teams")) { _, choice ->
                if (choice == 0) editTeamDialog() else showSavedTeams()
            }.show()
    }

    private fun editTeamDialog(team: Team? = null) {
        val input = EditText(this).apply {
            hint = "Enter team name"; setSingleLine(true); setText(team?.name ?: "")
        }
        AlertDialog.Builder(this).setTitle(if (team == null) "Create team" else "Edit team")
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) toast("Enter a team name")
                else if (teams.any { it !== team && it.name.equals(name, true) }) toast("Team already exists")
                else {
                    if (team == null) teams.add(Team(name))
                    else {
                        val index = teams.indexOf(team)
                        if (index >= 0) teams[index] = Team(name, team.players)
                    }
                    saveTeams(); toast("Team saved"); showSavedTeams()
                }
            }.show()
    }

    private fun showSavedTeams() {
        if (teams.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Saved teams")
                .setMessage("No teams saved yet.")
                .setPositiveButton("Add team") { _, _ -> editTeamDialog() }
                .setNegativeButton("Close", null).show()
            return
        }
        val labels = teams.map { "${it.name} (${it.players.size} players)" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose a team")
            .setItems(labels) { _, which -> showTeamActions(teams[which]) }
            .setPositiveButton("Add team") { _, _ -> editTeamDialog() }
            .setNegativeButton("Close", null).show()
    }

    private fun showTeamActions(team: Team) {
        AlertDialog.Builder(this).setTitle(team.name)
            .setItems(arrayOf("Manage players (${team.players.size})", "Edit team name", "Delete team")) { _, choice ->
                when (choice) {
                    0 -> showPlayers(team)
                    1 -> editTeamDialog(team)
                    2 -> AlertDialog.Builder(this).setTitle("Delete ${team.name}?")
                        .setMessage("Its saved player list will also be deleted.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            teams.remove(team); saveTeams(); showSavedTeams()
                        }.show()
                }
            }.show()
    }

    private fun showPlayers(team: Team) {
        val names = if (team.players.isEmpty()) arrayOf("No players added yet") else team.players.toTypedArray()
        AlertDialog.Builder(this).setTitle("${team.name} — Players")
            .setItems(names) { _, which ->
                if (team.players.isNotEmpty()) showPlayerActions(team, team.players[which])
            }.setPositiveButton("Add player") { _, _ -> editPlayerDialog(team) }
            .setNegativeButton("Back") { _, _ -> showTeamActions(team) }.show()
    }

    private fun showPlayerActions(team: Team, player: String) {
        AlertDialog.Builder(this).setTitle(player)
            .setItems(arrayOf("Edit player name", "Remove player")) { _, choice ->
                if (choice == 0) editPlayerDialog(team, player)
                else AlertDialog.Builder(this).setTitle("Remove player?")
                    .setMessage("Remove $player from ${team.name}?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove") { _, _ ->
                        team.players.remove(player); saveTeams(); showPlayers(team)
                    }.show()
            }.show()
    }

    private fun editPlayerDialog(team: Team, oldName: String? = null) {
        val input = EditText(this).apply {
            hint = "Enter player name"; setSingleLine(true); setText(oldName ?: "")
        }
        AlertDialog.Builder(this).setTitle(if (oldName == null) "Add player" else "Edit player")
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) toast("Enter a player name")
                else if (team.players.any { !it.equals(oldName, true) && it.equals(name, true) }) toast("Player already exists")
                else {
                    if (oldName == null) team.players.add(name)
                    else {
                        val index = team.players.indexOf(oldName)
                        if (index >= 0) team.players[index] = name
                    }
                    saveTeams(); showPlayers(team)
                }
            }.show()
    }

    private fun addRuns(runs: Int, legal: Boolean, label: String, wide: Int = 0,
                        noBall: Int = 0, bye: Int = 0, legBye: Int = 0) {
        if (!canScore()) return
        saveUndo()
        current.runs += runs; current.wides += wide; current.noBalls += noBall
        current.byes += bye; current.legByes += legBye
        if (legal) current.legalBalls++
        checkInningsEnd(); refresh(label)
    }

    private fun addWicket() {
        if (!canScore()) return
        if (current.wickets >= 10) return
        saveUndo(); current.wickets++; current.legalBalls++
        checkInningsEnd(); refresh("Wicket!")
    }

    private fun canScore(): Boolean {
        if (current.completed) { toast("This innings is complete. Start the next innings."); return false }
        return true
    }

    private fun checkInningsEnd() {
        val oversFinished = matchType != "Test" && current.legalBalls >= oversLimit * 6
        if (current.wickets >= 10 || oversFinished) current.completed = true
        if (inningsList.size >= 2 && currentIndex > 0 && current.runs > inningsList[0].runs) current.completed = true
    }

    private fun nextInnings() {
        if (!current.completed && current.wickets < 10) {
            AlertDialog.Builder(this).setTitle("End this innings?")
                .setMessage("The current innings is not complete. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ -> createNextInnings() }.show()
        } else createNextInnings()
    }

    private fun createNextInnings() {
        val maxInnings = if (matchType == "Test") 4 else 2
        if (inningsList.size >= maxInnings) { toast("All innings have been entered."); return }
        saveUndo()
        inningsList.add(Innings(current.bowling, current.batting))
        currentIndex = inningsList.lastIndex
        refresh("Innings ${currentIndex + 1} started")
    }

    private fun declareInnings() {
        if (matchType != "Test") { toast("Declaration is available in Test matches."); return }
        if (!canScore()) return
        saveUndo(); current.declared = true; current.completed = true; refresh("Innings declared")
    }

    private fun saveUndo() {
        undoStack.addLast(Snapshot(inningsList.map { it.copy() }, currentIndex, matchType,
            oversLimit, teamA, teamB))
        if (undoStack.size > 100) undoStack.removeFirst()
    }

    private fun undo() {
        if (undoStack.isEmpty()) { toast("Nothing to undo"); return }
        val s = undoStack.removeLast()
        inningsList.clear(); inningsList.addAll(s.innings.map { it.copy() })
        currentIndex = s.index; matchType = s.type; oversLimit = s.limit
        teamA = s.teamA; teamB = s.teamB; refresh("Last action undone")
    }

    private fun refresh(message: String = "") {
        if (!::scoreText.isInitialized) return
        val c = current
        scoreText.text = "${c.runs}/${c.wickets}"
        detailsText.text = "Overs ${c.overs}    •    Extras ${c.extras}    •    RR ${String.format(Locale.US, "%.2f", c.runRate)}"
        inningsText.text = "${matchType.uppercase(Locale.getDefault())} | INNINGS ${currentIndex + 1} | ${c.batting} batting"
        statusText.text = when { c.declared -> "DECLARED"; c.completed -> "INNINGS COMPLETE"; else -> message }
        val summary = StringBuilder()
        inningsList.forEachIndexed { i, inn ->
            summary.append("Innings ${i + 1}: ${inn.batting} ${inn.runs}/${inn.wickets} (${inn.overs} ov)")
            if (inn.declared) summary.append(" declared")
            if (inn.completed) summary.append(" complete")
            summary.append("\n")
        }
        if (inningsList.size >= 2) {
            val first = inningsList[0]; val second = inningsList[1]; val target = first.runs + 1
            if (currentIndex == 1 && !second.completed)
                summary.append("\nTarget: $target • Need ${maxOf(0, target - second.runs)} runs")
            if (currentIndex >= 1 && second.completed)
                summary.append("\n${if (second.runs >= target) second.batting + " won" else first.batting + " ahead"}")
        }
        historyText.text = summary.toString()
    }

    private fun showSetupDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val a = EditText(this).apply { hint = "Batting team"; setText(teamA) }
        val b = EditText(this).apply { hint = "Bowling team"; setText(teamB) }
        layout.addView(a); layout.addView(b)
        if (teams.isNotEmpty()) {
            Button(this).apply {
                text = "Choose saved batting team"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle("Select batting team")
                        .setItems(teams.map { it.name }.toTypedArray()) { _, i -> a.setText(teams[i].name) }.show()
                }
            }.also { layout.addView(it) }
            Button(this).apply {
                text = "Choose saved bowling team"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle("Select bowling team")
                        .setItems(teams.map { it.name }.toTypedArray()) { _, i -> b.setText(teams[i].name) }.show()
                }
            }.also { layout.addView(it) }
        }
        val typeButton = Button(this).apply { text = "Match type: $matchType" }
        layout.addView(typeButton)
        typeButton.setOnClickListener {
            val types = arrayOf("T20", "ODI", "Test", "Custom")
            AlertDialog.Builder(this).setTitle("Select match type").setItems(types) { _, which ->
                matchType = types[which]
                oversLimit = when (matchType) { "T20" -> 20; "ODI" -> 50; "Test" -> 0; else -> 20 }
                typeButton.text = "Match type: $matchType"
            }.show()
        }
        val overs = EditText(this).apply {
            hint = "Overs per innings (Custom only)"; inputType = 2
            setText(if (oversLimit > 0) oversLimit.toString() else "")
        }
        layout.addView(overs)
        AlertDialog.Builder(this).setTitle("Match setup").setView(layout).setCancelable(false)
            .setPositiveButton("Start match") { _, _ ->
                teamA = a.text.toString().trim().ifEmpty { "TEAM A" }
                teamB = b.text.toString().trim().ifEmpty { "TEAM B" }
                if (teamA.equals(teamB, true)) teamB += " B"
                if (matchType == "Custom") oversLimit = overs.text.toString().toIntOrNull()?.coerceIn(1, 200) ?: 20
                inningsList[0].batting = teamA; inningsList[0].bowling = teamB; refresh()
            }.setNegativeButton("Later", null).show()
    }

    private fun section(label: String) { addGap(14); root.addView(text(label, 13, Color.rgb(100,220,190), true)); addGap(5) }
    private fun addAction(parent: LinearLayout, label: String, color: Int = Color.rgb(35,75,110), action: () -> Unit) {
        val b = button(label, color); b.setOnClickListener { action() }; parent.addView(b, weightParams())
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private fun weightParams() = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
        setMargins(dp(3), dp(3), dp(3), dp(3))
    }
    private fun button(label: String, color: Int) = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
        setBackgroundColor(color); minHeight = dp(48)
    }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(5), dp(4), dp(5))
    }
    private fun addGap(height: Int) { root.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}