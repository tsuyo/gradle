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

package org.gradle.launcher.daemon.registry

import org.gradle.launcher.daemon.registry.DaemonRegistry.EmptyRegistryException
import org.gradle.messaging.remote.Address
import spock.lang.Specification
import org.gradle.launcher.daemon.server.DomainRegistryUpdater
import org.gradle.launcher.daemon.server.CompletionAware

/**
 * @author: Szczepan Faber, created at: 9/12/11
 */
public class DomainRegistryUpdaterTest extends Specification {

    def registry = Mock(DaemonRegistry)
    def address = {} as Address
    def updater = new DomainRegistryUpdater(registry, address)

    def "marks idle"() {
        when:
        updater.onCompleteActivity({ stopped : false} as CompletionAware )

        then:
        1 * registry.markIdle(address)
    }

    def "avoids marking idle when stopped"() {
        when:
        updater.onCompleteActivity({ stopped : true} as CompletionAware )

        then:
        0 * registry.markIdle(address)
    }

    def "ignores empty cache on marking idle"() {
        given:
        1 * registry.markIdle(address) >> { throw new EmptyRegistryException("") }

        when:
        updater.onCompleteActivity({ stopped : false} as CompletionAware )

        then:
        noExceptionThrown()
    }

    def "marks busy"() {
        when:
        updater.onStartActivity({ stopped : false} as CompletionAware )

        then:
        1 * registry.markBusy(address)
    }

    def "avoids marking busy when stopped"() {
        when:
        updater.onStartActivity({ stopped : true} as CompletionAware )

        then:
        0 * registry.markBusy(address)
    }

    def "ignores empty cache on marking busy"() {
        given:
        1 * registry.markBusy(address) >> { throw new EmptyRegistryException("") }

        when:
        updater.onStartActivity({ stopped : false} as CompletionAware )

        then:
        noExceptionThrown()
    }

     def "ignores empty cache on stopping"() {
        given:
        1 * registry.remove(address) >> { throw new EmptyRegistryException("") }

        when:
        updater.onStop()

        then:
        noExceptionThrown()
    }
}