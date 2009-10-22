package com.zutubi.pulse.core.postprocessors.unittestpp;

import com.zutubi.pulse.core.postprocessors.api.*;
import static com.zutubi.pulse.core.util.api.XMLStreamUtils.*;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

/**
 * Post-processor for UnitTest++ (and compatible) XML reports.  See:
 * http://unittest-cpp.sourceforge.net/
 */
public class UnitTestPlusPlusReportPostProcessor extends XMLTestReportPostProcessorSupport
{
    private static final String ELEMENT_RESULTS = "unittest-results";
    private static final String ELEMENT_TEST = "test";
    private static final String ELEMENT_FAILURE = "failure";

    private static final String ATTRIBUTE_MESSAGE = "message";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_TIME = "time";
    private static final String ATTRIBUTE_SUITE = "suite";

    public UnitTestPlusPlusReportPostProcessor(UnitTestPlusPlusReportPostProcessorConfiguration config)
    {
        super(config);
    }

    protected void extractTestResults(File file, PostProcessorContext ppContext, TestSuiteResult tests)
    {
        process(file, ppContext, tests, new XMLStreamCallback()
        {
            public void process(XMLStreamReader reader, TestSuiteResult tests) throws XMLStreamException
            {
                if (nextElement(reader))
                {
                    processTestResults(reader, tests);
                }
            }
        });
    }

    private void processTestResults(XMLStreamReader reader, TestSuiteResult tests) throws XMLStreamException
    {
        expectStartElement(ELEMENT_RESULTS, reader);
        reader.nextTag();

        Map<String, TestSuiteResult> suites = new TreeMap<String, TestSuiteResult>();

        while (reader.isStartElement())
        {
            if (isElement(ELEMENT_TEST, reader))
            {
                processTest(reader, suites);
            }
            else
            {
                nextElement(reader);
            }
        }

        addSuites(tests, suites);

        expectEndElement(ELEMENT_RESULTS, reader);
    }

    private void processTest(XMLStreamReader reader, Map<String, TestSuiteResult> suites) throws XMLStreamException
    {
        expectStartElement(ELEMENT_TEST, reader);
        Map<String, String> attributes = getAttributes(reader);

        String suite = attributes.get(ATTRIBUTE_SUITE);
        String name = attributes.get(ATTRIBUTE_NAME);
        long duration = getDuration(attributes);

        if(suite != null && name != null)
        {
            reader.nextTag();

            TestSuiteResult suiteResult = getSuite(suite, suites);

            TestCaseResult caseResult = null;
            while (reader.isStartElement())
            {
                if (caseResult == null)
                {
                    if (isElement(ELEMENT_FAILURE, reader))
                    {
                        attributes = getAttributes(reader);
                        caseResult = new TestCaseResult(name, duration, TestStatus.FAILURE, attributes.get(ATTRIBUTE_MESSAGE));
                    }
                }
                nextElement(reader);
            }

            if (caseResult == null)
            {
                caseResult = new TestCaseResult(name, duration, TestStatus.PASS);
            }

            suiteResult.addCase(caseResult);
        }
        else
        {
            skipElement(reader, false);
        }

        expectEndElement(ELEMENT_TEST, reader);
        reader.nextTag();
    }

    private long getDuration(Map<String, String> attributes)
    {
        String value = attributes.get(ATTRIBUTE_TIME);
        if(value != null)
        {
            try
            {
                return (long) (Double.parseDouble(value) * 1000);
            }
            catch (NumberFormatException e)
            {
                // Fall through
            }
        }

        return TestResult.DURATION_UNKNOWN;
    }

    private void addSuites(TestSuiteResult tests, Map<String, TestSuiteResult> suites)
    {
        for(TestSuiteResult suite: suites.values())
        {
            tests.addSuite(suite);
        }
    }

    private TestSuiteResult getSuite(String name, Map<String, TestSuiteResult> suites)
    {
        if(suites.containsKey(name))
        {
            return suites.get(name);
        }
        else
        {
            TestSuiteResult suite = new TestSuiteResult(name);
            suites.put(name, suite);
            return suite;
        }
    }
}
