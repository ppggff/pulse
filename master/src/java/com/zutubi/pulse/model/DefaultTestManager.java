package com.zutubi.pulse.model;

import com.opensymphony.util.TextUtils;
import com.zutubi.pulse.bootstrap.MasterConfigurationManager;
import com.zutubi.pulse.core.model.*;
import com.zutubi.pulse.model.persistence.TestCaseIndexDao;
import com.zutubi.pulse.util.StringUtils;
import com.zutubi.pulse.util.logging.Logger;
import nu.xom.Attribute;
import nu.xom.Element;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 */
public class DefaultTestManager implements TestManager
{
    private static final Logger LOG = Logger.getLogger(DefaultTestManager.class);

    private TestSuitePersister persister = new TestSuitePersister();
    private TestCaseIndexDao testCaseIndexDao;
    private MasterConfigurationManager configurationManager;

    public void index(BuildResult result)
    {
        if (!result.isPersonal())
        {
            for (RecipeResultNode node : result)
            {
                File testDir = new File(node.getResult().getAbsoluteOutputDir(configurationManager.getDataDirectory()), RecipeResult.TEST_DIR);
                if (testDir.isDirectory())
                {
                    indexTestsForStage(result, result.getSpecName().getId(), node.getStageName().getId(), testDir);
                }
            }
        }
    }

    private void indexTestsForStage(BuildResult result, long specNameId, long stageNameId, File testDir)
    {
        try
        {
            persister.read(new IndexingHandler(result.getProject().getId(), result.getId(), result.getNumber(), specNameId, stageNameId), null, testDir, true, false);
        }
        catch (Exception e)
        {
            LOG.severe("Unable to index test results for build: " + result.getNumber() + ": " + e.getMessage(), e);
        }
    }

    public void setTestCaseIndexDao(TestCaseIndexDao testCaseIndexDao)
    {
        this.testCaseIndexDao = testCaseIndexDao;
    }

    public void setConfigurationManager(MasterConfigurationManager configurationManager)
    {
        this.configurationManager = configurationManager;
    }

    private class IndexingHandler implements TestHandler
    {
        private Stack<TestSuiteResult> suites = new Stack<TestSuiteResult>();
        private Stack<Boolean> changed = new Stack<Boolean>();
        private long projectId;
        private long buildId;
        private long buildNumber;
        private long specNameId;
        private long stageNameId;
        private String path;
        private Map<String, TestCaseIndex> allCases;

        public IndexingHandler(long projectId, long buildId, long buildNumber, long specNameId, long stageNameId)
        {
            this.projectId = projectId;
            this.buildId = buildId;
            this.buildNumber = buildNumber;
            this.specNameId = specNameId;
            this.stageNameId = stageNameId;

            List<TestCaseIndex> cases = testCaseIndexDao.findByStage(stageNameId);
            allCases = new HashMap<String, TestCaseIndex>(cases.size());
            for(TestCaseIndex i: cases)
            {
                allCases.put(i.getName(), i);
            }
        }

        public void startSuite(TestSuiteResult suiteResult)
        {
            suites.push(suiteResult);
            changed.push(false);
            calculatePath();
        }

        public boolean endSuite()
        {
            suites.pop();
            calculatePath();
            return changed.pop();
        }

        private void calculatePath()
        {
            int i = 0;
            path = "";

            for (TestSuiteResult suite : suites)
            {
                if (i > 1)
                {
                    path += '/';
                }

                if (i > 0)
                {
                    path += StringUtils.urlEncode(suite.getName());
                }

                i++;
            }
        }

        private String getCasePath(String name)
        {
            name = StringUtils.urlEncode(name);
            if (TextUtils.stringSet(path))
            {
                return path + "/" + name;
            }
            else
            {
                return name;
            }
        }

        public void handleCase(TestCaseResult caseResult, Element element)
        {
            String casePath = getCasePath(caseResult.getName());
            TestCaseIndex caseIndex = allCases.get(casePath);
            if (caseIndex == null)
            {
                caseIndex = new TestCaseIndex(projectId, specNameId, stageNameId, casePath);
            }

            if (caseResult.hasBrokenTests() && !caseIndex.isHealthy())
            {
                // Broken in a previous build
                element.addAttribute(new Attribute(TestSuitePersister.ATTRIBUTE_BROKEN_SINCE, Long.toString(caseIndex.getBrokenSince())));
                element.addAttribute(new Attribute(TestSuitePersister.ATTRIBUTE_BROKEN_NUMBER, Long.toString(caseIndex.getBrokenNumber())));
                markChanged();
            }
            else if (!caseIndex.isHealthy() && caseResult.getStatus() == TestCaseResult.Status.PASS)
            {
                // Fixed in this build
                element.addAttribute(new Attribute(TestSuitePersister.ATTRIBUTE_FIXED, "true"));
                markChanged();
            }

            caseIndex.recordExecution(caseResult.getStatus(), buildId, buildNumber);
            testCaseIndexDao.save(caseIndex);
        }

        private void markChanged()
        {
            if (!changed.peek())
            {
                changed.pop();
                changed.push(true);
            }
        }
    }
}
