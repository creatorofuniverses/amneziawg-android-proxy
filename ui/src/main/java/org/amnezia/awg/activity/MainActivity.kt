/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.fragment.TunnelDetailFragment
import org.amnezia.awg.fragment.TunnelEditorFragment
import org.amnezia.awg.model.ObservableTunnel
import kotlinx.coroutines.launch

/**
 * CRUD interface for AmneziaWG tunnels. This activity serves as the main entry point to the
 * AmneziaWG application, and contains several fragments for listing, viewing details of, and
 * editing the configuration and interface state of AmneziaWG tunnels.
 */
class MainActivity : BaseActivity(), FragmentManager.OnBackStackChangedListener {
    private var actionBar: ActionBar? = null
    private var isTwoPaneLayout = false
    private var backPressedCallback: OnBackPressedCallback? = null

    companion object {
        /** Intent extra (tunnel name) requesting MainActivity open straight to that tunnel's detail. */
        const val INTENT_EXTRA_OPEN_TUNNEL = "open_tunnel"
    }

    private fun handleBackPressed() {
        val backStackEntries = supportFragmentManager.backStackEntryCount
        // If the two-pane layout does not have an editor open, going back should exit the app.
        if (isTwoPaneLayout && backStackEntries <= 1) {
            finish()
            return
        }

        if (backStackEntries >= 1)
            supportFragmentManager.popBackStack()

        // Deselect the current tunnel on navigating back from the detail pane to the one-pane list.
        if (backStackEntries == 1)
            selectedTunnel = null
    }

    override fun onBackStackChanged() {
        val backStackEntries = supportFragmentManager.backStackEntryCount
        backPressedCallback?.isEnabled = backStackEntries >= 1
        if (actionBar == null) return
        // Do not show the home menu when the two-pane layout is at the detail view (see above).
        val minBackStackEntries = if (isTwoPaneLayout) 2 else 1
        actionBar!!.setDisplayHomeAsUpEnabled(backStackEntries >= minBackStackEntries)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        actionBar = supportActionBar
        // Launcher icon uses the short app_name (manifest label); the in-app
        // header keeps the full app_title. The editor restores this same title.
        actionBar?.setTitle(R.string.app_title)
        isTwoPaneLayout = findViewById<View?>(R.id.master_detail_wrapper) != null
        supportFragmentManager.addOnBackStackChangedListener(this)
        backPressedCallback = onBackPressedDispatcher.addCallback(this) { handleBackPressed() }
        onBackStackChanged()

        // "Open «tunnel»" requests (e.g. from the duplicate card on the paste screen) navigate to
        // the detail view like a normal row tap — selecting the tunnel once the activity is
        // created so onSelectedTunnelChanged pushes the detail fragment and a back-stack entry.
        // (BaseActivity's selected_tunnel restore path only re-highlights; it does not navigate.)
        if (savedInstanceState == null) {
            intent?.getStringExtra(INTENT_EXTRA_OPEN_TUNNEL)?.let { name ->
                lifecycleScope.launch {
                    runCatching { Application.getTunnelManager().getTunnels()[name] }.getOrNull()?.let {
                        selectedTunnel = it
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // The back arrow in the action bar should act the same as the back button.
                onBackPressedDispatcher.onBackPressed()
                true
            }

            R.id.menu_action_edit -> {
                supportFragmentManager.commit {
                    replace(R.id.detail_container, TunnelEditorFragment())
                    setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    addToBackStack(null)
                }
                true
            }
            // This menu item is handled by the editor fragment.
            R.id.menu_action_save -> false
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSelectedTunnelChanged(
        oldTunnel: ObservableTunnel?,
        newTunnel: ObservableTunnel?
    ): Boolean {
        val fragmentManager = supportFragmentManager
        if (fragmentManager.isStateSaved) {
            return false
        }

        val backStackEntries = fragmentManager.backStackEntryCount
        if (newTunnel == null) {
            // Clear everything off the back stack (all editors and detail fragments).
            fragmentManager.popBackStackImmediate(0, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            return true
        }
        if (backStackEntries == 2) {
            // Pop the editor off the back stack to reveal the detail fragment. Use the immediate
            // method to avoid the editor picking up the new tunnel while it is still visible.
            fragmentManager.popBackStackImmediate()
        } else if (backStackEntries == 0) {
            // Create and show a new detail fragment.
            fragmentManager.commit {
                add(R.id.detail_container, TunnelDetailFragment())
                setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                addToBackStack(null)
            }
        }
        return true
    }
}
