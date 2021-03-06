/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.pulse.core.dependency.ivy;

import com.zutubi.util.logging.Logger;
import org.apache.ivy.util.Message;

/**
 * An adapter that allows us to direct log output generated by ivy to our own
 * logging system.
 */
public class IvyMessageLoggerAdapter extends org.apache.ivy.util.AbstractMessageLogger
{
    private static final Logger LOG = Logger.getLogger(IvyMessageLoggerAdapter.class);

    protected void doProgress()
    {
        // This is just the default logger.  This logger will not be used for actions that
        // provide progress feedback.
    }

    protected void doEndProgress(String msg)
    {
        // This is just the default logger.  This logger will not be used for actions that
        // provide progress feedback.
    }

    public void log(String msg, int level)
    {
        // send the message logging to one of our loggers.
        switch (level)
        {
            case Message.MSG_DEBUG:
                LOG.debug(msg);
                break;
            case Message.MSG_ERR:
                LOG.error(msg);
                break;
            case Message.MSG_INFO:
                LOG.info(msg);
                break;
            case Message.MSG_VERBOSE:
                LOG.finest(msg);
                break;
            case Message.MSG_WARN:
                LOG.warning(msg);
                break;
        }
    }

    public void rawlog(String msg, int level)
    {
        log(msg, level);
    }
}
