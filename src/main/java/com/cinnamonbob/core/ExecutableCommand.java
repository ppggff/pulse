package com.cinnamonbob.core;

import nu.xom.Element;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.cinnamonbob.util.IOHelper;

/**
 * A command that involves running an executable.
 */
public class ExecutableCommand implements Command
{
    private static final String CONFIG_NAME                   = "name";
    private static final String CONFIG_ATTR_EXECUTABLE        = "exe";
    private static final String CONFIG_ATTR_ARGUMENTS         = "args";
    private static final String CONFIG_ATTR_WORKING_DIRECTORY = "working-dir";
    private static final String OUTPUT_FILENAME               = "output.txt";
    
    private CommandCommon common;
    private String        executable;
    private String[]      arguments;
    private File          workingDirectory;
    
    
    private void loadConfig(ConfigContext context, Element element) throws ConfigException
    {
        String working;
        
        executable = XMLConfigUtils.getAttributeValue(context, element, CONFIG_ATTR_EXECUTABLE);
        working = XMLConfigUtils.getAttributeValue(context, element, CONFIG_ATTR_WORKING_DIRECTORY, null);
        if(working != null)
        {
            workingDirectory = new File(working);
        }
        
        arguments = XMLConfigUtils.getAttributeValue(context, element, CONFIG_ATTR_ARGUMENTS).split(" ");
    }


    private int runChild(ProcessBuilder builder, OutputStream outputStream) throws InternalBuildFailureException
    {
        try
        {
            Process        child       = builder.start();
            InputStream    childOutput = child.getInputStream();
            
            try
            {
                IOHelper.joinStreams(childOutput, outputStream);
            }
            catch(IOException e)
            {
                throw new InternalBuildFailureException("Error capturing child process output for command '" + common.getName() + "'", e);
            }
            
            return child.waitFor(); 
        }
        catch(IOException e)
        {
            // TODO should this be an internal failure? it is more a problem in the script...
            throw new InternalBuildFailureException("Error starting child process.", e);
        }
        catch(InterruptedException e)
        {
            // TODO Can we ever actually get interrupted?
            assert(false);
        }
        
        return -1;
    }
    
    
    private String constructCommandLine(List<String> command)
    {
        StringBuffer result = new StringBuffer();
        
        for(String part: command)
        {
            boolean containsSpaces = part.indexOf(' ') != -1;
            
            if(containsSpaces)
            {
                result.append('"');
            }

            result.append(part);
            
            if(containsSpaces)
            {
                result.append('"');
            }
            result.append(' ');
        }
        
        return result.toString();
    }


    public ExecutableCommand(ConfigContext context, Element element, CommandCommon common) throws ConfigException
    {
        this.common = common;
        loadConfig(context, element);        
    }

    
    public ExecutableCommandResult execute(File outputDir) throws InternalBuildFailureException
    {
        List<String> command = new LinkedList<String>(Arrays.asList(arguments));
        command.add(0, executable);
        
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory);
        builder.redirectErrorStream(true);

        File             outputFile = new File(outputDir, OUTPUT_FILENAME);
        FileOutputStream outputStream;
        int              result = -1;
        
        try
        {
            outputStream = new FileOutputStream(outputFile);
            result = runChild(builder, outputStream);
        }
        catch(FileNotFoundException e)
        {
            throw new InternalBuildFailureException("Could not create command output file '" + outputFile.getAbsolutePath() + "'", e);
        }
        
        return new ExecutableCommandResult(constructCommandLine(command), workingDirectory.getAbsolutePath(), result);
    }
    
    
    public List<ArtifactSpec> getArtifacts()
    {
        List<ArtifactSpec> list = new LinkedList<ArtifactSpec>();
        
        list.add(new ArtifactSpec("output", "Command Output", Artifact.TYPE_PLAIN, new File(OUTPUT_FILENAME)));
        
        return list;
    }
}
