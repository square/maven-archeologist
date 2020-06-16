package com.squareup.tools.maven.resolution

import com.squareup.tools.maven.resolution.ProxyEnvParseResult.NoConfig
import com.squareup.tools.maven.resolution.ProxyEnvParseResult.ProxyConfig
import com.squareup.tools.maven.resolution.ProxyExemptParseResult.NoProxyConfigured
import com.squareup.tools.maven.resolution.ProxyExemptParseResult.NotExempt
import java.lang.System.getenv
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Proxy.Type.HTTP
import java.net.URI
import java.util.regex.Pattern
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

private val URL_PATTERN =
  Pattern.compile("^(https?)://(([^:@]+?)(?::([^@]+?))?@)?([^:]+)(?::(\\d+))?/?$")

object ProxyHelper {
  private val config: ProxyEnvParseResult by lazy { ProxyUtils.getConfigFromEnvironment() }

  fun createProxyingClientFromEnv(url: String): OkHttpClient {
    val builder = OkHttpClient.Builder()
    val exemptResult = config.isExempt(url, getenv("no_proxy") ?: getenv("NO_PROXY"))
    if (exemptResult is NotExempt) {
      builder.proxy(exemptResult.proxy)
      ProxyUtils.createAuthenticatorIfNecessary(config)?.let { builder.authenticator(it) }
    }
    return builder.build()
  }
}

sealed class ProxyEnvParseResult {
  abstract fun isExempt(url: String, noProxyUrl: String?): ProxyExemptParseResult

  object NoConfig : ProxyEnvParseResult() {
    override fun isExempt(url: String, noProxyUrl: String?) = NoProxyConfigured
  }

  data class Error(val errorMessage: String) : ProxyEnvParseResult() {
    override fun isExempt(url: String, noProxyUrl: String?) = NoProxyConfigured
  }

  data class ProxyConfig(
    val protocol: String,
    val username: String?,
    val password: String?,
    val hostname: String,
    val port: Int
  ) : ProxyEnvParseResult() {
    override fun toString(): String {
      return "$protocol://$hostname:$port"
    }

    override fun isExempt(url: String, noProxyUrl: String?): ProxyExemptParseResult {
      if (!noProxyUrl.isNullOrEmpty()) {
        val noProxyUrlArray: Array<String> = noProxyUrl.split(",".toRegex()).toTypedArray()
        val uri = URI(url)
        val host: String = uri.host
        for (i in noProxyUrlArray.indices) {
          if (noProxyUrlArray[i].startsWith(".")) {
            // This entry applies to sub-domains only.
            if (host.endsWith(noProxyUrlArray[i])) {
              return ProxyExemptParseResult.Exempt(noProxyUrlArray[i])
            }
          } else {
            // This entry applies to the literal hostname and sub-domains.
            if (host == noProxyUrlArray[i] || host.endsWith(".${noProxyUrlArray[i]}")) {
              return ProxyExemptParseResult.Exempt(noProxyUrlArray[i])
            }
          }
        }
      }
      return NotExempt(Proxy(HTTP, InetSocketAddress(hostname, port)))
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
  fun getConfigFromEnvironment(
    proxyAddress: String? = getenv("https_proxy") ?: getenv("HTTPS_PROXY")
  ): ProxyEnvParseResult {
    if (proxyAddress == null) return NoConfig
    val matcher = URL_PATTERN.matcher(proxyAddress)
    if (!matcher.matches()) {
      return ProxyEnvParseResult.Error("Proxy address $proxyAddress is not a valid URL")
    }

    val protocol = matcher.group(1)
    // ignored wrapper group 2 is id and password together
    val username = matcher.group(3)
    val password = matcher.group(4)
    val hostname = matcher.group(5)
    val explicitPort = matcher.group(6)
    val https = when (protocol) {
      "https" -> true
      "http" -> false
      else -> return ProxyEnvParseResult.Error("Invalid proxy protocol for $proxyAddress")
    }

    val port = try {
      explicitPort?.toInt() ?: if (https) 443 else 80 // Default port numbers
    } catch (e: NumberFormatException) {
      return ProxyEnvParseResult.Error("Error parsing proxy port: $proxyAddress $e")
    }

    if (username != null && password == null) {
      return ProxyEnvParseResult.Error("No password given for proxy $proxyAddress")
    }
    return ProxyConfig(protocol, username, password, hostname!!, port)
  }

  // Here there be dragons.
  fun createAuthenticatorIfNecessary(config: ProxyEnvParseResult): Authenticator? =
    if (config is ProxyConfig && config.username != null && config.password != null) {
      info { "Setting proxy configuration $config" }
      object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
          return response.request
            .newBuilder()
            .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
            .build()
        }
      }
    } else null
}
