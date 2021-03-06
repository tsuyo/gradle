/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.gradle.api.*;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskFactory;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.specs.Spec;
import org.gradle.util.*;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import spock.lang.Issue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public abstract class AbstractTaskTest {
    public static final String TEST_TASK_NAME = "taskname";
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    private AbstractProject project;

    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private static final ITaskFactory TASK_FACTORY = new AnnotationProcessingTaskFactory(new TaskFactory(new AsmBackedClassGenerator()));

    @Before
    public void setUp() {
        project = HelperUtil.createRootProject();
    }

    public abstract AbstractTask getTask();

    public <T extends AbstractTask> T createTask(Class<T> type) {
        return createTask(type, project, TEST_TASK_NAME);
    }

    public Task createTask(Project project, String name) {
        return createTask(getTask().getClass(), project, name);
    }

    public <T extends AbstractTask> T createTask(Class<T> type, Project project, String name) {
        Task task = TASK_FACTORY.createTask((ProjectInternal) project,
                GUtil.map(Task.TASK_TYPE, type,
                        Task.TASK_NAME, name));
        assertTrue(type.isAssignableFrom(task.getClass()));
        return type.cast(task);
    }

    @Test
    public void testTask() {
        assertTrue(getTask().isEnabled());
        assertEquals(TEST_TASK_NAME, getTask().getName());
        assertNull(getTask().getDescription());
        assertSame(project, getTask().getProject());
        assertNotNull(getTask().getStandardOutputCapture());
        assertEquals(new HashMap(), getTask().getAdditionalProperties());
        assertNotNull(getTask().getInputs());
        assertNotNull(getTask().getOutputs());
        assertNotNull(getTask().getOnlyIf());
        assertTrue(getTask().getOnlyIf().isSatisfiedBy(getTask()));
    }

    @Test
    public void testPath() {
        DefaultProject rootProject = HelperUtil.createRootProject();
        DefaultProject childProject = HelperUtil.createChildProject(rootProject, "child");
        childProject.getProjectDir().mkdirs();
        DefaultProject childchildProject = HelperUtil.createChildProject(childProject, "childchild");
        childchildProject.getProjectDir().mkdirs();

        Task task = createTask(rootProject, TEST_TASK_NAME);
        assertEquals(Project.PATH_SEPARATOR + TEST_TASK_NAME, task.getPath());
        task = createTask(childProject, TEST_TASK_NAME);
        assertEquals(Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + TEST_TASK_NAME, task.getPath());
        task = createTask(childchildProject, TEST_TASK_NAME);
        assertEquals(Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + "childchild" + Project.PATH_SEPARATOR + TEST_TASK_NAME, task.getPath());
    }

    @Test
    public void testDependsOn() {
        Task dependsOnTask = createTask(project, "somename");
        Task task = createTask(project, TEST_TASK_NAME);
        project.getTasks().add("path1");
        project.getTasks().add("path2");

        task.dependsOn(Project.PATH_SEPARATOR + "path1");
        assertThat(task, dependsOn("path1"));
        task.dependsOn("path2", dependsOnTask);
        assertThat(task, dependsOn("path1", "path2", "somename"));
    }

    @Test
    public void testToString() {
        assertEquals("task '" + getTask().getPath() + "'", getTask().toString());
    }

    @Test
    public void testDoFirst() {
        Action<Task> action1 = createTaskAction();
        Action<Task> action2 = createTaskAction();
        int actionSizeBefore = getTask().getActions().size();
        assertSame(getTask(), getTask().doFirst(action2));
        assertEquals(actionSizeBefore + 1, getTask().getActions().size());
        assertEquals(action2, getTask().getActions().get(0));
        assertSame(getTask(), getTask().doFirst(action1));
        assertEquals(action1, getTask().getActions().get(0));
    }

    @Test
    public void testDoLast() {
        Action<Task> action1 = createTaskAction();
        Action<Task> action2 = createTaskAction();
        int actionSizeBefore = getTask().getActions().size();
        assertSame(getTask(), getTask().doLast(action1));
        assertEquals(actionSizeBefore + 1, getTask().getActions().size());
        assertEquals(action1, getTask().getActions().get(getTask().getActions().size() - 1));
        assertSame(getTask(), getTask().doLast(action2));
        assertEquals(action2, getTask().getActions().get(getTask().getActions().size() - 1));
    }

    @Test
    public void testDeleteAllActions() {
        Action<Task> action1 = createTaskAction();
        Action<Task> action2 = createTaskAction();
        getTask().doLast(action1);
        getTask().doLast(action2);
        assertSame(getTask(), getTask().deleteAllActions());
        assertEquals(new ArrayList(), getTask().getActions());
    }

    @Test(expected = InvalidUserDataException.class)
    public void testAddActionWithNull() {
        getTask().doLast((Closure) null);
    }

    @Test
    public void testAddActionsWithClosures() {
        GroovyTaskTestHelper.checkAddActionsWithClosures(getTask());
    }

    @Test
    public void testExecuteDelegatesToTaskExecuter() {
        final AbstractTask task = getTask();

        final TaskExecuter executer = context.mock(TaskExecuter.class);
        task.setExecuter(executer);

        context.checking(new Expectations(){{
            one(executer).execute(with(sameInstance(task)), with(notNullValue(TaskStateInternal.class)));
        }});

        task.execute();
    }

    @Test
    public void testConfigure() {
        getTask().setActions(new ArrayList());
        GroovyTaskTestHelper.checkConfigure(getTask());
    }

    public AbstractProject getProject() {
        return project;
    }

    public void setProject(AbstractProject project) {
        this.project = project;
    }

    @Test
    public void disableStandardOutCapture() {
        getTask().disableStandardOutputCapture();
        assertFalse(getTask().getLogging().isStandardOutputCaptureEnabled());
    }

    @Test
    public void captureStandardOut() {
        getTask().captureStandardOutput(LogLevel.DEBUG);
        assertTrue(getTask().getLogging().isStandardOutputCaptureEnabled());
        assertEquals(LogLevel.DEBUG, getTask().getLogging().getStandardOutputCaptureLevel());
    }

    @Test
    public void setGetDescription() {
        String testDescription = "testDescription";
        getTask().setDescription(testDescription);
        assertEquals(testDescription, getTask().getDescription());
    }

    @Test
    public void canSpecifyOnlyIfPredicateUsingClosure() {
        AbstractTask task = getTask();
        assertTrue(task.getOnlyIf().isSatisfiedBy(task));

        task.onlyIf(HelperUtil.toClosure("{ task -> false }"));
        assertFalse(task.getOnlyIf().isSatisfiedBy(task));
    }

    @Test
    public void canSpecifyOnlyIfPredicateUsingSpec() {
        final Spec<Task> spec = context.mock(Spec.class);

        final AbstractTask task = getTask();
        assertTrue(task.getOnlyIf().isSatisfiedBy(task));

        context.checking(new Expectations() {{
            allowing(spec).isSatisfiedBy(task);
            will(returnValue(false));
        }});

        task.onlyIf(spec);
        assertFalse(task.getOnlyIf().isSatisfiedBy(task));
    }

    @Test
    public void onlyIfPredicateIsTrueWhenTaskIsEnabledAndAllPredicatesAreTrue() {
        final AtomicBoolean condition1 = new AtomicBoolean(true);
        final AtomicBoolean condition2 = new AtomicBoolean(true);

        AbstractTask task = getTask();
        task.onlyIf(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return condition1.get();
            }
        });
        task.onlyIf(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return condition2.get();
            }
        });

        assertTrue(task.getOnlyIf().isSatisfiedBy(task));

        task.setEnabled(false);
        assertFalse(task.getOnlyIf().isSatisfiedBy(task));

        task.setEnabled(true);
        condition1.set(false);
        assertFalse(task.getOnlyIf().isSatisfiedBy(task));

        condition1.set(true);
        condition2.set(false);
        assertFalse(task.getOnlyIf().isSatisfiedBy(task));

        condition2.set(true);
        assertTrue(task.getOnlyIf().isSatisfiedBy(task));
    }

    @Test
    public void canReplaceOnlyIfSpec() {
        final AtomicBoolean condition1 = new AtomicBoolean(true);
        AbstractTask task = getTask();
        task.onlyIf(context.mock(Spec.class, "spec1"));
        task.setOnlyIf(new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return condition1.get();
            }
        });

        assertTrue(task.getOnlyIf().isSatisfiedBy(task));

        task.setEnabled(false);
        assertFalse(task.getOnlyIf().isSatisfiedBy(task));

        task.setEnabled(true);
        condition1.set(false);
        assertFalse(task.getOnlyIf().isSatisfiedBy(task));

        condition1.set(true);
        assertTrue(task.getOnlyIf().isSatisfiedBy(task));
    }

    @Test
    public void testDependentTaskDidWork() {
        final Task task1 = context.mock(Task.class, "task1");
        final Task task2 = context.mock(Task.class, "task2");
        final TaskDependency dependencyMock = context.mock(TaskDependency.class);
        getTask().dependsOn(dependencyMock);
        context.checking(new Expectations() {{
            allowing(dependencyMock).getDependencies(getTask()); will(returnValue(WrapUtil.toSet(task1, task2)));

            exactly(2).of(task1).getDidWork();
            will(returnValue(false));

            exactly(2).of(task2).getDidWork();
            will(onConsecutiveCalls(returnValue(false), returnValue(true)));
        }});

        assertFalse(getTask().dependsOnTaskDidWork());

        assertTrue(getTask().dependsOnTaskDidWork());
    }

    public static Action<Task> createTaskAction() {
        return new Action<Task>() {
            public void execute(Task task) {

            }
        };
    }
    
    @Test
    @Issue("http://issues.gradle.org/browse/GRADLE-2022")
    public void testGoodErrorMessageWhenTaskInstantiatedDirectly() {
        try {
            Class<? extends AbstractTask> clazz = getTask().getClass();
            clazz.newInstance();
            throw new RuntimeException("Direct instantiation of " + clazz + " should have produced an exception");
        } catch (Exception e) {
            assertEquals(TaskInstantiationException.class, e.getClass());
            assert e.getMessage().contains("has been instantiated directly which is not supported");
        }
    }
}
