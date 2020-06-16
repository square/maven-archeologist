package com.squareup.tools.maven.resolution

import com.squareup.tools.maven.resolution.ExemptionStatus.NoProxyConfigured
import com.squareup.tools.maven.resolution.ExemptionStatus.NotExempt
import com.squareup.tools.maven.resolution.ProxyConfig.ConfiguredProxy
import com.squareup.tools.maven.resolution.ProxyConfig.NoProxy
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
  private val config: ProxyConfig = createConfig(getenv("https_proxy") ?: getenv("HTTPS_PROXY"))

  internal fun createConfig(proxyAddress: String?): ProxyConfig {
    return proxyAddress?.run {
      try {
        val matcher = URL_PATTERN.matcher(proxyAddress)
        if (!matcher.matches()) throw IllegalStateException("Invalid URL")

        val protocol = matcher.group(1)!!
        // ignored wrapper group 2 is id and password together
        val username = matcher.group(3)
        val password = matcher.group(4)
        val hostname = matcher.group(5)!!
        val port = matcher.group(6)?.toInt() ?: if (isHttps(protocol)) 443 else 80

        if (username != null && password == null) {
          throw IllegalStateException("No password given for proxy")
        }

        ConfiguredProxy(protocol, username, password, hostname, port)
      } catch (e: java.lang.NumberFormatException) {
        ProxyConfig.Error("Error parsing port in: $proxyAddress $e")
      } catch (e: java.lang.IllegalStateException) {
        ProxyConfig.Error("${e.message}: $proxyAddress")
      }
    } ?: NoProxy
  }

  private fun isHttps(protocol: String?) = when (protocol) {
    "https" -> true
    "http" -> false
    else -> throw IllegalStateException("Invalid proxy protocol")
  }

  fun createProxyingClientFromEnv(url: String, client: OkHttpClient): OkHttpClient {
    val exemptResult = config.isExempt(url, getenv("no_proxy") ?: getenv("NO_PROXY"))
    if (exemptResult is NotExempt) {
      val builder = client.newBuilder()
      builder.proxy(exemptResult.proxy)
      config.authenticator()?.let { builder.authenticator(it) }
    }
    return client
  }
}

sealed class ProxyConfig {
  abstract fun isExempt(url: String, noProxyUrl: String?): ExemptionStatus
  abstract fun authenticator(): Authenticator?

  object NoProxy : ProxyConfig() {
    override fun isExempt(url: String, noProxyUrl: String?): ExemptionStatus = NoProxyConfigured
    override fun authenticator(): Authenticator? = null
  }

  data class Error(val errorMessage: String) : ProxyConfig() {
    override fun isExempt(url: String, noProxyUrl: String?): ExemptionStatus = NoProxyConfigured
    override fun authenticator(): Authenticator? = null
  }

  data class ConfiguredProxy(
    val protocol: String,
    val username: String?,
    val password: String?,
    val hostname: String,
    val port: Int
  ) : ProxyConfig() {
    override fun toString(): String {
      return "$protocol://$hostname:$port"
    }

    override fun isExempt(url: String, noProxyUrl: String?): ExemptionStatus {
      if (!noProxyUrl.isNullOrEmpty()) {
        val noProxyUrlArray: Array<String> = noProxyUrl.split(",".toRegex()).toTypedArray()
        val uri = URI(url)
        val host: String = uri.host
        for (i in noProxyUrlArray.indices) {
          if (noProxyUrlArray[i].startsWith(".")) {
            // This entry applies to sub-domains only.
            if (host.endsWith(noProxyUrlArray[i])) {
              return ExemptionStatus.Exempt(noProxyUrlArray[i])
            }
          } else {
            // This entry applies to the literal hostname and sub-domains.
            if (host == noProxyUrlArray[i] || host.endsWith(".${noProxyUrlArray[i]}")) {
              return ExemptionStatus.Exempt(noProxyUrlArray[i])
            }
          }
        }
      }
      return NotExempt(Proxy(HTTP, InetSocketAddress(hostname, port)))
    }

    override fun authenticator(): Authenticator? =
      if (username != null && password != null) {
        info { "Setting proxy configuration for $this" }
        object : Authenticator {
          override fun authenticate(route: Route?, response: Response): Request? {
            return response.request
              .newBuilder()
              .header("Proxy-Authorization", Credentials.basic(username, password))
              .build()
          }
        }
      } else null
  }
}

sealed class ExemptionStatus {
  object NoProxyConfigured : ExemptionStatus()
  data class Error(val errorMessage: String) : ExemptionStatus()
  data class Exempt(val url: String) : ExemptionStatus()
  data class NotExempt(val proxy: Proxy) : ExemptionStatus()
}
