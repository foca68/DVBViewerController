package org.dvbviewer.controller.data.remote.retrofit

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.dvbviewer.controller.data.entities.DVBTarget
import retrofit2.Converter

class TargetResponseBodyConverter : Converter<List<DVBTarget>, RequestBody> {

    override fun convert(value: List<DVBTarget>): RequestBody {
        return value.toString().toRequestBody(MEDIA_TYPE)
    }

    companion object {

        private val MEDIA_TYPE = "text".toMediaTypeOrNull()
    }

}
