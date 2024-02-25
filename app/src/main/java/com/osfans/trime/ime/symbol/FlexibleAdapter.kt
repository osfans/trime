package com.osfans.trime.ime.symbol

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.SimpleKeyItemBinding
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.appContext
import kotlinx.coroutines.launch

class FlexibleAdapter(theme: Theme) : RecyclerView.Adapter<FlexibleAdapter.ViewHolder>() {
    private val mBeans = mutableListOf<DatabaseBean>()

    // 映射条目的 id 和其在视图中位置的关系
    // 以应对增删条目时 id 和其位置的相对变化
    // [<id, position>, ...]
    private val mBeansId = mutableMapOf<Int, Int>()
    val beans get() = mBeans

    fun updateBeans(beans: List<DatabaseBean>) {
        val sorted =
            beans.sortedWith { b1, b2 ->
                when {
                    // 如果 b1 置顶而 b2 没置顶，则 b1 比 b2 小，排前面
                    b1.pinned && !b2.pinned -> -1
                    // 如果 b1 没置顶而 b2 置顶，则 b1 比 b2 大，排后面
                    !b1.pinned && b2.pinned -> 1
                    // 如果都置顶了或都没置顶，则比较 id，id 大的排前面
                    else -> b2.id.compareTo(b1.id)
                }
            }
        val prevSize = mBeans.size
        mBeans.clear()
        notifyItemRangeRemoved(0, prevSize)
        mBeans.addAll(sorted)
        notifyItemRangeChanged(0, sorted.size)
        mBeansId.clear()
        mBeans.forEachIndexed { index: Int, (id): DatabaseBean ->
            mBeansId[id] = index
        }
    }

    override fun getItemCount(): Int = mBeans.size

    private val mTypeface = FontManager.getTypeface("long_text_font")
    private val mLongTextColor = ColorManager.getColor("long_text_color")
    private val mKeyTextColor = ColorManager.getColor("key_text_color")
    private val mKeyLongTextSize = theme.style.getFloat("key_long_text_size")
    private val mLabelTextSize = theme.style.getFloat("label_text_size")

    // 这里不用 get() 会导致快速滑动时背景填充错误
    private val mBackground
        get() =
            ColorManager.getDrawable(
                "long_text_back_color",
                "key_border",
                "key_long_text_border",
                "round_corner",
                null,
            )

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = SimpleKeyItemBinding.inflate(LayoutInflater.from(parent.context))
        val holder = ViewHolder(binding)
        holder.simpleKeyText.apply {
            typeface = mTypeface
            (mLongTextColor ?: mKeyTextColor)?.let { setTextColor(it) }
            (mKeyLongTextSize.takeIf { it > 0f } ?: mLabelTextSize.takeIf { it > 0f })
                ?.let { textSize = it }
        }
        return holder
    }

    inner class ViewHolder(binding: SimpleKeyItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val simpleKeyText = binding.simpleKey
        val simpleKeyPin = binding.simpleKeyPin
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        with(viewHolder) {
            val bean = mBeans[position]
            simpleKeyText.text = bean.text
            simpleKeyPin.visibility = if (bean.pinned) View.VISIBLE else View.INVISIBLE
            itemView.background = mBackground
            if (!this@FlexibleAdapter::listener.isInitialized) return
            // 如果设置了回调，则设置点击事件
            itemView.setOnClickListener {
                listener.onPaste(bean)
            }
            itemView.setOnLongClickListener {
                val menu = PopupMenu(it.context, it)
                val scope = it.findViewTreeLifecycleOwner()!!.lifecycleScope
                menu.menu.apply {
                    add(R.string.edit).apply {
                        setIcon(R.drawable.ic_baseline_edit_24)
                        setOnMenuItemClickListener {
                            scope.launch {
                                listener.onEdit(bean)
                            }
                            true
                        }
                    }
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
                                scope.launch { CollectionHelper.insert(DatabaseBean(text = bean.text)) }
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
                    if (beans.isNotEmpty()) {
                        add(R.string.delete_all).apply {
                            setIcon(R.drawable.ic_baseline_delete_sweep_24)
                            setOnMenuItemClickListener {
                                scope.launch {
                                    askToDeleteAll()
                                }
                                true
                            }
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

    private fun delete(id: Int) {
        val position = mBeansId.getValue(id)
        mBeans.removeAt(position)
        mBeansId.remove(id)
        for (i in position until mBeans.size) {
            mBeansId[mBeans[i].id] = i
        }
        notifyItemRemoved(position)
    }

    private fun setPinStatus(
        id: Int,
        pinned: Boolean,
    ) {
        val position = mBeansId.getValue(id)
        mBeans[position] = mBeans[position].copy(pinned = pinned)
        // 置顶会改变条目的排列顺序
        updateBeans(mBeans)
    }

    private fun askToDeleteAll() {
        val service = TrimeInputMethodService.getService()
        val askDialog =
            AlertDialog.Builder(
                appContext,
                androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog_Alert,
            ).setTitle(R.string.liquid_keyboard_ask_to_delete_all)
                .setPositiveButton(R.string.ok) { _, _ ->
                    service.lifecycleScope.launch {
                        listener.onDeleteAll()
                    }
                }.setNegativeButton(R.string.cancel) { _, _ ->
                }.create()
        service.inputView?.showDialog(askDialog)
    }

    // 添加回调
    interface Listener {
        fun onPaste(bean: DatabaseBean)

        suspend fun onPin(bean: DatabaseBean)

        suspend fun onUnpin(bean: DatabaseBean)

        suspend fun onDelete(bean: DatabaseBean)

        suspend fun onEdit(bean: DatabaseBean)

        suspend fun onDeleteAll()

        val showCollectButton: Boolean
    }

    private lateinit var listener: Listener

    fun setListener(listener: Listener) {
        this.listener = listener
    }
}
