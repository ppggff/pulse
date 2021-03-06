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

package com.zutubi.pulse.core.commands.nant;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.zutubi.pulse.core.commands.api.OutputProducingCommandSupport;
import com.zutubi.pulse.core.commands.api.OutputProducingCommandTestCase;
import com.zutubi.pulse.core.commands.api.TestCommandContext;
import com.zutubi.pulse.core.commands.core.NamedArgumentCommand;
import com.zutubi.pulse.core.engine.api.ResultState;
import com.zutubi.util.SystemUtils;

import java.io.File;

public class NAntCommandTest extends OutputProducingCommandTestCase
{
    private static final String EXTENSION_XML = "xml";

    public void testBasicDefault() throws Exception
    {
        if (SystemUtils.IS_WINDOWS)
        {
            File destinationFile = new File(baseDir, "default.build");
            Resources.asByteSource(getInputURL(getName(), EXTENSION_XML)).copyTo(Files.asByteSink(destinationFile));

            TestCommandContext context = runCommand(new NamedArgumentCommand(new NAntCommandConfiguration()));
            assertEquals(ResultState.SUCCESS, context.getResultState());
            assertArtifactRegistered(new TestCommandContext.Artifact(OutputProducingCommandSupport.OUTPUT_NAME), context);
        }
    }

    public void testNoBuildFile() throws Exception
    {
        if (SystemUtils.IS_WINDOWS)
        {
            TestCommandContext context = runCommand(new NamedArgumentCommand(new NAntCommandConfiguration()));
            assertEquals(ResultState.FAILURE, context.getResultState());
            assertDefaultOutputContains("Could not find a '*.build' file");
        }
    }

    public void testSetBuildFile() throws Exception
    {
        if (SystemUtils.IS_WINDOWS)
        {
            copyInputToDirectory(EXTENSION_XML, baseDir);

            NAntCommandConfiguration commandConfiguration = new NAntCommandConfiguration();
            commandConfiguration.setBuildFile(getBuildFilename());
            TestCommandContext context = runCommand(new NamedArgumentCommand(commandConfiguration));
            assertEquals(ResultState.SUCCESS, context.getResultState());
        }
    }

    public void testSetBuildFileNonExistant() throws Exception
    {
        if (SystemUtils.IS_WINDOWS)
        {
            NAntCommandConfiguration commandConfiguration = new NAntCommandConfiguration();
            commandConfiguration.setBuildFile("custom.build");
            TestCommandContext context = runCommand(new NamedArgumentCommand(commandConfiguration));
            assertEquals(ResultState.FAILURE, context.getResultState());
            assertDefaultOutputContains("Could not find file", "custom.build");
        }
    }

    public void testSetTargets() throws Exception
    {
        if (SystemUtils.IS_WINDOWS)
        {
            copyInputToDirectory(EXTENSION_XML, baseDir);

            NAntCommandConfiguration commandConfiguration = new NAntCommandConfiguration();
            commandConfiguration.setBuildFile(getBuildFilename());
            commandConfiguration.setTargets("run1 run2");
            TestCommandContext context = runCommand(new NamedArgumentCommand(commandConfiguration));
            assertEquals(ResultState.SUCCESS, context.getResultState());
            assertDefaultOutputContains("run1", "run2");
        }
    }

    private String getBuildFilename()
    {
        return getName() + "." + EXTENSION_XML;
    }
}
