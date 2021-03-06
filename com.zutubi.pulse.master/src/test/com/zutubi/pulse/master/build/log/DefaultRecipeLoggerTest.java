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

package com.zutubi.pulse.master.build.log;

import com.google.common.io.Files;
import com.zutubi.pulse.core.test.api.PulseTestCase;
import com.zutubi.util.StringUtils;
import com.zutubi.util.SystemUtils;
import com.zutubi.util.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.zutubi.pulse.core.test.api.Matchers.matchesRegex;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class DefaultRecipeLoggerTest extends PulseTestCase
{
    private static final String LINE_ENDING_UNIX = "\n";
    private static final String LINE_ENDING_WINDOWS = "\r\n";
    private static final String LINE_ENDING_MAC = "\r";

    private static final String PATTERN_MARKER = "[0-9][0-9]?/[0-9][0-9]?/[0-9][0-9] [0-9][0-9]?:[0-9][0-9]:[0-9][0-9].*: ";

    private File tmpDir;
    private File logFile;
    private DefaultRecipeLogger logger;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        tmpDir = createTempDirectory();
        logFile = new File(tmpDir, "file.log");
        logger = new DefaultRecipeLogger(new LogFile(logFile, true), true);
        logger.prepare();
    }

    @Override
    protected void tearDown() throws Exception
    {
        logger.close();
        removeDirectory(tmpDir);
        super.tearDown();
    }
    
    public void testOutputFlushed() throws IOException
    {
        final String SHORT_STRING = "1";

        logger.log(SHORT_STRING.getBytes());
        assertTrue(Files.toString(logFile, Charset.defaultCharset()).length() > SHORT_STRING.length());
    }

    public void testOffsetAndLength() throws IOException
    {
        logger.log("0123456789".getBytes("US-ASCII"), 2, 4);
        assertLineContent(Files.toString(logFile, Charset.defaultCharset()), "2345");
    }

    public void testNewlineOnBoundaryUnix() throws IOException
    {
        newlineOnBoundaryHelper(LINE_ENDING_UNIX);
    }

    public void testNewlineOnBoundaryWindows() throws IOException
    {
        newlineOnBoundaryHelper(LINE_ENDING_WINDOWS);
    }

    public void testNewlineOnBoundaryMac() throws IOException
    {
        newlineOnBoundaryHelper(LINE_ENDING_MAC);
    }
    
    private void newlineOnBoundaryHelper(String lineEnding) throws IOException
    {
        logger.log(("line 1" + lineEnding + "line 2" + lineEnding).getBytes());
        assertLines(2, true);
        logger.log(("line 3" + lineEnding + "line 4").getBytes());
        assertLines(4, false);
    }

    public void testNewlineStraddlesBoundary() throws IOException
    {
        logger.log(("line 1\r").getBytes());
        logger.log("\nline 2".getBytes());

        assertLines(2, false);
    }

    public void testLineNotSplitUnix() throws IOException
    {
        lineNotSplitHelper(LINE_ENDING_UNIX);
    }

    public void testLineNotSplitWindows() throws IOException
    {
        lineNotSplitHelper(LINE_ENDING_WINDOWS);
    }

    public void testLineNotSplitMac() throws IOException
    {
        lineNotSplitHelper(LINE_ENDING_MAC);
    }

    private void lineNotSplitHelper(String lineEnding) throws IOException
    {
        // CIB-1782
        logger.log(("line 1" + lineEnding + "lin").getBytes());
        logger.log(("e 2" + lineEnding + "line 3").getBytes());

        assertLines(3, false);
    }

    public void testNewlineAtStartOfOutputUnix() throws IOException
    {
        newlineAtStartOfOutputHelper(LINE_ENDING_UNIX);
    }

    public void testNewlineAtStartOfOutputWindows() throws IOException
    {
        newlineAtStartOfOutputHelper(LINE_ENDING_WINDOWS);
    }

    public void testNewlineAtStartOfOutputMac() throws IOException
    {
        newlineAtStartOfOutputHelper(LINE_ENDING_MAC);
    }

    private void newlineAtStartOfOutputHelper(String lineEnding) throws IOException
    {
        logger.writePreRule();
        logger.log((lineEnding + "line 1" + lineEnding + "line 2").getBytes());

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(logFile));
            assertEquals(DefaultRecipeLogger.PRE_RULE, reader.readLine());
            reader.readLine();
            readLines(2, reader);
            assertNull(reader.readLine());
        }
        finally
        {
            IOUtils.close(reader);
        }
    }

    public void testNewlineAtEndOfOutputUnix() throws IOException
    {
        newlineAtEndOfOutputHelper(LINE_ENDING_UNIX);
    }

    public void testNewlineAtEndOfOutputWindows() throws IOException
    {
        newlineAtEndOfOutputHelper(LINE_ENDING_WINDOWS);
    }

    public void testNewlineAtEndOfOutputMac() throws IOException
    {
        newlineAtEndOfOutputHelper(LINE_ENDING_MAC);
    }

    private void newlineAtEndOfOutputHelper(String lineEnding) throws IOException
    {
        logger.log(("line 1" + lineEnding + "line 2" + lineEnding).getBytes());
        logger.writePostRule();

        assertEndOfOutput();
    }

    public void testNoNewlineAtEndOfOutputUnix() throws IOException
    {
        noNewlineAtEndOfOutputHelper(LINE_ENDING_UNIX);
    }

    public void testNoNewlineAtEndOfOutputWindows() throws IOException
    {
        noNewlineAtEndOfOutputHelper(LINE_ENDING_WINDOWS);
    }

    public void testNoNewlineAtEndOfOutputMac() throws IOException
    {
        noNewlineAtEndOfOutputHelper(LINE_ENDING_MAC);
    }

    private void noNewlineAtEndOfOutputHelper(String lineEnding) throws IOException
    {
        logger.log(("line 1" + lineEnding + "line 2").getBytes());
        logger.writePostRule();

        assertEndOfOutput();
    }

    public void testReopenSameLogFile() throws IOException
    {
        logger.logMarker("First line");
        logger.close();
        logger = new DefaultRecipeLogger(new LogFile(logFile, true), true);
        logger.prepare();
        logger.logMarker("Second line");

        String content = Files.toString(logFile, Charset.defaultCharset());
        assertThat(content, containsString("First line"));
        assertThat(content, containsString("Second line"));
    }

    private void assertEndOfOutput() throws IOException
    {
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(logFile));
            readLines(2, reader);
            assertEquals(DefaultRecipeLogger.POST_RULE, reader.readLine());
            assertNull(reader.readLine());
        }
        finally
        {
            IOUtils.close(reader);
        }
    }

    private void assertLines(int count, boolean newlineTerminator) throws IOException
    {
        List<String> lines = new ArrayList<String>(count);
        for (int i = 1; i <= count; i++)
        {
            lines.add("line " + i);
        }
        String expected = StringUtils.join(SystemUtils.LINE_SEPARATOR, lines);
        if (newlineTerminator)
        {
            expected += SystemUtils.LINE_SEPARATOR;
        }
        String contents = Files.asCharSource(logFile, Charset.defaultCharset()).read();
        contents = contents.replaceAll(PATTERN_MARKER, "");
        assertEquals(expected, contents);
    }

    private void readLines(int count, BufferedReader reader) throws IOException
    {
        for (int i = 1; i <= count; i++)
        {
            assertLineContent(reader.readLine(), "line " + i);
        }
    }

    private void assertLineContent(String line, String content)
    {
        assertThat(line, matchesRegex(PATTERN_MARKER + Pattern.quote(content)));
    }
}
