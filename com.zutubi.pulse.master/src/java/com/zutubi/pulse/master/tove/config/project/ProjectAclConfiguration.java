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

package com.zutubi.pulse.master.tove.config.project;

import com.zutubi.pulse.master.tove.config.group.GroupConfiguration;
import com.zutubi.tove.annotations.*;
import com.zutubi.tove.config.api.AbstractConfiguration;
import com.zutubi.validation.annotations.Required;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents the authority to perform some action on some project.
 */
@SymbolicName("zutubi.projectAclConfig")
@Table(columns = {"group", "allowedActions"})
@Form(fieldOrder = {"group", "allowedActions"})
@PermissionConfiguration
public class ProjectAclConfiguration extends AbstractConfiguration
{
    @Reference
    @Required
    private GroupConfiguration group;
    @ItemPicker(optionProvider = "ProjectAuthorityProvider") @Ordered(allowReordering = false)
    private List<String> allowedActions = new LinkedList<String>();

    public ProjectAclConfiguration()
    {
    }

    public ProjectAclConfiguration(GroupConfiguration group, String... allowedActions)
    {
        this.group = group;
        this.allowedActions.addAll(Arrays.asList(allowedActions));
    }

    public GroupConfiguration getGroup()
    {
        return group;
    }

    public void setGroup(GroupConfiguration group)
    {
        this.group = group;
    }

    public List<String> getAllowedActions()
    {
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions)
    {
        this.allowedActions = allowedActions;
    }
}
