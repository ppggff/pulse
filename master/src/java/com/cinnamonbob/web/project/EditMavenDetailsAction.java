package com.cinnamonbob.web.project;

import com.cinnamonbob.model.BobFileDetails;
import com.cinnamonbob.model.MavenBobFileDetails;
import com.opensymphony.util.TextUtils;

/**
 *
 *
 */
public class EditMavenDetailsAction extends AbstractEditDetailsAction
{
    private MavenBobFileDetails details = new MavenBobFileDetails();

    public void prepare()
    {
        details = getBobFileDetailsDao().findByIdAndType(getId(), MavenBobFileDetails.class);
    }

    public BobFileDetails getDetails()
    {
        if (!TextUtils.stringSet(details.getBaseDir()))
        {
            details.setBaseDir(null);
        }

        if (!TextUtils.stringSet(details.getTargets()))
        {
            details.setTargets(null);
        }

        return details;
    }
}
