package com.osfans.trime.ime.symbol

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.Config
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.databinding.SimpleKeyItemBinding
import kotlinx.coroutines.launch

class FlexibleAdapter(
    private val theme: Config
) : RecyclerView.Adapter<FlexibleAdapter.ViewHolder>() {
    private val mBeans = mutableListOf<DatabaseBean>()
    // 映射条目的 id 和其在视图中位置的关系
    // 以应对增删条目时 id 和其位置的相对变化
    private val mBeansId = mutableMapOf<Int, Int>()
    val beans: List<DatabaseBean>
        get() = mBeans

    fun updateBeans(beans: List<DatabaseBean>) {
        val sorted = beans.sortedByDescending { it.id }
        mBeans.clear()
        mBeans.addAll(sorted)
        mBeansId.clear()
        mBeans.forEachIndexed { index: Int, (id): DatabaseBean ->
            mBeansId[id] = index
        }
    }

    override fun getItemCount(): Int = mBeans.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = SimpleKeyItemBinding.inflate(LayoutInflater.from(parent.context))
        return ViewHolder(binding)
    }

    inner class ViewHolder(binding: SimpleKeyItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val simpleKeyText = binding.simpleKey
        val simpleKeyPin = binding.simpleKeyPin
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        with(viewHolder) {
            val bean = mBeans[position]
            simpleKeyText.apply {
                this.text = bean.text
                this.typeface = theme.getFont("long_text_font")

                val longTextSize = theme.getFloat("key_long_text_size")
                val labelTextSize = theme.getFloat("label_text_size")
                if (longTextSize > 0)
                    this.textSize = longTextSize
                else if (labelTextSize > 0)
                    this.textSize = labelTextSize
            }
            simpleKeyPin.visibility = if (bean.pinned) View.VISIBLE else View.INVISIBLE

            // if (background != null) viewHolder.itemView.setBackground(background);
            (itemView as CardView).background = theme.getDrawable(
                "long_text_back_color",
                "key_border",
                "key_long_text_border",
                "round_corner",
                null
            )

            // 如果设置了回调，则设置点击事件
            if (this@FlexibleAdapter::listener.isInitialized) {
                itemView.setOnClickListener {
                    listener.onPaste(bean)
                }
                itemView.setOnLongClickListener {
                    val menu = PopupMenu(it.context, it)
                    val scope = it.findViewTreeLifecycleOwner()!!.lifecycleScope
                    menu.menu.apply {
                        if (bean.pinned) {
                            add(R.string.simple_key_unpin).apply {
                                setIcon(R.drawable.ic_outline_push_pin_24)
                                setOnMenuItemClickListener {
                                    scope.launch {
                                        listener.onUnpin(bean)
                                        setPinStatus(bean.id, false)
                                    }
                                    true
                                }
                            }
                        } else {
                            add(R.string.simple_key_pin).apply {
                                setIcon(R.drawable.ic_baseline_push_pin_24)
                                setOnMenuItemClickListener {
                                    scope.launch {
                                        listener.onPin(bean)
                                        setPinStatus(bean.id, true)
                                    }
                                    true
                                }
                            }
                        }
                        if (listener.showCollectButton) {
                            add(R.string.collect).apply {
                                setIcon(R.drawable.ic_baseline_star_24)
                                setOnMenuItemClickListener {
                                    scope.launch { CollectionHelper.insert(bean.copy(text = bean.text)) }
                                    true
                                }
                            }
                        }
                        add(R.string.delete).apply {
                            setIcon(R.drawable.ic_baseline_delete_24)
                            setOnMenuItemClickListener {
                                scope.launch {
                                    listener.onDelete(bean)
                                    delete(bean.id)
                                }
                                true
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        menu.setForceShowIcon(true)
                    }
                    menu.show()
                    true
                }
            }
        }
    }

    private fun delete(id: Int) {
        val position = mBeansId.getValue(id)
        mBeans.removeAt(position)
        mBeansId.remove(id)
        for (i in position until mBeans.size) {
            mBeansId[mBeans[i].id] = i
        }
        notifyItemRemoved(position)
    }

    private fun setPinStatus(id: Int, pinned: Boolean) {
        val position = mBeansId.getValue(id)
        mBeans[position] = mBeans[position].copy(pinned = pinned)
        notifyItemChanged(position)
        // 置顶会改变条目的排列顺序
        updateBeans(mBeans)
    }

    // 添加回调
    interface Listener {
        fun onPaste(bean: DatabaseBean)
        suspend fun onPin(bean: DatabaseBean)
        suspend fun onUnpin(bean: DatabaseBean)
        suspend fun onDelete(bean: DatabaseBean)

        val showCollectButton: Boolean
    }

    private lateinit var listener: Listener

    fun setListener(listener: Listener) {
        this.listener = listener
    }
}
