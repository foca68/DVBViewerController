package org.dvbviewer.controller.data.stream.retrofit

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.dvbviewer.controller.data.entities.FFMpegPresetList
import retrofit2.Converter

internal class FFMpegRequestBodyConverter : Converter<FFMpegPresetList, RequestBody> {

    override fun convert(value: FFMpegPresetList): RequestBody {
        return value.toString().toRequestBody(MEDIA_TYPE)
    }

    companion object {

        private val MEDIA_TYPE = "text".toMediaTypeOrNull()
    }

}
