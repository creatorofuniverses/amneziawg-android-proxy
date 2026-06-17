/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.databinding.DataBindingUtil
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
        bindCollapsibleSection("interface", b.interfaceHeader, b.interfaceBody, b.interfaceChevron, defaultExpanded = true)
        bindCollapsibleSection("obfuscation", b.obfuscationHeader, b.obfuscationBody, b.obfuscationChevron, defaultExpanded = false)
        bindCollapsibleSection("peer", b.peerHeader, b.peerBody, b.peerChevron, defaultExpanded = true)
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
                } catch (_: Throwable) {
                    binding.config = null
                }
            }
        }
        lastState = Tunnel.State.TOGGLE
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

    private suspend fun updatePublicEndpoint() {
        val binding = binding ?: return
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
            binding.summaryTransfer.text = getString(
                R.string.transfer_rx_tx,
                QuantityFormatter.formatBytes(statistics.totalRx()),
                QuantityFormatter.formatBytes(statistics.totalTx())
            )
            val latestHandshake = statistics.peers()
                .map { statistics.peer(it)?.latestHandshakeEpochMillis ?: 0L }
                .maxOrNull() ?: 0L
            binding.summaryHandshake.text =
                if (latestHandshake <= 0L) getString(R.string.stat_ago_never)
                else QuantityFormatter.formatEpochAgo(latestHandshake)
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
                    peer.latestHandshakeText.text = QuantityFormatter.formatEpochAgo(peerStats.latestHandshakeEpochMillis)
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
}
