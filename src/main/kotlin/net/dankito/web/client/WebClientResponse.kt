package net.dankito.web.client

import java.io.InputStream


open class WebClientResponse(val isSuccessful: Boolean,
                             val responseCode: Int = -1,
                             val headers: Map<String, String>? = null,
                             val cookies: List<Cookie> = listOf(),
                             val error: Exception? = null,
                             val body: String? = null,
                             val receivedData: ByteArray? = null,
                             val responseStream: InputStream? = null
) {

    open fun getHeaderValue(headerName: String): String? {
        val headerNameLowerCased = headerName.toLowerCase() // header names are case insensitive, so compare them lower cased

        headers?.keys?.forEach {
            if(it.toLowerCase() == headerNameLowerCased) {
                return headers[it]
            }
        }

        return null
    }


    open val isInformationalResponse: Boolean
        get() = responseCode >= 100 && responseCode < 200

    open val isSuccessResponse: Boolean
        get() = responseCode >= 200 && responseCode < 300

    open val isRedirectionResponse: Boolean
        get() = responseCode >= 300 && responseCode < 400

    open val isClientErrorResponse: Boolean
        get() = responseCode >= 400 && responseCode < 500

    open val isServerErrorResponse: Boolean
        get() = responseCode >= 500 && responseCode < 600

    open fun containsCookie(cookieName: String): Boolean {
        return getCookie(cookieName) != null
    }

    open fun getCookie(cookieName: String): Cookie? {
        return cookies.firstOrNull { cookieName == it.name }
    }

}