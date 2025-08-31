package com.example.moneytracker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ExpensesActivity : AppCompatActivity() {

    private lateinit var person: Person
    private lateinit var expensesContainer: LinearLayout
    private lateinit var tvTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expenses)

        val id = intent.getLongExtra("personId", -1)
        person = PersonsRepo.find(id)

        tvTitle = findViewById(R.id.tvTitle)
        expensesContainer = findViewById(R.id.expensesContainer)
        val btnAdd: ImageButton = findViewById(R.id.btnAddExpense)

        // 🆕 кнопка Назад
        val btnBack: Button = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()   // просто закрывает эту активити и возвращает на MainActivity
        }

        tvTitle.text = "Окно трат — ${if (person.name.isBlank()) "без имени" else person.name}\nОсталось: ${centsToUsdText(person.remainingCents())}"

        // нарисуем все существующие траты
        refreshList()

        btnAdd.setOnClickListener {
            person.expenses.add(Expense())
            PersonsRepo.update(person)
            refreshList()
        }
    }

    private fun refreshList() {
        expensesContainer.removeAllViews()
        person.expenses.forEachIndexed { index, exp ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_expense, expensesContainer, false)
            val etAmount = v.findViewById<EditText>(R.id.etExpenseAmount)
            val etNote = v.findViewById<EditText>(R.id.etExpenseDescription)
            val btnDel = v.findViewById<ImageButton>(R.id.btnRemoveExpense)

            etAmount.setText(if (exp.amountCents == 0L) "" else (exp.amountCents / 100.0).toString())
            etNote.setText(exp.note)

            etAmount.addTextChangedListener(simpleWatcher {
                exp.amountCents = parseUsdToCents(it)
                PersonsRepo.update(person)
                tvTitle.text = "Окно трат — ${if (person.name.isBlank()) "без имени" else person.name}\nОсталось: ${centsToUsdText(person.remainingCents())}"
            })
            etNote.addTextChangedListener(simpleWatcher { text ->
                exp.note = text; PersonsRepo.update(person)
            })

            btnDel.setOnClickListener {
                if (person.expenses.size > 1) {
                    person.expenses.removeAt(index)
                    PersonsRepo.update(person)
                    refreshList()
                }
            }
            expensesContainer.addView(v)
        }
    }

    private fun simpleWatcher(onAfter: (String) -> Unit) =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onAfter(s?.toString().orEmpty()) }
        }
}
