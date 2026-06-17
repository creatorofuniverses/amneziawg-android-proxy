/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.Observable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import org.amnezia.awg.BR
import org.amnezia.awg.R
import org.amnezia.awg.config.SplitTunnelSummary
import org.amnezia.awg.databinding.AppListDialogFragmentBinding
import org.amnezia.awg.databinding.ObservableKeyedArrayList
import org.amnezia.awg.model.ApplicationData
import org.amnezia.awg.util.ErrorMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListDialogFragment : DialogFragment() {
    private val appData = ObservableKeyedArrayList<String, ApplicationData>()
    private val allApps = ArrayList<ApplicationData>()
    private val isLoading = androidx.databinding.ObservableBoolean(true)
    private var currentlySelectedApps = emptyList<String>()
    private var initiallyExcluded = false

    /** True when the user has selected "Exclude" mode (exclude from tunnel). */
    private var isExcluded = false

    private var binding: AppListDialogFragmentBinding? = null

    private fun loadData() {
        val activity = activity ?: return
        val pm = activity.packageManager
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val applicationData: MutableList<ApplicationData> = ArrayList()
                withContext(Dispatchers.IO) {
                    val packageInfos = getPackagesHoldingPermissions(pm, arrayOf(Manifest.permission.INTERNET))
                    packageInfos.forEach {
                        val packageName = it.packageName
                        val appInfo = it.applicationInfo
                        val appData =
                            ApplicationData(appInfo?.loadIcon(pm) ?: pm.defaultActivityIcon, appInfo?.loadLabel(pm)?.toString() ?: packageName, packageName, currentlySelectedApps.contains(packageName))
                        applicationData.add(appData)
                        appData.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
                            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                                if (propertyId == BR.selected)
                                    updateRoutedSummary()
                            }
                        })
                    }
                }
                // Sort once at open time: currently-selected apps first, then alphabetical
                // within each group. Not re-sorted on toggle (selection set at construction).
                applicationData.sortWith(
                    compareByDescending<ApplicationData> { it.isSelected }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                )
                withContext(Dispatchers.Main.immediate) {
                    allApps.clear()
                    allApps.addAll(applicationData)
                    appData.clear()
                    appData.addAll(applicationData)
                    isLoading.set(false)
                    updateRoutedSummary()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    isLoading.set(false)
                    val error = ErrorMessages[e]
                    val message = activity.getString(R.string.error_fetching_apps, error)
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentlySelectedApps = (arguments?.getStringArrayList(KEY_SELECTED_APPS) ?: emptyList())
        initiallyExcluded = arguments?.getBoolean(KEY_IS_EXCLUDED) ?: true
        isExcluded = initiallyExcluded
        setStyle(STYLE_NORMAL, R.style.ThemeOverlay_Awg_FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = AppListDialogFragmentBinding.inflate(inflater, container, false)
        binding = b

        b.fragment = this
        b.appData = appData
        b.loading = isLoading

        // Toolbar: back = apply selection + dismiss
        b.toolbar.setNavigationOnClickListener {
            applySelectionAndDismiss()
        }

        // Toolbar overflow: toggle-all action
        b.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.toggle_all) {
                val selectAll = allApps.none { it.isSelected }
                allApps.forEach { it.isSelected = selectAll }
                true
            } else {
                false
            }
        }

        // Segmented Include/Exclude toggle
        // Exclude tab (old tab 0) ↔ isExcluded == true
        // Include tab (old tab 1) ↔ isExcluded == false
        val initialButtonId = if (isExcluded) R.id.btn_exclude else R.id.btn_include
        b.modeToggle.check(initialButtonId)

        b.modeToggle.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean ->
            if (isChecked) {
                isExcluded = (checkedId == R.id.btn_exclude)
                updateRoutedSummary()
            }
        }

        b.appSearchText.doAfterTextChanged { text ->
            val q = text?.trim()?.toString()?.lowercase().orEmpty()
            appData.clear()
            appData.addAll(if (q.isEmpty()) allApps else allApps.filter { it.name.lowercase().contains(q) })
        }

        loadData()
        return b.root
    }

    private fun updateRoutedSummary() {
        val b = binding ?: return
        val total = allApps.size
        val selected = allApps.count { it.isSelected }
        val included = if (isExcluded) 0 else selected
        val excluded = if (isExcluded) selected else 0
        b.appRoutedSummary.text = if (SplitTunnelSummary.isAllApps(included, excluded))
            getString(R.string.split_tunnel_all_apps)
        else
            getString(R.string.split_tunnel_summary, SplitTunnelSummary.routedCount(included, excluded, total), total)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun getPackagesHoldingPermissions(pm: PackageManager, permissions: Array<String>): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackagesHoldingPermissions(permissions, PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackagesHoldingPermissions(permissions, 0)
        }
    }

    private fun emitSelectionResult() {
        val selectedApps: MutableList<String> = ArrayList()
        for (data in allApps) {
            if (data.isSelected) {
                selectedApps.add(data.packageName)
            }
        }
        setFragmentResult(
            REQUEST_SELECTION, bundleOf(
                KEY_SELECTED_APPS to selectedApps.toTypedArray(),
                KEY_IS_EXCLUDED to isExcluded
            )
        )
    }

    private fun applySelectionAndDismiss() {
        emitSelectionResult()
        dismiss()
    }

    override fun onCancel(dialog: DialogInterface) {
        emitSelectionResult()
        super.onCancel(dialog)
    }

    companion object {
        const val KEY_SELECTED_APPS = "selected_apps"
        const val KEY_IS_EXCLUDED = "is_excluded"
        const val REQUEST_SELECTION = "request_selection"

        fun newInstance(selectedApps: ArrayList<String?>?, isExcluded: Boolean): AppListDialogFragment {
            val extras = Bundle()
            extras.putStringArrayList(KEY_SELECTED_APPS, selectedApps)
            extras.putBoolean(KEY_IS_EXCLUDED, isExcluded)
            val fragment = AppListDialogFragment()
            fragment.arguments = extras
            return fragment
        }
    }
}
