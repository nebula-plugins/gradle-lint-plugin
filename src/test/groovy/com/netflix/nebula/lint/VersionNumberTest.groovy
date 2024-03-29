package com.netflix.nebula.lint


import org.gradle.util.internal.VersionNumber
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import spock.lang.Specification

class VersionNumberTest extends Specification {
    def "construction"() {
        expect:
        VersionNumber.version(5) == new VersionNumber(5, 0, 0, null)
    }

    def "parsing"() {
        expect:
        VersionNumber.parse("1") == new VersionNumber(1, 0, 0, null)
        VersionNumber.parse("1.0") == new VersionNumber(1, 0, 0, null)
        VersionNumber.parse("1.0.0") == new VersionNumber(1, 0, 0, null)

        VersionNumber.parse("1.2") == new VersionNumber(1, 2, 0, null)
        VersionNumber.parse("1.2.3") == new VersionNumber(1, 2, 3, null)

        VersionNumber.parse("1-rc1-SNAPSHOT") == new VersionNumber(1, 0, 0, "rc1-SNAPSHOT")
        VersionNumber.parse("1.2-rc1-SNAPSHOT") == new VersionNumber(1, 2, 0, "rc1-SNAPSHOT")
        VersionNumber.parse("1.2.3-rc1-SNAPSHOT") == new VersionNumber(1, 2, 3, "rc1-SNAPSHOT")

        VersionNumber.parse("1.rc1-SNAPSHOT") == new VersionNumber(1, 0, 0, "rc1-SNAPSHOT")
        VersionNumber.parse("1.2.rc1-SNAPSHOT") == new VersionNumber(1, 2, 0, "rc1-SNAPSHOT")
        VersionNumber.parse("1.2.3.rc1-SNAPSHOT") == new VersionNumber(1, 2, 3, "rc1-SNAPSHOT")

        VersionNumber.parse("11.22") == new VersionNumber(11, 22, 0, null)
        VersionNumber.parse("11.22.33") == new VersionNumber(11, 22, 33, null)
        VersionNumber.parse("11.22.33-eap") == new VersionNumber(11, 22, 33, "eap")

        VersionNumber.parse("11.fortyfour") == new VersionNumber(11, 0, 0, "fortyfour")

        VersionNumber.parse("1.0.0.0") == new VersionNumber(1, 0, 0, "0")
        VersionNumber.parse("1.0.0.0.0.0.0") == new VersionNumber(1, 0, 0, "0.0.0.0")
        VersionNumber.parse("1.2.3.4-rc1-SNAPSHOT") == new VersionNumber(1, 2, 3, "4-rc1-SNAPSHOT")
        VersionNumber.parse("1.2.3.4.rc1-SNAPSHOT") == new VersionNumber(1, 2, 3, "4.rc1-SNAPSHOT")
    }

    def "parsing with patch number"() {
        expect:
        def defaultScheme = VersionNumber.scheme()
        defaultScheme.parse("1") == new VersionNumber(1, 0, 0, null)
        defaultScheme.parse("1.2") == new VersionNumber(1, 2, 0, null)
        defaultScheme.parse("1.2.3") == new VersionNumber(1, 2, 3, null)
        defaultScheme.parse("1.2.3-qualifier") == new VersionNumber(1, 2, 3, "qualifier")
        defaultScheme.parse("1.2.3.4") == new VersionNumber(1, 2, 3, "4")

        def patchScheme = VersionNumber.withPatchNumber()
        patchScheme.parse("1") == new VersionNumber(1, 0, 0, null)
        patchScheme.parse("1.2") == new VersionNumber(1, 2, 0, null)
        patchScheme.parse("1.2.3") == new VersionNumber(1, 2, 3, null)
        patchScheme.parse("1.2.3.4") == new VersionNumber(1, 2, 3, 4, null)
        patchScheme.parse("1.2.3_4") == new VersionNumber(1, 2, 3, 4, null)
        patchScheme.parse("1.2.3.4-qualifier") == new VersionNumber(1, 2, 3, 4, "qualifier")
        patchScheme.parse("1.2.3.4.qualifier") == new VersionNumber(1, 2, 3, 4, "qualifier")
        patchScheme.parse("1.2.3.4.5.6") == new VersionNumber(1, 2, 3, 4, "5.6")
    }

    def "unparseable version number is represented as UNKNOWN (0.0.0.0)"() {
        expect:
        VersionNumber.parse(null) == VersionNumber.UNKNOWN
        VersionNumber.parse("") == VersionNumber.UNKNOWN
        VersionNumber.parse("foo") == VersionNumber.UNKNOWN
        VersionNumber.parse("1.") == VersionNumber.UNKNOWN
        VersionNumber.parse("1.2.3-") == VersionNumber.UNKNOWN
        VersionNumber.parse(".") == VersionNumber.UNKNOWN
        VersionNumber.parse("_") == VersionNumber.UNKNOWN
        VersionNumber.parse("-") == VersionNumber.UNKNOWN
        VersionNumber.parse(".1") == VersionNumber.UNKNOWN
        VersionNumber.parse("a.1") == VersionNumber.UNKNOWN
        VersionNumber.parse("1_2") == VersionNumber.UNKNOWN
        VersionNumber.parse("1_2_2") == VersionNumber.UNKNOWN
        VersionNumber.parse("1.2.3_4") == VersionNumber.UNKNOWN
    }
    def "accessors"() {
        when:
        def version = new VersionNumber(1, 2, 3, 4, "foo")
        then:
        version.major == 1
        version.minor == 2
        version.micro == 3
        version.patch == 4
        version.qualifier == "foo"
    }
    def "string representation"() {
        expect:
        VersionNumber.parse("1.0").toString() == "1.0.0"
        VersionNumber.parse("1.2.3").toString() == "1.2.3"
        VersionNumber.parse("1.2.3.4").toString() == "1.2.3-4"
        VersionNumber.parse("1-rc-1").toString() == "1.0.0-rc-1"
        VersionNumber.parse("1.2.3-rc-1").toString() == "1.2.3-rc-1"
        def patchScheme = VersionNumber.withPatchNumber()
        patchScheme.parse("1").toString() == "1.0.0.0"
        patchScheme.parse("1.2").toString() == "1.2.0.0"
        patchScheme.parse("1.2.3").toString() == "1.2.3.0"
        patchScheme.parse("1.2.3.4").toString() == "1.2.3.4"
        patchScheme.parse("1.2-rc-1").toString() == "1.2.0.0-rc-1"
    }
    def "equality"() {
        def version = new VersionNumber(1, 1, 1, 1, null)
        def qualified = new VersionNumber(1, 1, 1, 1, "beta-2")
        expect:
        new VersionNumber(1, 1, 1, 1, null) strictlyEqual(version)
        new VersionNumber(2, 1, 1, 1, null) != version
        new VersionNumber(1, 2, 1, 1, null) != version
        new VersionNumber(1, 1, 2, 1, null) != version
        new VersionNumber(1, 1, 1, 2, null) != version
        new VersionNumber(1, 1, 1, 1, "rc") != version
        new VersionNumber(1, 1, 1, 1, "beta-2") strictlyEqual(qualified)
        new VersionNumber(1, 1, 1, 1, "beta-3") != qualified
    }
    def "comparison"() {
        expect:
        (new VersionNumber(1, 1, 1, null) <=> new VersionNumber(1, 1, 1, null)) == 0
        (new VersionNumber(1, 1, 1, null) <=> new VersionNumber(1, 1, 1, 0, null)) == 0
        new VersionNumber(2, 1, 1, null) > new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 2, 1, null) > new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 2, null) > new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 1, 2, null) > new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 1, "rc") < new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 1, "beta") > new VersionNumber(1, 1, 1, "alpha")
        new VersionNumber(1, 1, 1, "RELEASE") > new VersionNumber(1, 1, 1, "beta")
        new VersionNumber(1, 1, 1, "SNAPSHOT") < new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 1, null) < new VersionNumber(2, 1, 1, null)
        new VersionNumber(1, 1, 1, null) < new VersionNumber(1, 2, 1, null)
        new VersionNumber(1, 1, 1, null) < new VersionNumber(1, 1, 2, null)
        new VersionNumber(1, 1, 1, null) > new VersionNumber(1, 1, 1, "rc")
        new VersionNumber(1, 1, 1, "alpha") < new VersionNumber(1, 1, 1, "beta")
        new VersionNumber(1, 1, 1, "beta") < new VersionNumber(1, 1, 1, "RELEASE")
    }
    def "base version"() {
        expect:
        new VersionNumber(1, 2, 3, null).baseVersion == new VersionNumber(1, 2, 3, null)
        new VersionNumber(1, 2, 3, "beta").baseVersion == new VersionNumber(1, 2, 3, null)
    }

    static <T> Matcher<T> strictlyEqual(final T other) {
        return new BaseMatcher<T>() {
            @Override
            boolean matches(Object o) {
                return strictlyEquals(o, other)
            }

            @Override
            void describeTo(Description description) {
                description.appendText("an Object that strictly equals ").appendValue(other)
            }
        }
    }

    static boolean strictlyEquals(Object a, Object b) {
        if (!a.equals(b)) {
            return false
        }
        if (!b.equals(a)) {
            return false
        }
        if (!a.equals(a)) {
            return false
        }
        if (b.equals(new Object())) {
            return false
        }
        return a.hashCode() == b.hashCode()
    }
}
