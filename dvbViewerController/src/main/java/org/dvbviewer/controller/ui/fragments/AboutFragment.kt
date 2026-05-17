package org.dvbviewer.controller.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.util.Linkify.TransformFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.util.LinkifyCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.dvbviewer.controller.R
import org.dvbviewer.controller.data.api.ApiResponse
import org.dvbviewer.controller.data.api.ApiStatus
import org.dvbviewer.controller.data.version.VersionRepository
import org.dvbviewer.controller.data.version.VersionViewModel
import org.dvbviewer.controller.data.version.VersionViewModelFactory
import org.dvbviewer.controller.databinding.FragmentAboutBinding
import org.dvbviewer.controller.ui.base.BaseFragment
import java.util.regex.Pattern


class AboutFragment : BaseFragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.paypalButton.setOnClickListener { activity?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.donation_link)))) }
        val pattern = Pattern.compile(".")
        val myTransformFilter = TransformFilter { _, url ->
            url.substring(0, url.length-1)
        }
        LinkifyCompat.addLinks(binding.privacyLabelTextView, pattern, getString(R.string.privacy_link), null, myTransformFilter)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val versionName = activity?.packageManager?.getPackageInfo(activity?.packageName!!, 0)?.versionName
        binding.versionTextView.text = versionName
        val repo = VersionRepository(activity!!.application!!, getDmsInterface())
        val vFac = VersionViewModelFactory(activity!!.application, repo)
        val versionViewModel = ViewModelProvider(this, vFac)
                .get(VersionViewModel::class.java)
        val versionObserver = Observer<ApiResponse<Boolean>> { response ->
            if (response?.status == ApiStatus.SUCCESS && response.data == true) {
                binding.donationRow.visibility = View.VISIBLE
            }
        }
        versionViewModel.isSupported(MINIMUM_VERSION).observe(this, versionObserver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val MINIMUM_VERSION = "1.33.0.0"
    }

}
