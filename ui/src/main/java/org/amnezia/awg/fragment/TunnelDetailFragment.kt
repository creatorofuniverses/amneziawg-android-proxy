/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.databinding.DataBindingUtil
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import org.amnezia.awg.config.BadConfigException
import org.amnezia.awg.config.Interface
import org.amnezia.awg.config.SplitTunnelSummary
import org.amnezia.awg.viewmodel.ConfigProxy
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import org.amnezia.awg.R
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.ObfuscationMode
import org.amnezia.awg.databinding.TunnelDetailFragmentBinding
import org.amnezia.awg.databinding.TunnelDetailPeerBinding
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.util.QuantityFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment that shows details about a specific tunnel.
 */
class TunnelDetailFragment : BaseFragment(), MenuProvider {
    private var binding: TunnelDetailFragmentBinding? = null
    private var lastState = Tunnel.State.TOGGLE
    private var timerActive = true
    private var totalInstalledApps: Int? = null

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return false
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.tunnel_detail, menu)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = TunnelDetailFragmentBinding.inflate(inflater, container, false)
        binding?.executePendingBindings()
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        val b = binding ?: return
        bindCollapsibleSection("interface", b.interfaceHeader, b.interfaceCard, b.interfaceChevron, defaultExpanded = true)
        bindCollapsibleSection("obfuscation", b.obfuscationHeader, b.obfuscationCard, b.obfuscationChevron, defaultExpanded = false)
        bindCollapsibleSection("peer", b.peerHeader, b.peerCard, b.peerChevron, defaultExpanded = true)
        // Tapping the summary card while off connects the tunnel ("Tap to connect").
        b.connectionSummaryCard.setOnClickListener { card ->
            if (b.tunnel?.state != Tunnel.State.UP) setTunnelState(card, true)
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        timerActive = true
        lifecycleScope.launch {
            while (timerActive) {
                updateStats()
                applySummaryState()
                updatePublicEndpoint()
                delay(1000)
            }
        }
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?) {
        val binding = binding ?: return
        binding.tunnel = newTunnel
        if (newTunnel == null) {
            binding.config = null
        } else {
            lifecycleScope.launch {
                try {
                    val config = newTunnel.getConfigAsync()
                    binding.config = config
                    populateObfuscation(config)
                    updateSplitTunnelSummary(config.getInterface(), fetchTotalInstalledApps())
                } catch (_: Throwable) {
                    binding.config = null
                }
            }
        }
        lastState = Tunnel.State.TOGGLE
        applySummaryState()
        lifecycleScope.launch { updateStats() }
    }

    override fun onStop() {
        timerActive = false
        super.onStop()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        binding ?: return
        binding!!.fragment = this
        onSelectedTunnelChanged(null, selectedTunnel)
        super.onViewStateRestored(savedInstanceState)
    }

    private fun bindCollapsibleSection(
        id: String, header: View, body: View, chevron: View, defaultExpanded: Boolean
    ) {
        val key = booleanPreferencesKey("detail_section_${id}_expanded")
        val store = org.amnezia.awg.Application.getPreferencesDataStore()
        header.setOnClickListener {
            val now = body.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(body.parent as ViewGroup, AutoTransition())
            applySection(body, chevron, now, animate = true)
            lifecycleScope.launch { store.edit { it[key] = now } }
        }
        lifecycleScope.launch {
            val expanded = store.data.map { it[key] ?: defaultExpanded }.first()
            applySection(body, chevron, expanded, animate = false)
        }
    }

    private fun applySection(body: View, chevron: View, expanded: Boolean, animate: Boolean) {
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        val target = if (expanded) 180f else 0f
        if (animate) chevron.animate().rotation(target).setDuration(200).start()
        else chevron.rotation = target
    }

    private fun populateObfuscation(config: org.amnezia.awg.config.Config) {
        val binding = binding ?: return
        val iface = config.`interface`
        // Protocol badge
        val badge = binding.protoBadge
        badge.text = when (ObfuscationMode.of(iface) ?: ObfuscationMode.WG) {
            ObfuscationMode.WG -> getString(R.string.proto_badge_wg)
            ObfuscationMode.AMNEZIA -> getString(R.string.proto_badge_amnezia)
            ObfuscationMode.PROXY -> getString(R.string.proto_badge_proxy)
        }
        // Param chips: name + value pairs that are actually set
        val chips = buildList {
            iface.junkPacketCount.ifPresent { add("Jc $it") }
            iface.junkPacketMinSize.ifPresent { add("Jmin $it") }
            iface.junkPacketMaxSize.ifPresent { add("Jmax $it") }
            iface.initPacketJunkSize.ifPresent { add("S1 $it") }
            iface.responsePacketJunkSize.ifPresent { add("S2 $it") }
            iface.cookieReplyPacketJunkSize.ifPresent { add("S3 $it") }
            iface.transportPacketJunkSize.ifPresent { add("S4 $it") }
            iface.initPacketMagicHeader.ifPresent { add("H1 $it") }
            iface.responsePacketMagicHeader.ifPresent { add("H2 $it") }
            iface.underloadPacketMagicHeader.ifPresent { add("H3 $it") }
            iface.transportPacketMagicHeader.ifPresent { add("H4 $it") }
            listOf(iface.specialJunkI1, iface.specialJunkI2, iface.specialJunkI3,
                   iface.specialJunkI4, iface.specialJunkI5).forEachIndexed { i, opt ->
                opt.ifPresent { add("I${i + 1}") }
            }
        }
        val group = binding.obfuscationChips
        group.removeAllViews()
        for (label in chips) {
            val tv = TextView(group.context).apply {
                text = label
                setBackgroundResource(R.drawable.bg_stat_chip)
                setTextAppearance(R.style.TextAppearance_Awg_Mono)
                val padH = resources.getDimensionPixelSize(R.dimen.space_sm)
                setPadding(padH, padH / 2, padH, padH / 2)
            }
            group.addView(tv)
        }
        binding.obfuscationSummary.text = resources.getString(R.string.detail_section_count, chips.size)
    }

    private suspend fun fetchTotalInstalledApps(): Int {
        totalInstalledApps?.let { return it }
        val pm = requireContext().packageManager
        val count = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackagesHoldingPermissions(
                    arrayOf(Manifest.permission.INTERNET),
                    PackageManager.PackageInfoFlags.of(0L)
                ).size
            } else {
                @Suppress("DEPRECATION")
                pm.getPackagesHoldingPermissions(arrayOf(Manifest.permission.INTERNET), 0).size
            }
        }
        totalInstalledApps = count
        return count
    }

    private fun updateSplitTunnelSummary(iface: Interface, totalApps: Int) {
        val inc = iface.includedApplications.size
        val exc = iface.excludedApplications.size
        binding?.applicationsSummary?.text = if (SplitTunnelSummary.isAllApps(inc, exc))
            getString(R.string.split_tunnel_all_apps)
        else
            getString(R.string.split_tunnel_summary, SplitTunnelSummary.routedCount(inc, exc, totalApps), totalApps)
    }

    /** Opens the split-tunnel picker and persists the new selection back to the tunnel config. */
    fun onApplicationsClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val config = binding?.config ?: return
        val iface = config.getInterface()
        var isExcluded = true
        var selectedApps = ArrayList(iface.excludedApplications)
        if (selectedApps.isEmpty()) {
            selectedApps = ArrayList(iface.includedApplications)
            if (selectedApps.isNotEmpty()) isExcluded = false
        }
        val fragment = AppListDialogFragment.newInstance(selectedApps, isExcluded)
        childFragmentManager.setFragmentResultListener(AppListDialogFragment.REQUEST_SELECTION, viewLifecycleOwner) { _, bundle ->
            val tunnel = binding?.tunnel ?: return@setFragmentResultListener
            val current = binding?.config ?: return@setFragmentResultListener
            val newSelections = bundle.getStringArray(AppListDialogFragment.KEY_SELECTED_APPS) ?: return@setFragmentResultListener
            val excluded = bundle.getBoolean(AppListDialogFragment.KEY_IS_EXCLUDED)
            val proxy = ConfigProxy(current)
            if (excluded) {
                proxy.`interface`.includedApplications.clear()
                proxy.`interface`.excludedApplications.apply { clear(); addAll(newSelections) }
            } else {
                proxy.`interface`.excludedApplications.clear()
                proxy.`interface`.includedApplications.apply { clear(); addAll(newSelections) }
            }
            lifecycleScope.launch {
                val newConfig = try {
                    proxy.resolve()
                } catch (e: BadConfigException) {
                    Log.w(TAG, "Failed to resolve split-tunnel config", e)
                    return@launch
                }
                try {
                    tunnel.setConfigAsync(newConfig)
                    binding?.config = newConfig
                    updateSplitTunnelSummary(newConfig.getInterface(), fetchTotalInstalledApps())
                } catch (e: Throwable) {
                    binding?.root?.let { Snackbar.make(it, ErrorMessages[e], Snackbar.LENGTH_LONG).show() }
                }
            }
        }
        fragment.show(childFragmentManager, null)
    }

    /**
     * Tints the summary card per connection status and, when disconnected, shows the
     * "Tap to connect" hint in place of the resolved server address. Connected/connecting
     * keep the teal-tinted treatment; the address itself is filled by [updatePublicEndpoint].
     */
    private fun applySummaryState() {
        val binding = binding ?: return
        val card = binding.connectionSummaryCard
        val connected = binding.tunnel?.connectionStatus != ObservableTunnel.ConnectionStatus.DISCONNECTED
        if (connected) {
            card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.awg_connected_fill))
            card.strokeColor = ContextCompat.getColor(card.context, R.color.awg_connected_stroke)
        } else {
            card.setCardBackgroundColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainer))
            card.strokeColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant)
            binding.publicEndpointText.text = getString(R.string.detail_tap_to_connect)
            binding.publicEndpointText.visibility = View.VISIBLE
        }
    }

    private suspend fun updatePublicEndpoint() {
        val binding = binding ?: return
        // Disconnected state owns the endpoint line ("Tap to connect"); don't overwrite it.
        if (binding.tunnel?.connectionStatus == ObservableTunnel.ConnectionStatus.DISCONNECTED) return
        val config = binding.config ?: return
        val peer = config.peers.firstOrNull() ?: return
        val text = withContext(Dispatchers.IO) {
            val configured = peer.endpoint
            configured.flatMap { it.resolved }
                .map { it.toString() }
                .orElseGet { configured.map { it.toString() }.orElse("") }
        }
        binding.publicEndpointText.text = text
        binding.publicEndpointText.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    private suspend fun updateStats() {
        val binding = binding ?: return
        val tunnel = binding.tunnel ?: return
        if (!isResumed) return
        val state = tunnel.state
        if (state != Tunnel.State.UP && lastState == state) return
        lastState = state
        try {
            val statistics = tunnel.getStatisticsAsync()
            binding.summaryDownload.text = QuantityFormatter.formatBytes(statistics.totalRx())
            binding.summaryUpload.text = QuantityFormatter.formatBytes(statistics.totalTx())
            val latestHandshake = statistics.peers()
                .map { statistics.peer(it)?.latestHandshakeEpochMillis ?: 0L }
                .maxOrNull() ?: 0L
            binding.summaryHandshake.text = QuantityFormatter.formatEpochAgoShort(latestHandshake)
            for (i in 0 until binding.peersLayout.childCount) {
                val peer: TunnelDetailPeerBinding = DataBindingUtil.getBinding(binding.peersLayout.getChildAt(i))
                    ?: continue
                val publicKey = peer.item!!.publicKey
                val peerStats = statistics.peer(publicKey)
                if (peerStats == null || (peerStats.rxBytes == 0L && peerStats.txBytes == 0L)) {
                    peer.transferLabel.visibility = View.GONE
                    peer.transferText.visibility = View.GONE
                } else {
                    peer.transferText.text = getString(
                        R.string.transfer_rx_tx,
                        QuantityFormatter.formatBytes(peerStats.rxBytes),
                        QuantityFormatter.formatBytes(peerStats.txBytes)
                    )
                    peer.transferLabel.visibility = View.VISIBLE
                    peer.transferText.visibility = View.VISIBLE
                }
                if (peerStats == null || peerStats.latestHandshakeEpochMillis == 0L) {
                    peer.latestHandshakeLabel.visibility = View.GONE
                    peer.latestHandshakeText.visibility = View.GONE
                } else {
                    peer.latestHandshakeText.text = QuantityFormatter.formatEpochAgoShort(peerStats.latestHandshakeEpochMillis)
                    peer.latestHandshakeLabel.visibility = View.VISIBLE
                    peer.latestHandshakeText.visibility = View.VISIBLE
                }
            }
        } catch (e: Throwable) {
            for (i in 0 until binding.peersLayout.childCount) {
                val peer: TunnelDetailPeerBinding = DataBindingUtil.getBinding(binding.peersLayout.getChildAt(i))
                    ?: continue
                peer.transferLabel.visibility = View.GONE
                peer.transferText.visibility = View.GONE
                peer.latestHandshakeLabel.visibility = View.GONE
                peer.latestHandshakeText.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val TAG = "AmneziaWG/TunnelDetailFragment"
    }
}
