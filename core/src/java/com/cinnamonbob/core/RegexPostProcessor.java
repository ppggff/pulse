package com.cinnamonbob.core;

import com.cinnamonbob.core.model.PlainFeature;
import com.cinnamonbob.core.model.StoredArtifact;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import com.cinnamonbob.util.logging.Logger;
import java.util.regex.Matcher;


/**
 * 
 *
 */
public class RegexPostProcessor implements PostProcessor
{
    private static final Logger LOG = Logger.getLogger(RegexPostProcessor.class.getName());

    private String name;

    private List<RegexPattern> patterns;

    public RegexPostProcessor()
    {
        patterns = new LinkedList<RegexPattern>();
    }

    public RegexPostProcessor(String name)
    {
        this.name = name;
        patterns = new LinkedList<RegexPattern>();
    }

    public void process(StoredArtifact artifact)
    {
        try
        {
            BufferedReader reader = new BufferedReader(new FileReader(artifact.getFile()));
            String line;
            long lineNumber = 0;

            while((line = reader.readLine()) != null)
            {
                lineNumber++;
                processLine(artifact, line, lineNumber);
            }
        }
        catch(IOException e)
        {
            LOG.warning("I/O error post-processing artifact '" + artifact.getName() + "': " + e.getMessage());
        }

    }

    private void processLine(StoredArtifact artifact, String line, long lineNumber)
    {
        for(RegexPattern p: patterns)
        {
            String summary = p.match(line);
            if(summary != null)
            {
                artifact.addFeature(new PlainFeature(p.getCategory(), summary, lineNumber));
            }
        }
    }

    public RegexPattern createPattern()
    {
        RegexPattern pattern = new RegexPattern();
        addRegexPattern(pattern);
        return pattern;
    }

    /* Hrm, if we call this addPattern it gets magically picked up by FileLoader */
    public void addRegexPattern(RegexPattern pattern)
    {
        patterns.add(pattern);
    }

    public String getName()
    {
        return name;
    }

    public Object getValue()
    {
        return this;
    }

    public void setName(String name)
    {
        this.name = name;
    }
}
