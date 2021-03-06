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

package com.zutubi.tove.ui.forms;

import com.zutubi.tove.type.EnumType;
import com.zutubi.tove.type.TypeProperty;
import com.zutubi.util.EnumUtils;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An option provider for enums.  By default, all enum values are listed,
 * with a "nice" conversion for typical UPPER_CASE names.
 */
public class EnumOptionProvider extends MapOptionProvider
{
    public Option getEmptyOption(TypeProperty property, FormContext context)
    {
        return new Option("", "");
    }

    public Map<String,String> getMap(TypeProperty property, FormContext context)
    {
        EnumType enumType = (EnumType) property.getType().getTargetType();
        Class<? extends Enum> enumClass = enumType.getClazz();
        Map<String, String> options = new LinkedHashMap<String, String>();

        EnumSet<? extends Enum> allValues = EnumSet.allOf(enumClass);
        for(Enum e: allValues)
        {
            if (includeOption(e))
            {
                options.put(e.toString(), getPrettyName(e));
            }
        }
        
        return options;
    }

    /**
     * Returns true if the given value should be included.  Allows subclasses
     * to filter available values.
     *
     * @param e the value to test
     * @return true for all options by default
     */
    protected boolean includeOption(Enum e)
    {
        return true;
    }

    /**
     * Returns a pretty name for the given value, to display to the user.
     * By default, this is the enum name converted to lower case and with
     * spaces in place of underscores.
     *
     * @param e value to get the pretty name for
     * @return a user-firendly name for the value
     */
    public static String getPrettyName(Enum e)
    {
        return EnumUtils.toPrettyString(e);
    }
}
