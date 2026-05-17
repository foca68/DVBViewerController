package org.dvbviewer.controller.data.timer.retrofit

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.dvbviewer.controller.data.entities.Timer
import retrofit2.Converter

internal class TimerRequestBodyConverter : Converter<List<Timer>, RequestBody> {

    override fun convert(value: List<Timer>): RequestBody {
        return value.toString().toRequestBody(MEDIA_TYPE)
    }

    companion object {

        private val MEDIA_TYPE = "text".toMediaTypeOrNull()
    }

}
