package com.pesutgo.app.core

import android.net.Uri

object DomainPolicy {
    private val trustedHosts = setOf("pesutgo.com", "www.pesutgo.com")

    fun isTrusted(uri: Uri): Boolean {
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return trustedHosts.any { host == it || host.endsWith(".$it") }
    }

    fun isPesutGoHost(uri: Uri): Boolean = isTrusted(uri)
}
