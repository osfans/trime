package com.osfans.trime.ime.symbol

import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.SizeUtils
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.SimpleItemOneBinding
import com.osfans.trime.databinding.SimpleItemRowBinding

class SimpleAdapter(private val theme: Theme, private val columnSize: Int) : RecyclerView.Adapter<SimpleAdapter.ViewHolder>() {
    private val mBeans: MutableList<SimpleKeyBean> = ArrayList()
    private val mBeansByRows: MutableList<List<SimpleKeyBean>> = ArrayList()
    val beans: List<SimpleKeyBean>
        get() = mBeans

    fun updateBeans(beans: List<SimpleKeyBean>?) {
        mBeans.clear()
        mBeans.addAll(beans!!)
        mBeansByRows.clear()
        var t = ArrayList<SimpleKeyBean>()
        for (i in mBeans.indices) {
            t.add(mBeans[i])
            if ((i + 1) % columnSize == 0) {
                mBeansByRows.add(t)
                t = ArrayList()
            }
        }
        if (t.isNotEmpty()) {
            mBeansByRows.add(t)
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return mBeansByRows.size
    }

    override fun getItemId(position: Int): Long {
        return position * 1000L
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = SimpleItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val size = SizeUtils.dp2px(theme.liquid.getFloat("single_width"))
        val p = ViewGroup.LayoutParams(size, size)
        val bindings: MutableList<SimpleItemOneBinding> = ArrayList()
        for (i in 0 until columnSize) {
            val view = SimpleItemOneBinding.inflate(LayoutInflater.from(parent.context), null, false)
            bindings.add(view)
            binding.wrapper.addView(view.root, p)
        }
        val holder = ViewHolder(binding, bindings)
        for (i in holder.simpleKeyTexts.indices) {
            holder.wrappers[i].tag = i
            val textView = holder.simpleKeyTexts[i]
            textView.setTextColor(theme.colors.getColor("key_text_color")!!)
            textView.typeface = FontManager.getTypeface("key_font")
            textView.gravity = Gravity.CENTER
            textView.ellipsize = TextUtils.TruncateAt.MARQUEE
            val labelTextSize = theme.style.getFloat("label_text_size")
            if (labelTextSize > 0) textView.textSize = labelTextSize
            textView.background =
                theme.colors.getDrawable(
                    "key_back_color",
                    "key_border",
                    "key_border_color",
                    "round_corner",
                    null,
                )
        }
        return holder
    }

    class ViewHolder(binding: SimpleItemRowBinding, views: List<SimpleItemOneBinding>) : RecyclerView.ViewHolder(binding.root) {
        var simpleKeyTexts: MutableList<TextView> = ArrayList()
        var wrappers: MutableList<ViewGroup> = ArrayList()

        init {
            for (i in views.indices) {
                simpleKeyTexts.add(views[i].root.getChildAt(0) as TextView)
                (views[i].root as ViewGroup).getChildAt(1).visibility = View.GONE
                wrappers.add(views[i].root)
            }
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val bean = mBeansByRows[position]
        for (i in holder.simpleKeyTexts.indices) {
            holder.simpleKeyTexts[i].text = ""
            if (i < bean.size) {
                holder.wrappers[i].visibility = View.VISIBLE
                holder.simpleKeyTexts[i].text = bean[i].getLabel()
            } else {
                holder.wrappers[i].visibility = View.INVISIBLE
            }
            if (listener != null) {
                holder.wrappers[i]
                    .setOnClickListener { view: View ->
                        if (view.tag != null) {
                            listener!!(position * columnSize + view.tag as Int)
                        }
                    }
            }
        }
    }

    /** 添加OnItemClickListener回调 */
    private var listener: ((Int) -> Unit)? = null

    fun setListener(listener: ((Int) -> Unit)?) {
        this.listener = listener
    }
}
