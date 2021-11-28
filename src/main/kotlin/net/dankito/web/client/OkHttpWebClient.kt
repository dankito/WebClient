package net.dankito.web.client

import okhttp3.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap


open class OkHttpWebClient : IWebClient {

    companion object {
        private val log = LoggerFactory.getLogger(OkHttpWebClient::class.java)
    }


    // TODO: in this way concurrent calls are not supported as one call may concurrently overwrites the cookies of another call
    protected open val receivedCookies = ConcurrentHashMap<HttpUrl, List<okhttp3.Cookie>>()

    protected open val cookiesToSetInNextCall = ConcurrentHashMap<HttpUrl, List<okhttp3.Cookie>>()

    protected open val cookieJar = object : CookieJar {

        override fun saveFromResponse(url: HttpUrl?, cookies: MutableList<okhttp3.Cookie>?) {
            url?.let {
                cookies?.let {
                    receivedCookies.put(url, cookies)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl?): MutableList<okhttp3.Cookie> {
            return cookiesToSetInNextCall[url]?.toMutableList() ?: mutableListOf()
        }

    }

    // avoid creating several instances, should be singleton
    protected open val client: OkHttpClient


    @JvmOverloads
    constructor(parameters: WebClientParameters = WebClientParameters()) {
        this.client = createOkHttpClient { applyParametersToClient(it, parameters) }
    }

    constructor(configureClient: (OkHttpClient.Builder) -> Unit) {
        this.client = createOkHttpClient {  clientBuilder ->
            applyParametersToClient(clientBuilder, WebClientParameters())

            configureClient(clientBuilder)
        }
    }

    constructor(client: OkHttpClient) {
        this.client = client
    }


    protected open fun createOkHttpClient(configureClient: (OkHttpClient.Builder) -> Unit): OkHttpClient {
        val builder = OkHttpClient.Builder()

        configureClient(builder)

        return builder.build()
    }

    protected open fun applyParametersToClient(builder: OkHttpClient.Builder, parameters: WebClientParameters) {
        builder.followRedirects(parameters.followRedirects)
        builder.retryOnConnectionFailure(parameters.retryOnConnectionFailure)

        builder.connectTimeout(parameters.connectTimeoutMillis, TimeUnit.MILLISECONDS)
        builder.readTimeout(parameters.readTimeoutMillis, TimeUnit.MILLISECONDS)
        builder.writeTimeout(parameters.writeTimeoutMillis, TimeUnit.MILLISECONDS)

        builder.cookieJar(cookieJar)
    }


    override fun get(parameters: RequestParameters): WebClientResponse {
        try {
            val request = createGetRequest(parameters)

            return executeAndGetResponse(parameters, request)
        } catch (e: Exception) {
            return getRequestFailed(parameters, e)
        }
    }

    override fun getAsync(parameters: RequestParameters, callback: (response: WebClientResponse) -> Unit) {
        try {
            val request = createGetRequest(parameters)

            executeRequestAsync(parameters, request, callback)
        } catch (e: Exception) {
            asyncGetRequestFailed(parameters, e, callback)
        }

    }

    protected open fun createGetRequest(parameters: RequestParameters): Request {
        val requestBuilder = Request.Builder()

        applyParameters(requestBuilder, parameters)

        return requestBuilder.build()
    }


    override fun post(parameters: RequestParameters): WebClientResponse {
        try {
            val request = createPostRequest(parameters)

            return executeAndGetResponse(parameters, request)
        } catch (e: Exception) {
            return postRequestFailed(parameters, e)
        }

    }

    override fun postAsync(parameters: RequestParameters, callback: (response: WebClientResponse) -> Unit) {
        try {
            val request = createPostRequest(parameters)

            executeRequestAsync(parameters, request, callback)
        } catch (e: Exception) {
            asyncPostRequestFailed(parameters, e, callback)
        }

    }

    protected open fun createPostRequest(parameters: RequestParameters): Request {
        val requestBuilder = Request.Builder()

        setPostBody(requestBuilder, parameters)

        applyParameters(requestBuilder, parameters)

        return requestBuilder.build()
    }

    protected open fun setPostBody(requestBuilder: Request.Builder, parameters: RequestParameters) {
        val requestBody = createRequestBody(parameters)

        requestBuilder.post(requestBody)
    }


    override fun put(parameters: RequestParameters): WebClientResponse {
        try {
            val request = createPutRequest(parameters)

            return executeAndGetResponse(parameters, request)
        } catch (e: Exception) {
            return postRequestFailed(parameters, e)
        }

    }

    override fun putAsync(parameters: RequestParameters, callback: (response: WebClientResponse) -> Unit) {
        try {
            val request = createPutRequest(parameters)

            executeRequestAsync(parameters, request, callback)
        } catch (e: Exception) {
            asyncPostRequestFailed(parameters, e, callback)
        }

    }

    protected open fun createPutRequest(parameters: RequestParameters): Request {
        val requestBuilder = Request.Builder()

        setPutBody(requestBuilder, parameters)

        applyParameters(requestBuilder, parameters)

        return requestBuilder.build()
    }

    protected open fun setPutBody(requestBuilder: Request.Builder, parameters: RequestParameters) {
        val requestBody = createRequestBody(parameters)

        requestBuilder.put(requestBody)
    }


    override fun head(parameters: RequestParameters): WebClientResponse {
        try {
            val request = createHeadRequest(parameters)

            return executeAndGetResponse(parameters, request)
        } catch (e: Exception) {
            return headRequestFailed(parameters, e)
        }
    }

    override fun headAsync(parameters: RequestParameters, callback: (response: WebClientResponse) -> Unit) {
        try {
            val request = createHeadRequest(parameters)

            executeRequestAsync(parameters, request, callback)
        } catch (e: Exception) {
            asyncHeadRequestFailed(parameters, e, callback)
        }

    }

    protected open fun createHeadRequest(parameters: RequestParameters): Request {
        val requestBuilder = Request.Builder()

        applyParameters(requestBuilder, parameters)

        requestBuilder.head()

        return requestBuilder.build()
    }


    protected open fun applyParameters(requestBuilder: Request.Builder, parameters: RequestParameters) {
        requestBuilder.url(parameters.url)

        parameters.userAgent?.let { userAgent ->
            requestBuilder.header("User-Agent", userAgent)
        }

        parameters.headers.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        cookiesToSetInNextCall.put(HttpUrl.get(parameters.url), parameters.cookies.map { mapCookie(it) })
    }

    @Throws(Exception::class)
    protected open fun executeRequest(parameters: RequestParameters, request: Request): Response {
        val response = client.newCall(request).execute()

        if (response.isSuccessful == false && parameters.isCountConnectionRetriesSet()) {
            response.close() // to avoid memory leak
            prepareConnectionRetry(parameters, Exception("${response.code()}: ${response.message()}"))

            return executeRequest(parameters, request)
        }
        else {
            return response
        }
    }

    protected open fun executeRequestAsync(parameters: RequestParameters, request: Request, callback: (response: WebClientResponse) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                asyncRequestFailed(parameters, request, e, callback)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                callback(getResponse(parameters, response))
            }
        })
    }

    protected open fun getRequestFailed(parameters: RequestParameters, e: Exception): WebClientResponse {
        return requestFailed(parameters, e) { get(parameters) }
    }

    protected open fun asyncGetRequestFailed(parameters: RequestParameters, e: Exception, callback: (response: WebClientResponse) -> Unit) {
        asyncRequestFailed(parameters, e, callback) { getAsync(parameters, callback) }
    }

    protected open fun postRequestFailed(parameters: RequestParameters, e: Exception): WebClientResponse {
        return requestFailed(parameters, e) { post(parameters) }
    }

    protected open fun asyncPostRequestFailed(parameters: RequestParameters, e: Exception, callback: (response: WebClientResponse) -> Unit) {
        asyncRequestFailed(parameters, e, callback) { postAsync(parameters, callback) }
    }

    protected open fun headRequestFailed(parameters: RequestParameters, e: Exception): WebClientResponse {
        return requestFailed(parameters, e) { head(parameters) }
    }

    protected open fun asyncHeadRequestFailed(parameters: RequestParameters, e: Exception, callback: (response: WebClientResponse) -> Unit) {
        asyncRequestFailed(parameters, e, callback) { headAsync(parameters, callback) }
    }

    protected open fun requestFailed(parameters: RequestParameters, e: Exception, retry: (RequestParameters) -> WebClientResponse): WebClientResponse {
        if (shouldRetryConnection(parameters, e)) {
            prepareConnectionRetry(parameters, e)

            return retry(parameters)
        }
        else {
            return requestFailedFinally(parameters, e)
        }
    }

    protected open fun asyncRequestFailed(parameters: RequestParameters, e: Exception, callback: (response: WebClientResponse) -> Unit,
                                             retry: (RequestParameters) -> Unit) {
        if (shouldRetryConnection(parameters, e)) {
            prepareConnectionRetry(parameters, e)
            retry(parameters)
        }
        else {
            callback(requestFailedFinally(parameters, e))
        }
    }

    protected open fun asyncRequestFailed(parameters: RequestParameters, request: Request, e: Exception, callback: (response: WebClientResponse) -> Unit) {
        if (shouldRetryConnection(parameters, e)) {
            prepareConnectionRetry(parameters, e)
            executeRequestAsync(parameters, request, callback)
        }
        else {
            log.error("Failure on Request to " + request.url(), e)
            callback(requestFailedFinally(parameters, e))
        }
    }

    protected open fun requestFailedFinally(parameters: RequestParameters, e: Exception): WebClientResponse {
        log.error("Could not request url " + parameters.url, e)

        clearCookiesForUrl(HttpUrl.get(parameters.url))

        return WebClientResponse(false, error = e)
    }

    protected open fun clearCookiesForUrl(url: HttpUrl) {
        cookiesToSetInNextCall.remove(url)
    }

    protected open fun prepareConnectionRetry(parameters: RequestParameters, e: Exception) {
        log.info("Could not connect to " + parameters.url + ", going to retry (count tries left: " +
                parameters.countConnectionRetries + ")", e)

        parameters.decrementCountConnectionRetries()
    }

    protected open fun shouldRetryConnection(parameters: RequestParameters, e: Exception): Boolean {
        return parameters.isCountConnectionRetriesSet() && isConnectionException(e)
    }

    protected open fun isConnectionException(e: Exception): Boolean {
        val errorMessage = e.message?.toLowerCase() ?: ""
        return errorMessage.contains("timeout") || errorMessage.contains("failed to connect")
    }

    protected open fun executeAndGetResponse(parameters: RequestParameters, request: Request): WebClientResponse {
        val response = executeRequest(parameters, request)

        return getResponse(parameters, response)
    }

    @Throws(IOException::class)
    protected open fun getResponse(parameters: RequestParameters, response: Response): WebClientResponse {
        val responseCode = response.code()
        val successful = responseCode >= 200 && responseCode < 300
        val headers = copyHeaders(response)
        val cookies = mapCookies(response)

        clearCookiesForUrl(response.request().url())

        return when (parameters.responseType) {
            ResponseType.String -> {
                val body = response.body()?.string()
                response.close() // to avoid memory leak
                WebClientResponse(successful, responseCode, headers, cookies, body = body)
            }
            ResponseType.Bytes -> {
                val receivedData = response.body()?.bytes()
                response.close() // to avoid memory leak
                WebClientResponse(successful, responseCode, headers, cookies, receivedData = receivedData)
            }
            ResponseType.Stream -> WebClientResponse(successful, responseCode, headers, cookies, responseStream = response.body()?.byteStream())
            ResponseType.StreamWithProgressListener -> streamBinaryResponse(parameters, response, successful, responseCode, headers, cookies)
        }
    }

    protected open fun copyHeaders(response: Response): Map<String, String>? {
        val headers = HashMap<String, String>()

        response.headers().names().forEach { name ->
            headers.put(name, response.header(name) ?: "")
        }

        return headers
    }

    protected open fun mapCookies(response: Response): List<Cookie> {
        val cookies = receivedCookies.remove(response.request().url())

        return cookies?.map { mapCookie(it) } ?: listOf()
    }

    protected open fun mapCookie(cookie: okhttp3.Cookie): Cookie {
        return Cookie(cookie.name(), cookie.value(), cookie.domain(), cookie.path(), cookie.expiresAt(),
                cookie.secure(), cookie.httpOnly(), cookie.persistent(), cookie.hostOnly())
    }

    protected open fun mapCookie(cookie: Cookie): okhttp3.Cookie {
        val builder = okhttp3.Cookie.Builder()
                .name(cookie.name)
                .value(cookie.value)
                .domain(cookie.domain)
                .path(cookie.path)
                .expiresAt(cookie.expiresAt)

        if (cookie.secure) {
            builder.secure()
        }

        if (cookie.httpOnly) {
            builder.httpOnly()
        }

        if (cookie.hostOnly) {
            builder.hostOnlyDomain(cookie.domain)
        }

        return builder.build()
    }


    protected open fun createRequestBody(parameters: RequestParameters): RequestBody {
        val body = parameters.body ?: "" // requests may have an empty body
        val mediaType = getMediaType(parameters.contentType)

        return RequestBody.create(mediaType, body)
    }

    protected open fun getMediaType(contentType: String?): MediaType? {
        try {
            contentType?.let {
                return MediaType.parse(contentType)
            }
        } catch (e: Exception) {
            log.error("Could not parse '$contentType' to a MediaType")
        }

        return null
    }


    protected open fun streamBinaryResponse(parameters: RequestParameters, response: Response, successful: Boolean, responseCode: Int,
                                            headers: Map<String, String>?, cookies: List<Cookie>): WebClientResponse {

        var inputStream: InputStream? = null
        try {
            inputStream = response.body()?.byteStream()

            val buffer = ByteArray(parameters.downloadBufferSize)
            var downloaded: Long = 0
            val contentLength = response.body()?.contentLength() ?: 0

            publishProgress(parameters, ByteArray(0), 0L, contentLength)
            while (true) {
                val read = inputStream!!.read(buffer)
                if(read == -1) {
                    break
                }

                downloaded += read.toLong()

                publishProgress(parameters, buffer, downloaded, contentLength, read)

                if(isCancelled(parameters)) {
                    return WebClientResponse(false, response.code(), headers, cookies)
                }
            }

            return WebClientResponse(successful, responseCode, headers, cookies)
        } catch (e: IOException) {
            log.error("Could not download binary Response for Url " + parameters.url, e)
            return WebClientResponse(false, responseCode, headers, cookies, e)
        } finally {
            inputStream?.let { try { it.close() } catch (ignored: Exception) { } }
            try { response.close() } catch (ignored: Exception) { }
        }
    }

    protected open fun isCancelled(parameters: RequestParameters): Boolean {
        return false // TODO: implement mechanism to abort download
    }

    protected open fun publishProgress(parameters: RequestParameters, buffer: ByteArray, downloaded: Long, contentLength: Long, read: Int) {
        var downloadedData = buffer

        if(read < parameters.downloadBufferSize) {
            downloadedData = Arrays.copyOfRange(buffer, 0, read)
        }

        publishProgress(parameters, downloadedData, downloaded, contentLength)
    }

    protected open fun publishProgress(parameters: RequestParameters, downloadedChunk: ByteArray, currentlyDownloaded: Long, total: Long) {
        val progressListener = parameters.downloadProgressListener

        if(progressListener != null) {
            val progress = if (total <= 0) java.lang.Float.NaN else currentlyDownloaded / total.toFloat()
            progressListener(progress, downloadedChunk)
        }
    }

}