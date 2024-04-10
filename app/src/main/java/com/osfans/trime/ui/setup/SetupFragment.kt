// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.databinding.FragmentSetupBinding
import com.osfans.trime.ui.setup.SetupPage.Companion.isLastPage
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.serializable
import timber.log.Timber

class SetupFragment : Fragment() {
    private val viewModel: SetupViewModel by activityViewModels()
    private lateinit var binding: FragmentSetupBinding

    private val page: SetupPage by lazy { requireArguments().serializable("page")!! }

    private var isDone: Boolean = false
        set(new) {
            if (new && page.isLastPage()) {
                viewModel.isAllDone.value = true
            }
            with(binding) {
                stepText.text = page.getStepText(requireContext())
                hintText.text = page.getHintText(requireContext())
                actionButton.visibility = if (new) View.GONE else View.VISIBLE
                actionButton.text = page.getButtonText(requireContext())
                actionButton.setOnClickListener { getButtonAction(page, requireContext()) }
                doneText.visibility = if (new) View.VISIBLE else View.GONE
            }
            field = new
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentSetupBinding.inflate(inflater)
        sync()
        return binding.root
    }

    // Called on window focus changed
    fun sync() {
        isDone = page.isDone()
    }

    override fun onResume() {
        super.onResume()
        sync()
    }

    fun getButtonAction(
        setupPage: SetupPage,
        context: Context,
    ) {
        when (setupPage) {
            SetupPage.Permissions -> openDirectory()
            SetupPage.Enable -> InputMethodUtils.showImeEnablerActivity(context)
            SetupPage.Select -> InputMethodUtils.showImePicker()
        }
    }

    private fun saveUri(uri: Uri) {
        AppPrefs.defaultInstance().profile.userDataDir = uri.toString()
    }

    private fun openDirectory() {
        startForResult.launch(getFolderIntent())
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val context = requireContext()
                result.data?.data?.also { uri ->
                    Timber.d("Selected URI is %s", uri.toString())

                    val contentResolver = context.contentResolver

                    val takeFlags: Int =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    saveUri(uri)
                }
            }
        }

    companion object {
        fun getFolderIntent(): Intent {
            val pickerInitialUri = AppPrefs.Profile.URI_PREFIX + "document/primary%3Arime".toUri()

            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Optionally, specify a URI for the directory that should be opened in
                // the system file picker when it loads.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
                }
            }
        }
    }
}
