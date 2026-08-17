package com.tubelite.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse

class DownloaderImpl private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    override fun execute(request: NPRequest): NPResponse {
        val builder = Request.Builder().url(request.url())

        val body = request.dataToSend()
        if (body != null) {
            builder.method(request.httpMethod(), body.toRequestBody())
        } else if (request.httpMethod() == "POST") {
            builder.method("POST", ByteArray(0).toRequestBody())
        }

        for ((key, values) in request.headers()) {
            for (value in values) builder.addHeader(key, value)
        }
        if (request.headers()["User-Agent"] == null) {
            builder.addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"
            )
        }

        client.newCall(builder.build()).execute().use { resp ->
            val bodyString = resp.body?.string() ?: ""
            return NPResponse(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                bodyString,
                resp.request.url.toString()
            )
        }
    }

    companion object {
        val instance: DownloaderImpl by lazy { DownloaderImpl() }
    }
}
