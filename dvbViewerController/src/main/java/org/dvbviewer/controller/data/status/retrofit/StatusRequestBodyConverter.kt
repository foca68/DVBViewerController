package org.dvbviewer.controller.data.status.retrofit

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.dvbviewer.controller.data.entities.Status
import retrofit2.Converter

internal class StatusRequestBodyConverter : Converter<Status, RequestBody> {

    override fun convert(value: Status): RequestBody {
        return value.toString().toRequestBody(MEDIA_TYPE)
    }

    companion object {

        private val MEDIA_TYPE = "text".toMediaTypeOrNull()
    }

}
