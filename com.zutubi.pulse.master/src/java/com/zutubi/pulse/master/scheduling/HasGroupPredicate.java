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

package com.zutubi.pulse.master.scheduling;

import com.google.common.base.Predicate;

/**
 * A predicate that is satisfied by any trigger with a specified group.
 */
public class HasGroupPredicate implements Predicate<Trigger>
{
    private String group;

    public HasGroupPredicate(String group)
    {
        this.group = group;
    }

    public boolean apply(Trigger trigger)
    {
        return trigger.getGroup().equals(group);
    }
}
