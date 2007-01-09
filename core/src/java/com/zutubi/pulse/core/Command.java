package com.zutubi.pulse.core;

import com.zutubi.pulse.core.model.CommandResult;
import com.zutubi.validation.annotations.Required;

import java.util.List;

/**
 * 
 *
 */
public interface Command
{
    /**
     * Execute the command.
     *
     * @param context defines the context in which the command is being executed.
     * @param result defines the command result instance used by the command to record its execution details.
     */
    void execute(CommandContext context, CommandResult result);

    /**
     *
     * @return a list of artifacts generated by this command implementation.
     */
    List<Artifact> getArtifacts();

    /**
     * The name of the command is used to identify it.
     *
     * @return name of the command.
     */
    @Required String getName();

    /**
     * Set the name of the command.
     *
     * @param name value. 
     */
    void setName(String name);

    /**
     * The terminate method allows the commands execution to be interupted.
     */
    void terminate();
}
