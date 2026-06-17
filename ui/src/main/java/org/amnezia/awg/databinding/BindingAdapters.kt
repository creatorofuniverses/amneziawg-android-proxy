/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.databinding

import android.content.res.ColorStateList
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.databinding.ObservableList
import androidx.databinding.ViewDataBinding
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.databinding.adapters.ListenerUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.amnezia.awg.BR
import org.amnezia.awg.R
import org.amnezia.awg.backend.Statistics
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.databinding.ObservableKeyedRecyclerViewAdapter.RowConfigurationHandler
import org.amnezia.awg.widget.ToggleSwitch
import org.amnezia.awg.widget.ToggleSwitch.OnBeforeCheckedChangeListener
import org.amnezia.awg.widget.TvCardView
import org.amnezia.awg.config.Attribute
import org.amnezia.awg.config.InetNetwork
import java.net.InetAddress
import java.util.Optional

/**
 * Static methods for use by generated code in the Android data binding library.
 */
object BindingAdapters {
    @JvmStatic
    @BindingAdapter("checked")
    fun setChecked(view: ToggleSwitch, checked: Boolean) {
        view.setCheckedInternal(checked)
    }

    @JvmStatic
    @BindingAdapter("filter")
    fun setFilter(view: TextView, filter: InputFilter) {
        view.filters = arrayOf(filter)
    }

    @JvmStatic
    @BindingAdapter("items", "layout", "fragment")
    fun <E> setItems(
        view: LinearLayout,
        oldList: ObservableList<E>?, oldLayoutId: Int, @Suppress("UNUSED_PARAMETER") oldFragment: Fragment?,
        newList: ObservableList<E>?, newLayoutId: Int, newFragment: Fragment?
    ) {
        if (oldList === newList && oldLayoutId == newLayoutId)
            return
        var listener: ItemChangeListener<E>? = ListenerUtil.getListener(view, R.id.item_change_listener)
        // If the layout changes, any existing listener must be replaced.
        if (listener != null && oldList != null && oldLayoutId != newLayoutId) {
            listener.setList(null)
            listener = null
            // Stop tracking the old listener.
            ListenerUtil.trackListener<Any?>(view, null, R.id.item_change_listener)
        }
        // Avoid adding a listener when there is no new list or layout.
        if (newList == null || newLayoutId == 0)
            return
        if (listener == null) {
            listener = ItemChangeListener(view, newLayoutId, newFragment)
            ListenerUtil.trackListener(view, listener, R.id.item_change_listener)
        }
        // Either the list changed, or this is an entirely new listener because the layout changed.
        listener.setList(newList)
    }

    @JvmStatic
    @BindingAdapter("items", "layout")
    fun <E> setItems(
        view: LinearLayout,
        oldList: Iterable<E>?, oldLayoutId: Int,
        newList: Iterable<E>?, newLayoutId: Int
    ) {
        if (oldList === newList && oldLayoutId == newLayoutId)
            return
        view.removeAllViews()
        if (newList == null)
            return
        val layoutInflater = LayoutInflater.from(view.context)
        for (item in newList) {
            val binding = DataBindingUtil.inflate<ViewDataBinding>(layoutInflater, newLayoutId, view, false)
            binding.setVariable(BR.collection, newList)
            binding.setVariable(BR.item, item)
            binding.executePendingBindings()
            view.addView(binding.root)
        }
    }

    @JvmStatic
    @BindingAdapter(requireAll = false, value = ["items", "layout", "configurationHandler"])
    fun <K, E : Keyed<out K>> setItems(
        view: RecyclerView,
        oldList: ObservableKeyedArrayList<K, E>?, oldLayoutId: Int,
        @Suppress("UNUSED_PARAMETER") oldRowConfigurationHandler: RowConfigurationHandler<*, *>?,
        newList: ObservableKeyedArrayList<K, E>?, newLayoutId: Int,
        newRowConfigurationHandler: RowConfigurationHandler<*, *>?
    ) {
        if (view.layoutManager == null)
            view.layoutManager = LinearLayoutManager(view.context, RecyclerView.VERTICAL, false)
        if (oldList === newList && oldLayoutId == newLayoutId)
            return
        // The ListAdapter interface is not generic, so this cannot be checked.
        @Suppress("UNCHECKED_CAST") var adapter = view.adapter as? ObservableKeyedRecyclerViewAdapter<K, E>?
        // If the layout changes, any existing adapter must be replaced.
        if (adapter != null && oldList != null && oldLayoutId != newLayoutId) {
            adapter.setList(null)
            adapter = null
        }
        // Avoid setting an adapter when there is no new list or layout.
        if (newList == null || newLayoutId == 0)
            return
        if (adapter == null) {
            adapter = ObservableKeyedRecyclerViewAdapter(view.context, newLayoutId, newList)
            view.adapter = adapter
        }
        adapter.setRowConfigurationHandler(newRowConfigurationHandler)
        // Either the list changed, or this is an entirely new listener because the layout changed.
        adapter.setList(newList)
    }

    @JvmStatic
    @BindingAdapter("onBeforeCheckedChanged")
    fun setOnBeforeCheckedChanged(
        view: ToggleSwitch,
        listener: OnBeforeCheckedChangeListener?
    ) {
        view.setOnBeforeCheckedChangeListener(listener)
    }

    @JvmStatic
    @BindingAdapter("onFocusChange")
    fun setOnFocusChange(
        view: EditText,
        listener: View.OnFocusChangeListener?
    ) {
        view.onFocusChangeListener = listener
    }

    @JvmStatic
    @BindingAdapter("android:text")
    fun setOptionalText(view: TextView, text: Optional<*>?) {
        view.text = text?.map { it.toString() }?.orElse("") ?: ""
    }

    @JvmStatic
    @BindingAdapter("android:text")
    fun setInetNetworkSetText(view: TextView, networks: Iterable<InetNetwork?>?) {
        view.text = if (networks != null) Attribute.join(networks) else ""
    }

    @JvmStatic
    @BindingAdapter("android:text")
    fun setInetAddressSetText(view: TextView, addresses: Iterable<InetAddress?>?) {
        view.text = if (addresses != null) Attribute.join(addresses.map { it?.hostAddress }) else ""
    }

    @JvmStatic
    @BindingAdapter("android:text")
    fun setStringSetText(view: TextView, strings: Iterable<String?>?) {
        view.text = if (strings != null) Attribute.join(strings) else ""
    }

    /**
     * Two-way binding for a [MaterialAutoCompleteTextView] used as an exposed-dropdown picker.
     * Sets the text with filtering disabled ([MaterialAutoCompleteTextView.setText] with
     * filter = false) so the popup always lists every item instead of being filtered down to
     * the rows matching the currently selected value.
     */
    @JvmStatic
    @BindingAdapter("dropdownValue")
    fun setDropdownValue(view: MaterialAutoCompleteTextView, value: String?) {
        val v = value ?: ""
        if (v != view.text.toString()) view.setText(v, false)
    }

    @JvmStatic
    @InverseBindingAdapter(attribute = "dropdownValue", event = "dropdownValueAttrChanged")
    fun getDropdownValue(view: MaterialAutoCompleteTextView): String = view.text.toString()

    @JvmStatic
    @BindingAdapter("dropdownValueAttrChanged")
    fun setDropdownValueListener(view: MaterialAutoCompleteTextView, listener: InverseBindingListener?) {
        if (listener == null) return
        view.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = listener.onChange()
        })
    }

    @JvmStatic
    fun tryParseInt(s: String?): Int {
        if (s == null)
            return 0
        return try {
            Integer.parseInt(s)
        } catch (_: Throwable) {
            0
        }
    }

    @JvmStatic
    @BindingAdapter("backgroundTintColor")
    fun setBackgroundTintColor(view: View, color: Int) {
        view.backgroundTintList = ColorStateList.valueOf(color)
    }

    // Per-tunnel throughput samples (key -> rx, tx, elapsedRealtime ms) for rate deltas.
    private val statsSamples = HashMap<String, Triple<Long, Long, Long>>()
    private val byteUnits = arrayOf("B", "KB", "MB", "GB", "TB")

    private fun humanBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < byteUnits.lastIndex) { v /= 1024; i++ }
        return (if (v < 10 && i > 0) String.format("%.1f", v) else String.format("%.0f", v)) + " " + byteUnits[i]
    }

    private fun latestHandshakeEpochMillis(stats: Statistics?): Long {
        if (stats == null) return 0
        var max = 0L
        for (key in stats.peers()) {
            val p = stats.peer(key) ?: continue
            if (p.latestHandshakeEpochMillis() > max) max = p.latestHandshakeEpochMillis()
        }
        return max
    }

    /**
     * Render the connected-row throughput strip. Shown only while the tunnel is UP;
     * download/upload are rates derived from the delta since the previous sample, and
     * handshake is "time since latest handshake". Re-runs whenever statistics, state, or
     * name change — ObservableTunnel.statistics refreshes itself opportunistically (~1s).
     */
    @JvmStatic
    @BindingAdapter("statsValue", "statsState", "statsKey", requireAll = false)
    fun setTunnelStats(root: View, stats: Statistics?, state: Tunnel.State?, key: String?) {
        if (state != Tunnel.State.UP) {
            root.visibility = View.GONE
            if (key != null) statsSamples.remove(key)
            return
        }
        root.visibility = View.VISIBLE
        val ctx = root.context
        val rx = stats?.totalRx() ?: 0L
        val tx = stats?.totalTx() ?: 0L
        val now = SystemClock.elapsedRealtime()
        val prev = if (key != null) statsSamples.put(key, Triple(rx, tx, now)) else null
        var rxRate = 0L
        var txRate = 0L
        if (prev != null) {
            val dt = (now - prev.third).coerceAtLeast(1L)
            rxRate = (rx - prev.first).coerceAtLeast(0L) * 1000L / dt
            txRate = (tx - prev.second).coerceAtLeast(0L) * 1000L / dt
        }
        root.findViewById<TextView>(R.id.stat_download)?.text = ctx.getString(R.string.stat_rate_format, humanBytes(rxRate))
        root.findViewById<TextView>(R.id.stat_upload)?.text = ctx.getString(R.string.stat_rate_format, humanBytes(txRate))

        val handshakeText = root.findViewById<TextView>(R.id.stat_handshake)
        val epoch = latestHandshakeEpochMillis(stats)
        handshakeText?.text = if (epoch <= 0) {
            ctx.getString(R.string.stat_ago_never)
        } else {
            val secs = ((System.currentTimeMillis() - epoch) / 1000L).coerceAtLeast(0L)
            if (secs < 60) ctx.getString(R.string.stat_ago_seconds, secs.toInt())
            else ctx.getString(R.string.stat_ago_minutes, (secs / 60L).toInt())
        }
    }

    @JvmStatic
    @BindingAdapter("isUp")
    fun setIsUp(card: TvCardView, up: Boolean) {
        card.isUp = up
    }

    @JvmStatic
    @BindingAdapter("isDeleting")
    fun setIsDeleting(card: TvCardView, deleting: Boolean) {
        card.isDeleting = deleting
    }
}
