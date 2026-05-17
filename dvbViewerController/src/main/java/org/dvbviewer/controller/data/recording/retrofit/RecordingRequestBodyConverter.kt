package org.dvbviewer.controller.data.recording.retrofit

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.dvbviewer.controller.data.entities.Recording
import retrofit2.Converter

internal class RecordingRequestBodyConverter : Converter<List<Recording>, RequestBody> {

    override fun convert(value: List<Recording>): RequestBody {
        return value.toString().toRequestBody(MEDIA_TYPE)
    }

    companion object {

        private val MEDIA_TYPE = "text".toMediaTypeOrNull()
    }

}
