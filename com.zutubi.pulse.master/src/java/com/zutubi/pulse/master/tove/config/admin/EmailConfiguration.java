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

package com.zutubi.pulse.master.tove.config.admin;

import com.google.common.base.Objects;
import com.zutubi.tove.annotations.*;
import com.zutubi.tove.config.api.AbstractConfiguration;
import com.zutubi.util.StringUtils;
import com.zutubi.validation.Validateable;
import com.zutubi.validation.ValidationContext;
import com.zutubi.validation.ValidationException;
import com.zutubi.validation.annotations.Email;
import com.zutubi.validation.annotations.Numeric;
import com.zutubi.validation.validators.EmailValidator;

/**
 *
 *
 */
@SymbolicName("zutubi.emailConfig")
@Form(fieldOrder = { "host", "ssl", "from", "username", "password", "subjectPrefix", "customPort", "port", "localhost"})
@Classification(single = "email")
public class EmailConfiguration extends AbstractConfiguration implements Validateable
{
    private String host;
    private boolean ssl = false;
    @Email
    private String from;
    private String username;
    @Password
    private String password;
    private String subjectPrefix;
    private String localhost;

    @ControllingCheckbox(checkedFields = {"port"})
    private boolean customPort;
    
    @Numeric(min = 1)
    private int port;

    public EmailConfiguration()
    {
        setPermanent(true);
    }

    public String getHost()
    {
        return host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public boolean getSsl()
    {
        return ssl;
    }

    public void setSsl(boolean ssl)
    {
        this.ssl = ssl;
    }

    public String getFrom()
    {
        return from;
    }

    public void setFrom(String from)
    {
        this.from = from;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getSubjectPrefix()
    {
        return subjectPrefix;
    }

    public void setSubjectPrefix(String subjectPrefix)
    {
        this.subjectPrefix = subjectPrefix;
    }

    public boolean isCustomPort()
    {
        return customPort;
    }

    public void setCustomPort(boolean customPort)
    {
        this.customPort = customPort;
    }

    public int getPort()
    {
        if(customPort)
        {
            return port;
        }
        else
        {
            return ssl ? 465 : 25;
        }
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public String getLocalhost()
    {
        return localhost;
    }

    public void setLocalhost(String localhost)
    {
        this.localhost = localhost;
    }

    public boolean isEquivalentTo(EmailConfiguration other)
    {
        return other != null &&
                Objects.equal(host, other.host) &&
                ssl == other.ssl &&
                Objects.equal(from, other.from) &&
                Objects.equal(username, other.username) &&
                Objects.equal(password, other.password) &&
                Objects.equal(subjectPrefix, other.subjectPrefix) &&
                Objects.equal(localhost, other.localhost) &&
                customPort == other.customPort &&
                (!customPort || port == other.port);
    }

    public void validate(ValidationContext context)
    {
        if (StringUtils.stringSet(host))
        {
            if (!StringUtils.stringSet(from))
            {
                context.addFieldError("from", "from address is required when smtp host is provided");
            }
        }

        // If the from address is specified, then ensure that a valid value is set.
        if (StringUtils.stringSet(from))
        {
            EmailValidator validator = new EmailValidator();
            validator.setValidationContext(context);
            validator.setFieldName("from");
            try
            {
                validator.validate(this);
            }
            catch (ValidationException e)
            {
                context.addFieldError("from", e.getMessage());
            }
        }
    }
}
