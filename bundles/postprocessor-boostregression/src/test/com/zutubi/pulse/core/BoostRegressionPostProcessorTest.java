package com.zutubi.pulse.core;

import com.zutubi.pulse.core.model.TestCaseResult;
import com.zutubi.pulse.core.model.TestSuiteResult;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 */
public class BoostRegressionPostProcessorTest extends XMLReportPostProcessorTestBase
{
    public BoostRegressionPostProcessorTest()
    {
        super(new BoostRegressionPostProcessor());
    }

    protected File getOutputDir()
    {
        URL resource = getClass().getResource("BoostRegressionPostProcessorTest.run.xml");
        return new File(resource.getPath()).getParentFile();
    }

    public void testBasic()
    {
        singleLogHelper("compilefail", "iterator", "interoperable_fail", TestCaseResult.Status.PASS, null);
    }

    public void testRun()
    {
        singleLogHelper("run", "statechart", "InvalidResultCopyTestRelaxed", TestCaseResult.Status.PASS, null);
    }

    public void testBroken()
    {
        singleLogHelper("broken", "statechart", "InvalidResultCopyTestRelaxed", TestCaseResult.Status.FAILURE, "============================[ compile output below ]============================\n" +
                "    compiler error here\n" +
                "============================[ compile output above ]============================\n");
    }

    public void testNested()
    {
        TestSuiteResult tests = runProcessor("nested");
        assertEquals(1, tests.getSuites().size());
        TestSuiteResult suite = tests.getSuites().get(0);
        assertEquals("algorithm", suite.getName());
        assertEquals(1, suite.getTotal());
        suite = suite.getSuite("minmax");
        assertNotNull(suite);
        checkCase(suite.getCase("minmax"), "minmax", TestCaseResult.Status.PASS, TestCaseResult.UNKNOWN_DURATION, null);
    }

    private void singleLogHelper(String testName, String suiteName, String caseName, TestCaseResult.Status status, String message)
    {
        TestSuiteResult tests = runProcessor(testName);
        assertEquals(1, tests.getSuites().size());
        TestSuiteResult suite = tests.getSuites().get(0);
        assertEquals(suiteName, suite.getName());
        assertEquals(1, suite.getTotal());
        checkCase(suite.getCase(caseName), caseName, status, TestCaseResult.UNKNOWN_DURATION, message);
    }

}
