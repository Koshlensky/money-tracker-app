package com.example.moneytracker

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etTotalAmount: EditText
    private lateinit var expensesContainer: LinearLayout
    private lateinit var mainLayout: View

    private val expenseItems = mutableListOf<ExpenseItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        addFirstExpense()
    }

    private fun initViews() {
        etTotalAmount = findViewById(R.id.etTotalAmount)
        expensesContainer = findViewById(R.id.expensesContainer)
        mainLayout = findViewById(R.id.mainLayout)
    }

    private fun setupClickListeners() {
        val btnAddExpense: ImageButton = findViewById(R.id.btnAddExpense)
        btnAddExpense.setOnClickListener {
            addExpenseItem()
        }

        // Hiding the keyboard when clicking outside the input fields
        mainLayout.setOnClickListener {
            hideKeyboard()
        }
    }

    private fun addFirstExpense() {
        addExpenseItem()
    }

    private fun addExpenseItem() {
        val expenseView = LayoutInflater.from(this).inflate(R.layout.item_expense, null)
        val etExpenseAmount = expenseView.findViewById<EditText>(R.id.etExpenseAmount)
        val etExpenseDescription = expenseView.findViewById<EditText>(R.id.etExpenseDescription)
        val btnRemoveExpense = expenseView.findViewById<ImageButton>(R.id.btnRemoveExpense)

        val expenseItem = ExpenseItem(etExpenseAmount, etExpenseDescription, expenseView)
        expenseItems.add(expenseItem)

        // Adding a change listener for the amount spent
        etExpenseAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateTotalAmount()
            }
        })

        // Waste deletion handler
        btnRemoveExpense.setOnClickListener {
            if (expenseItems.size > 1) {
                expensesContainer.removeView(expenseView)
                expenseItems.remove(expenseItem)
                updateTotalAmount()
            }
        }

        expensesContainer.addView(expenseView)
    }

    private fun updateTotalAmount() {
        var total = 0.0

        for (expenseItem in expenseItems) {
            val amountText = expenseItem.amountEditText.text.toString()
            if (amountText.isNotEmpty()) {
                total += amountText.toDoubleOrNull() ?: 0.0
            }
        }

        etTotalAmount.setText(String.format("%.2f", total))
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }

    data class ExpenseItem(
            val amountEditText: EditText,
            val descriptionEditText: EditText,
            val view: View
    )
}