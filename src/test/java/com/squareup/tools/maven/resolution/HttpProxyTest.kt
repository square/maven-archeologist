package com.squareup.tools.maven.resolution

import com.google.common.truth.Truth.assertThat
import com.squareup.tools.maven.resolution.ProxyEnvParseResult.ProxyConfig
import okhttp3.Authenticator
import org.junit.Test

class HttpProxyTest {
  @Test fun testHttpProxyParsing() {
    val result = ProxyUtils.getConfigFromEnvironment("http://www.myproxy.com")
    assertThat(result).isInstanceOf(ProxyConfig::class.java)
    val proxyConfig = result as ProxyConfig

    assertThat(proxyConfig.hostname).isEqualTo("www.myproxy.com")
    assertThat(proxyConfig.port).isEqualTo(80)
  }

  @Test fun testHttpsProxyParsing() {
    val result = ProxyUtils.getConfigFromEnvironment("https://www.myproxy.com")
    assertThat(result).isInstanceOf(ProxyConfig::class.java)
    val proxyConfig = result as ProxyConfig

    assertThat(proxyConfig.hostname).isEqualTo("www.myproxy.com")
    assertThat(proxyConfig.port).isEqualTo(443)
  }

  @Test fun testProxyParsingError() {
    val result = ProxyUtils.getConfigFromEnvironment("http:/www.myproxy.com")
    assertThat(result).isInstanceOf(ProxyEnvParseResult.Error::class.java)
  }

  @Test fun testProxyUserNamePassword() {
    val result = ProxyUtils.getConfigFromEnvironment("https://userid:password@www.myproxy.com")
    assertThat(result).isInstanceOf(ProxyEnvParseResult.ProxyConfig::class.java)

    require(result is ProxyConfig) { "Incorrect proxy config type ${result.javaClass}" }
    assertThat(result.hostname).isEqualTo("www.myproxy.com")
    assertThat(result.port).isEqualTo(443)
    assertThat(result.username).isEqualTo("userid")
    assertThat(result.password).isEqualTo("password")
  }

  @Test fun testProxyExempt() {
    val proxyConfig = ProxyUtils.getConfigFromEnvironment("http://www.myproxy.com")
    val result = proxyConfig.isExempt("http://www.cnn.com", ".cnn.com")
    assertThat(result).isInstanceOf(ProxyExemptParseResult.Exempt::class.java)
  }

  @Test fun testProxyNotExempt() {
    val proxyConfig = ProxyUtils.getConfigFromEnvironment("http://www.myproxy.com")
    val result = proxyConfig.isExempt("http://www.cnn.com", ".abcnews.com")
    assertThat(result).isInstanceOf(ProxyExemptParseResult.NotExempt::class.java)
  }

  @Test fun testAuthenticator() {
    val proxyConfig = ProxyUtils.getConfigFromEnvironment("https://userid:password@www.myproxy.com")
    val result = ProxyUtils.createAuthenticatorIfNecessary(proxyConfig)
    assertThat(result).isNotNull()
    assertThat(result).isInstanceOf(Authenticator::class.java)
  }

  @Test fun testNoAuthenticator() {
    val proxyConfig = ProxyUtils.getConfigFromEnvironment("https://www.myproxy.com")
    val result = ProxyUtils.createAuthenticatorIfNecessary(proxyConfig)
    assertThat(result).isNull()
  }
}
