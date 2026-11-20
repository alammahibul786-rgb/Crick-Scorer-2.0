package com.crickscorer.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private var runs = 0
    private var wickets = 0
    private var balls = 0
    private lateinit var score: TextView
    private lateinit var overs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(20, 30, 20, 20)
        root.setBackgroundColor(Color.rgb(16, 24, 32))

        fun addText(text: String, size: Float, color: Int) {
            val view = TextView(this)
            view.text = text
            view.textSize = size
            view.setTextColor(color)
            view.gravity = Gravity.CENTER
            view.setPadding(8, 12, 8, 12)
            root.addView(view)
        }

        addText("🏏 CRICK SCORER 2.0", 26f, Color.GREEN)
        addText("LOCAL CRICKET SCOREBOARD", 14f, Color.WHITE)

        score = TextView(this)
        score.textSize = 42f
        score.setTextColor(Color.WHITE)
        score.gravity = Gravity.CENTER
        root.addView(score)

        overs = TextView(this)
        overs.textSize = 20f
        overs.setTextColor(Color.YELLOW)
        overs.gravity = Gravity.CENTER
        root.addView(overs)

        fun updateScore() {
            score.text = "$runs / $wickets"
            overs.text = "Overs: ${balls / 6}.${balls % 6}"
        }

        fun addButton(label: String, action: () -> Unit) {
            val button = Button(this)
            button.text = label
            button.textSize = 18f
            button.setOnClickListener {
                action()
                updateScore()
            }
            root.addView(button)
        }

        addButton("0 RUN") { balls++ }
        addButton("1 RUN") { runs++; balls++ }
        addButton("2 RUNS") { runs += 2; balls++ }
        addButton("3 RUNS") { runs += 3; balls++ }
        addButton("4 RUNS") { runs += 4; balls++ }
        addButton("6 RUNS") { runs += 6; balls++ }
        addButton("WICKET") {
            if (wickets < 10) wickets++
            balls++
        }
        addButton("UNDO / RESET SCORE") {
            runs = 0
            wickets = 0
            balls = 0
        }

        updateScore()
        setContentView(root)
    }
}
