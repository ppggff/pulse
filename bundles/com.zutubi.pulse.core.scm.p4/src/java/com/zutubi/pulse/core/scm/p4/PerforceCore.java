package com.zutubi.pulse.core.scm.p4;

import com.zutubi.pulse.core.scm.api.Revision;
import com.zutubi.pulse.core.scm.api.ScmCancelledException;
import com.zutubi.pulse.core.scm.api.ScmException;
import static com.zutubi.pulse.core.scm.p4.PerforceConstants.*;
import com.zutubi.pulse.core.util.process.AsyncProcess;
import com.zutubi.pulse.core.util.process.LineHandler;
import com.zutubi.util.StringUtils;
import com.zutubi.util.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
public class PerforceCore
{
    private static final Logger LOG = Logger.getLogger(PerforceCore.class);

    private static final String ASCII_CHARSET = "US-ASCII";
    private static final long P4_TIMEOUT = Long.getLong("pulse.p4.inactivity.timeout", 300);

    private Map<String, String> p4Env = new HashMap<String, String>();
    private ProcessBuilder p4Builder;
    private Pattern changesPattern;
    private Pattern lineSplitterPattern;
    private static final String ROOT_PREFIX = "Root:";

    public class P4Result
    {
        public StringBuffer stdout;
        public StringBuffer stderr;
        public int exitCode;

        public P4Result()
        {
            stdout = new StringBuffer();
            stderr = new StringBuffer();
        }
    }

    public PerforceCore()
    {
        p4Builder = new ProcessBuilder();
        // Output of p4 changes -s submitted -m 1:
        //   Change <number> on <date> by <user>@<client>
        changesPattern = Pattern.compile("^Change ([0-9]+) on (.+) by (.+)@(.+) '(.+)'$", Pattern.MULTILINE);
        lineSplitterPattern = Pattern.compile("\r?\n");
    }

    public Map<String, String> getEnv()
    {
        return p4Env;
    }

    public void setEnv(String variable, String value)
    {
        if (value != null)
        {
            p4Env.put(variable, value);
            p4Builder.environment().put(variable, value);
        }
    }

    public void setWorkingDir(File dir)
    {
        p4Builder.directory(dir);
    }

    public P4Result runP4(String input, String... commands) throws ScmException
    {
        return runP4(true, input, commands);
    }

    public P4Result runP4(boolean throwOnStderr, String input, String... commands) throws ScmException
    {
        final P4Result result = new P4Result();

        runP4WithHandler(new PerforceErrorDetectingHandler(throwOnStderr)
        {
            public void handleStdout(String line)
            {
                result.stdout.append(line);
                result.stdout.append('\n');
            }

            public void handleExitCode(int code) throws ScmException
            {
                super.handleExitCode(code);
                result.stderr = getStderr();
                result.exitCode = code;
            }

            public void checkCancelled() throws ScmCancelledException
            {
            }
        }, input, commands);

        return result;
    }

    public void runP4WithHandler(final PerforceHandler handler, String input, String... commands) throws ScmException
    {
        if (LOG.isLoggable(Level.FINE))
        {
            LOG.fine(StringUtils.join(" ", commands));
        }

        Process child;

        p4Builder.command(commands);

        try
        {
            child = p4Builder.start();
        }
        catch (IOException e)
        {
            throw new ScmException("Could not start p4 process: " + e.getMessage(), e);
        }

        if (input != null)
        {
            try
            {
                OutputStream stdinStream = child.getOutputStream();

                stdinStream.write(input.getBytes(ASCII_CHARSET));
                stdinStream.close();
            }
            catch (IOException e)
            {
                throw new ScmException("Error writing to input of p4 process", e);
            }
        }

        final AtomicBoolean activity = new AtomicBoolean(false);
        AsyncProcess async = new AsyncProcess(child, new LineHandler()
        {
            public void handle(String line, boolean error)
            {
                activity.set(true);
                if (error)
                {
                    handler.handleStderr(line);
                }
                else
                {
                    handler.handleStdout(line);
                }
            }
        }, true);

        try
        {
            long lastActivityTime = System.currentTimeMillis();

            Integer exitCode;
            do
            {
                handler.checkCancelled();
                exitCode = async.waitFor(10, TimeUnit.SECONDS);
                if(activity.getAndSet(false))
                {
                    lastActivityTime = System.currentTimeMillis();
                }
                else
                {
                    long secondsSinceActivity = (System.currentTimeMillis() - lastActivityTime) / 1000;
                    if(secondsSinceActivity >= P4_TIMEOUT)
                    {
                        throw new ScmException("Timing out p4 process after " + secondsSinceActivity + " seconds of inactivity");
                    }
                }
            }
            while(exitCode == null);

            handler.handleExitCode(exitCode);
        }
        catch (InterruptedException e)
        {
            // Do nothing
        }
        catch(IOException e)
        {
            throw new ScmException("Error reading output of p4 process", e);
        }
        finally
        {
            async.destroy();
        }
    }

    public boolean workspaceExists(String clientName) throws ScmException
    {
        P4Result result = runP4(null, getP4Command(COMMAND_CLIENTS), COMMAND_CLIENTS);
        String[] lines = splitLines(result);
        for (String line : lines)
        {
            String[] parts = line.split(" ");
            if (parts.length > 1 && parts[1].equals(clientName))
            {
                return true;
            }
        }

        return false;
    }

    public PerforceWorkspace createWorkspace(String templateClient, String clientName, String description, String root) throws ScmException
    {
        PerforceCore.P4Result result = runP4(null, getP4Command(COMMAND_CLIENT), FLAG_CLIENT, templateClient, COMMAND_CLIENT, FLAG_OUTPUT);

        PerforceWorkspace workspace = PerforceWorkspace.parseSpecification(result.stdout.toString());
        workspace.rename(clientName);
        workspace.setHost(null);
        workspace.setDescription(Arrays.asList(description));
        workspace.setRoot(root);
        workspace.deleteOption(OPTION_LOCKED);

        runP4(workspace.toSpecification(), getP4Command(COMMAND_CLIENT), COMMAND_CLIENT, FLAG_INPUT);

        return workspace;
    }

    public void deleteWorkspace(String clientName) throws ScmException
    {
        runP4(null, getP4Command(COMMAND_CLIENT), COMMAND_CLIENT, FLAG_DELETE, clientName);
    }

    public File getClientRoot() throws ScmException
    {
        final File[] result = new File[1];

        runP4WithHandler(new PerforceErrorDetectingHandler(true)
        {
            public void handleStdout(String line)
            {
                if (line.startsWith(ROOT_PREFIX))
                {
                    result[0] = new File(line.substring(ROOT_PREFIX.length()).trim());
                }
            }

            public void checkCancelled() throws ScmCancelledException
            {
            }
        }, null, getP4Command(COMMAND_CLIENT), COMMAND_CLIENT, FLAG_OUTPUT);

        return result[0];
    }

    public Map<String, String> getServerInfo(String client) throws ScmException
    {
        Map<String, String> info = new TreeMap<String, String>();
        PerforceCore.P4Result result;

        if (client == null)
        {
            result = runP4(null, getP4Command(COMMAND_INFO), COMMAND_INFO);
        }
        else
        {
            result = runP4(null, getP4Command(COMMAND_INFO), FLAG_CLIENT, client, COMMAND_INFO);
        }

        for (String line : splitLines(result))
        {
            int index = line.indexOf(':');
            if (index > 0 && index < line.length() - 1)
            {
                info.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
            }
        }

        return info;
    }

    public Revision getLatestRevisionForFiles(String clientName, String... files) throws ScmException
    {
        List<String> args = new ArrayList<String>(8 + files.length);

        args.add(getP4Command(COMMAND_CHANGES));

        if (clientName != null)
        {
            args.add(FLAG_CLIENT);
            args.add(clientName);
        }

        args.add(COMMAND_CHANGES);
        args.add(FLAG_STATUS);
        args.add(VALUE_SUBMITTED);
        args.add(FLAG_MAXIMUM);
        args.add("1");

        args.addAll(Arrays.asList(files));

        PerforceCore.P4Result result = runP4(null, args.toArray(new String[args.size()]));
        Matcher matcher = changesPattern.matcher(result.stdout);

        if (matcher.find())
        {
            return new Revision(matcher.group(1));
        }
        else
        {
            return new Revision(0);
        }
    }

    public long createChangelist(String description) throws ScmException
    {
        PerforceCore.P4Result result = runP4(null, getP4Command(COMMAND_CHANGE), COMMAND_CHANGE, FLAG_OUTPUT);
        String changeSpec = result.stdout.toString();

        changeSpec = changeSpec.replaceAll("<enter description here>", Matcher.quoteReplacement(description));
        result = runP4(changeSpec, getP4Command(COMMAND_CHANGE), COMMAND_CHANGE, FLAG_INPUT);
        Pattern created = Pattern.compile("Change ([0-9]+) created.");
        String response = result.stdout.toString().trim();
        Matcher m = created.matcher(response);
        if (m.matches())
        {
            return Long.parseLong(m.group(1));
        }
        else
        {
            throw new ScmException("Unrecognised response from p4 change '" + response + "'");
        }
    }

    public void submit(String comment) throws ScmException
    {
        P4Result result = runP4(null, getP4Command(COMMAND_CHANGE), COMMAND_CHANGE, FLAG_OUTPUT);
        String out = result.stdout.toString();
        out = out.replace("<enter description here>", comment);
        runP4(out, getP4Command(COMMAND_SUBMIT), COMMAND_SUBMIT, FLAG_INPUT);
    }

    public String[] splitLines(P4Result result)
    {
        return lineSplitterPattern.split(result.stdout);
    }

    public Pattern getChangesPattern()
    {
        return changesPattern;
    }
}
