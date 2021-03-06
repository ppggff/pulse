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

package com.zutubi.pulse.core.model;

import java.util.Comparator;

/**
 * A comparator implementation that can be used to compare entities by
 * their ids.
 */
public class EntityComparator<T extends Entity> implements Comparator<T>
{
    public int compare(T o1, T o2)
    {
        long diff = o2.getId() - o1.getId();
        if (diff > 0)
        {
            return -1;
        }
        if (diff < 0)
        {
            return 1;
        }
        return 0;
    }
}
