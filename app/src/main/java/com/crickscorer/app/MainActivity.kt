package com.crickscorer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private data class Team(val name: String, val players: MutableList<String> = mutableListOf())
    private data class Batter(
        val name: String, var runs: Int = 0, var balls: Int = 0,
        var fours: Int = 0, var sixes: Int = 0, var out: Boolean = false
    ) {
        val strikeRate: Double get() = if (balls == 0) 0.0 else runs * 100.0 / balls
    }
    private data class Bowler(
        val name: String, var balls: Int = 0, var runs: Int = 0,
        var wickets: Int = 0, var wides: Int = 0, var noBalls: Int = 0
    ) {
        val overs: String get() = "${balls / 6}.${balls % 6}"
        val economy: Double get() = if (balls == 0) 0.0 else runs * 6.0 / balls
    }
    private data class Innings(
        var batting: String, var bowling: String,
        var runs: Int = 0, var wickets: Int = 0, var legalBalls: Int = 0,
        var wides: Int = 0, var noBalls: Int = 0, var byes: Int = 0,
        var legByes: Int = 0, var penalty: Int = 0,
        var declared: Boolean = false, var completed: Boolean = false,
        var striker: String = "", var nonStriker: String = "", var bowlerName: String = "",
        val batters: MutableList<Batter> = mutableListOf(),
        val bowlers: MutableList<Bowler> = mutableListOf(),
        val commentary: MutableList<String> = mutableListOf(),
        val fallOfWickets: MutableList<String> = mutableListOf()
    ) {
        val overs: String get() = "${legalBalls / 6}.${legalBalls % 6}"
        val extras: Int get() = wides + noBalls + byes + legByes + penalty
        val runRate: Double get() = if (legalBalls == 0) 0.0 else runs * 6.0 / legalBalls
    }
    private data class MatchRecord(val date: String, val teamA: String, val teamB: String, val result: String, val score: String)

    private val inningsList = mutableListOf<Innings>()
    private val undoStack = mutableListOf<String>()
    private val teams = mutableListOf<Team>()
    private val matchHistory = mutableListOf<MatchRecord>()
    private var currentIndex = 0
    private var matchType = "T20"
    private var oversLimit = 20
    private var teamA = "TEAM A"
    private var teamB = "TEAM B"
    private var tournament = "Friendly"
    private var matchFinished = false
    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var scoreText: TextView
    private lateinit var detailsText: TextView
    private lateinit var statusText: TextView
    private lateinit var inningsText: TextView
    private lateinit var playersText: TextView
    private lateinit var commentaryText: TextView
    private lateinit var summaryText: TextView
    private val current: Innings get() = inningsList[currentIndex]
    private val striker: Batter? get() = current.batters.firstOrNull { it.name == current.striker }
    private val nonStriker: Batter? get() = current.batters.firstOrNull { it.name == current.nonStriker }
    private val currentBowler: Bowler? get() = current.bowlers.firstOrNull { it.name == current.bowlerName }

    private val bg = Color.rgb(10, 20, 35)
    private val panel = Color.rgb(32, 55, 78)
    private val teal = Color.rgb(0, 135, 110)
    private val pale = Color.rgb(190, 210, 230)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("cricket_scorer_data", MODE_PRIVATE)
        loadTeams()
        loadMatchHistory()
        startNewMatch()
        buildScreen()
        showSetupDialog()
    }

    private fun loadTeams() {
        teams.clear()
        try {
            val arr = JSONArray(prefs.getString("teams_json", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val p = mutableListOf<String>()
                val pa = o.optJSONArray("players") ?: JSONArray()
                for (j in 0 until pa.length()) p.add(pa.optString(j))
                teams.add(Team(o.optString("name", "Team"), p))
            }
        } catch (_: Exception) { teams.clear() }
    }

    private fun saveTeams() {
        val arr = JSONArray()
        teams.forEach { t ->
            val p = JSONArray()
            t.players.forEach { p.put(it) }
            arr.put(JSONObject().put("name", t.name).put("players", p))
        }
        prefs.edit().putString("teams_json", arr.toString()).apply()
    }

    private fun loadMatchHistory() {
        matchHistory.clear()
        try {
            val arr = JSONArray(prefs.getString("match_history", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                matchHistory.add(MatchRecord(o.optString("date"), o.optString("a"),
                    o.optString("b"), o.optString("result"), o.optString("score")))
            }
        } catch (_: Exception) { matchHistory.clear() }
    }

    private fun saveMatchHistory() {
        val arr = JSONArray()
        matchHistory.forEach { m ->
            arr.put(JSONObject().put("date", m.date).put("a", m.teamA).put("b", m.teamB)
                .put("result", m.result).put("score", m.score))
        }
        prefs.edit().putString("match_history", arr.toString()).apply()
    }

    private fun startNewMatch() {
        inningsList.clear()
        undoStack.clear()
        currentIndex = 0
        matchFinished = false
        inningsList.add(Innings(teamA, teamB))
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(26))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        setContentView(scroll)

        addText("🏏 CRICKET SCORER PRO", 25, Color.WHITE, true, true)
        addText("OFFLINE MATCH CENTRE", 12, Color.rgb(100, 220, 190), true, true)
        addButton("MATCH SETUP  •  $matchType", panel) { showSetupDialog() }
        addButton("TEAMS & PLAYERS", teal) { showTeamManager() }
        addButton("MATCH HISTORY", panel) { showHistory() }
        addGap(8)
        inningsText = addText("", 14, pale, true, true)
        scoreText = addText("0/0", 48, Color.WHITE, true, true)
        detailsText = addText("", 15, pale, false, true)
        statusText = addText("", 14, Color.rgb(100, 220, 190), true, true)
        section("BATTERS")
        playersText = addText("", 14, Color.WHITE, false, false)
        addButton("SELECT STRIKER", panel) { chooseBatter(true) }
        addButton("SELECT NON-STRIKER", panel) { chooseBatter(false) }
        addButton("SELECT / CHANGE BOWLER", panel) { chooseBowler() }

        section("SCORE RUNS")
        val runRow = row()
        listOf(0, 1, 2, 3, 4, 6).forEach { n ->
            val b = button(n.toString(), if (n == 4 || n == 6) teal else Color.rgb(35, 75, 110))
            b.textSize = 19f
            b.setOnClickListener { scoreBatRuns(n) }
            runRow.addView(b, weightParams())
        }
        root.addView(runRow)

        section("EXTRAS")
        row().also { r ->
            addAction(r, "WIDE +1") { scoreExtra("WIDE") }
            addAction(r, "NO-BALL +1") { scoreExtra("NO BALL") }
            root.addView(r)
        }
        row().also { r ->
            addAction(r, "BYE +1") { scoreExtra("BYE") }
            addAction(r, "LEG-BYE +1") { scoreExtra("LEG-BYE") }
            root.addView(r)
        }
        section("MATCH CONTROLS")
        row().also { r ->
            addAction(r, "WICKET", Color.rgb(165, 50, 60)) { wicket() }
            addAction(r, "UNDO", Color.rgb(90, 90, 105)) { undo() }
            root.addView(r)
        }
        row().also { r ->
            addAction(r, "NEXT INNINGS") { nextInnings() }
            addAction(r, "DECLARE") { declareInnings() }
            root.addView(r)
        }
        addButton("FULL SCORECARD", panel) { showScorecard() }
        addButton("LIVE COMMENTARY", panel) { showCommentary() }
        addButton("SAVE / FINISH MATCH", teal) { finishMatch() }
        addButton("SHARE SCORECARD", panel) { shareScorecard() }
        addButton("NEW MATCH", Color.rgb(120, 70, 35)) { confirmNewMatch() }
        section("INNINGS SUMMARY")
        summaryText = addText("", 14, pale, false, false)
        section("RECENT COMMENTARY")
        commentaryText = addText("", 13, pale, false, false)
        refresh()
    }

    private fun showSetupDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        val a = EditText(this).apply { hint = "Batting team"; setText(teamA) }
        val b = EditText(this).apply { hint = "Bowling team"; setText(teamB) }
        val t = EditText(this).apply { hint = "Tournament / series"; setText(tournament) }
        box.addView(a); box.addView(b); box.addView(t)
        val types = arrayOf("T10", "T20", "ODI", "Test", "Custom")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, types)
            setSelection(types.indexOf(matchType).coerceAtLeast(0))
        }
        box.addView(spinner)
        val overs = EditText(this).apply {
            hint = "Custom overs"; inputType = 2; setText(if (oversLimit > 0) oversLimit.toString() else "")
        }
        box.addView(overs)
        AlertDialog.Builder(this).setTitle("Match setup").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                teamA = a.text.toString().trim().ifEmpty { "TEAM A" }
                teamB = b.text.toString().trim().ifEmpty { "TEAM B" }
                if (teamA.equals(teamB, true)) teamB += " B"
                tournament = t.text.toString().trim().ifEmpty { "Friendly" }
                matchType = types[spinner.selectedItemPosition]
                oversLimit = when (matchType) {
                    "T10" -> 10
                    "T20" -> 20
                    "ODI" -> 50
                    "Test" -> 0
                    else -> overs.text.toString().toIntOrNull()?.coerceIn(1, 200) ?: 20
                }
                startNewMatch()
                inningsList[0].batting = teamA
                inningsList[0].bowling = teamB
                selectPlayingXIs()
            }.show()
    }

    private fun selectPlayingXIs() {
        val options = arrayOf("Use saved teams", "Enter Playing XIs manually")
        AlertDialog.Builder(this).setTitle("Playing XI").setItems(options) { _, which ->
            if (which == 0) selectSavedTeamsForMatch() else manualXis()
        }.setNegativeButton("Later") { _, _ -> refresh() }.show()
    }

    private fun selectSavedTeamsForMatch() {
        if (teams.size < 2) {
            toast("Save at least two teams first")
            showTeamManager()
            return
        }
        AlertDialog.Builder(this).setTitle("Select batting team")
            .setItems(teams.map { it.name }.toTypedArray()) { _, i ->
                val battingTeam = teams[i]
                val otherTeams = teams.filter { it.name != battingTeam.name }
                AlertDialog.Builder(this).setTitle("Select bowling team")
                    .setItems(otherTeams.map { it.name }.toTypedArray()) { _, j ->
                        val bowlingTeam = otherTeams[j]
                        teamA = battingTeam.name; teamB = bowlingTeam.name
                        inningsList[0].batting = teamA; inningsList[0].bowling = teamB
                        chooseXI(battingTeam) { batXI ->
                            chooseXI(bowlingTeam) { bowlXI -> initializePlayers(batXI, bowlXI) }
                        }
                    }.show()
            }.show()
    }

    private fun chooseXI(team: Team, done: (List<String>) -> Unit) {
        if (team.players.isEmpty()) {
            val input = EditText(this).apply { hint = "Player names separated by commas" }
            AlertDialog.Builder(this).setTitle("${team.name} Playing XI").setView(input)
                .setPositiveButton("Continue") { _, _ ->
                    done(input.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(11))
                }.show()
            return
        }
        val checked = BooleanArray(team.players.size) { it < 11 }
        AlertDialog.Builder(this).setTitle("${team.name}: select up to 11 players")
            .setMultiChoiceItems(team.players.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Continue") { _, _ ->
                done(team.players.filterIndexed { i, _ -> checked[i] }.distinct().take(11))
            }.show()
    }

    private fun manualXis() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(4)) }
        val a = EditText(this).apply { hint = "$teamA players, comma separated" }
        val b = EditText(this).apply { hint = "$teamB players, comma separated" }
        box.addView(a); box.addView(b)
        AlertDialog.Builder(this).setTitle("Enter Playing XIs").setView(box)
            .setPositiveButton("Continue") { _, _ ->
                val bat = a.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(11)
                val bowl = b.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(11)
                initializePlayers(bat, bowl)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun initializePlayers(battingXI: List<String>, bowlingXI: List<String>) {
        if (battingXI.size < 2 || bowlingXI.isEmpty()) {
            toast("Enter at least two batters and one bowler")
            return
        }
        current.batters.clear(); current.bowlers.clear()
        battingXI.forEach { current.batters.add(Batter(it)) }
        bowlingXI.forEach { current.bowlers.add(Bowler(it)) }
        current.striker = battingXI[0]
        current.nonStriker = battingXI[1]
        current.bowlerName = bowlingXI[0]
        addCommentary("Match started: ${current.batting} vs ${current.bowling}")
        refresh("Playing XI selected")
    }

    private fun showTeamManager() {
        AlertDialog.Builder(this).setTitle("Teams & players")
            .setItems(arrayOf("Create team", "Manage saved teams")) { _, choice ->
                if (choice == 0) editTeamDialog() else showSavedTeams()
            }.show()
    }

    private fun editTeamDialog(team: Team? = null) {
        val input = EditText(this).apply { hint = "Team name"; setText(team?.name ?: "") }
        AlertDialog.Builder(this).setTitle(if (team == null) "Create team" else "Edit team")
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) toast("Enter a team name")
                else if (teams.any { it !== team && it.name.equals(name, true) }) toast("Team already exists")
                else {
                    if (team == null) teams.add(Team(name))
                    else {
                        val i = teams.indexOf(team)
                        if (i >= 0) teams[i] = Team(name, team.players)
                    }
                    saveTeams(); showSavedTeams()
                }
            }.show()
    }

    private fun showSavedTeams() {
        if (teams.isEmpty()) { editTeamDialog(); return }
        AlertDialog.Builder(this).setTitle("Saved teams")
            .setItems(teams.map { "${it.name} (${it.players.size})" }.toTypedArray()) { _, i -> showTeamActions(teams[i]) }
            .setPositiveButton("Add team") { _, _ -> editTeamDialog() }.show()
    }

    private fun showTeamActions(team: Team) {
        AlertDialog.Builder(this).setTitle(team.name)
            .setItems(arrayOf("Add player", "Remove player", "Rename team", "Delete team")) { _, choice ->
                when (choice) {
                    0 -> editPlayerDialog(team)
                    1 -> {
                        if (team.players.isEmpty()) toast("No players saved")
                        else AlertDialog.Builder(this).setTitle("Remove player")
                            .setItems(team.players.toTypedArray()) { _, i ->
                                team.players.removeAt(i); saveTeams(); showTeamActions(team)
                            }.show()
                    }
                    2 -> editTeamDialog(team)
                    3 -> AlertDialog.Builder(this).setTitle("Delete team?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ -> teams.remove(team); saveTeams(); showSavedTeams() }.show()
                }
            }.show()
    }

    private fun editPlayerDialog(team: Team) {
        val input = EditText(this).apply { hint = "Player name" }
        AlertDialog.Builder(this).setTitle("Add player to ${team.name}").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && team.players.none { it.equals(name, true) }) {
                    team.players.add(name); saveTeams()
                }
                showTeamActions(team)
            }.show()
    }

    private fun hasPlayers(): Boolean {
        if (current.batters.size < 2 || current.striker.isBlank() ||
            current.nonStriker.isBlank() || current.bowlerName.isBlank()) {
            toast("Select Playing XI, two batters and a bowler first")
            return false
        }
        return true
    }

    private fun canScore(): Boolean {
        if (current.completed || matchFinished) { toast("This innings is complete"); return false }
        if (!hasPlayers()) return false
        return true
    }

    private fun saveUndo() {
        undoStack.add(JSONObject().put("state", stateToJson()).toString())
        if (undoStack.size > 100) undoStack.removeAt(0)
    }

    private fun stateToJson(): JSONObject {
        val all = JSONArray()
        inningsList.forEach { inn ->
            val bats = JSONArray()
            inn.batters.forEach { bats.put(JSONObject().put("n", it.name).put("r", it.runs)
                .put("b", it.balls).put("4", it.fours).put("6", it.sixes).put("out", it.out)) }
            val bowls = JSONArray()
            inn.bowlers.forEach { bowls.put(JSONObject().put("n", it.name).put("b", it.balls)
                .put("r", it.runs).put("w", it.wickets).put("wd", it.wides).put("nb", it.noBalls)) }
            all.put(JSONObject().put("batting", inn.batting).put("bowling", inn.bowling)
                .put("runs", inn.runs).put("wickets", inn.wickets).put("balls", inn.legalBalls)
                .put("wides", inn.wides).put("noBalls", inn.noBalls).put("byes", inn.byes)
                .put("legByes", inn.legByes).put("penalty", inn.penalty).put("declared", inn.declared)
                .put("completed", inn.completed).put("striker", inn.striker).put("nonStriker", inn.nonStriker)
                .put("bowlerName", inn.bowlerName).put("batters", bats).put("bowlers", bowls)
                .put("commentary", JSONArray(inn.commentary)).put("fow", JSONArray(inn.fallOfWickets)))
        }
        return JSONObject().put("innings", all).put("index", currentIndex)
            .put("type", matchType).put("limit", oversLimit).put("a", teamA).put("b", teamB)
            .put("tournament", tournament).put("finished", matchFinished)
    }

    private fun restoreState(json: JSONObject) {
        inningsList.clear()
        val all = json.optJSONArray("innings") ?: JSONArray()
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            val inn = Innings(o.optString("batting"), o.optString("bowling"),
                o.optInt("runs"), o.optInt("wickets"), o.optInt("balls"),
                o.optInt("wides"), o.optInt("noBalls"), o.optInt("byes"),
                o.optInt("legByes"), o.optInt("penalty"), o.optBoolean("declared"),
                o.optBoolean("completed"), o.optString("striker"), o.optString("nonStriker"),
                o.optString("bowlerName"))
            val bats = o.optJSONArray("batters") ?: JSONArray()
            for (j in 0 until bats.length()) {
                val x = bats.getJSONObject(j)
                inn.batters.add(Batter(x.optString("n"), x.optInt("r"), x.optInt("b"),
                    x.optInt("4"), x.optInt("6"), x.optBoolean("out")))
            }
            val bowls = o.optJSONArray("bowlers") ?: JSONArray()
            for (j in 0 until bowls.length()) {
                val x = bowls.getJSONObject(j)
                inn.bowlers.add(Bowler(x.optString("n"), x.optInt("b"), x.optInt("r"),
                    x.optInt("w"), x.optInt("wd"), x.optInt("nb")))
            }
            val comm = o.optJSONArray("commentary") ?: JSONArray()
            for (j in 0 until comm.length()) inn.commentary.add(comm.optString(j))
            val fow = o.optJSONArray("fow") ?: JSONArray()
            for (j in 0 until fow.length()) inn.fallOfWickets.add(fow.optString(j))
            inningsList.add(inn)
        }
        if (inningsList.isEmpty()) inningsList.add(Innings(teamA, teamB))
        currentIndex = json.optInt("index", 0).coerceIn(0, inningsList.lastIndex)
        matchType = json.optString("type", "T20")
        oversLimit = json.optInt("limit", 20)
        teamA = json.optString("a", "TEAM A"); teamB = json.optString("b", "TEAM B")
        tournament = json.optString("tournament", "Friendly")
        matchFinished = json.optBoolean("finished", false)
    }

    private fun undo() {
        if (undoStack.isEmpty()) { toast("Nothing to undo"); return }
        val old = JSONObject(undoStack.removeAt(undoStack.lastIndex)).getJSONObject("state")
        restoreState(old)
        refresh("Last action undone")
    }

    private fun scoreBatRuns(runs: Int) {
        if (!canScore()) return
        saveUndo()
        val batter = striker ?: return
        val bowler = currentBowler ?: return
        batter.runs += runs; batter.balls++
        if (runs == 4) batter.fours++
        if (runs == 6) batter.sixes++
        current.runs += runs
        bowler.runs += runs; bowler.balls++
        current.legalBalls++
        addCommentary("${current.overs}: ${batter.name} ${if (runs == 0) "defends" else "scores $runs"}")
        if (runs % 2 == 1) swapStrike()
        afterLegalBall()
        refresh()
    }

    private fun scoreExtra(kind: String) {
        if (!canScore()) return
        saveUndo()
        val bowler = currentBowler ?: return
        when (kind) {
            "WIDE" -> {
                current.runs++; current.wides++; bowler.runs++; bowler.wides++
                addCommentary("Wide — 1 extra")
            }
            "NO BALL" -> {
                current.runs++; current.noBalls++; bowler.runs++; bowler.noBalls++
                addCommentary("No-ball — 1 extra")
            }
            "BYE", "LEG-BYE" -> {
                current.runs++
                if (kind == "BYE") current.byes++ else current.legByes++
                bowler.balls++; current.legalBalls++
                swapStrike()
                addCommentary("$kind — 1 run")
                afterLegalBall()
            }
        }
        refresh()
    }

    private fun wicket() {
        if (!canScore()) return
        val dismissed = striker ?: return
        saveUndo()
        dismissed.out = true
        dismissed.balls++
        current.wickets++
        current.legalBalls++
        currentBowler?.let { it.balls++; it.wickets++ }
        current.fallOfWickets.add("${current.runs}/${current.wickets} (${current.overs}) ${dismissed.name}")
        addCommentary("WICKET — ${dismissed.name} dismissed")
        if (finishCondition()) { refresh("Innings complete"); return }
        val available = current.batters.filter { !it.out && it.name != current.nonStriker }
        if (available.isEmpty()) { current.completed = true; refresh(); return }
        AlertDialog.Builder(this).setTitle("Select next batter")
            .setItems(available.map { it.name }.toTypedArray()) { _, i ->
                current.striker = available[i].name
                afterLegalBall()
                refresh("New batter: ${current.striker}")
            }.setNegativeButton("Later") { _, _ -> refresh("Select next batter") }.show()
    }

    private fun afterLegalBall() {
        if (finishCondition()) { current.completed = true; return }
        if (current.legalBalls > 0 && current.legalBalls % 6 == 0) {
            swapStrike()
            addCommentary("End of over ${current.legalBalls / 6}. Select next bowler.")
            chooseBowler()
        }
    }

    private fun finishCondition(): Boolean {
        val oversDone = matchType != "Test" && oversLimit > 0 && current.legalBalls >= oversLimit * 6
        val chaseWon = inningsList.size >= 2 && currentIndex % 2 == 1 &&
            current.runs > inningsList[currentIndex - 1].runs
        val allOut = current.wickets >= 10
        if (oversDone || chaseWon || allOut || current.declared) current.completed = true
        return current.completed
    }

    private fun swapStrike() {
        val old = current.striker
        current.striker = current.nonStriker
        current.nonStriker = old
    }

    private fun chooseBatter(isStriker: Boolean) {
        val available = current.batters.filter {
            !it.out && it.name != if (isStriker) current.nonStriker else current.striker
        }
        if (available.isEmpty()) { toast("No available batter"); return }
        AlertDialog.Builder(this).setTitle(if (isStriker) "Select striker" else "Select non-striker")
            .setItems(available.map { it.name }.toTypedArray()) { _, i ->
                if (isStriker) current.striker = available[i].name else current.nonStriker = available[i].name
                refresh()
            }.show()
    }

    private fun chooseBowler() {
        if (current.bowlers.isEmpty()) { toast("Bowling XI is not selected"); return }
        AlertDialog.Builder(this).setTitle("Select bowler")
            .setItems(current.bowlers.map { "${it.name} (${it.overs})" }.toTypedArray()) { _, i ->
                current.bowlerName = current.bowlers[i].name
                refresh("Bowler: ${current.bowlerName}")
            }.show()
    }

    private fun addCommentary(line: String) {
        current.commentary.add(0, line)
        if (current.commentary.size > 100) current.commentary.removeAt(current.commentary.lastIndex)
    }

    private fun nextInnings() {
        if (matchFinished) { toast("Match already finished"); return }
        if (!current.completed) {
            AlertDialog.Builder(this).setTitle("End innings?")
                .setMessage("This innings has not reached its normal end. End it now?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("End innings") { _, _ -> createNextInnings() }.show()
        } else createNextInnings()
    }

    private fun createNextInnings() {
        val maxInnings = if (matchType == "Test") 4 else 2
        if (inningsList.size >= maxInnings) { toast("All innings have been entered"); return }
        saveUndo()
        val old = current
        val nextBatting = old.bowling
        val nextBowling = old.batting
        val next = Innings(nextBatting, nextBowling)
        inningsList.add(next)
        currentIndex = inningsList.lastIndex
        selectNextInningsPlayers()
        refresh("Innings ${currentIndex + 1}")
    }

    private fun selectNextInningsPlayers() {
        val previous = inningsList[currentIndex - 1]
        val batNames = previous.bowlers.map { it.name }
        val bowlNames = previous.batters.map { it.name }
        if (batNames.size >= 2 && bowlNames.isNotEmpty()) {
            current.batters.clear(); batNames.forEach { current.batters.add(Batter(it)) }
            current.bowlers.clear(); bowlNames.forEach { current.bowlers.add(Bowler(it)) }
            current.striker = batNames[0]; current.nonStriker = batNames[1]
            current.bowlerName = bowlNames[0]
        } else {
            toast("Select the new innings Playing XI")
            manualXis()
        }
    }

    private fun declareInnings() {
        if (matchType != "Test") { toast("Declaration is available in Test matches only"); return }
        if (!canScore()) return
        saveUndo(); current.declared = true; current.completed = true
        addCommentary("${current.batting} declared at ${current.runs}/${current.wickets}")
        refresh("Innings declared")
    }

    private fun refresh(message: String = "") {
        if (!::scoreText.isInitialized || inningsList.isEmpty()) return
        val c = current
        scoreText.text = "${c.runs}/${c.wickets}"
        detailsText.text = "Overs ${c.overs}  •  Extras ${c.extras}  •  RR ${String.format(Locale.US, "%.2f", c.runRate)}"
        inningsText.text = "$matchType  |  ${tournament.uppercase(Locale.getDefault())}  |  INNINGS ${currentIndex + 1}: ${c.batting}"
        statusText.text = when {
            matchFinished -> "MATCH FINISHED"
            c.declared -> "DECLARED"
            c.completed -> "INNINGS COMPLETE"
            else -> message.ifBlank { "Batting: ${c.striker.ifBlank { "Select striker" }}  •  Bowling: ${c.bowlerName.ifBlank { "Select bowler" }}" }
        }
        playersText.text = buildString {
            val s = striker
            val n = nonStriker
            append("★ ${s?.name ?: "Striker not selected"}  ${s?.runs ?: 0} (${s?.balls ?: 0})  4s:${s?.fours ?: 0}  6s:${s?.sixes ?: 0}  SR:${String.format(Locale.US, "%.1f", s?.strikeRate ?: 0.0)}\n")
            append("  ${n?.name ?: "Non-striker not selected"}  ${n?.runs ?: 0} (${n?.balls ?: 0})  4s:${n?.fours ?: 0}  6s:${n?.sixes ?: 0}  SR:${String.format(Locale.US, "%.1f", n?.strikeRate ?: 0.0)}\n")
            val bw = currentBowler
            append("\nBowler: ${bw?.name ?: "Select bowler"}  ${bw?.overs ?: "0.0"}-${bw?.runs ?: 0}-${bw?.wickets ?: 0}  Econ:${String.format(Locale.US, "%.2f", bw?.economy ?: 0.0)}")
            append("\nRemaining batters: ${c.batters.count { !it.out && it.name != c.striker && it.name != c.nonStriker }}")
        }
        summaryText.text = inningsList.mapIndexed { i, inn ->
            "Innings ${i + 1}: ${inn.batting} ${inn.runs}/${inn.wickets} (${inn.overs})${if (inn.declared) " dec" else ""}"
        }.joinToString("\n")
        commentaryText.text = c.commentary.take(8).joinToString("\n").ifBlank { "Commentary will appear here." }
    }

    private fun showScorecard() {
        val lines = StringBuilder()
        inningsList.forEachIndexed { index, inn ->
            lines.append("INNINGS ${index + 1}: ${inn.batting} ${inn.runs}/${inn.wickets} (${inn.overs})\n")
            lines.append("BATTERS\n")
            inn.batters.forEach { b ->
                lines.append("${b.name}: ${b.runs} (${b.balls})  4s ${b.fours}  6s ${b.sixes}  SR ${String.format(Locale.US, "%.1f", b.strikeRate)}  ${if (b.out) "OUT" else "NOT OUT"}\n")
            }
            lines.append("EXTRAS: ${inn.extras} (Wd ${inn.wides}, Nb ${inn.noBalls}, B ${inn.byes}, Lb ${inn.legByes})\n")
            lines.append("BOWLING\n")
            inn.bowlers.forEach { b ->
                lines.append("${b.name}: ${b.overs}-${b.runs}-${b.wickets}  Econ ${String.format(Locale.US, "%.2f", b.economy)}\n")
            }
            if (inn.fallOfWickets.isNotEmpty()) lines.append("FALL OF WICKETS: ${inn.fallOfWickets.joinToString("; ")}\n")
            lines.append("\n")
        }
        val view = TextView(this).apply {
            text = lines.toString(); textSize = 13f; setTextColor(Color.BLACK)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val scroll = ScrollView(this); scroll.addView(view)
        AlertDialog.Builder(this).setTitle("Full scorecard").setView(scroll)
            .setPositiveButton("Share") { _, _ ->
    shareText(lines.toString())
}
.setNeutralButton("Save PDF") { _, _ ->
    saveScorecardPdf(lines.toString())
}
.setNegativeButton("Close", null).show()
    }

    private fun showCommentary() {
        val all = inningsList.flatMapIndexed { i, inn -> inn.commentary.map { "Innings ${i + 1}: $it" } }
        val msg = all.joinToString("\n").ifBlank { "No commentary yet." }
        AlertDialog.Builder(this).setTitle("Live commentary")
            .setMessage(msg).setPositiveButton("Close", null).show()
    }

    private fun finishMatch() {
        if (inningsList.isEmpty()) return
        val result = calculateResult()
        val score = inningsList.mapIndexed { i, inn ->
            "I${i + 1} ${inn.batting} ${inn.runs}/${inn.wickets} (${inn.overs})"
        }.joinToString(" | ")
        if (!matchFinished) {
            matchHistory.add(0, MatchRecord(java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(java.util.Date()),
                teamA, teamB, result, score))
            if (matchHistory.size > 100) matchHistory.removeAt(matchHistory.lastIndex)
            saveMatchHistory()
            matchFinished = true
        }
        AlertDialog.Builder(this).setTitle("Match saved")
            .setMessage("$result\n\n$score")
            .setPositiveButton("Scorecard") { _, _ -> showScorecard() }
            .setNegativeButton("Close", null).show()
        refresh()
    }

    private fun calculateResult(): String {
        if (inningsList.size < 2) return "Match saved — result pending"
        val first = inningsList[0]; val second = inningsList[1]
        return when {
            second.runs > first.runs -> "${second.batting} won by ${10 - second.wickets} wickets"
            second.runs == first.runs -> "Match tied"
            else -> "${first.batting} won by ${first.runs - second.runs} runs"
        }
    }

    private fun showHistory() {
        if (matchHistory.isEmpty()) { toast("No saved matches yet"); return }
        val labels = matchHistory.map { "${it.date} | ${it.teamA} vs ${it.teamB}\n${it.result}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Saved match history")
            .setItems(labels) { _, i ->
                val m = matchHistory[i]
                AlertDialog.Builder(this).setTitle("${m.teamA} vs ${m.teamB}")
                    .setMessage("${m.date}\n${m.score}\n\n${m.result}")
                    .setPositiveButton("Share") { _, _ -> shareText("${m.teamA} vs ${m.teamB}\n${m.score}\n${m.result}") }
                    .setNegativeButton("Close", null).show()
            }.show()
    }

    private fun shareScorecard() {
        val content = buildString {
            append("🏏 $teamA vs $teamB\nTournament: $tournament\n")
            append(inningsList.mapIndexed { i, inn -> "Innings ${i + 1}: ${inn.batting} ${inn.runs}/${inn.wickets} (${inn.overs})" }.joinToString("\n"))
            append("\n\n")
            inningsList.forEachIndexed { i, inn ->
                append("INNINGS ${i + 1}\n")
                inn.batters.forEach { append("${it.name}: ${it.runs} (${it.balls})\n") }
                inn.bowlers.forEach { append("${it.name}: ${it.overs}-${it.runs}-${it.wickets}\n") }
            }
        }
        shareText(content)
    }

    private fun shareText(content: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Cricket scorecard")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(send, "Share score"))
    }

    private fun confirmNewMatch() {
        AlertDialog.Builder(this).setTitle("Start new match?")
            .setMessage("The current match on this screen will be cleared. Saved teams and match history will remain.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ -> startNewMatch(); refresh(); showSetupDialog() }.show()
    }

    private fun section(label: String) {
        addGap(12); addText(label, 13, Color.rgb(100, 220, 190), true, false); addGap(4)
    }
    private fun addButton(label: String, color: Int, action: () -> Unit) {
        val b = button(label, color)
        b.setOnClickListener { action() }
        root.addView(b)
    }
    private fun addAction(parent: LinearLayout, label: String, color: Int = Color.rgb(35, 75, 110), action: () -> Unit) {
        val b = button(label, color); b.setOnClickListener { action() }
        parent.addView(b, weightParams())
    }
    private fun button(label: String, color: Int) = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false
        setTextColor(Color.WHITE); setBackgroundColor(color); minHeight = dp(48)
    }
    private fun addText(value: String, size: Int, color: Int, bold: Boolean, centered: Boolean): TextView {
        val v = TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(5), dp(4), dp(5))
            if (centered) gravity = Gravity.CENTER
        }
        root.addView(v)
        return v
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private fun weightParams() = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
        setMargins(dp(2), dp(3), dp(2), dp(3))
    }
    private fun addGap(height: Int) { root.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

private fun saveScorecardPdf(scorecardText: String) {
    val pdf = PdfDocument()

    try {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val lineHeight = 16

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }

        val wrappedLines = mutableListOf<String>()
        val maxWidth = (pageWidth - margin * 2).toFloat()

        scorecardText.split("\n").forEach { originalLine ->
            if (originalLine.isBlank()) {
                wrappedLines.add("")
            } else {
                var currentLine = ""

                originalLine.split(" ").forEach { word ->
                    val testLine =
                        if (currentLine.isEmpty()) word
                        else "$currentLine $word"

                    if (textPaint.measureText(testLine) > maxWidth &&
                        currentLine.isNotEmpty()
                    ) {
                        wrappedLines.add(currentLine)
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }

                if (currentLine.isNotEmpty()) {
                    wrappedLines.add(currentLine)
                }
            }
        }

        val linesPerPage = 42
        val pageCount =
            (wrappedLines.size + linesPerPage - 1) / linesPerPage

        for (pageNumber in 0 until pageCount) {
            val pageInfo = PdfDocument.PageInfo.Builder(
                pageWidth, pageHeight, pageNumber + 1
            ).create()

            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText(
                "CRICKET SCORER PRO",
                margin.toFloat(),
                48f,
                titlePaint
            )

            canvas.drawLine(
                margin.toFloat(), 62f,
                (pageWidth - margin).toFloat(), 62f,
                textPaint
            )

            val start = pageNumber * linesPerPage
            val end = minOf(start + linesPerPage, wrappedLines.size)
            var y = 88f

            for (i in start until end) {
                canvas.drawText(
                    wrappedLines[i],
                    margin.toFloat(),
                    y,
                    textPaint
                )
                y += lineHeight
            }

            canvas.drawText(
                "Page ${pageNumber + 1} of $pageCount",
                margin.toFloat(),
                815f,
                textPaint
            )

            pdf.finishPage(page)
        }

        val folder = File(filesDir, "scorecards")
        if (!folder.exists()) folder.mkdirs()

        val file = File(
            folder,
            "Scorecard_${System.currentTimeMillis()}.pdf"
        )

        FileOutputStream(file).use { output ->
            pdf.writeTo(output)
        }

        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(
                contentResolver,
                "Scorecard PDF",
                uri
            )
        }

        Toast.makeText(
            this,
            "PDF saved successfully",
            Toast.LENGTH_LONG
        ).show()

        startActivity(
            Intent.createChooser(shareIntent, "Share Scorecard PDF")
        )

    } catch (e: Exception) {
        Toast.makeText(
            this,
            "PDF Error: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
    } finally {
        pdf.close()
    }
}

}