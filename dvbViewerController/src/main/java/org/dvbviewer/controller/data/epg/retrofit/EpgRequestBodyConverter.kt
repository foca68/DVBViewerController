package org.dvbviewer.controller.data.epg.retrofit

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.dvbviewer.controller.data.entities.EpgEntry
import retrofit2.Converter

internal class EpgRequestBodyConverter : Converter<List<EpgEntry>, RequestBody> {

    override fun convert(value: List<EpgEntry>): RequestBody {
        return value.toString().toRequestBody(MEDIA_TYPE)
    }

    companion object {

        private val MEDIA_TYPE = "text".toMediaTypeOrNull()
    }

}
