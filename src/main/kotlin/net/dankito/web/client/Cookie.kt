package net.dankito.web.client


class Cookie(val name: String,
             val value: String,
             val domain: String,
             val path: String,
             val expiresAt: Long = Long.MIN_VALUE,
             val secure: Boolean = false,
             val httpOnly: Boolean = false,
             val persistent: Boolean = false,
             val hostOnly: Boolean = false) {

    override fun toString(): String {
        return "$name: $value"
    }

}