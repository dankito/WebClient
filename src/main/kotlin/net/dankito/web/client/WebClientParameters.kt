package net.dankito.web.client


open class WebClientParameters(
  open val followRedirects: Boolean = true,
  open val retryOnConnectionFailure: Boolean = true,
  open val connectTimeoutMillis: Long = DefaultConnectionTimeoutMillis,
  open val readTimeoutMillis: Long = DefaultReadTimeoutMillis,
  open val writeTimeoutMillis: Long = DefaultWriteTimeoutMillis
) {

  companion object {

    const val DefaultConnectionTimeoutMillis = 2000L

    const val DefaultReadTimeoutMillis = 15000L

    const val DefaultWriteTimeoutMillis = 30000L

  }

}