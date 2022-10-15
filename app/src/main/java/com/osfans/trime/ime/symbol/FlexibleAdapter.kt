package com.osfans.trime.ime.symbol

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.data.Config
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.databinding.SimpleKeyItemBinding

abstract class FlexibleAdapter : RecyclerView.Adapter<FlexibleAdapter.ViewHolder>() {
    private val mBeans = mutableListOf<DatabaseBean>()
    // 映射条目的 id 和其在视图中位置的关系
    // 以应对增删条目时 id 和其位置的相对变化
    private val mBeansId = mutableMapOf<Int, Int>()
    val beans: List<DatabaseBean>
        get() = mBeans

    abstract val theme: Config

    fun getPositionById(id: Int) = mBeansId.getValue(id)

    fun getBeanById(id: Int) = beans[getPositionById(id)]

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

    inner class ViewHolder(val binding: SimpleKeyItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val simpleKeyText = binding.simpleKey
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        with(viewHolder) {
            val (id, text) = mBeans[position]
            simpleKeyText.apply {
                this.text = text
                this.typeface = theme.getFont("long_text_font")

                val longTextSize = theme.getFloat("key_long_text_size")
                val labelTextSize = theme.getFloat("label_text_size")
                if (longTextSize > 0)
                    this.textSize = longTextSize
                else if (labelTextSize > 0)
                    this.textSize = labelTextSize
            }

            // TODO 设置剪贴板列表样式
            // copy SimpleAdapter 会造成高度始终为 3 行无法自适应的效果。

            // if (background != null) viewHolder.itemView.setBackground(background);
            (itemView as CardView).background = theme.getDrawable(
                "long_text_back_color",
                "key_border",
                "key_long_text_border",
                "round_corner",
                null
            )

            // 如果设置了回调，则设置点击事件
            itemView.setOnClickListener {
                onPaste(id)
            }
            itemView.setOnLongClickListener {
                //  TODO 长按删除、编辑剪贴板
                //  当文本较长时，目前样式只缩略显示为 3 行，长按时 toast 消息可以预览全文，略有用处。
                ToastUtils.showShort(text)
                true
            }
            itemView.isClickable = true
        }
    }

    // 添加回调
    abstract fun onPaste(id: Int)
}
