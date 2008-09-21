package com.zutubi.pulse.core.scm.git;

import com.zutubi.pulse.core.scm.ScmCancelledException;
import com.zutubi.pulse.core.scm.ScmException;
import com.zutubi.pulse.core.scm.ScmEventHandler;
import com.zutubi.pulse.util.process.AsyncProcess;
import com.zutubi.pulse.util.process.LineHandler;
import com.zutubi.util.StringUtils;
import com.zutubi.util.Constants;
import com.zutubi.util.logging.Logger;
import com.opensymphony.util.TextUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.List;
import java.util.LinkedList;
import java.text.SimpleDateFormat;
import java.text.ParseException;

/**
 * The native git object is a wrapper around the implementation details for running native git operations.
 */
public class NativeGit
{
    private static final Logger LOG = Logger.getLogger(NativeGit.class);
    private static final long PROCESS_TIMEOUT = Long.getLong("pulse.git.inactivity.timeout", 300);
    private static final String ASCII_CHARSET = "US-ASCII";
    private final static SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy z");

    private ScmEventHandler scmHandler;

    private ProcessBuilder git;

    private static final String GIT = "git";
    private static final String PULL_COMMAND = "pull";
    private static final String FETCH_COMMAND = "fetch";
    private static final String LOG_COMMAND = "log";
    private static final String CLONE_COMMAND = "clone";
    private static final String CHECKOUT_COMMAND = "checkout";
    private static final String BRANCH_COMMAND = "branch";
    private static final String BRANCH_OPTION = "-b";

    public NativeGit()
    {
        git = new ProcessBuilder();
    }

    public void setWorkingDirectory(File dir)
    {
        git.directory(dir);
    }

    public void setScmEventHandler(ScmEventHandler scmHandler)
    {
        this.scmHandler = scmHandler;
    }

    public void clone(String repository, String dir) throws ScmException
    {
        run(GIT, CLONE_COMMAND, repository, dir);
    }

    public void pull() throws ScmException
    {
        run(GIT, PULL_COMMAND);
    }

    public void fetch(String ...remote) throws ScmException
    {
        String[] command = new String[2 + remote.length];
        command[0] = GIT;
        command[1] = FETCH_COMMAND;

        System.arraycopy(remote, 0, command, 2, remote.length);

        run(command);
    }

    public List<GitLogEntry> log(String from, String to) throws ScmException
    {
        String[] command = {GIT, LOG_COMMAND, from+".."+to};

        LogOutputHandler handler = new LogOutputHandler();
        
        runWithHandler(handler, null, command);

        if (handler.getExitCode() != 0)
        {
            LOG.warning("Git command: " + StringUtils.join(" ", command) + " exited " +
                    "with non zero exit code: " + handler.getExitCode());
            LOG.warning(handler.getError());
        }

        return handler.getEntries();
    }

    public void checkout(String branch) throws ScmException
    {
        run(GIT, CHECKOUT_COMMAND, BRANCH_OPTION, branch, "origin/" + branch);
    }

    public List<GitBranchEntry> branch() throws ScmException
    {
        String[] command = {GIT, BRANCH_COMMAND};

        BranchOutputHandler handler = new BranchOutputHandler();

        runWithHandler(handler, null, command);

        if (handler.getExitCode() != 0)
        {
            LOG.warning("Git command: " + StringUtils.join(" ", command) + " exited " +
                    "with non zero exit code: " + handler.getExitCode());
            LOG.warning(handler.getError());
        }

        return handler.getBranches();
    }

    protected void run(String... commands) throws ScmException
    {
        OutputHandlerAdapter handler = new OutputHandlerAdapter();

        runWithHandler(handler, null, commands);

        if (handler.getExitCode() != 0)
        {
            LOG.warning("Git command: " + StringUtils.join(" ", commands) + " exited " +
                    "with non zero exit code: " + handler.getExitCode());
            LOG.warning(handler.getError());
        }
    }

    protected void runWithHandler(final OutputHandler handler, String input, String... commands) throws ScmException
    {
        if (LOG.isLoggable(Level.FINE))
        {
            LOG.fine(StringUtils.join(" ", commands));
        }

        Process child;

        git.command(commands);

        try
        {
            child = git.start();
        }
        catch (IOException e)
        {
            throw new ScmException("Could not start git process: " + e.getMessage(), e);
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
                throw new ScmException("Error writing to input of git process", e);
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
                if (activity.getAndSet(false))
                {
                    lastActivityTime = System.currentTimeMillis();
                }
                else
                {
                    long secondsSinceActivity = (System.currentTimeMillis() - lastActivityTime) / 1000;
                    if (secondsSinceActivity >= PROCESS_TIMEOUT)
                    {
                        throw new ScmException("Timing out git process after " + secondsSinceActivity + " seconds of inactivity");
                    }
                }
            }
            while (exitCode == null);

            handler.handleExitCode(exitCode);
        }
        catch (InterruptedException e)
        {
            // Do nothing
        }
        catch (IOException e)
        {
            throw new ScmException("Error reading output of git process", e);
        }
        finally
        {
            async.destroy();
        }
    }

    interface OutputHandler
    {
        void handleStdout(String line);

        void handleStderr(String line);

        void handleExitCode(int code) throws ScmException;

        int getExitCode();

        void checkCancelled() throws ScmCancelledException;
    }

    private class OutputHandlerAdapter implements OutputHandler
    {
        private int exitCode;
        
        private String error;

        public void handleStdout(String line)
        {
            if (scmHandler != null)
            {
                scmHandler.status(line);
            }
        }

        public void handleStderr(String line)
        {
            if (!TextUtils.stringSet(error))
            {
                error = "";
            }
            error = error + line + Constants.LINE_SEPARATOR;
        }

        public String getError()
        {
            return error;
        }

        public void handleExitCode(int code) throws ScmException
        {
            this.exitCode = code;
        }

        public int getExitCode()
        {
            return exitCode;
        }

        public void checkCancelled() throws ScmCancelledException
        {
            if (scmHandler != null)
            {
                scmHandler.checkCancelled();
            }
        }
    }

    /**
     * Read the output from the git log command, interpretting the output.
     *
     * Sample output:
     *
     * commit 78be6b2f12399ea2332a5148440086913cb910fb
     * Author: Daniel Ostermeier <daniel@zutubi.com>
     * Date:   Fri Sep 12 11:30:12 2008 +1000
     *
     *    update
     */
    private class LogOutputHandler extends OutputHandlerAdapter
    {
        private static final String COMMIT_TAG =    "commit";
        private static final String AUTHOR_TAG =    "Author:";
        private static final String DATE_TAG =      "Date:";

        private List<GitLogEntry> entries = new LinkedList<GitLogEntry>();
        
        private GitLogEntry currentEntry;

        public void handleStdout(String line)
        {
            if (line.startsWith(COMMIT_TAG))
            {
                currentEntry = new GitLogEntry();
                entries.add(currentEntry);
                currentEntry.setCommit(line.substring(COMMIT_TAG.length()).trim());
            }
            else if (line.startsWith(AUTHOR_TAG))
            {
                currentEntry.setAuthor(line.substring(AUTHOR_TAG.length()).trim());
            }
            else if (line.startsWith(DATE_TAG))
            {
                String dtStr = line.substring(DATE_TAG.length()).trim();
                try
                {
                    currentEntry.setDate(LOG_DATE_FORMAT.parse(dtStr));
                }
                catch (ParseException e)
                {
                    LOG.warning(e);
                }
            }
            else
            {
                // trim the leading whitespace.
                int i = 0;
                for (; i < line.length(); i++)
                {
                    if (!Character.isWhitespace(line.charAt(i)))
                    {
                        break;
                    }
                }

                currentEntry.setComment(currentEntry.getComment() + line.substring(i));
            }
        }

        public List<GitLogEntry> getEntries()
        {
            return entries;
        }
    }

    /**
     * Read the output from the git branch command, interpretting the information as
     * necessary.
     */
    private class BranchOutputHandler extends OutputHandlerAdapter
    {
        private List<GitBranchEntry> branches = new LinkedList<GitBranchEntry>();

        public void handleStdout(String line)
        {
            GitBranchEntry entry = new GitBranchEntry();
            if (line.startsWith("*"))
            {
                entry.setActive(true);
                line = line.substring(2);
            }
            entry.setName(line.trim());
            branches.add(entry);
        }

        public List<GitBranchEntry> getBranches()
        {
            return branches;
        }
    }
}
