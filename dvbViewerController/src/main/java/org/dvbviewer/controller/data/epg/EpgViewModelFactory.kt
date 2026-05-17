package org.dvbviewer.controller.data.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.dvbviewer.controller.data.xmltv.XmltvRepository

class EpgViewModelFactory(
    private val mRepo: EPGRepository,
    private val xmltvRepository: XmltvRepository? = null
) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChannelEpgViewModel(mRepo, xmltvRepository) as T
    }
}
