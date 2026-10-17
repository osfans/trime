/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.T9Action
import com.osfans.trime.core.T9SpanProto
import com.osfans.trime.core.T9StateProto
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.candidates.BoundActionTextView
import com.osfans.trime.ime.candidates.CandidateIdentity
import com.osfans.trime.ime.candidates.CandidateItemUi
import com.osfans.trime.ime.candidates.VersionedClickBinding
import com.osfans.trime.ime.candidates.candidateRowHeight
import com.osfans.trime.ime.candidates.popup.LabeledCandidateItemUi
import com.osfans.trime.ime.candidates.popup.PaginationUi
import com.osfans.trime.ime.composition.PreeditTextView
import com.osfans.trime.ime.composition.T9DisambiguationView

/** Real Android View node/action checks; this does not run or certify TalkBack. */
internal object T13CandidateAccessibilityProbe {
    /** Captures the public event dispatch even if no accessibility service is enabled. */
    fun verifyRebindEvents(host: ViewGroup): Map<String, Any> {
        check(Looper.myLooper() == Looper.getMainLooper() && host.isAttachedToWindow && host.isShown)
        val events = mutableListOf<Int>()
        val view = object : BoundActionTextView(ContextThemeWrapper(host.context, R.style.Theme_TrimeAppTheme)) {
            override fun sendAccessibilityEvent(eventType: Int) {
                events += eventType
            }
        }
        // Constructor events are outside the binding invalidation being tested.
        events.clear()
        val binding = VersionedClickBinding<Int>(view, R.string.a11y_t9_choose_syllable)
        binding.bind(1) { }
        check(events.isEmpty()) { "Detached binding must not send an event" }
        host.addView(view, ViewGroup.LayoutParams(1, 1))
        try {
            events.clear()
            binding.bind(2) { }
            check(events == listOf(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
            binding.bind(2) { }
            check(events.size == 1) { "Identical binding must not repeatedly invalidate the node" }
            view.visibility = View.INVISIBLE
            events.clear()
            binding.bind(3) { }
            check(events.isEmpty()) { "Hidden binding must not send an event" }
        } finally {
            host.removeView(view)
        }
        return mapOf("passed" to true, "event_type" to AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, "checks" to listOf("detached silent", "changed attached binding invalidates node", "same binding silent", "hidden binding silent"), "service_event_delivery_tested" to false)
    }

    fun verify(host: ViewGroup, scope: ThemeScope): Map<String, Any> {
        check(Looper.myLooper() == Looper.getMainLooper() && host.isAttachedToWindow && host.isShown)
        val container = FrameLayout(host.context)
        val width = host.width.coerceAtLeast(320)
        val height = host.height.coerceAtLeast(320)
        host.addView(container, ViewGroup.LayoutParams(width, height))
        try {
            container.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            container.layout(0, 0, width, height)
            return verifyAttached(container, scope)
        } finally {
            host.removeView(container)
        }
    }

    private fun verifyAttached(host: ViewGroup, scope: ThemeScope): Map<String, Any> {
        check(Looper.myLooper() == Looper.getMainLooper())
        val context = host.context
        fun attach(view: View) {
            host.addView(view, ViewGroup.LayoutParams(host.width, ViewGroup.LayoutParams.WRAP_CONTENT))
            view.measure(View.MeasureSpec.makeMeasureSpec(host.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(host.height, View.MeasureSpec.AT_MOST))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            check(view.isAttachedToWindow && view.isShown) { "Accessibility fixture must belong to the actual window" }
        }
        val checks = mutableListOf<String>()
        fun verify(name: String, condition: Boolean, details: () -> String = { "" }) {
            check(condition) { "T13 candidate accessibility: $name ${details()}".trimEnd() }
            checks += name
        }
        val first = CandidateIdentity(CandidateSnapshot(7, 11, 19), 2)
        val replacement = first.copy(snapshot = first.snapshot.copy(generation = 12))
        val longText = "虚构候选完整长句，包含汉字、大小写 Android 16 和字符😀，末尾必须完整保留。"
        val candidate = CandidateItemUi(context, scope)
        attach(candidate.root)
        val clicks = mutableListOf<CandidateIdentity?>()
        var menus = 0
        candidate.root.setOnClickListener { clicks += candidate.root.identity }
        candidate.root.setOnLongClickListener {
            menus++
            true
        }
        candidate.root.identity = first
        candidate.update(CandidateProto(longText, "han yu", "1"), highlighted = true)
        node(candidate.root) {
            verify("candidate-button-selected-full-text", it.className == "android.widget.Button" && it.isSelected && it.contentDescription?.toString()?.contains(longText) == true)
            verify("candidate-click-and-menu-actions", it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_CLICK } && it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_LONG_CLICK })
        }
        verify("candidate-service-click-once", candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && clicks == listOf(first))
        verify("candidate-service-menu-once", candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null) && menus == 1)
        candidate.root.identity = replacement
        candidate.update(CandidateProto("替换候选", "", "1"), highlighted = false)
        verify("rebound-unqueried-node-refuses-action", !candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && clicks.size == 1)
        node(candidate.root) { verify("rebound-node-no-longer-selected", !it.isSelected && it.contentDescription == "替换候选") }
        verify("fresh-node-clicks-current-identity-once", candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && clicks == listOf(first, replacement))
        // Long-press routing was checked through its service action above;
        // remove the timer listener before the held-touch rebind check.
        candidate.root.setOnLongClickListener(null)
        val now = SystemClock.uptimeMillis()
        touch(candidate.root, now, MotionEvent.ACTION_DOWN)
        candidate.root.identity = first
        touch(candidate.root, now, MotionEvent.ACTION_UP)
        verify("held-touch-rebind-does-not-select", clicks.size == 2)
        candidate.root.identity = null
        node(candidate.root) { verify("placeholder-has-no-candidate-action", !it.isClickable && !it.isLongClickable) }
        verify("placeholder-refuses-service-action", !candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

        val popup = LabeledCandidateItemUi(context, scope)
        attach(popup.root)
        popup.root.identity = first
        popup.root.setOnClickListener { }
        popup.update(CandidateProto(longText, "han yu", "1"), highlighted = true)
        node(popup.root) { verify("popup-full-text-and-selected", it.contentDescription.toString().contains(longText) && it.isSelected) }

        val page = PaginationUi(context, scope)
        attach(page.root)
        var nextPages = 0
        val paged = Candidates.Paged(hasNextPage = true).apply { snapshot = first.snapshot }
        page.update(paged, { error("Disabled previous page clicked") }, { nextPages++ })
        fun pageDetails(view: View, info: AccessibilityNodeInfo): String = "view=${view.javaClass.name}, attached=${view.isAttachedToWindow}, shown=${view.isShown}, enabled=${view.isEnabled}, " +
            "delegate=${ViewCompat.getAccessibilityDelegate(view)?.javaClass?.name}, nodeClass=${info.className}, " +
            "nodeEnabled=${info.isEnabled}, nodeClickable=${info.isClickable}, description=${info.contentDescription}, " +
            "actions=${info.actionList.map { it.id to it.label?.toString() }}, snapshot=${paged.snapshot}, nextPages=$nextPages"
        verify("next-page-unqueried-refuses-action", !page.nextIcon.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && nextPages == 0)
        node(page.prevIcon) { info ->
            verify("previous-page-is-labelled-disabled", !info.isEnabled && !info.isClickable && !info.contentDescription.isNullOrEmpty()) { pageDetails(page.prevIcon, info) }
        }
        node(page.nextIcon) { info ->
            verify("next-page-is-labelled-button", info.isEnabled && info.className == "android.widget.Button" && !info.contentDescription.isNullOrEmpty()) { pageDetails(page.nextIcon, info) }
            verify("next-page-action-label-is-unique", info.actionList.count { it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label?.toString() == context.getString(R.string.a11y_next_candidates) } == 1) { pageDetails(page.nextIcon, info) }
        }
        verify("next-page-action-once", page.nextIcon.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && nextPages == 1)

        // Android's posted View click must not accidentally run a newly bound callback.
        val versioned = BoundActionTextView(ContextThemeWrapper(context, R.style.Theme_TrimeAppTheme))
        attach(versioned)
        val binding = VersionedClickBinding<Int>(versioned, R.string.a11y_t9_choose_syllable)
        val revisions = mutableListOf<Int>()
        binding.bind(31) { revisions += 31 }
        node(versioned) { }
        binding.bind(32) { revisions += 32 }
        verify("syllable-unqueried-revision-refuses-action", !versioned.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && revisions.isEmpty())
        node(versioned) { }
        verify("syllable-current-revision-action-once", versioned.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && revisions == listOf(32))
        versioned.layout(0, 0, 100, 100)
        touch(versioned, now, MotionEvent.ACTION_DOWN)
        binding.bind(33) { revisions += 33 }
        touch(versioned, now, MotionEvent.ACTION_UP)
        verify("syllable-held-touch-rebind-refuses-replacement", revisions == listOf(32))

        val t9Actions = mutableListOf<Pair<Int, T9Action>>()
        val t9ActionDetails = mutableListOf<Map<String, Any>>()
        val t9 = T9DisambiguationView(context, scope) { revision, action, start, end, spelling ->
            t9Actions += revision to action
            t9ActionDetails += mapOf("revision" to revision, "action" to action.name, "start" to start, "end" to end, "spelling" to spelling)
        }
        val span = T9SpanProto(0, 3, "han", completion = false, locked = true)
        t9.update(T9StateProto(enabled = true, revision = 41, input = "426", canUndo = true, segments = arrayOf(span), choices = arrayOf(span)))
        attach(t9)
        val width = (context.resources.displayMetrics.density * 360).toInt()
        t9.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST))
        t9.layout(0, 0, t9.measuredWidth, t9.measuredHeight)
        val choices = descendants(t9).filterIsInstance<RecyclerView>().single()
        val syllable = choices.children.filterIsInstance<TextView>().first()
        node(syllable) { verify("t9-choice-button-selected-full-spelling", it.className == "android.widget.Button" && it.isSelected && it.contentDescription.toString().contains("han")) }
        verify("t9-choice-action-original-revision", syllable.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9Actions == listOf(41 to T9Action.Lock))
        val undo = descendants(t9).first { it.contentDescription == context.getString(R.string.undo) }
        node(undo) { verify("t9-undo-button-enabled", it.className == "android.widget.Button" && it.isEnabled) }
        verify("t9-undo-original-revision", undo.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9Actions.last() == (41 to T9Action.Undo))
        t9.update(T9StateProto())
        node(undo) { verify("t9-disabled-undo-no-action", !it.isEnabled && !it.isClickable) }
        verify("t9-disabled-undo-refuses-action", !undo.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9Actions.size == 2)

        // The compact mode hides t9 itself; attach and lay out its separate sidebar.
        t9.update(T9StateProto(enabled = true, revision = 51, input = "426", choices = arrayOf(span)))
        t9.setKeyboardLayout(active = true, side = true, compactSidebar = true)
        attach(t9.sidebar)
        val sideChoices = descendants(t9.sidebar).filterIsInstance<RecyclerView>().single()
        val sideAdapter = checkNotNull(sideChoices.adapter)
        val changes = mutableListOf<String>()
        val observer = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                changes += "all"
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                changes += "insert:$positionStart:$itemCount"
            }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                changes += "remove:$positionStart:$itemCount"
            }
        }
        fun layoutSidebar() {
            t9.sidebar.measure(View.MeasureSpec.makeMeasureSpec(host.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(host.height, View.MeasureSpec.EXACTLY))
            t9.sidebar.layout(0, 0, t9.sidebar.measuredWidth, t9.sidebar.measuredHeight)
            check(t9.sidebar.isAttachedToWindow && t9.sidebar.isShown && sideChoices.isShown)
        }
        fun sideChoice(position: Int) = checkNotNull(sideChoices.findViewHolderForAdapterPosition(position)).itemView as TextView
        sideAdapter.registerAdapterDataObserver(observer)
        try {
            layoutSidebar()
            verify("t9-sidebar-initial-choice-without-header", sideAdapter.itemCount == 1 && sideAdapter.getItemViewType(0) == 0)
            node(sideChoice(0)) { verify("t9-sidebar-unlocked-choice-node", !it.isSelected) }
            verify("t9-sidebar-initial-revision-action", sideChoice(0).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9Actions.last() == (51 to T9Action.Lock) && t9Actions.size == 3)

            t9.update(T9StateProto(enabled = true, revision = 52, input = "426", segments = arrayOf(span), choices = arrayOf(span)))
            layoutSidebar()
            verify("t9-sidebar-header-inserted-with-choice-offset", sideAdapter.itemCount == 2 && sideAdapter.getItemViewType(0) == 1 && sideAdapter.getItemViewType(1) == 0 && "insert:0:1" in changes)
            verify("t9-sidebar-insertion-rebound-node-refuses-action", !sideChoice(1).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9Actions.size == 3)
            node(sideChoice(1)) { verify("t9-sidebar-inserted-header-selected-choice", it.isSelected && it.contentDescription.toString().contains("han")) }
            verify("t9-sidebar-inserted-header-current-revision", sideChoice(1).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9Actions.last() == (52 to T9Action.Lock) && t9Actions.size == 4)
            val originalHeaderHeight = checkNotNull(sideChoices.findViewHolderForAdapterPosition(0)).itemView.height

            // Header content/height and choice selection can change without a list insertion.
            val annotated = span.copy(locked = false, sources = 1)
            t9.update(T9StateProto(enabled = true, revision = 52, input = "426", segments = arrayOf(annotated), choices = arrayOf(span)))
            layoutSidebar()
            val headerView = checkNotNull(sideChoices.findViewHolderForAdapterPosition(0)).itemView
            val headerCaption = descendants(headerView).filterIsInstance<TextView>().single()
            val headerMetrics = headerCaption.paint.fontMetricsInt
            verify("t9-sidebar-header-content-and-height-rebound", headerView.height >= originalHeaderHeight && headerView.height >= 2 * (headerMetrics.bottom - headerMetrics.top) && headerCaption.maxLines == 2 && headerCaption.contentDescription.toString().contains("n/l"))
            node(sideChoice(1)) { verify("t9-sidebar-selection-updates-with-header-content", !it.isSelected) }

            t9.update(T9StateProto(enabled = true, revision = 53, input = "426", choices = arrayOf(span)))
            layoutSidebar()
            verify("t9-sidebar-header-removed-with-choice-offset", sideAdapter.itemCount == 1 && sideAdapter.getItemViewType(0) == 0 && "remove:0:1" in changes)
            verify("t9-sidebar-removal-rebound-node-refuses-action", !sideChoice(0).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9Actions.size == 4)
            node(sideChoice(0)) { verify("t9-sidebar-remaining-choice-node", !it.isSelected && it.contentDescription.toString().contains("han")) }
            verify("t9-sidebar-removed-header-current-revision", sideChoice(0).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9Actions.last() == (53 to T9Action.Lock) && t9Actions.size == 5)
            verify("t9-sidebar-never-invalidates-entire-list", "all" !in changes)
        } finally {
            sideAdapter.unregisterAdapterDataObserver(observer)
        }

        // Exercise RecyclerView's real pooling path, including its delegate cleanup.
        // This entire helper runs in one main-thread callback: no service can query
        // a freshly rebound node between the old query and the stale-action check.
        val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val managerEnabled = accessibility.isEnabled
        val touchExplorationEnabled = accessibility.isTouchExplorationEnabled
        val poolPuts = mutableListOf<Pair<Int, RecyclerView.ViewHolder>>()
        val poolGets = mutableListOf<Pair<Int, RecyclerView.ViewHolder>>()
        val pool = object : RecyclerView.RecycledViewPool() {
            override fun putRecycledView(scrap: RecyclerView.ViewHolder) {
                poolPuts += scrap.itemViewType to scrap
                super.putRecycledView(scrap)
            }

            override fun getRecycledView(viewType: Int): RecyclerView.ViewHolder? = super.getRecycledView(viewType).also { holder ->
                if (holder != null) poolGets += viewType to holder
            }
        }.apply {
            setMaxRecycledViews(0, 1)
            setMaxRecycledViews(1, 1)
        }
        val sideLayout = checkNotNull(sideChoices.layoutManager)
        sideLayout.isItemPrefetchEnabled = false
        sideChoices.setItemViewCacheSize(0)
        // RecyclerView has no public cache-size getter. Read only this fixture's
        // pinned AndroidX fields; neither platform state nor production is changed.
        val fixtureRecycler = RecyclerView::class.java.getDeclaredField("mRecycler").apply { isAccessible = true }.get(sideChoices)
        fun cacheSize(field: String) = RecyclerView.Recycler::class.java.getDeclaredField(field).apply { isAccessible = true }.getInt(fixtureRecycler)
        val cacheSettings = mapOf("requested" to cacheSize("mRequestedCacheMax"), "effective" to cacheSize("mViewCacheMax"))
        verify("t9-pool-prefetch-and-cache-disabled", !sideLayout.isItemPrefetchEnabled && cacheSettings.values.all { it == 0 }) {
            "prefetch=${sideLayout.isItemPrefetchEnabled}, cache=$cacheSettings"
        }
        sideChoices.setRecycledViewPool(pool)
        fun poolCounts() = mapOf("choice" to pool.getRecycledViewCount(0), "header" to pool.getRecycledViewCount(1))
        fun layoutFlags() = mapOf("pending" to sideChoices.hasPendingAdapterUpdates(), "computing" to sideChoices.isComputingLayout, "suppressed" to sideChoices.isLayoutSuppressed)
        val previousSpelling = T9SpanProto(0, 2, "mi", completion = false, locked = false)
        val nextSpelling = previousSpelling.copy(spelling = "ni", locked = true)
        t9.update(T9StateProto(enabled = true, revision = 61, input = "64", segments = arrayOf(previousSpelling), choices = arrayOf(previousSpelling)))
        layoutSidebar()
        verify("t9-pool-original-layout-settled", !sideChoices.hasPendingAdapterUpdates() && !sideChoices.isComputingLayout && !sideChoices.isLayoutSuppressed) { "pool=${poolCounts()}, flags=${layoutFlags()}" }
        val oldHolder = checkNotNull(sideChoices.findViewHolderForAdapterPosition(1)) { "Original choice missing: pool=${poolCounts()}, flags=${layoutFlags()}" }
        val oldChoice = oldHolder.itemView
        fun poolRows(rows: List<Pair<Int, RecyclerView.ViewHolder>>) = rows.map { (type, holder) ->
            mapOf("view_type" to type, "holder_identity" to System.identityHashCode(holder), "is_original_choice_holder" to (holder === oldHolder))
        }
        fun poolDetails(): String = "pool=${poolCounts()}, old_holder=${System.identityHashCode(oldHolder)}, current_holder=${sideChoices.findViewHolderForAdapterPosition(1)?.let { System.identityHashCode(it) }}, puts=${poolRows(poolPuts)}, gets=${poolRows(poolGets)}, flags=${layoutFlags()}, prefetch=${sideLayout.isItemPrefetchEnabled}, cache=$cacheSettings"
        node(oldChoice) {
            verify("t9-pool-original-node-published", it.className == "android.widget.Button" && it.isClickable && !it.isSelected && it.contentDescription == "mi")
        }
        val oldDelegate = ViewCompat.getAccessibilityDelegate(oldChoice)?.javaClass?.name ?: "<none>"
        verify("t9-pool-original-choice-still-attached", oldChoice.parent === sideChoices && oldChoice.isAttachedToWindow && oldChoice.isShown) { poolDetails() }
        val poolCountsBeforeClear = poolCounts()
        // Earlier han -> mi preparation can leave han in the one-slot pool.
        // Clear only pooled entries while the published mi holder stays attached.
        pool.clear()
        val poolCountsAfterClear = poolCounts()
        verify("t9-pool-real-pool-cleared-with-choice-attached", poolCountsAfterClear.values.all { it == 0 } && oldChoice.parent === sideChoices && oldChoice.isAttachedToWindow) {
            "before=$poolCountsBeforeClear, after=$poolCountsAfterClear, ${poolDetails()}"
        }
        poolPuts.clear()
        poolGets.clear()
        val actionsBeforePool = t9ActionDetails.size
        t9.update(T9StateProto(enabled = true, revision = 62, input = "64", segments = arrayOf(nextSpelling), choices = arrayOf(nextSpelling)))
        verify("t9-pool-pending-update-refuses-action", sideChoices.hasPendingAdapterUpdates() && !oldChoice.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9ActionDetails.size == actionsBeforePool) { poolDetails() }
        // swapAdapter(..., true) recycles all existing holders while keeping the
        // compatible pool. Merely scrolling away would not prove a pool round trip.
        sideChoices.swapAdapter(sideAdapter, true)
        layoutSidebar()
        verify("t9-pool-rebound-layout-settled", !sideChoices.hasPendingAdapterUpdates() && !sideChoices.isComputingLayout && !sideChoices.isLayoutSuppressed) { poolDetails() }
        val reboundHolder = checkNotNull(sideChoices.findViewHolderForAdapterPosition(1)) { "Rebound choice missing: ${poolDetails()}" }
        val reboundChoice = reboundHolder.itemView
        verify(
            "t9-pool-same-holder-put-and-get",
            reboundHolder === oldHolder && reboundChoice === oldChoice &&
                poolPuts.count { it.first == 0 && it.second === oldHolder } == 1 &&
                poolGets.count { it.first == 0 && it.second === oldHolder } == 1,
        ) { "before_clear=$poolCountsBeforeClear, after_clear=$poolCountsAfterClear, ${poolDetails()}" }
        // Do not create a new node before this assertion. Android actions themselves
        // carry no old snapshot once a service has legitimately queried the new node.
        verify("t9-pool-rebound-unqueried-node-refuses-action", !reboundChoice.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && t9ActionDetails.size == actionsBeforePool) { poolDetails() }
        var collection = emptyMap<String, Any>()
        node(reboundChoice) { info ->
            verify(
                "t9-pool-rebound-button-label-selected",
                info.className == "android.widget.Button" && info.isClickable && info.isSelected && info.contentDescription == "ni" &&
                    info.actionList.count { it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label?.toString() == context.getString(R.string.a11y_t9_choose_syllable) } == 1,
            )
            info.collectionItemInfo?.let {
                collection = mapOf("row_index" to it.rowIndex, "row_span" to it.rowSpan, "column_index" to it.columnIndex, "column_span" to it.columnSpan, "selected" to it.isSelected)
            }
        }
        val delegate = ViewCompat.getAccessibilityDelegate(reboundChoice)
        verify(
            "t9-pool-enabled-service-retains-collection",
            !managerEnabled ||
                (delegate === checkNotNull(sideChoices.compatAccessibilityDelegate).itemDelegate && collection.isNotEmpty()),
        )
        val expectedLock = mapOf("revision" to 62, "action" to T9Action.Lock.name, "start" to 0, "end" to 2, "spelling" to "ni")
        verify(
            "t9-pool-fresh-node-locks-exact-new-tuple-once",
            reboundChoice.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) &&
                t9ActionDetails.drop(actionsBeforePool) == listOf(expectedLock),
        )
        verify("t9-pool-service-state-stayed-observed", accessibility.isEnabled == managerEnabled && accessibility.isTouchExplorationEnabled == touchExplorationEnabled)
        val recycledChoice = mapOf(
            "manager_enabled" to managerEnabled,
            "touch_exploration_enabled" to touchExplorationEnabled,
            "manager_enabled_after" to accessibility.isEnabled,
            "touch_exploration_enabled_after" to accessibility.isTouchExplorationEnabled,
            "original_delegate_class" to oldDelegate,
            "rebound_delegate_class" to (delegate?.javaClass?.name ?: "<none>"),
            "collection_item" to collection,
            "prefetch_enabled" to sideLayout.isItemPrefetchEnabled,
            "cache_size_readback" to cacheSettings,
            "pool_counts_before_clear" to poolCountsBeforeClear,
            "pool_counts_after_clear" to poolCountsAfterClear,
            "pool_puts" to poolRows(poolPuts),
            "pool_gets" to poolRows(poolGets),
            "same_holder_reused" to (reboundHolder === oldHolder),
            "callbacks_after_reuse" to t9ActionDetails.drop(actionsBeforePool),
            "service_state_changed_by_fixture" to false,
            "native_engine_lock_claimed" to false,
        )

        val preedit = PreeditTextView(context)
        attach(preedit)
        val positions = mutableListOf<Pair<Int, String>>()
        preedit.onMoveCursor = { position, text -> positions += position to text }
        preedit.updateComposition("a汉😀b", 2)
        node(preedit) { verify("preedit-full-text-cursor-node", it.contentDescription.toString().contains("a汉😀b") && it.textSelectionStart == 2 && it.textSelectionEnd == 2) }
        val preeditDown = SystemClock.uptimeMillis()
        touch(preedit, preeditDown, MotionEvent.ACTION_DOWN)
        touch(preedit, preeditDown, MotionEvent.ACTION_CANCEL)
        verify("preedit-cancel-does-not-request-cursor-change", positions.isEmpty() && preedit.text.toString() == "a汉😀b")
        // Do not query another node: both actions must accept the unchanged published identity.
        verify("preedit-cancel-preserves-published-click", preedit.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && positions == listOf(4 to "a汉😀b"))
        positions.clear()
        val character = Bundle().apply { putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER) }
        verify("preedit-next-codepoint-native-byte-offset", preedit.performAccessibilityAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, character) && positions == listOf(8 to "a汉😀b"))
        val halfSurrogate = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 3)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 3)
        }
        verify("preedit-rejects-half-surrogate-selection", !preedit.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, halfSurrogate) && positions.size == 1)
        preedit.updateComposition("a汉😀b", 1)
        verify("preedit-unqueried-cursor-change-refuses-action", !preedit.performAccessibilityAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, character) && positions.size == 1)
        node(preedit) { verify("preedit-refreshed-cursor-node", it.textSelectionStart == 1 && it.textSelectionEnd == 1) }
        preedit.updateComposition("new", 0)
        verify("preedit-unqueried-change-refuses-action", !preedit.performAccessibilityAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, character) && positions.size == 1)
        preedit.clearTransientContent()
        preedit.updateComposition("", 0)
        node(preedit) { verify("cleared-preedit-has-no-old-description", it.contentDescription.isNullOrEmpty()) }
        verify("cleared-preedit-refuses-action", !preedit.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

        val normal = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1f })
        val enlarged = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 2f })
        val normalHeight = candidateRowHeight(normal, scope.theme)
        val enlargedHeight = candidateRowHeight(enlarged, scope.theme)
        verify("candidate-font-scale-does-not-reduce-row", enlargedHeight >= normalHeight)
        return mapOf("passed" to true, "checks" to checks, "normal_row_dp" to normalHeight, "font_scale_2_row_dp" to enlargedHeight, "recycled_choice" to recycledChoice, "touch_service_interleaving" to verifyTouchServiceInterleaving(host, scope), "actual_talkback_tested" to false)
    }

    /** Same-main-thread input interleaving on attached Views, not a TalkBack test. */
    private fun verifyTouchServiceInterleaving(host: ViewGroup, scope: ThemeScope): Map<String, Any> {
        val checks = mutableListOf<String>()
        fun verify(name: String, condition: Boolean, details: () -> String = { "" }) {
            check(condition) { "T13 touch/service interleaving: $name ${details()}".trimEnd() }
            checks += name
        }
        fun attach(view: View) {
            host.addView(view, ViewGroup.LayoutParams(host.width, 100))
            view.measure(View.MeasureSpec.makeMeasureSpec(host.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, host.width, 100)
            check(view.isAttachedToWindow && view.isShown && view.width > 0 && view.height > 0)
        }
        val candidate = CandidateItemUi(host.context, scope)
        val versioned = QueuedTouchTextView(host.context)
        attach(candidate.root)
        attach(versioned)
        val candidateClicks = mutableListOf<CandidateIdentity?>()
        val candidateReentry = mutableListOf<Boolean>()
        var menus = 0
        var inCandidateReentry = false
        val identity = CandidateIdentity(CandidateSnapshot(71, 73, 79), 3)
        val revisions = mutableListOf<Int>()
        val versionedReentry = mutableListOf<Boolean>()
        var inVersionedReentry = false
        try {
            candidate.root.identity = identity
            candidate.update(CandidateProto("虚构交错候选", "", "1"), highlighted = false)
            candidate.root.setOnClickListener {
                candidateClicks += candidate.root.identity
                if (!inCandidateReentry) {
                    inCandidateReentry = true
                    try {
                        candidateReentry += candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
                    } finally {
                        inCandidateReentry = false
                    }
                }
            }
            node(candidate.root) { verify("candidate-published-before-touch", it.isClickable) }
            var down = SystemClock.uptimeMillis()
            touch(candidate.root, down, MotionEvent.ACTION_DOWN)
            // Install only after DOWN: no long-press coroutine/timer is started.
            candidate.root.setOnLongClickListener {
                menus++
                true
            }
            verify("candidate-touch-refuses-service-click-and-long-click", !candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && !candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null) && candidateClicks.isEmpty() && menus == 0)
            touch(candidate.root, down, MotionEvent.ACTION_UP)
            verify("candidate-touch-up-calls-original-once-and-refuses-reentry", candidateClicks == listOf(identity) && candidateReentry == listOf(false) && menus == 0)
            candidate.root.setOnLongClickListener(null)
            down = SystemClock.uptimeMillis()
            touch(candidate.root, down, MotionEvent.ACTION_DOWN)
            touch(candidate.root, down, MotionEvent.ACTION_CANCEL)
            verify("candidate-cancel-restores-service-click-without-new-query", candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && candidateClicks == listOf(identity, identity) && candidateReentry == listOf(false, false))

            val binding = VersionedClickBinding<Int>(versioned, R.string.a11y_t9_choose_syllable)
            versioned.isLongClickable = false
            versioned.isFocusableInTouchMode = false
            fun bind(revision: Int?) {
                binding.bind(revision) {
                    revisions += checkNotNull(revision)
                    if (!inVersionedReentry) {
                        inVersionedReentry = true
                        try {
                            versionedReentry += versioned.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
                        } finally {
                            inVersionedReentry = false
                        }
                    }
                }
            }
            fun serviceClick() = versioned.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
            versioned.capturePosts = true
            bind(101)
            node(versioned) { verify("versioned-published-before-touch", it.isClickable) }
            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            verify("versioned-down-refuses-service-click", !serviceClick() && revisions.isEmpty())
            touch(versioned, down, MotionEvent.ACTION_UP)
            val queuedAfterUp = versioned.queuedNames()
            verify("versioned-up-queues-real-framework-click-and-unpress", queuedAfterUp.any { it.endsWith("\$PerformClick") } && queuedAfterUp.any { it.endsWith("\$UnsetPressedState") } && revisions.isEmpty()) { "queue=$queuedAfterUp" }
            verify("versioned-pending-up-refuses-service-click", !serviceClick() && revisions.isEmpty())
            versioned.onWindowFocusChanged(false)
            verify("versioned-focus-loss-clears-pressed-but-not-pending-action", !versioned.isPressed && !serviceClick() && revisions.isEmpty())
            versioned.drain()
            verify("versioned-posted-click-original-once-and-refuses-reentry", revisions == listOf(101) && versionedReentry == listOf(false))
            versioned.onWindowFocusChanged(true)
            verify("versioned-drained-click-restores-service-without-new-query", serviceClick() && revisions == listOf(101, 101) && versionedReentry == listOf(false, false))

            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            touch(versioned, down, MotionEvent.ACTION_CANCEL)
            versioned.drain()
            verify("versioned-cancel-restores-service-click", serviceClick() && revisions == listOf(101, 101, 101) && versionedReentry.all { !it })

            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            val outside = -(versioned.width + 100).toFloat()
            touch(versioned, down, MotionEvent.ACTION_MOVE, outside, outside)
            touch(versioned, down, MotionEvent.ACTION_UP, outside, outside)
            verify("versioned-move-out-up-has-no-framework-click", versioned.queuedNames().none { it.endsWith("\$PerformClick") } && !serviceClick() && revisions.size == 3)
            versioned.drain()
            verify("versioned-no-click-up-cleanup-restores-service", revisions.size == 3 && serviceClick() && revisions == listOf(101, 101, 101, 101))

            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            touch(versioned, down, MotionEvent.ACTION_UP)
            val overlappingDown = SystemClock.uptimeMillis()
            touch(versioned, overlappingDown, MotionEvent.ACTION_DOWN)
            bind(102)
            node(versioned) { verify("versioned-overlap-replacement-published", it.isClickable) }
            verify("versioned-overlap-refuses-service-while-old-up-pending", !serviceClick())
            touch(versioned, overlappingDown, MotionEvent.ACTION_UP)
            touch(versioned, overlappingDown, MotionEvent.ACTION_CANCEL)
            verify("versioned-overlap-cancel-retains-old-up-guard", !serviceClick() && revisions.size == 4)
            versioned.drain()
            verify("versioned-old-queued-click-never-invokes-new-binding", revisions == listOf(101, 101, 101, 101))
            verify("versioned-overlap-cleanup-restores-current-service", serviceClick() && revisions == listOf(101, 101, 101, 101, 102))

            bind(null)
            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            bind(103)
            node(versioned) { verify("versioned-null-down-new-binding-published", it.isClickable) }
            verify("versioned-null-down-still-blocks-new-service-action", !serviceClick() && revisions.size == 5)
            touch(versioned, down, MotionEvent.ACTION_UP)
            versioned.drain()
            verify("versioned-null-down-up-never-invokes-new-binding", revisions.size == 5)
            verify("versioned-null-down-finish-restores-current-service", serviceClick() && revisions == listOf(101, 101, 101, 101, 102, 103))
            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            touch(versioned, down, MotionEvent.ACTION_UP)
            versioned.drain()
            verify("versioned-new-normal-touch-restored-once", revisions == listOf(101, 101, 101, 101, 102, 103, 103))
            verify("versioned-all-callback-reentry-refused", versionedReentry == List(revisions.size) { false })

            candidate.root.isEnabled = false
            down = SystemClock.uptimeMillis()
            touch(candidate.root, down, MotionEvent.ACTION_DOWN)
            verify("candidate-disabled-down-refuses-service", !candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && candidateClicks.size == 2)
            candidate.root.isEnabled = true
            node(candidate.root) { }
            verify("candidate-disabled-down-does-not-block-reenabled-service", candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && candidateClicks.size == 3)
            down = SystemClock.uptimeMillis()
            touch(candidate.root, down, MotionEvent.ACTION_DOWN)
            candidate.root.isEnabled = false
            candidate.root.isEnabled = true
            node(candidate.root) { }
            verify("candidate-disable-during-touch-restores-service", candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && candidateClicks.size == 4)
            down = SystemClock.uptimeMillis()
            touch(candidate.root, down, MotionEvent.ACTION_DOWN)
            host.removeView(candidate.root)
            verify("candidate-touch-actually-detached", !candidate.root.isAttachedToWindow)
            attach(candidate.root)
            node(candidate.root) { }
            verify("candidate-reattach-restores-service-without-old-touch", candidate.root.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && candidateClicks == List(5) { identity } && candidateReentry == List(5) { false })

            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            versioned.isEnabled = false
            bind(104)
            node(versioned) { verify("versioned-disabled-bind-has-no-service-action", !it.isClickable && !serviceClick()) }
            versioned.isEnabled = true
            node(versioned) { }
            versioned.drain()
            verify("versioned-disable-bind-cancels-active-touch", revisions.size == 7 && serviceClick() && revisions.last() == 104 && revisions.size == 8)
            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            touch(versioned, down, MotionEvent.ACTION_UP)
            versioned.isEnabled = false
            bind(105)
            verify("versioned-disabled-bind-removes-framework-posted-click", versioned.queuedNames().none { it.endsWith("\$PerformClick") })
            versioned.isEnabled = true
            node(versioned) { }
            versioned.drain()
            verify("versioned-disable-bind-never-runs-old-up-on-new-binding", revisions.size == 8 && serviceClick() && revisions.last() == 105 && revisions.size == 9)
            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            host.removeView(versioned)
            verify("versioned-active-touch-actually-detached", !versioned.isAttachedToWindow)
            attach(versioned)
            node(versioned) { }
            versioned.drain()
            verify("versioned-reattach-restores-service-without-old-touch", revisions.size == 9 && serviceClick() && revisions.last() == 105 && revisions.size == 10)
            down = SystemClock.uptimeMillis()
            touch(versioned, down, MotionEvent.ACTION_DOWN)
            touch(versioned, down, MotionEvent.ACTION_UP)
            host.removeView(versioned)
            verify("versioned-pending-up-detach-removes-framework-click", !versioned.isAttachedToWindow && versioned.queuedNames().none { it.endsWith("\$PerformClick") })
            bind(106)
            attach(versioned)
            node(versioned) { }
            versioned.drain()
            verify("versioned-pending-up-reattach-never-invokes-replacement", revisions.size == 10 && serviceClick() && revisions.last() == 106 && revisions.size == 11)
            verify("versioned-lifecycle-callback-reentry-stays-refused", versionedReentry == List(revisions.size) { false })
            return mapOf(
                "passed" to true,
                "checks" to checks,
                "candidate_callback_identities" to candidateClicks.map { it.toString() },
                "candidate_reentry_results" to candidateReentry,
                "candidate_menu_callbacks" to menus,
                "versioned_callback_revisions" to revisions,
                "versioned_reentry_results" to versionedReentry,
                "framework_up_queued_classes" to queuedAfterUp,
                "queued_runnables" to versioned.history,
                "delayed_runnables_run_by_fixture_fifo" to true,
                "real_timing_or_talkback_claimed" to false,
            )
        } finally {
            versioned.discardQueued()
            host.removeView(versioned)
            host.removeView(candidate.root)
        }
    }

    /** Runs only this fixture's real View callbacks, without advancing global UI tasks. */
    private class QueuedTouchTextView(context: Context) : BoundActionTextView(ContextThemeWrapper(context, R.style.Theme_TrimeAppTheme)) {
        private data class Posted(val action: Runnable, val delayMillis: Long)
        private val queue = mutableListOf<Posted>()
        val history = mutableListOf<Map<String, Any>>()
        var capturePosts = false

        override fun post(action: Runnable): Boolean = if (capturePosts) enqueue(action, 0) else super.post(action)

        override fun postDelayed(action: Runnable, delayMillis: Long): Boolean = if (capturePosts) enqueue(action, delayMillis) else super.postDelayed(action, delayMillis)

        override fun removeCallbacks(action: Runnable): Boolean {
            val removed = if (capturePosts) queue.removeAll { it.action === action } else false
            return super.removeCallbacks(action) || removed
        }

        private fun enqueue(action: Runnable, delayMillis: Long): Boolean {
            check(queue.size < 32 && history.size < 128) { "Touch fixture callback queue exceeded its bound" }
            queue += Posted(action, delayMillis)
            history += mapOf("event" to "post", "class" to action.javaClass.name, "delay_ms" to delayMillis)
            return true
        }

        fun queuedNames() = queue.map { it.action.javaClass.name }

        fun drain() {
            var count = 0
            while (queue.isNotEmpty()) {
                check(++count <= 32) { "Touch fixture callback drain exceeded its bound: ${queuedNames()}" }
                val next = queue.removeAt(0)
                history += mapOf("event" to "run", "class" to next.action.javaClass.name, "delay_ms" to next.delayMillis)
                next.action.run()
            }
        }

        fun discardQueued() {
            queue.clear()
            capturePosts = false
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
    }

    @Suppress("DEPRECATION") // Node recycling is required on the retained API 21 platform.
    private inline fun node(view: View, check: (AccessibilityNodeInfo) -> Unit) {
        val info = view.createAccessibilityNodeInfo()
        try {
            check(info)
        } finally {
            info.recycle()
        }
    }

    private fun touch(view: View, downTime: Long, action: Int, x: Float = 2f, y: Float = 2f) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
