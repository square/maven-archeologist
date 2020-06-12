package com.squareup.tools.maven.resolution

import com.google.common.truth.Truth.assertThat
import com.squareup.tools.maven.resolution.ProxyEnvParseResult.ProxyConfig
import okhttp3.Authenticator
import org.junit.Test

class HttpProxyTest {

  @Test fun testHttpProxyParsing() {
    val result = ProxyUtils.getConfig("http://www.myproxy.com")
    assertThat(result).isInstanceOf(ProxyEnvParseResult.ProxyConfig::class.java)
    val proxyConfig = result as ProxyConfig

    assertThat(proxyConfig.hostname).isEqualTo("www.myproxy.com")
    assertThat(proxyConfig.port).isEqualTo(80)
  }

  @Test fun testHttpsProxyParsing() {
    val result = ProxyUtils.getConfig("https://www.myproxy.com")
    assertThat(result).isInstanceOf(ProxyEnvParseResult.ProxyConfig::class.java)
    val proxyConfig = result as ProxyConfig

    assertThat(proxyConfig.hostname).isEqualTo("www.myproxy.com")
    assertThat(proxyConfig.port).isEqualTo(443)
  }

  @Test fun testProxyParsingError() {
    val result = ProxyUtils.getConfig("http:/www.myproxy.com")
    assertThat(result).isInstanceOf(ProxyEnvParseResult.Error::class.java)
  }

  @Test fun testProxyUserNamePassword() {
    val result = ProxyUtils.getConfig("https://userid:password@www.myproxy.com")
    assertThat(result).isInstanceOf(ProxyEnvParseResult.ProxyConfig::class.java)

    val proxyConfig = result as ProxyConfig
    assertThat(proxyConfig.hostname).isEqualTo("www.myproxy.com")
    assertThat(proxyConfig.port).isEqualTo(443)
    assertThat(proxyConfig.username).isEqualTo("userid")
    assertThat(proxyConfig.password).isEqualTo("password")
  }

  @Test fun testProxyExempt() {
    val proxyConfig = ProxyUtils.getConfig("http://www.myproxy.com")
    var result = ProxyUtils.checkUrlIfProxyExempt(proxyConfig, "http://www.cnn.com", ".cnn.com")
    assertThat(result).isInstanceOf(ProxyExemptParseResult.Exempt::class.java)
  }

  @Test fun testProxyNotExempt() {
    val proxyConfig = ProxyUtils.getConfig("http://www.myproxy.com")
    var result = ProxyUtils.checkUrlIfProxyExempt(proxyConfig, "http://www.cnn.com", ".abcnews.com")
    assertThat(result).isInstanceOf(ProxyExemptParseResult.NotExempt::class.java)
  }

  @Test fun testAuthenticator() {
    val proxyConfig = ProxyUtils.getConfig("https://userid:password@www.myproxy.com")
    val result = ProxyUtils.createAuthenticatorIfNecessary(proxyConfig)
    assertThat(result).isInstanceOf(Authenticator::class.java)
  }

  @Test fun testNoAuthenticator() {
    val proxyConfig = ProxyUtils.getConfig("https://www.myproxy.com")
    val result = ProxyUtils.createAuthenticatorIfNecessary(proxyConfig)
    assertThat(result).isNull()
  }
}
