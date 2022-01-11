package com.osfans.trime.util

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.databinding.DialogLoadingBinding

fun createLoadingDialog(context: Context, textId: Int): AlertDialog {
    val binding = DialogLoadingBinding.inflate(LayoutInflater.from(context), null, false)
    binding.loadingText.text = context.getText(textId)

    return AlertDialog.Builder(context)
        .setCancelable(false)
        .setView(binding.root)
        .create()
}
