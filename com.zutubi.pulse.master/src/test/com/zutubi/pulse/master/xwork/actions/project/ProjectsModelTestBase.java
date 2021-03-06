/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.pulse.master.xwork.actions.project;

import com.google.common.base.Function;
import com.zutubi.pulse.core.test.api.PulseTestCase;
import com.zutubi.pulse.master.model.BuildResult;
import com.zutubi.pulse.master.model.Project;
import com.zutubi.pulse.master.tove.config.LabelConfiguration;
import com.zutubi.pulse.master.tove.config.project.ProjectConfiguration;
import com.zutubi.pulse.master.tove.config.user.BrowseViewConfiguration;
import com.zutubi.pulse.master.tove.config.user.ProjectsSummaryConfiguration;
import com.zutubi.pulse.master.webwork.Urls;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

public abstract class ProjectsModelTestBase extends PulseTestCase
{
    private long nextId = 1;

    protected ProjectsSummaryConfiguration config = new BrowseViewConfiguration();
    protected Urls urls = Urls.getBaselessInstance();
    private Map<String, ProjectsModel> groups = new HashMap<String, ProjectsModel>();

    protected void assertProjectsModelLists(List<ProjectsModel> expectedModels, List<ProjectsModel> gotModels)
    {
        assertEquals(expectedModels.size(), gotModels.size());
        for (int i = 0; i < expectedModels.size(); i++)
        {
            assertProjectsModels(expectedModels.get(i), gotModels.get(i));
        }
    }

    protected void assertProjectsModels(ProjectsModel expected, ProjectsModel got)
    {
        assertEquals(expected.isLabelled(), got.isLabelled());
        if (expected.isLabelled())
        {
            assertEquals(expected.getGroupName(), got.getGroupName());
        }

        assertTemplateModels(expected.getRoot(), got.getRoot());
    }

    protected void assertModels(ProjectModel expected, ProjectModel got)
    {
        assertEquals(expected.getName(), got.getName());
        assertSame(expected.getClass(), got.getClass());

        if (expected instanceof TemplateProjectModel)
        {
            assertTemplateModels((TemplateProjectModel) expected, (TemplateProjectModel) got);
        }
        else
        {
            assertConcreteModels((ConcreteProjectModel) expected, (ConcreteProjectModel) got);
        }
    }

    protected void assertTemplateModels(TemplateProjectModel expected, TemplateProjectModel got)
    {
        List<ProjectModel> expectedChildren = expected.getChildren();
        List<ProjectModel> gotChildren = got.getChildren();
        assertEquals(expectedChildren.size(), gotChildren.size());
        for (int i = 0; i < expectedChildren.size(); i++)
        {
            assertModels(expectedChildren.get(i), gotChildren.get(i));
        }
    }

    protected void assertConcreteModels(ConcreteProjectModel expected, ConcreteProjectModel got)
    {
        assertSame(expected.getName(), got.getName());
    }

    protected ProjectsModel createGroup(String label)
    {
        if (!groups.containsKey(label))
        {
            groups.put(label, new ProjectsModel(label, label != null, false));
        }
        return groups.get(label);
    }

    protected ConcreteProjectModel createConcrete(ProjectsModel group, Project project)
    {
        return new ConcreteProjectModel(group, project, Collections.<BuildResult>emptyList(), null, config, urls, Collections.<String>emptySet(), ProjectHealth.UNKNOWN, ProjectMonitoring.NONE);
    }

    protected TemplateProjectModel createTemplates(String label, String projectName, Object... members)
    {
        ProjectsModel group = createGroup(label);
        TemplateProjectModel root = new TemplateProjectModel(null, projectName, false);
        for (Object member : members)
        {
            if (member instanceof String)
            {
                Project project = createProject((String)member);
                root.addChild(createConcrete(group, project));
            }
            else if (member instanceof Project)
            {
                root.addChild(createConcrete(group, (Project)member));
            }
            else
            {
                root.addChild((ProjectModel)member);
            }
        }
        return root;
    }

    protected ProjectsModel createFlatGroup(String label, Object... members)
    {
        ProjectsModel group = createGroup(label);
        for (Object member: members)
        {
            if (member instanceof Project)
            {
                group.getRoot().addChild(createConcrete(group, (Project)member));
            }
            else
            {
                Project project = createProject((String)member);
                group.getRoot().addChild(createConcrete(group, project));
            }
        }
        return group;
    }

    protected ProjectsModel createHierarchicalGroup(String label, Object... members)
    {
        ProjectsModel group = createGroup(label);
        TemplateProjectModel node = group.getRoot();
        for (Object member: members)
        {
            if (member instanceof String)
            {
                Project project = createProject((String)member);
                node.addChild(createConcrete(group, project));
            }
            else if (member instanceof Project)
            {
                node.addChild(createConcrete(group, (Project) member));
            }
            else
            {
                node.addChild((ProjectModel) member);
            }
        }

        return group;
    }

    protected Project createProject(String name, String... labels)
    {
        ProjectConfiguration configuration = new ProjectConfiguration();
        configuration.setName(name);
        configuration.setLabels(newArrayList(transform(asList(labels), new Function<String, LabelConfiguration>()
        {
            public LabelConfiguration apply(String s)
            {
                LabelConfiguration label = new LabelConfiguration();
                label.setLabel(s);
                return label;
            }
        })));

        Project project = new Project();
        project.setId(nextId++);
        project.setConfig(configuration);
        return project;
    }
}
