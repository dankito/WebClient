package net.dankito.web.client


interface IWebClient {

    fun get(parameters: RequestParameters): WebClientResponse
    fun get(url: String): WebClientResponse {
        return get(RequestParameters(url))
    }

    fun getAsync(parameters: RequestParameters, callback: (response: WebClientResponse) -> Unit)
    fun getAsync(url: String, callback: (response: WebClientResponse) -> Unit) {
        getAsync(RequestParameters(url), callback)
    }

    fun post(parameters: RequestParameters): WebClientResponse
    fun postAsync(parameters: RequestParameters, callback: (response: WebClientResponse) -> Unit)

    fun put(parameters: RequestParameters): WebClientResponse
    fun putAsync(parameters: RequestParameters, callback: (response: WebClientResponse) -> Unit)

    fun head(parameters: RequestParameters): WebClientResponse
    fun head(url: String): WebClientResponse {
        return head(RequestParameters(url))
    }

    fun headAsync(parameters: RequestParameters, callback: (response: WebClientResponse) -> Unit)
    fun headAsync(url: String, callback: (response: WebClientResponse) -> Unit) {
        headAsync(RequestParameters(url), callback)
    }

}