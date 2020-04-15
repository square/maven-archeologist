/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.tools.maven.resolution

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MavenVersionTest {
  @Test fun `test compare even versions`() {
    assertThat(v("1.9.3.4")).isLessThan(v("1.10.2.5"))
    assertThat(v("1.2")).isLessThan(v("1.3"))
    assertThat(v("1.5.2")).isEquivalentAccordingToCompareTo(v("1.5.2"))
  }

  @Test fun `test compare uneven versions`() {
    assertThat(v("1.9.3")).isLessThan(v("1.9.3.4"))

    // this seems wrong, but the use of "." separator makes it a sub-version, not a qualified version
    assertThat(v("1.9.3")).isLessThan(v("1.9.3.alpha"))
  }

  @Test fun `test compare qualified versions`() {
    assertThat(v("1.5.2-RC1")).isEquivalentAccordingToCompareTo(v("1.5.2-RC1"))
    assertThat(v("1.5.2-RC1")).isLessThan(v("1.5.2-RC2"))

    // This is contrary to developer expectation, but "beta" isn't a separator. so this is lexically
    // sorted
    // TODO special case alpha/beta/gamma/rc and treat them as special separators
    assertThat(v("1.5.2beta10")).isLessThan(v("1.5.2beta2"))
  }

  @Test fun `test plain greater than qualified`() {
    assertThat(v("1.5.2")).isGreaterThan(v("1.5.2-RC1"))
    assertThat(v("1.5.2-RC1")).isLessThan(v("1.5.2"))
    assertThat(v("1.5.2")).isGreaterThan(v("1.5.2-beta"))
  }

  @Test fun `test unseparated qualifiers`() {
    assertThat(v("1.5.2")).isLessThan(v("1.5.2beta2")) // TODO fix this.
  }

  @Test fun `test release greater than snapshots`() {
    assertThat(v("1.5.2")).isGreaterThan(v("1.5.2-SNAPSHOT"))
    assertThat(v("1.5.2-beta")).isGreaterThan(v("1.5.2-beta-SNAPSHOT"))
    assertThat(v("1.5.2alpha1")).isGreaterThan(v("1.5.2alpha1-SNAPSHOT"))
  }

  @Test fun `test short versions`() {
    assertThat(v("1")).isEquivalentAccordingToCompareTo(v("1"))
    assertThat(v("1")).isLessThan(v("2"))
    assertThat(v("2")).isGreaterThan(v("1"))
    assertThat(v("1-SNAPSHOT")).isLessThan(v("1"))
    assertThat(v("1-beta")).isLessThan(v("1"))
  }

  @Test fun `test numericCompareTo both numbers`() {
    assertThat("5".numberOrStringComparison("5") == 0).isTrue()
    assertThat("4".numberOrStringComparison("5") < 0).isTrue()
    assertThat("5".numberOrStringComparison("4") > 0).isTrue()
    assertThat("10".numberOrStringComparison("9") > 0).isTrue()
  }

  @Test fun `test numericCompareTo both non-numbers`() {
    assertThat("a5".numberOrStringComparison("a5") == 0).isTrue()
    assertThat("4a".numberOrStringComparison("5a") < 0).isTrue()
    assertThat("c5".numberOrStringComparison("c4") > 0).isTrue()
    assertThat("a10".numberOrStringComparison("a9") < 0).isTrue()
  }

  @Test fun `test VersionElement no qualifiers`() {
    assertThat(ve("5")).isEquivalentAccordingToCompareTo(ve("5"))
    assertThat(ve("5")).isLessThan(ve("6"))
    assertThat(ve("6")).isGreaterThan(ve("5"))
    assertThat(ve("a10")).isLessThan(ve("a9")) // not a number, lexical ordering.
  }

  @Test fun `test VersionElement with qualifiers`() {
    assertThat(ve("5-1")).isLessThan(ve("5-2")) // numeric qualifier
    assertThat(ve("5-01")).isLessThan(ve("5-2")) // numeric qualifier with extra steps

    assertThat(ve("5")).isGreaterThan(ve("5-2")) // no qualifier
    assertThat(ve("5-RC1")).isLessThan(ve("5")) // no qualifier
  }

  // These special cases aren't yet handled, so this test confirms they're lexically sorted.
  // TODO handle these cases specially.
  @Test fun `test VersionElement with special-case qualifiers`() {
    assertThat(ve("5")).isLessThan(ve("5beta")) // numeric qualifier
    assertThat(ve("5beta10")).isLessThan(ve("5beta2")) // numeric qualifier with extra steps
    assertThat(ve("5-RC10")).isLessThan(ve("5-RC2")) // qualifier isn't numeric
    assertThat(ve("5-beta1")).isGreaterThan(ve("5-2")) // not a number, lexical ordering.
  }

  @Test fun `test VersionElement with snapshots`() {
    assertThat(ve("5")).isGreaterThan(ve("5-SNAPSHOT"))
    assertThat(ve("5-SNAPSHOT")).isLessThan(ve("5")) // Release trumps snapshot

    // 5 is greater than 5-2, even 5-snapshot. 5 and 5-2 are compared first.
    assertThat(ve("5-SNAPSHOT")).isGreaterThan(ve("5-2"))
  }

  @Test fun `test VersionElement terminal with non-terminal`() {
    assertThat(ve("5", false)).isGreaterThan(ve("5-SNAPSHOT"))
    assertThat(ve("6", false)).isGreaterThan(ve("5-SNAPSHOT"))
    assertThat(ve("5-a", false)).isGreaterThan(ve("5-SNAPSHOT"))
    assertThat(ve("5a", false)).isGreaterThan(ve("5a-SNAPSHOT"))

    // This one is counter-intuitive, but because non-terminal version numbers don't have
    // qualifiers, and terminal ones do, 5-a is treated as "raw", but it's compared to "5"
    // (from 5-a-SNAPSHOT), because the latter is terminal, so it's parsed. "5-a" is lexically
    // greater than "5" (since it's not a numeric, because the former isn't parsed), so we get
    // the below behavior.
    // This is a rare edge-case (comparing 1.3.5-a.4 vs. 1.3.5-a-SNAPSHOT). These are incoherent
    // version numbers and there's no good way to interpret them.
    assertThat(ve("5-a", false)).isGreaterThan(ve("5-a-SNAPSHOT"))
  }

  @Test fun `test VersionElement no dots`() {
    assertThrows(IllegalArgumentException::class.java) { ve("5.1") }
  }

  private fun ve(raw: String, terminal: Boolean = true): VersionElement =
      VersionElement.from(raw, terminal)

  private fun v(raw: String): MavenVersion = MavenVersion.from(raw)
}
