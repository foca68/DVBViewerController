package org.dvbviewer.controller.data.channel.retrofit

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.dvbviewer.controller.data.entities.ChannelRoot
import retrofit2.Converter

internal class ChannelRequestBodyConverter : Converter<List<ChannelRoot>, RequestBody> {

    override fun convert(value: List<ChannelRoot>): RequestBody {
        return value.toString().toRequestBody(MEDIA_TYPE)
    }

    companion object {

        private val MEDIA_TYPE = "text".toMediaTypeOrNull()
    }

}
