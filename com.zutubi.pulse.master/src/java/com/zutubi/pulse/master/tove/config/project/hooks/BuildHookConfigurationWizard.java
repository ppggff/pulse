package com.zutubi.pulse.master.tove.config.project.hooks;

import com.zutubi.tove.type.CompositeType;
import com.zutubi.tove.type.TypeException;
import com.zutubi.tove.type.TypeRegistry;
import com.zutubi.tove.type.record.MutableRecord;
import com.zutubi.tove.ui.forms.FormContext;
import com.zutubi.tove.ui.model.TypedWizardStepModel;
import com.zutubi.tove.ui.model.WizardModel;
import com.zutubi.tove.ui.model.WizardTypeModel;
import com.zutubi.tove.ui.wizards.ConfigurationWizard;
import com.zutubi.tove.ui.wizards.WizardContext;
import com.zutubi.tove.ui.wizards.WizardModelBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * Wizard for creating build hooks with tasks.
 */
public class BuildHookConfigurationWizard implements ConfigurationWizard
{
    private static final String KEY_TASK = "task";

    private WizardModelBuilder wizardModelBuilder;
    private TypeRegistry typeRegistry;

    @Override
    public WizardModel buildModel(CompositeType type, FormContext context) throws TypeException
    {
        WizardModel model = new WizardModel();
        model.appendStep(wizardModelBuilder.buildStepForType("", type, context));
        TypedWizardStepModel taskStep = wizardModelBuilder.buildStepForClass(KEY_TASK, BuildHookTaskConfiguration.class, context);
        for (WizardTypeModel taskModel: taskStep.getTypes())
        {
            CompositeType taskType = typeRegistry.getType(taskModel.getType().getSymbolicName());
            CompatibleHooks annotation = taskType.getAnnotation(CompatibleHooks.class, true);
            if (annotation != null)
            {
                Set<String> symbolicNames = new HashSet<>();
                for (Class<? extends BuildHookConfiguration> clazz: annotation.value())
                {
                    symbolicNames.add(typeRegistry.getType(clazz).getSymbolicName());
                }
                taskModel.setTypeFilter("", symbolicNames);
            }
        }
        model.appendStep(taskStep);
        return model;
    }

    @Override
    public MutableRecord buildRecord(CompositeType type, WizardContext wizardContext) throws TypeException
    {
        MutableRecord hookRecord = wizardModelBuilder.buildAndValidateRecord(type, "", wizardContext);
        CompositeType taskType = wizardModelBuilder.getCompositeType(BuildHookTaskConfiguration.class);
        hookRecord.put(KEY_TASK, wizardModelBuilder.buildAndValidateRecord(taskType, KEY_TASK, wizardContext));
        return hookRecord;
    }

    public void setWizardModelBuilder(WizardModelBuilder wizardModelBuilder)
    {
        this.wizardModelBuilder = wizardModelBuilder;
    }

    public void setTypeRegistry(TypeRegistry typeRegistry)
    {
        this.typeRegistry = typeRegistry;
    }
}
