/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.client.android.Intents
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.launch
import org.amnezia.awg.R
import org.amnezia.awg.util.QrCodeFromFileScanner

/**
 * Network-Teal QR capture screen (Option A2). Hosts ZXing-Embedded's
 * [DecoratedBarcodeView] in our own themed layout so we can restyle the chrome
 * (toolbar, viewfinder, prompt card, torch, gallery) without touching the decode
 * loop. The result is returned through the same [Intents.Scan.RESULT] extra the
 * library's stock CaptureActivity uses, so `ScanContract` — and the existing
 * duplicate-tunnel dialog — keep working unchanged.
 */
class AwgCaptureActivity : AppCompatActivity() {

    private lateinit var capture: CaptureManager
    private lateinit var barcodeView: DecoratedBarcodeView
    private var torchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.awg_capture)

        barcodeView = findViewById(R.id.zxing_barcode_scanner)

        // Standard ZXing lifecycle bridge — returns the result via setResult/finish,
        // exactly like the library's built-in CaptureActivity.
        capture = CaptureManager(this, barcodeView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()

        findViewById<Toolbar>(R.id.qr_toolbar)
            .setNavigationOnClickListener { finish() }

        val torch = findViewById<MaterialButton>(R.id.qr_torch_button)
        torch.setOnClickListener {
            torchOn = !torchOn
            if (torchOn) barcodeView.setTorchOn() else barcodeView.setTorchOff()
            torch.setIconResource(if (torchOn) R.drawable.ic_flash else R.drawable.ic_flash_off)
            torch.contentDescription = getString(if (torchOn) R.string.qr_torch_off else R.string.qr_torch_on)
        }
        // Hide torch entirely on devices without a flash unit.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
            torch.visibility = View.GONE

        findViewById<MaterialButton>(R.id.qr_gallery_button)
            .setOnClickListener { pickImage.launch("image/*") }
    }

    // Gallery path: decode a chosen image with the same ZXing reader the file-import
    // path uses, then hand the text back through the standard scan-result Intent so
    // it flows through ScanContract (incl. duplicate detection).
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        if (!QrCodeFromFileScanner.validContentType(contentResolver, uri)) {
            showNoCodeSnackbar()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val text = try {
                QrCodeFromFileScanner(contentResolver, QRCodeReader()).scan(uri).text
            } catch (_: Exception) {
                null
            }
            if (text != null) {
                setResult(RESULT_OK, Intent().putExtra(Intents.Scan.RESULT, text))
                finish()
            } else {
                showNoCodeSnackbar()
            }
        }
    }

    private fun showNoCodeSnackbar() =
        Snackbar.make(barcodeView, R.string.qr_no_code_in_image, Snackbar.LENGTH_SHORT).show()

    // CaptureManager lifecycle passthrough — required.
    override fun onResume() { super.onResume(); capture.onResume() }
    override fun onPause() { super.onPause(); capture.onPause() }
    override fun onDestroy() { super.onDestroy(); capture.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); capture.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
