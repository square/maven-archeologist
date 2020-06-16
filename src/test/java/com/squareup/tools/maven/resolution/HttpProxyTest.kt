package com.squareup.tools.maven.resolution

import com.google.common.truth.Truth.assertThat
import com.squareup.tools.maven.resolution.ProxyConfig.ConfiguredProxy
import org.junit.Test

class HttpProxyTest {
  @Test fun testHttpProxyParsing() {
    val config = ProxyHelper.createConfig("http://www.myproxy.com")
    require(config is ConfiguredProxy) { "Should be a Configured Proxy" }
    assertThat(config.hostname).isEqualTo("www.myproxy.com")
    assertThat(config.port).isEqualTo(80)
  }

  @Test fun testHttpsProxyParsing() {
    val config = ProxyHelper.createConfig("https://www.myproxy.com")
    require(config is ConfiguredProxy) { "Should be a Configured Proxy" }
    assertThat(config.hostname).isEqualTo("www.myproxy.com")
    assertThat(config.port).isEqualTo(443)
  }

  @Test fun testProxyParsingError() {
    val config = ProxyHelper.createConfig("http:/www.myproxy.com")
    assertThat(config).isInstanceOf(ProxyConfig.Error::class.java)
  }

  @Test fun testProxyUserNamePassword() {
    val config = ProxyHelper.createConfig("https://userid:password@www.myproxy.com")
    assertThat(config).isInstanceOf(ConfiguredProxy::class.java)

    require(config is ConfiguredProxy) { "Incorrect proxy config type ${config.javaClass}" }
    assertThat(config.hostname).isEqualTo("www.myproxy.com")
    assertThat(config.port).isEqualTo(443)
    assertThat(config.username).isEqualTo("userid")
    assertThat(config.password).isEqualTo("password")
  }

  @Test fun testProxyExempt() {
    val config = ProxyHelper.createConfig("https://www.myproxy.com")
    val status = config.isExempt("http://www.cnn.com", ".cnn.com")
    assertThat(status).isInstanceOf(ExemptionStatus.Exempt::class.java)
  }

  @Test fun testProxyNotExempt() {
    val config = ProxyHelper.createConfig("https://www.myproxy.com")
    val status = config.isExempt("http://www.cnn.com", ".abcnews.com")
    assertThat(status).isInstanceOf(ExemptionStatus.NotExempt::class.java)
  }

  @Test fun testAuthenticator() {
    val config = ProxyHelper.createConfig("https://userid:password@www.myproxy.com")
    assertThat(config.authenticator()).isNotNull()
  }

  @Test fun testNoAuthenticator() {
    val config = ProxyHelper.createConfig("https://www.myproxy.com")
    assertThat(config.authenticator()).isNull()
  }
}
