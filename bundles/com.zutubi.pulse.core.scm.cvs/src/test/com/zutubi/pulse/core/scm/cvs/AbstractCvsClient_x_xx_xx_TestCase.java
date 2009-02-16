package com.zutubi.pulse.core.scm.cvs;

import com.zutubi.pulse.core.test.api.PulseTestCase;
import com.zutubi.pulse.core.scm.ScmContextImpl;
import com.zutubi.pulse.core.PulseExecutionContext;
import com.zutubi.util.io.IOUtils;
import com.zutubi.util.FileSystemUtils;

import java.io.IOException;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AbstractCvsClient_x_xx_xx_TestCase extends PulseTestCase
{
    private static final SimpleDateFormat SERVER_DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    
    protected File tmp;
    protected ScmContextImpl scmContext;
    protected PulseExecutionContext exeContext;
    protected File exeBaseDir;
    protected File scmBaseDir;

    public AbstractCvsClient_x_xx_xx_TestCase()
    {
    }

    public AbstractCvsClient_x_xx_xx_TestCase(String name)
    {
        super(name);
    }

    protected void setUp() throws Exception
    {
        super.setUp();

        tmp = FileSystemUtils.createTempDir();

        scmBaseDir = new File(tmp, "scmContext");
        scmContext = new ScmContextImpl();
        scmContext.setPersistentWorkingDir(scmBaseDir);

        exeBaseDir = new File(tmp, "work");
        exeContext = new PulseExecutionContext();
        exeContext.setWorkingDir(exeBaseDir);
    }

    protected void tearDown() throws Exception
    {
        removeDirectory(tmp);

        super.tearDown();
    }

    protected String getPassword(String name) throws IOException
    {
        return CvsTestUtils.getPassword(name);
    }

    protected void assertFileExists(String path)
    {
        assertTrue(isFileExists(path));
    }

    protected void assertFileNotExists(String path)
    {
        assertFalse(isFileExists(path));
    }

    protected boolean isFileExists(String path)
    {
        return new File(exeBaseDir, path).exists();
    }

    /**
     * When creating time based revisions, we need the time to be server time, which is in GMT.
     * However, when we create our revision, we need local time.  This method handles the conversion
     * from the server time format to the cvs revisions expected time format.
     *
     * @param time  server time.
     * @return  cvs revision time.
     *
     * @throws java.text.ParseException if the format of the time argument is incorrect.
     */
    protected String localTime(String time) throws ParseException
    {
        return CvsRevision.DATE_FORMAT.format(SERVER_DATE.parse(time));
    }

    protected void cleanWorkDir()
    {
        assertTrue(FileSystemUtils.rmdir(exeContext.getWorkingDir()));
        assertTrue(exeContext.getWorkingDir().mkdirs());
    }
}
