class MainActivity : AppCompatActivity() {
    ...
    private val adapter = PeopleAdapter { personId -> 
        startActivity(Intent(this, ExpensesActivity::class.java).putExtra("personId", personId))
    }
    ...
}

private class PeopleVH(
    v: View,
    private val onOpen: (Long) -> Unit
) : RecyclerView.ViewHolder(v) {

    private val btnRemove: ImageButton = v.findViewById(R.id.btnRemovePerson)

    fun bind(p: Person) {
        ...
        btnRemove.setOnClickListener {
            AlertDialog.Builder(itemView.context)
                .setTitle("Удалить?")
                .setMessage("Удалить карточку «${if (p.name.isBlank()) "без имени" else p.name}»?")
                .setPositiveButton("Удалить") { _, _ ->
                    PersonsRepo.people.removeAll { it.id == p.id }
                    (itemView.parent as? RecyclerView)?.adapter?.notifyDataSetChanged()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }
}
