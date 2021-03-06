/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Project
import org.gradle.util.GradleVersion
import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification

class ToolingApiIntegrationTest extends Specification {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    final ToolingApi toolingApi = new ToolingApi(dist)
    final BasicGradleDistribution otherVersion = dist.previousVersion('1.0-milestone-3')
    TestFile projectDir = dist.testDir

    def "tooling api uses to the current version of gradle when none has been specified"() {
        projectDir.file('build.gradle') << "assert gradle.gradleVersion == '${GradleVersion.current().version}'"

        when:
        Project model = toolingApi.withConnection { connection -> connection.getModel(Project.class) }

        then:
        model != null
    }

    def "tooling api uses the wrapper properties to determine which version to use"() {
        projectDir.file('build.gradle').text = """
task wrapper(type: Wrapper) { distributionUrl = '${otherVersion.binDistribution.toURI()}' }
task check << { assert gradle.gradleVersion == '${otherVersion.version}' }
"""
        dist.executer().withTasks('wrapper').run()

        when:
        toolingApi.withConnector { connector ->
            connector.useDefaultDistribution()
            maybeDisableDaemon(otherVersion, connector)
        }
        toolingApi.withConnection { connection -> connection.newBuild().forTasks('check').run() }

        then:
        notThrown(Throwable)
    }

    def "tooling api searches up from the project directory to find the wrapper properties"() {
        projectDir.file('settings.gradle') << "include 'child'"
        projectDir.file('build.gradle') << """
task wrapper(type: Wrapper) { distributionUrl = '${otherVersion.binDistribution.toURI()}' }
allprojects {
    task check << { assert gradle.gradleVersion == '${otherVersion.version}' }
}
"""
        projectDir.file('child').createDir()
        dist.executer().withTasks('wrapper').run()

        when:
        toolingApi.withConnector { connector ->
            connector.useDefaultDistribution()
            connector.searchUpwards(true)
            connector.forProjectDirectory(projectDir.file('child'))
            maybeDisableDaemon(otherVersion, connector)
        }
        toolingApi.withConnection { connection -> connection.newBuild().forTasks('check').run() }

        then:
        notThrown(Throwable)
    }

    def "can specify a gradle installation to use"() {
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${otherVersion.version}'"

        when:
        toolingApi.withConnector { connector ->
            connector.useInstallation(otherVersion.gradleHomeDir)
            maybeDisableDaemon(otherVersion, connector)
        }
        Project model = toolingApi.withConnection { connection -> connection.getModel(Project.class) }

        then:
        model != null
    }

    def "can specify a gradle distribution to use"() {
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${otherVersion.version}'"

        when:
        toolingApi.withConnector { connector ->
            connector.useDistribution(otherVersion.binDistribution.toURI())
            maybeDisableDaemon(otherVersion, connector)
        }
        Project model = toolingApi.withConnection { connection -> connection.getModel(Project.class) }

        then:
        model != null
    }

    def "can specify a gradle version to use"() {
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${otherVersion.version}'"

        when:
        toolingApi.withConnector { connector ->
            connector.useGradleVersion(otherVersion.version)
            maybeDisableDaemon(otherVersion, connector)
        }
        Project model = toolingApi.withConnection { connection -> connection.getModel(Project.class) }

        then:
        model != null
    }

    def "tooling api reports an error when the specified gradle version does not support the tooling api"() {
        def dist = dist.previousVersion('0.9.2').binDistribution

        when:
        toolingApi.withConnector { connector -> connector.useDistribution(dist.toURI()) }
        def e = toolingApi.maybeFailWithConnection { connection -> connection.getModel(Project.class) }

        then:
        e.class == UnsupportedVersionException
        e.message == "The specified Gradle distribution '${dist.toURI()}' is not supported by this tooling API version (${GradleVersion.current().version}, protocol version 4)"
    }

    def "tooling api honours jvm args specified in gradle.properties"() {
        projectDir.file('build.gradle') << """
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx16m')
assert System.getProperty('some-prop') == 'some-value'
"""
        projectDir.file('gradle.properties') << "org.gradle.jvmargs=-Dsome-prop=some-value -Xmx16m"

        when:
        toolingApi.isEmbedded = false
        Project model = toolingApi.withConnection { connection -> connection.getModel(Project.class) }

        then:
        model != null
    }
    
    private def maybeDisableDaemon(BasicGradleDistribution otherVersion, GradleConnector connector) {
        if (!otherVersion.daemonSupported()) { connector.embedded(true) }
    }
}
