package com.squareup.tools.maven.resolution

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.net.URI
import java.net.Proxy
import java.net.InetSocketAddress
import java.util.regex.Pattern

sealed class ProxyEnvParseResult {
  object NoConfig : ProxyEnvParseResult()

  data class Error(val errorMessage: String) : ProxyEnvParseResult()

  data class ProxyConfig(
    val protocol: String,
    val username: String?,
    val password: String?,
    val hostname: String,
    val port: Int
  ) : ProxyEnvParseResult() {
    override fun toString(): String {
      return "${protocol}://${hostname}:${port}"
    }
  }
}

sealed class ProxyExemptParseResult {
  object NoProxyConfigured : ProxyExemptParseResult()

  data class Error(val errorMessage: String) : ProxyExemptParseResult()
  data class Exempt(val url: String) : ProxyExemptParseResult()
  data class NotExempt(val proxy: Proxy) : ProxyExemptParseResult()
}

object ProxyUtils {

  fun getConfig(proxyAddress: String = System.getenv("https_proxy") ?: System.getenv("HTTPS_PROXY")): ProxyEnvParseResult {
    val urlPattern =
      Pattern.compile("^(https?)://(([^:@]+?)(?::([^@]+?))?@)?([^:]+)(?::(\\d+))?/?$")
    val matcher = urlPattern.matcher(proxyAddress)
    if (!matcher.matches()) {
      return ProxyEnvParseResult.Error("Proxy address $proxyAddress is not a valid URL")
    }

    val protocol = matcher.group(1)
    val idAndPassword = matcher.group(2)
    val username = matcher.group(3)
    val password = matcher.group(4)
    val hostname = matcher.group(5)
    val portRaw = matcher.group(6)

    val https: Boolean
    https = when (protocol) {
      "https" -> true
      "http" -> false
      else -> return ProxyEnvParseResult.Error("Invalid proxy protocol for $proxyAddress")
    }
    var port = if (https) 443 else 80 // Default port numbers
    if (portRaw != null) {
      port = try {
        portRaw.toInt()
      } catch (e: NumberFormatException) {
        return ProxyEnvParseResult.Error("Error parsing proxy port: $proxyAddress ${e}")
      }
    }

    if (username != null) {
      if (password == null) {
        return ProxyEnvParseResult.Error("No password given for proxy $proxyAddress")
      }
    }

    return ProxyEnvParseResult.ProxyConfig(
      protocol = protocol, username = username, password = password, hostname = hostname,
      port = port
    )
  }

  fun checkUrlIfProxyExempt(
    proxyEnvConfig: ProxyEnvParseResult,
    requestedUrl: String,
    noProxyUrl: String? = System.getenv("no_proxy") ?: System.getenv("NO_PROXY")
  ): ProxyExemptParseResult {

    if (proxyEnvConfig !is ProxyEnvParseResult.ProxyConfig) {
      return ProxyExemptParseResult.NoProxyConfigured
    }

    if (!noProxyUrl.isNullOrEmpty()) {
      val noProxyUrlArray: Array<String> = noProxyUrl.split(",".toRegex())
        .toTypedArray()
      val uri = URI(requestedUrl)

      val requestedHost: String = uri.getHost()
      for (i in noProxyUrlArray.indices) {
        if (noProxyUrlArray[i]
            .startsWith(".")
        ) {
          // This entry applies to sub-domains only.
          if (requestedHost.endsWith(noProxyUrlArray[i])) {
            return ProxyExemptParseResult.Exempt(noProxyUrlArray[i])
          }
        } else {
          // This entry applies to the literal hostname and sub-domains.
          if (requestedHost == noProxyUrlArray[i] || requestedHost.endsWith(
              "." + noProxyUrlArray[i]
            )
          ) {
            return ProxyExemptParseResult.Exempt(noProxyUrlArray[i])
          }
        }
      }
    }

    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyEnvConfig.hostname, proxyEnvConfig.port))
    return ProxyExemptParseResult.NotExempt(proxy)
  }

  fun createAuthenticatorIfNecessary(proxyEnvConfig: ProxyEnvParseResult) : Authenticator? {
    // Here there be dragons.
    if (proxyEnvConfig is ProxyEnvParseResult.ProxyConfig) {
      if (proxyEnvConfig.username != null && proxyEnvConfig.password != null) {
        info { "Setting proxy configuration $proxyEnvConfig" }

        val credentials = Credentials.basic(proxyEnvConfig.username, proxyEnvConfig.password)

        val proxyAuthenticator = object : Authenticator {
          override fun authenticate(
            route: Route?,
            response: Response
          ): Request? {
            val request = response.request

            return request
              .newBuilder()
              .header("Proxy-Authorization", credentials)
              .build()
          }
        }
        return proxyAuthenticator
      }
    }
    return null
  }
}
