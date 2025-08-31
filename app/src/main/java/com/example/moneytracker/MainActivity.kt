package com.example.moneytracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var tvCurrency: TextView
    private lateinit var btnCurrency: ImageButton

    private val adapter = PeopleAdapter(
        onOpenRaw = { personId ->
            startActivity(Intent(this, ExpensesActivity::class.java).putExtra("personId", personId))
        },
        onChange = { PersonsRepo.save(this) },
        onRemove = { id -> PersonsRepo.remove(this, id) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PersonsRepo.load(this)

        rv = findViewById(R.id.rvPeople)
        fab = findViewById(R.id.fabAddPerson)
        tvCurrency = findViewById(R.id.tvCurrency)
        btnCurrency = findViewById(R.id.btnCurrency)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        adapter.submit(PersonsRepo.people)

        updateCurrencyLabel()

        fab.setOnClickListener {
            PersonsRepo.addPerson(this)
            adapter.submit(PersonsRepo.people)
        }

        btnCurrency.setOnClickListener { showCurrencyMenu(it) }
    }

    override fun onResume() {
        super.onResume()
        PersonsRepo.load(this)
        adapter.submit(PersonsRepo.people)
        updateCurrencyLabel()
    }

    private fun updateCurrencyLabel() {
        tvCurrency.text = "Валюта: ${PersonsRepo.currentCurrency.name}"
    }

    private fun showCurrencyMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "USD ($)")
        menu.menu.add(0, 2, 1, "JPY (¥)")
        menu.menu.add(0, 3, 2, "RUB (₽)")
        menu.setOnMenuItemClickListener { item ->
            val target = when (item.itemId) {
                1 -> Currency.USD
                2 -> Currency.JPY
                3 -> Currency.RUB
                else -> return@setOnMenuItemClickListener false
            }
            convertTo(target); true
        }
        menu.show()
    }

    private fun convertTo(target: Currency) {
        if (target == PersonsRepo.currentCurrency) return
        Toast.makeText(this, "Получаем курс…", Toast.LENGTH_SHORT).show()

        thread {
            try {
                val rates = CurrencyManagerInline.fetchUsdRates()
                val factor = CurrencyManagerInline.factor(
                    current = PersonsRepo.currentCurrency,
                    target = target,
                    usdRates = rates
                )
                PersonsRepo.convertAll(this, target, factor)

                Handler(Looper.getMainLooper()).post {
                    adapter.submit(PersonsRepo.people)
                    updateCurrencyLabel()
                    Toast.makeText(this, "Сконвертировано в ${target.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Ошибка курса: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/* ----------------------------- Adapter ----------------------------- */

private class PeopleAdapter(
    private val onOpenRaw: (Long) -> Unit,
    private val onChange: () -> Unit,
    private val onRemove: (Long) -> Unit
) : RecyclerView.Adapter<PeopleVH>() {

    private var items: List<Person> = emptyList()

    init { setHasStableIds(true) }

    fun submit(list: List<Person>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size
    override fun getItemId(position: Int): Long = items[position].id

    private var lastOpenAt = 0L
    private fun openSafe(id: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastOpenAt < 500) return
        lastOpenAt = now
        onOpenRaw(id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeopleVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
        return PeopleVH(v, ::openSafe, onChange, onRemove)
    }

    override fun onBindViewHolder(holder: PeopleVH, position: Int) {
        holder.bind(items[position])
    }
}

/* ----------------------------- ViewHolder ----------------------------- */

private class PeopleVH(
    v: View,
    private val openSafe: (Long) -> Unit,
    private val onChange: () -> Unit,
    private val onRemove: (Long) -> Unit
) : RecyclerView.ViewHolder(v) {

    private val etName: EditText = v.findViewById(R.id.etName)
    private val etBase: EditText = v.findViewById(R.id.etBaseTotal)
    private val tvRemaining: TextView = v.findViewById(R.id.tvRemaining)
    private val clickAway: View = v.findViewById(R.id.clickAway)
    private val btnRemove: ImageButton = v.findViewById(R.id.btnRemovePerson)

    private var current: Person? = null
    private var nameWatcher: TextWatcher? = null
    private var baseWatcher: TextWatcher? = null

    fun bind(p: Person) {
        current = p
        nameWatcher?.let { etName.removeTextChangedListener(it) }
        baseWatcher?.let { etBase.removeTextChangedListener(it) }

        etName.setText(p.name)
        etBase.setText(
            if (p.baseTotalCents == 0L) "" else
                when (PersonsRepo.currentCurrency) {
                    Currency.JPY -> (p.baseTotalCents).toString()
                    else -> "%.2f".format(p.baseTotalCents / 100.0)
                }
        )
        tvRemaining.text = "Осталось: ${centsToText(p.remainingCents(), PersonsRepo.currentCurrency)}"

        nameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                current?.let { it.name = s?.toString().orEmpty(); onChange() }
            }
        }.also { etName.addTextChangedListener(it) }

        baseWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                current?.let { person ->
                    val txt = s?.toString().orEmpty()
                    val cents = when (PersonsRepo.currentCurrency) {
                        Currency.JPY -> txt.filter { ch -> ch.isDigit() }.toLongOrNull() ?: 0L
                        else -> parseUsdToCents(txt)
                    }
                    person.baseTotalCents = cents
                    tvRemaining.text = "Осталось: ${centsToText(person.remainingCents(), PersonsRepo.currentCurrency)}"
                    onChange()
                }
            }
        }.also { etBase.addTextChangedListener(it) }

        itemView.setOnClickListener {
            if (!etName.isFocused && !etBase.isFocused) openSafe(p.id)
        }
        clickAway.setOnClickListener { openSafe(p.id) }

        btnRemove.setOnClickListener {
            AlertDialog.Builder(itemView.context)
                .setTitle("Удалить?")
                .setMessage("Удалить карточку «${if (p.name.isBlank()) "без имени" else p.name}»?")
                .setPositiveButton("Удалить") { _, _ ->
                    onRemove(p.id)
                    (itemView.parent as? RecyclerView)?.adapter?.notifyDataSetChanged()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }
}

/* ======================= CurrencyManager (встроили сюда) ======================= */

object CurrencyManagerInline {

    data class UsdRates(val usdToJpy: Double, val usdToRub: Double)

    fun fetchUsdRates(): UsdRates {
        // 1) exchangerate.host
        runCatching {
            val u = java.net.URL("https://api.exchangerate.host/latest?base=USD&symbols=JPY,RUB")
            val (jpy, rub) = getRatesFrom(u, "JPY", "RUB")
            if (jpy != null && rub != null) return UsdRates(jpy, rub)
        }

        // 2) fallback: open.er-api.com
        runCatching {
            val u = java.net.URL("https://open.er-api.com/v6/latest/USD")
            val (jpy, rub) = getRatesFrom(u, "JPY", "RUB", rootKey = "rates")
            if (jpy != null && rub != null) return UsdRates(jpy, rub)
        }

        throw RuntimeException("Не удалось получить курсы валют. Проверьте интернет.")
    }

    private fun getRatesFrom(
        url: java.net.URL,
        key1: String,
        key2: String,
        rootKey: String = "rates"
    ): Pair<Double?, Double?> {
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }

        conn.inputStream.use { input ->
            val text = java.io.BufferedReader(java.io.InputStreamReader(input)).readText()
            val json = org.json.JSONObject(text)
            if (!json.has(rootKey)) return null to null
            val rates = json.getJSONObject(rootKey)
            val v1 = rates.optDouble(key1, Double.NaN)
            val v2 = rates.optDouble(key2, Double.NaN)
            return (if (v1.isNaN()) null else v1) to (if (v2.isNaN()) null else v2)
        }
    }

    /** коэффициент пересчёта current -> target исходя из USD->(JPY,RUB) */
    fun factor(current: Currency, target: Currency, usdRates: UsdRates): Double {
        if (current == target) return 1.0

        // сколько целевых денежных единиц за 1 USD
        fun usdTo(c: Currency) = when (c) {
            Currency.USD -> 1.0
            Currency.JPY -> usdRates.usdToJpy   // JPY за 1 USD
            Currency.RUB -> usdRates.usdToRub   // RUB за 1 USD
        }

        // масштаб микроединиц хранения
        fun minor(c: Currency) = when (c) {
            Currency.USD, Currency.RUB -> 100.0 // центы/копейки
            Currency.JPY -> 1.0                 // у иены нет «центов»
        }

        // переводим микроединицы current -> микроединицы target
        val currToUsd = 1.0 / usdTo(current)        // major current -> USD major
        val usdToTarget = usdTo(target)             // USD major -> target major
        val scale = minor(target) / minor(current)  // учитываем разницу «центов»
        return currToUsd * usdToTarget * scale
    }
}