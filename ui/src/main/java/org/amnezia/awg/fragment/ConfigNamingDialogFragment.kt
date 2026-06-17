/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.util.NameError
import org.amnezia.awg.util.NameValidator
import org.amnezia.awg.databinding.ConfigNamingDialogFragmentBinding
import org.amnezia.awg.config.BadConfigException
import org.amnezia.awg.config.Config
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class ConfigNamingDialogFragment : DialogFragment() {
    private var binding: ConfigNamingDialogFragmentBinding? = null
    private var config: Config? = null

    private fun createTunnelAndDismiss() {
        val binding = binding ?: return
        val activity = activity ?: return
        val name = binding.tunnelNameText.text.toString()
        activity.lifecycleScope.launch {
            try {
                Application.getTunnelManager().create(name, config)
                dismiss()
            } catch (e: Throwable) {
                binding.tunnelNameTextLayout.error = e.message
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configText = requireArguments().getString(KEY_CONFIG_TEXT)
        val configBytes = configText!!.toByteArray(StandardCharsets.UTF_8)
        config = try {
            Config.parse(ByteArrayInputStream(configBytes))
        } catch (e: Throwable) {
            when (e) {
                is BadConfigException, is IOException -> throw IllegalArgumentException("Invalid config passed to ${javaClass.simpleName}", e)
                else -> throw e
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val alertDialogBuilder = MaterialAlertDialogBuilder(activity)
        alertDialogBuilder.setTitle(R.string.import_from_qr_code)
        binding = ConfigNamingDialogFragmentBinding.inflate(activity.layoutInflater, null, false)
        binding?.apply {
            executePendingBindings()
            alertDialogBuilder.setView(root)
        }
        alertDialogBuilder.setPositiveButton(R.string.create_tunnel) { _, _ -> createTunnelAndDismiss() }
        alertDialogBuilder.setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
        val dialog = alertDialogBuilder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.setOnShowListener {
            val createButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            fun refresh() {
                val name = binding?.tunnelNameText?.text?.toString().orEmpty()
                // Empty just disables (no scary message before they've typed); bad chars / too long explain.
                binding?.tunnelNameTextLayout?.error = when (NameValidator.validate(name)) {
                    NameError.BAD_CHARS -> getString(R.string.paste_name_invalid_chars)
                    NameError.TOO_LONG -> getString(R.string.paste_name_too_long)
                    NameError.EMPTY, null -> null
                }
                createButton.isEnabled = NameValidator.validate(name) == null
            }
            binding?.tunnelNameText?.doAfterTextChanged { refresh() }
            refresh()
        }
        return dialog
    }

    companion object {
        private const val KEY_CONFIG_TEXT = "config_text"

        fun newInstance(configText: String?): ConfigNamingDialogFragment {
            val extras = Bundle()
            extras.putString(KEY_CONFIG_TEXT, configText)
            val fragment = ConfigNamingDialogFragment()
            fragment.arguments = extras
            return fragment
        }
    }
}
