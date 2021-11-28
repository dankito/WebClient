package net.dankito.web.client


open class RequestParameters(val url: String, var body: String? = null,
                             var contentType: String? = null,
                             var userAgent: String? = DefaultUserAgent,
                             var headers: Map<String, String> = mutableMapOf(),
                             var cookies: List<Cookie> = mutableListOf(),
                             var countConnectionRetries: Int = DefaultCountConnectionRetries,
                             var responseType: ResponseType = ResponseType.String,
                             var downloadBufferSize: Int = DefaultDownloadBufferSize,
                             var downloadProgressListener: ((progress: Float, downloadedChunk: ByteArray) -> Unit)? = null) {

    companion object {
        const val DefaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.77 Safari/537.36"

        const val DefaultMobileUserAgent = "Mozilla/5.0 (Linux; U; Android 4.0.3; ko-kr; LG-L160L Build/IML74K) AppleWebkit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"

        const val DefaultDownloadBufferSize = 8 * 1024

        const val DefaultCountConnectionRetries = 2
    }


    open fun isCountConnectionRetriesSet(): Boolean {
        return countConnectionRetries > 0
    }

    open fun decrementCountConnectionRetries() {
        this.countConnectionRetries--
    }

}