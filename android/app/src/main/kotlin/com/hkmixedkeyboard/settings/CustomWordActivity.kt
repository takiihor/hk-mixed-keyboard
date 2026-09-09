package com.hkmixedkeyboard.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hkmixedkeyboard.memory.CustomWordEntity
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import kotlinx.coroutines.launch

class CustomWordActivity : AppCompatActivity() {

    private lateinit var wordList: LinearLayout
    private val dao by lazy { UserMemoryDatabase.get(this).customWordDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "自訂詞庫"
            textSize = 20f
            setPadding(0, 0, 0, dp(12))
        })

        // Add new word form
        val displayInput = EditText(this).apply { hint = "詞語（如: 我哋）" }
        val codeInput    = EditText(this).apply { hint = "Quick 碼（如: qirp）" }
        val addBtn = Button(this).apply {
            text = "新增"
            setOnClickListener { addWord(displayInput.text.toString(), codeInput.text.toString()) }
        }
        root.addView(displayInput, lp())
        root.addView(codeInput, lp())
        root.addView(addBtn, lp())

        root.addView(divider())

        wordList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(ScrollView(this).apply {
            addView(wordList)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        setContentView(root)
        refreshList()
    }

    private fun addWord(display: String, code: String) {
        val valid = when (val result = CustomWordValidator.validate(display, code)) {
            is CustomWordValidator.Result.Invalid -> {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                return
            }
            is CustomWordValidator.Result.Valid -> result
        }
        lifecycleScope.launch {
            dao.insert(CustomWordEntity(display = valid.display, quickCode = valid.quickCode))
            KeyboardSettings.bumpCustomWordsToken(this@CustomWordActivity)
            runOnUiThread { refreshList() }
        }
    }

    private fun refreshList() {
        lifecycleScope.launch {
            val words = dao.loadAll()
            runOnUiThread {
                wordList.removeAllViews()
                if (words.isEmpty()) {
                    wordList.addView(TextView(this@CustomWordActivity).apply {
                        text = "（未有自訂詞語）"
                        textSize = 14f
                        setTextColor(0xFF94A3B8.toInt())
                    })
                }
                words.forEach { word ->
                    wordList.addView(buildWordRow(word))
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildWordRow(word: CustomWordEntity) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, dp(8))

        addView(TextView(this@CustomWordActivity).apply {
            text = "${word.display}  (${word.quickCode})"
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(Button(this@CustomWordActivity).apply {
            text = "刪除"
            textSize = 12f
            setOnClickListener {
                lifecycleScope.launch {
                    dao.delete(word.id)
                    KeyboardSettings.bumpCustomWordsToken(this@CustomWordActivity)
                    runOnUiThread { refreshList() }
                }
            }
        })
    }

    private fun divider() = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1).also { it.setMargins(0, dp(12), 0, dp(12)) }
        setBackgroundColor(0xFFE2E8F0.toInt())
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
        it.bottomMargin = dp(8)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
}
