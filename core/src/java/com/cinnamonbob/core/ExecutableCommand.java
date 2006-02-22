package com.cinnamonbob.core;

import com.cinnamonbob.core.model.CommandResult;
import com.cinnamonbob.core.model.StoredArtifact;
import com.cinnamonbob.core.util.IOUtils;
import com.cinnamonbob.util.logging.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * 
 *
 */
public class ExecutableCommand implements Command
{
    private static final Logger LOG = Logger.getLogger(ExecutableCommand.class);

    private String exe;
    private List<Arg> args = new LinkedList<Arg>();
    private File workingDir;

    private String name;

    private List<Environment> env = new LinkedList<Environment>();
    private Process child;
    private volatile boolean terminated = false;


    public void execute(File baseDir, File outputDir, CommandResult cmdResult)
    {
        List<String> command = new LinkedList<String>();
        command.add(exe);

        if (args != null)
        {
            for (Arg arg : args)
            {
                command.add(arg.getText());
            }
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDir == null)
        {
            builder.directory(baseDir);
        }
        else
        {
            builder.directory(workingDir);
        }

        for (Environment setting : env)
        {
            builder.environment().put(setting.getName(), setting.getValue());
        }

        builder.redirectErrorStream(true);

        try
        {
            child = builder.start();

            if (terminated)
            {
                // Catches the case where we were asked to terminate before
                // creating the child process.
                cmdResult.error("Command terminated");
                return;
            }

            File outputFile = new File(outputDir, "output.txt");
            FileOutputStream output = null;
            try
            {
                output = new FileOutputStream(outputFile);
                InputStream input = child.getInputStream();

                IOUtils.joinStreams(input, output);
            }
            finally
            {
                IOUtils.close(output);
            }

            final int result = child.waitFor();
            String commandLine = constructCommandLine(builder);

            if (result == 0)
            {
                cmdResult.success();
            }
            else
            {
                cmdResult.failure("Command '" + commandLine + "' exited with code '" + result + "'");
            }

            cmdResult.getProperties().put("exit code", Integer.toString(result));
            cmdResult.getProperties().put("command line", commandLine);

            if (builder.directory() != null)
            {
                cmdResult.getProperties().put("working directory", builder.directory().getAbsolutePath());
            }

            // TODO awkward to add this stored artifact to the model...
            FileArtifact outputArtifact = new FileArtifact("output", outputFile);
            outputArtifact.setTitle("command output");
            outputArtifact.setType("text/plain");
            cmdResult.addArtifact(new StoredArtifact(outputArtifact, outputFile.getName()));
        }
        catch (IOException e)
        {
            throw new BuildException(e);
        }
        catch (InterruptedException e)
        {
            throw new BuildException(e);
        }
    }

    public List<String> getArtifactNames()
    {
        return Arrays.asList("output");
    }

    public String getExe()
    {
        return exe;
    }

    public void setExe(String exe)
    {
        this.exe = exe;
    }

    public void setArgs(String args)
    {
        for (String arg : args.split(" "))
        {
            this.args.add(new Arg(arg));
        }
    }

    public void setWorkingDir(File d)
    {
        this.workingDir = d;
    }

    public Arg createArg()
    {
        Arg arg = new Arg();
        args.add(arg);
        return arg;
    }

    protected void addArguments(String ...arguments)
    {
        for (String arg : arguments)
        {
            Arg argument = new Arg(arg);
            args.add(argument);
        }
    }

    public Environment createEnvironment()
    {
        Environment setting = new Environment();
        env.add(setting);
        return setting;
    }

    private String constructCommandLine(ProcessBuilder builder)
    {
        StringBuffer result = new StringBuffer();
        boolean first = true;

        for (String part : builder.command())
        {
            if (first)
            {
                first = false;
            }
            else
            {
                result.append(' ');
            }

            boolean containsSpaces = part.indexOf(' ') != -1;

            if (containsSpaces)
            {
                result.append('"');
            }

            result.append(part);

            if (containsSpaces)
            {
                result.append('"');
            }
        }

        return result.toString();
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void terminate()
    {
        terminated = true;
        if (child != null)
        {
            child.destroy();
        }
    }

    List<Arg> getArgs()
    {
        return args;
    }

    /**
     */
    public class Arg
    {
        private String text;

        public Arg()
        {
            text = "";
        }

        public Arg(String text)
        {
            this.text = text;
        }

        public void addText(String text)
        {
            this.text += text;
        }

        public String getText()
        {
            return text;
        }
    }

    /**
     */
    public class Environment
    {
        private String name;
        private String value;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }
}
