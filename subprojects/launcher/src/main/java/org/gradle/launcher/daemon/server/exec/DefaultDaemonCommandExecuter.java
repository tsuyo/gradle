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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.server.DaemonStateCoordinator;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.DisconnectAwareConnectionDecorator;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * The default implementation of how to execute commands that the daemon receives.
 */
public class DefaultDaemonCommandExecuter implements DaemonCommandExecuter {

    private final ExecutorFactory executorFactory;
    private final LoggingManagerInternal loggingManager;
    private final GradleLauncherFactory launcherFactory;
    private final ProcessEnvironment processEnvironment;

    public DefaultDaemonCommandExecuter(ServiceRegistry loggingServices, ExecutorFactory executorFactory, ProcessEnvironment processEnvironment) {
        this.executorFactory = executorFactory;
        this.processEnvironment = processEnvironment;
        this.loggingManager = loggingServices.getFactory(LoggingManagerInternal.class).create();
        this.launcherFactory = new DefaultGradleLauncherFactory(loggingServices);
    }

    public void executeCommand(Connection<Object> connection, Command command, DaemonContext daemonContext, DaemonStateCoordinator daemonStateCoordinator) {
        new DaemonCommandExecution(
            new DisconnectAwareConnectionDecorator<Object>(connection, executorFactory.create("DefaultDaemonCommandExecuter > DisconnectAwareConnectionDecorator")),
            command,
            daemonContext,
            daemonStateCoordinator,
            createActions()
        ).proceed();
    }

    protected List<DaemonCommandAction> createActions() {
        return new LinkedList<DaemonCommandAction>(Arrays.asList(
            new StopConnectionAfterExecution(),
            new HandleClientDisconnectBeforeSendingCommand(),
            new CatchAndForwardDaemonFailure(),
            new HandleStop(),
            new StartBuildOrRespondWithBusy(),
            new EstablishBuildEnvironment(processEnvironment),
            new LogToClient(loggingManager), // from this point down, logging is sent back to the client
            new ForwardClientInput(executorFactory),
            new ReturnResult(),
            new ResetDeprecationLogger(),
            new WatchForDisconnection(),
            new ExecuteBuild(launcherFactory)
        ));
    }
}