package com.cinnamonbob.scheduling;

import com.cinnamonbob.model.Project;

import java.util.List;

/**
 * <class-comment/>
 */
public interface ScheduleManager
{
    Schedule getSchedule(long id);

    Schedule getSchedule(Project project, String name);

    void schedule(String name, Project project, Trigger trigger, Task task);

    List<Schedule> getSchedules(Project project);

    void delete(Schedule schedule);
}
