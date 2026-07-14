package com.urlxl.mail.pgp

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.urlxl.mail.R
import com.urlxl.mail.applyPrimaryButtonTheme
import com.urlxl.mail.applyThemeToActivity
import com.urlxl.mail.applyTopInsetWithHeader
import com.urlxl.mail.push.PushRuntime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "My QR Code": mints a short-lived PGP QR token via [PgpQrClient.mintToken] and renders its
 * `url` as a QR code for another device to scan (see [ScanAddContactKeyActivity]).
 *
 * Pairing creds are read the same way [com.urlxl.mail.SettingsActivity] reads them —
 * `PushRuntime.graph(context).repository.state.first().pairing` — rather than inventing a second
 * pairing-lookup path.
 */
class MyQrCodeActivity : AppCompatActivity() {

    private lateinit var qrImage: ImageView
    private lateinit var expiresText: TextView
    private lateinit var statusText: TextView
    private lateinit var refreshButton: Button

    private val client = PgpQrClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_qr_code)
        setTitle(R.string.pgp_qr_my_code_title)
        applyThemeToActivity(this)
        applyTopInsetWithHeader(this, findViewById(R.id.myQrCodeRoot))

        qrImage = findViewById(R.id.pgpQrImage)
        expiresText = findViewById(R.id.pgpQrExpiresText)
        statusText = findViewById(R.id.pgpQrStatusText)
        refreshButton = findViewById(R.id.btnRefreshQr)

        refreshButton.setOnClickListener { mintAndRender() }

        mintAndRender()
    }

    override fun onResume() {
        super.onResume()
        applyThemeToActivity(this)
        applyPrimaryButtonTheme(this, refreshButton)
    }

    private fun mintAndRender() {
        qrImage.visibility = View.GONE
        expiresText.text = ""
        statusText.text = getString(R.string.pgp_qr_my_code_loading)

        lifecycleScope.launch {
            val pairing = PushRuntime.graph(this@MyQrCodeActivity).repository.state.first().pairing
            if (pairing == null) {
                statusText.text = getString(R.string.pgp_qr_my_code_not_paired)
                return@launch
            }

            when (val result = client.mintToken(pairing.serverUrl, pairing.subscriberId, pairing.subscriberHash)) {
                is PgpQrTokenResult.Success -> renderQr(result.token)
                is PgpQrTokenResult.NoIdentity -> statusText.text = getString(R.string.pgp_qr_my_code_no_identity)
                is PgpQrTokenResult.Unauthorized -> statusText.text = getString(R.string.pgp_qr_my_code_unauthorized)
                is PgpQrTokenResult.ServiceUnavailable -> statusText.text = getString(R.string.pgp_qr_my_code_unavailable)
                is PgpQrTokenResult.Retryable -> statusText.text = result.message
            }
        }
    }

    private fun renderQr(token: PgpQrTokenDto) {
        val bitmap = runCatching { renderQrBitmap(token.url, QR_SIZE_PX) }.getOrNull()
        if (bitmap == null) {
            statusText.text = getString(R.string.pgp_qr_my_code_render_failed)
            return
        }
        qrImage.setImageBitmap(bitmap)
        qrImage.visibility = View.VISIBLE
        statusText.text = ""
        expiresText.text = getString(R.string.pgp_qr_my_code_expires, token.expiresAt)
    }

    private fun renderQrBitmap(content: String, sizePx: Int): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    companion object {
        private const val QR_SIZE_PX = 720
    }
}
