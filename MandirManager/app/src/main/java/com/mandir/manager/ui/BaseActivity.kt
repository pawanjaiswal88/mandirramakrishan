package com.mandir.manager.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mandir.manager.data.AppDatabase
import com.mandir.manager.databinding.ItemRowBinding
import com.mandir.manager.util.Prefs

open class BaseActivity : AppCompatActivity() {
    protected val db: AppDatabase by lazy { AppDatabase.get(this) }
    protected val prefs: Prefs by lazy { Prefs(this) }

    protected fun enableUp() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

/** One list-row adapter reused for donations, members and expenses. */
class RowAdapter<T : Any>(
    idOf: (T) -> Long,
    private val bind: (T, ItemRowBinding) -> Unit,
    private val onClick: (T) -> Unit
) : ListAdapter<T, RowAdapter.VH>(Diff(idOf)) {

    class VH(val b: ItemRowBinding) : RecyclerView.ViewHolder(b.root)

    class Diff<T : Any>(val idOf: (T) -> Long) : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T) = idOf(oldItem) == idOf(newItem)
        @android.annotation.SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: T, newItem: T) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        bind(item, holder.b)
        holder.b.root.setOnClickListener { onClick(item) }
    }
}
