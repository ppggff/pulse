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

package com.zutubi.pulse.core.commands;

import com.zutubi.pulse.core.ConfiguredInstanceFactory;
import com.zutubi.pulse.core.commands.api.Artifact;
import com.zutubi.pulse.core.commands.api.ArtifactConfiguration;

/**
 * Default implementation of {@link ArtifactFactory}, which uses the object
 * factory to build artifacts.
 */
public class DefaultArtifactFactory extends ConfiguredInstanceFactory<Artifact, ArtifactConfiguration> implements ArtifactFactory
{
    protected Class<? extends Artifact> getType(ArtifactConfiguration configuration)
    {
        return configuration.artifactType();
    }
}