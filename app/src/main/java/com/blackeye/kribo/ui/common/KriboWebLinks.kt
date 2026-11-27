package com.blackeye.kribo.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.blackeye.kribo.R

/**
 * Single source of truth for Kribo Web links and actions shared across Drive, Settings and Guide.
 */
object KriboWebLinks {

    const val BASE_URL = "https://kribo.iareal.net"
    const val WEB_APP_URL = "$BASE_URL/app"
    const val WEB_APP_DISPLAY = "kribo.iareal.net/app"

    fun openWebApp(context: Context) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEB_APP_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun copyWebAppUrl(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.kribo_web_title), WEB_APP_URL),
        )
        Toast.makeText(
            context,
            context.getString(R.string.kribo_web_copied),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun shareWebAppToPc(context: Context) {
        val subject = context.getString(R.string.kribo_web_title)
        val body = context.getString(R.string.kribo_web_share_text, WEB_APP_URL)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.kribo_web_send_pc)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }
}
