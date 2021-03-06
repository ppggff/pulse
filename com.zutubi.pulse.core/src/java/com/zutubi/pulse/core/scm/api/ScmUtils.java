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

package com.zutubi.pulse.core.scm.api;

import com.google.common.base.Predicate;
import com.google.common.io.ByteSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

/**
 * Helpers shared amongst SCM implementations.
 */
public class ScmUtils
{
    /**
     * An inplace filter that removes changes from the changelist whose paths are not
     * accepted by the filter.
     *
     * @param changelists the changelists whose changes are analysed
     * @param predicate the filter that determines which paths are accepted and which are filtered.
     *
     * @return the filtered list of changelists.
     */
    public static List<Changelist> filter(List<Changelist> changelists, Predicate<String> predicate)
    {
        List<Changelist> filteredChangelists = new LinkedList<Changelist>();

        for (Changelist ch : changelists)
        {
            List<FileChange> changes = new LinkedList<FileChange>();
            for (FileChange c : ch.getChanges())
            {
                if (predicate == null || predicate.apply(c.getPath()))
                {
                    changes.add(c);
                }
            }
            if (changes.size() > 0)
            {
                Changelist filtered = new Changelist(ch.getRevision(), ch.getTime(), ch.getAuthor(), ch.getComment(), changes);
                filteredChangelists.add(filtered);
            }
        }
        return filteredChangelists;
    }

    /**
     * A convenience wrapper for {@link ScmClient#retrieve(ScmContext, String, Revision)} which reads the entire file
     * into a string using the default character set.  Useful for testing.
     *
     * @param client   client to retrieve the file from
     * @param context  context to pass to retrieve
     * @param path     path to pass to retrieve
     * @param revision revision to pass to retrieve
     * @return the content of the retrieved file as a string
     * @throws IOException if the retrieve call fails or there is an error reading the content
     */
    public static String retrieveContent(final ScmClient client, final ScmContext context, final String path, final Revision revision) throws ScmException, IOException
    {
        return new ByteSource()
        {
            @Override
            public InputStream openStream() throws IOException
            {
                try
                {
                    return client.retrieve(context, path, revision);
                }
                catch (ScmException e)
                {
                    throw new IOException(e);
                }
            }
        }.asCharSource(Charset.defaultCharset()).read();
    }
}
