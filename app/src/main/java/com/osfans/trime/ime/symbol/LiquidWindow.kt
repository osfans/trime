// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

@InputScope
@Inject
class LiquidWindow(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val theme: Theme,
    private val windowManager: BoardWindowManager,
    lazyCommonKeyboardActionListener: Lazy<CommonKeyboardActionListener>,
) : BoardWindow.BarBoardWindow(),
    ResidentWindow,
    ClipboardHelper.OnClipboardUpdateListener {
    override val showTitle = false

    private val commonKeyboardActionListener by lazyCommonKeyboardActionListener
    private lateinit var liquidLayout: LiquidLayout
    private val symbolHistory = SymbolHistory(180)
    var currentDataType: LiquidData.Type = LiquidData.Type.SINGLE
        private set

    private val mainAdapter by lazy {
        LiquidAdapter(theme) {
            when (currentDataType) {
                LiquidData.Type.SYMBOL -> triggerSymbolInput(this.text)
                LiquidData.Type.TABS -> {
                    val realPosition = LiquidData.getTagList()
                        .indexOfFirst { it.label == this.text }
                    setDataByIndex(realPosition)
                }
                else -> {
                    service.commitText(this.text)
                    if (currentDataType != LiquidData.Type.HISTORY) {
                        symbolHistory.insert(this.text)
                        symbolHistory.save()
                    }
                }
            }
        }
    }

    private val dbAdapter by lazy {
        DbAdapter(service, theme, this)
    }

    private val mainLayoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.SPACE_AROUND
            alignItems = AlignItems.BASELINE
        }
    }

    private val dbLayoutManager by lazy {
        StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidWindow

    override fun onCreateView(): View = LiquidLayout(context, theme, commonKeyboardActionListener).apply {
        liquidLayout = this
        tabsUi.apply {
            setTags(LiquidData.getTagList())
            setOnTabClickListener { i ->
                setDataByIndex(i)
            }
        }
        liquidView.apply {
            layoutManager = mainLayoutManager
            adapter = mainAdapter
        }
        dbView.apply {
            layoutManager = dbLayoutManager
            adapter = dbAdapter
        }
    }

    override fun onCreateBarView() = liquidLayout.tabsUi.root

    override fun onAttached() {
        // 注册剪贴板更新监听器
        ClipboardHelper.addOnUpdateListener(this)
    }

    override fun onDetached() {
        ClipboardHelper.removeOnUpdateListener(this)
    }

    fun setDataByIndex(i: Int) {
        val tag = LiquidData.getTagList()[i]
        currentDataType = tag.type
        liquidLayout.tabsUi.activateTab(i)
        when (tag.type) {
            LiquidData.Type.CLIPBOARD -> submitDbData { ClipboardHelper.getAll() }
            LiquidData.Type.COLLECTION -> submitDbData { CollectionHelper.getAll() }
            LiquidData.Type.DRAFT -> submitDbData { DraftHelper.getAll() }
            LiquidData.Type.HISTORY -> {
                symbolHistory.load()
                submitData(symbolHistory.toOrderedList().map { LiquidKeyboard.KeyItem(it) })
            }
            else -> {
                val data = LiquidData.getDataByIndex(i)
                submitData(data)
            }
        }
    }

    private fun submitData(data: List<LiquidKeyboard.KeyItem>) {
        liquidLayout.updateState(LiquidLayout.State.Main)
        mainAdapter.submitList(data)
    }

    private fun submitDbData(data: suspend () -> List<DatabaseBean>) {
        liquidLayout.updateState(LiquidLayout.State.Database)
        service.lifecycleScope.launch {
            dbAdapter.submitList(data())
        }
    }

    private fun triggerSymbolInput(symbol: String) {
        commonKeyboardActionListener.listener.onPress(KeyEvent.KEYCODE_UNKNOWN)
        rime.launchOnReady {
            val (isAsciiMode, isAsciiPunch) = it.statusCached.run { isAsciiMode to isAsciiPunch }
            if (isAsciiMode) it.setRuntimeOption("ascii_mode", false)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", false)
            commonKeyboardActionListener.listener.onText("{Escape}$symbol")
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", true)
            ContextCompat.getMainExecutor(service).execute {
                windowManager.attachWindow(KeyboardWindow)
            }
        }
    }

    /**
     * 实现 OnClipboardUpdateListener 中的 onUpdate
     * 当剪贴板内容变化且剪贴板视图处于开启状态时，更新视图.
     */
    override fun onUpdate(bean: DatabaseBean) {
        if (currentDataType != LiquidData.Type.CLIPBOARD) return
        submitDbData { ClipboardHelper.getAll() }
    }
}
