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

package com.zutubi.tove.ui;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.zutubi.i18n.Messages;
import com.zutubi.tove.ConventionSupport;
import com.zutubi.tove.annotations.Listing;
import com.zutubi.tove.annotations.NoOverride;
import com.zutubi.tove.annotations.Password;
import com.zutubi.tove.config.*;
import com.zutubi.tove.config.api.ActionVariant;
import com.zutubi.tove.config.api.Configuration;
import com.zutubi.tove.config.cleanup.RecordCleanupTask;
import com.zutubi.tove.config.docs.ConfigurationDocsManager;
import com.zutubi.tove.security.AccessManager;
import com.zutubi.tove.type.*;
import com.zutubi.tove.type.record.PathUtils;
import com.zutubi.tove.type.record.Record;
import com.zutubi.tove.type.record.TemplateRecord;
import com.zutubi.tove.ui.actions.ActionManager;
import com.zutubi.tove.ui.format.ConfigPropertyFormatter;
import com.zutubi.tove.ui.format.StateDisplayManager;
import com.zutubi.tove.ui.forms.FormContext;
import com.zutubi.tove.ui.links.LinkManager;
import com.zutubi.tove.ui.model.*;
import com.zutubi.tove.ui.model.forms.FieldModel;
import com.zutubi.tove.ui.model.forms.FormModel;
import com.zutubi.util.Sort;
import com.zutubi.util.StringUtils;
import com.zutubi.util.adt.Pair;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Builds ConfigModel instances given relevant path and type info.
 */
public class ConfigModelBuilder
{
    private ActionManager actionManager;
    private ConfigurationSecurityManager configurationSecurityManager;
    private ConfigurationTemplateManager configurationTemplateManager;
    private FormModelBuilder formModelBuilder;
    private TableModelBuilder tableModelBuilder;
    private LinkManager linkManager;
    private ConfigurationRegistry configurationRegistry;
    private TypeRegistry typeRegistry;
    private ConfigurationPersistenceManager configurationPersistenceManager;
    private ConfigurationDocsManager configurationDocsManager;
    private StateDisplayManager stateDisplayManager;
    private ConfigPropertyFormatter configPropertyFormatter;
    private ConfigurationRefactoringManager configurationRefactoringManager;

    public ConfigModel buildModel(String[] filters, String path, int depth) throws TypeException
    {
        String parentPath = PathUtils.getParentPath(path);
        ComplexType type = configurationTemplateManager.getType(path);
        ComplexType parentType = configurationTemplateManager.getType(parentPath);
        Record record = configurationTemplateManager.getRecord(path);
        return buildModel(filters, path, type, parentType, record, depth);
    }

    public ConfigModel buildModel(String[] filters, String path, ComplexType type, ComplexType parentType, Record record, int depth) throws TypeException
    {
        String label = getLabel(path, type, parentType, record);
        ConfigModel model;
        if (type instanceof CollectionType)
        {
            model = createCollectionModel(path, (CollectionType) type, label, record, filters);
        }
        else
        {
            CompositeType compositeType = (CompositeType) type;
            if (configurationTemplateManager.isPersistent(path))
            {
                if (record == null)
                {
                    model = createTypeSelectionModel(path, compositeType, label, filters);
                }
                else
                {
                    model = createCompositeModel(path, compositeType, label, parentType == null || parentType.hasSignificantKeys(), record, filters);
                }
            }
            else
            {
                model = createTransientModel(path, compositeType, label, filters);
            }
        }

        if (depth != 0 && record != null && type != null)
        {
            model.setNested(getNested(filters, path, type, record, depth - 1));
        }

        return model;
    }

    private boolean isFieldSelected(String[] filters, String fieldName)
    {
        if (filters == null)
        {
            return true;
        }

        fieldName = fieldName.toLowerCase();

        // Fields may have the form <parent>.<field>, e.g. "type.simpleProperties".  In this case
        // filtering of the parent works the same as at the top level: if nothing in the parent is
        // filtered then all fields are included, but as soon as anything in the parent is filtered
        // only explicitly-mentioned fields are included.  To included only the non-filterable
        // fields of a parent field you can pass filter <parent>., e.g. "type.".
        boolean parentIsFiltered = false;
        int separatorIndex = fieldName.indexOf('.');
        if (separatorIndex > 0 && separatorIndex < fieldName.length() - 1)
        {
            String parentPrefix = fieldName.substring(0, separatorIndex + 1);
            for (String filter: filters)
            {
                if (filter.startsWith(parentPrefix))
                {
                    parentIsFiltered = true;
                    break;
                }
            }
        }
        else
        {
            for (String filter: filters)
            {
                if (filter.indexOf('.') == -1)
                {
                    parentIsFiltered = true;
                    break;
                }
            }
        }

        for (String filter: filters)
        {
            if (filter.equals(fieldName) || filter.equals(fieldName + "."))
            {
                return true;
            }
        }

        return !parentIsFiltered;
    }

    private String getLabel(String path, ComplexType type, ComplexType parentType, Record value)
    {
        String label = ToveUiUtils.getDisplayName(path, type, parentType, value);
        if (!StringUtils.stringSet(label))
        {
            label = PathUtils.getBaseName(path);
        }
        return label;
    }

    private ConfigModel createCollectionModel(String path, CollectionType type, String label, Record record, String[] filters)
    {
        String baseName = PathUtils.getBaseName(path);
        boolean deeplyValid = configurationTemplateManager.isDeeplyValid(path);
        CollectionModel model = new CollectionModel(baseName, Long.toString(record.getHandle()), label, deeplyValid);
        if (isFieldSelected(filters, "table"))
        {
            model.setTable(tableModelBuilder.createTable(type));
        }

        if (isFieldSelected(filters, "type"))
        {
            CollectionTypeModel typeModel = new CollectionTypeModel(type);
            if (typeModel.getTargetType() != null)
            {
                typeModel.getTargetType().setDocs(configurationDocsManager.getDocs((CompositeType) type.getTargetType()));
            }

            model.setType(typeModel);
        }

        if (isFieldSelected(filters, "allowedActions"))
        {
            if (configurationSecurityManager.hasPermission(path, AccessManager.ACTION_CREATE))
            {
                model.addAllowedAction(AccessManager.ACTION_CREATE);
            }

            if (configurationSecurityManager.hasPermission(path, AccessManager.ACTION_WRITE))
            {
                model.addAllowedAction(AccessManager.ACTION_WRITE);
            }
        }

        if (isFieldSelected(filters, "hiddenItems"))
        {
            addHiddenItems(path, type, record, model);
        }

        if (isFieldSelected(filters, "state"))
        {
            Configuration instance = configurationTemplateManager.getInstance(path);
            if (instance != null)
            {
                Map<String, Object> configState = stateDisplayManager.getConfigState(instance);
                if (configState.size() > 0)
                {
                    model.setState(new StateModel(configState, Messages.getInstance(type.getTargetType().getClazz())));
                }
            }
        }

        if (type.isOrdered() && isFieldSelected(filters, "order"))
        {
            List<String> declaredOrder = CollectionType.getDeclaredOrder(record);
            if (declaredOrder.size() > 0)
            {
                model.setDeclaredOrder(declaredOrder);
                if (record instanceof TemplateRecord)
                {
                    TemplateRecord templateRecord = (TemplateRecord) record;
                    String orderTemplateOwner = templateRecord.getMetaOwner(CollectionType.ORDER_KEY);
                    String orderOverriddenOwner = null;
                    if (orderTemplateOwner.equals(templateRecord.getOwner()) && templateRecord.getParent() != null)
                    {
                        orderOverriddenOwner = templateRecord.getParent().getMetaOwner(CollectionType.ORDER_KEY);
                    }

                    model.decorateWithOrderTemplateDetails(orderTemplateOwner, orderOverriddenOwner);
                }
            }
        }

        templateDecorateModel(path, record, model);

        return model;
    }

    private void addHiddenItems(String path, CollectionType type, Record record, CollectionModel model)
    {
        if (record instanceof TemplateRecord)
        {
            TemplateRecord templateRecord = (TemplateRecord) record;
            TemplateRecord templateParent = templateRecord.getParent();

            if (templateParent != null)
            {
                String parentId = templateParent.getOwner();
                String[] elements = PathUtils.getPathElements(path);
                String parentPath = PathUtils.getPath(elements[0], parentId, PathUtils.getPath(2, elements));

                List<String> hiddenKeys = new LinkedList<>(templateRecord.getHiddenKeys());
                Collections.sort(hiddenKeys, type.getKeyComparator(record));
                for (String hidden : hiddenKeys)
                {
                    String parentItemPath = PathUtils.getPath(parentPath, hidden);
                    Configuration instance = configurationTemplateManager.getInstance(parentItemPath, Configuration.class);
                    if (instance != null)
                    {
                        model.addHiddenItem(new HiddenItemModel(hidden, templateParent.getOwner(hidden)));
                    }
                }
            }
        }
    }

    private ConfigModel createTypeSelectionModel(String path, CompositeType compositeType, String label, String[] filters)
    {
        String baseName = PathUtils.getBaseName(path);
        TypeSelectionModel model = new TypeSelectionModel(baseName, label);

        String closestExistingPath = path;
        if (configurationTemplateManager.isPersistent(path))
        {
            while (!configurationTemplateManager.pathExists(closestExistingPath))
            {
                closestExistingPath = PathUtils.getParentPath(closestExistingPath);
            }

            String templateOwnerPath = configurationTemplateManager.getTemplateOwnerPath(closestExistingPath);
            model.decorateWithTemplateDetails(configurationTemplateManager.isConcrete(closestExistingPath), true, PathUtils.getSuffix(templateOwnerPath, 1), null);
        }
        else
        {
            closestExistingPath = null;
        }

        if (isFieldSelected(filters, "type"))
        {
            FormContext context = new FormContext(closestExistingPath);
            model.setType(buildCompositeTypeModel(compositeType, context, filters));
        }

        if (isFieldSelected(filters, "configuredDescendants"))
        {
            model.setConfiguredDescendants(getConfiguredDescendants(path));
        }

        return model;
    }

    private List<Pair<Integer, String>> getConfiguredDescendants(String path)
    {
        String elements[] = PathUtils.getPathElements(path);
        if (elements.length >= 2)
        {
            String ownerPath = PathUtils.getPath(0, 2, elements);
            TemplateNode node = configurationTemplateManager.getTemplateNode(ownerPath);
            if (node != null)
            {
                final List<Pair<Integer, String>> result = new ArrayList<>();
                final String remainderPath = PathUtils.getPath(2, elements);
                final int topDepth = node.getDepth() + 1;
                node.forEachDescendant(new Function<TemplateNode, Boolean>()
                {
                    public Boolean apply(TemplateNode currentNode)
                    {
                        String descendantPath = PathUtils.getPath(currentNode.getPath(), remainderPath);
                        if (configurationTemplateManager.pathExists(descendantPath) && configurationSecurityManager.hasPermission(descendantPath, AccessManager.ACTION_VIEW))
                        {
                            result.add(new Pair<>(currentNode.getDepth() - topDepth, currentNode.getId()));
                            return false;
                        }

                        return true;
                    }
                }, true, new NodeIdComparator());

                return result;
            }
        }

        return null;
    }

    private CompositeModel createCompositeModel(String path, CompositeType type, String label, boolean keyed, Record record, String[] filters) throws TypeException
    {
        String baseName = PathUtils.getBaseName(path);
        boolean deeplyValid = configurationTemplateManager.isDeeplyValid(path);
        CompositeModel model = new CompositeModel(Long.toString(record.getHandle()), baseName, label, keyed, deeplyValid);
        if (type == null)
        {
            // If this record has a type that is not recognised, it's most likely due to a missing plugin.  Show the
            // user an error to this effect to help them recover.
            CompositeTypeModel typeModel = new CompositeTypeModel();
            typeModel.setSymbolicName(record.getSymbolicName());
            typeModel.setForm(new FormModel());
            model.setType(typeModel);
            Map<String, List<String>> errors = new HashMap<>();
            errors.put("", Collections.singletonList("Record has unrecognised type '" + record.getSymbolicName() + "', likely due to a missing plugin. Restore the plugin or delete this record to reconfigure."));
            model.setValidationErrors(errors);
            return model;
        }

        Configuration instance = configurationTemplateManager.getInstance(path);
        if (isFieldSelected(filters, "properties"))
        {
            model.setProperties(getProperties(configurationTemplateManager.getTemplateOwnerPath(path), type, record));
        }

        if (isFieldSelected(filters, "formattedProperties"))
        {
            model.setFormattedProperties(getFormattedProperties(path, type));
        }

        if (instance != null && isFieldSelected(filters, "validationErrors"))
        {
            model.setValidationErrors(getValidationErrors(instance));
        }

        if (isFieldSelected(filters, "type"))
        {
            model.setType(buildCompositeTypeModel(type, new FormContext(instance), filters));
            if (model.getType().getForm() != null)
            {
                if (record instanceof TemplateRecord)
                {
                    templateDecorateForm(model.getType().getForm(), type, (TemplateRecord) record);
                }
            }
        }

        if (isFieldSelected(filters, "actions"))
        {
            addActions(model, path, type, instance);
        }

        if (isFieldSelected(filters, "links"))
        {
            model.setLinks(linkManager.getLinks(instance));
        }

        if (isFieldSelected(filters, "state"))
        {
            Map<String, Object> configState = stateDisplayManager.getConfigState(instance);
            if (configState.size() > 0)
            {
                model.setState(new StateModel(configState, Messages.getInstance(type.getClazz())));
            }
        }

        templateDecorateModel(path, record, model);
        return model;
    }

    public Map<String, Object> getProperties(String templateOwnerPath, CompositeType type, Record record) throws TypeException
    {
        Map<String, Object> result = new HashMap<>();
        for (TypeProperty property: type.getProperties(SimpleType.class))
        {
            Object value = property.getType().toXmlRpc(templateOwnerPath, record.get(property.getName()));
            if (value != null && property.getAnnotation(Password.class) != null)
            {
                value = ToveUiUtils.SUPPRESSED_PASSWORD;
            }

            result.put(property.getName(), value);
        }

        for (TypeProperty property: type.getProperties(CollectionType.class))
        {
            if (property.getType().getTargetType() instanceof SimpleType)
            {
                Object value = property.getType().toXmlRpc(templateOwnerPath, record.get(property.getName()));
                result.put(property.getName(), value);
            }
        }

        return result;
    }

    private Map<String, Object> getFormattedProperties(String path, CompositeType type) throws TypeException
    {
        Configuration instance = configurationTemplateManager.getInstance(path);
        if (instance != null)
        {
            Map<String, Object> properties = configPropertyFormatter.getFormattedProperties(instance, type);
            if (properties.size() > 0)
            {
                return properties;
            }
        }

        return null;
    }

    private Map<String, List<String>> getValidationErrors(Configuration instance)
    {
        Map<String, List<String>> errors = new HashMap<>();
        if (!instance.getInstanceErrors().isEmpty())
        {
            errors.put("", new ArrayList<>(instance.getInstanceErrors()));
        }

        for (Map.Entry<String, List<String>> entry: instance.getFieldErrors().entrySet())
        {
            if (!entry.getValue().isEmpty())
            {
                errors.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        return errors.isEmpty() ? null : errors;
    }

    public CompositeTypeModel buildCompositeTypeModel(CompositeType type, FormContext context, String[] filters)
    {
        CompositeTypeModel typeModel = new CompositeTypeModel(type);
        if (!type.isExtendable())
        {
            if (isFieldSelected(filters, "type.form"))
            {
                typeModel.setForm(formModelBuilder.createForm(type));
                formModelBuilder.applyContextToForm(context, type, typeModel.getForm());
            }

            CompositeType checkType = configurationRegistry.getConfigurationCheckType(type);
            if (checkType != null)
            {
                CompositeTypeModel checkTypeModel = new CompositeTypeModel(checkType);
                checkTypeModel.setForm(formModelBuilder.createForm(checkType));
                typeModel.setCheckType(checkTypeModel);
            }
        }

        if (isFieldSelected(filters, "type.extensions"))
        {
            List<CompositeType> extensions = type.getExtensions();
            for (CompositeType extension: extensions)
            {
                typeModel.addSubType(buildCompositeTypeModel(extension, context, filters));
            }
        }

        if (isFieldSelected(filters, "type.docs"))
        {
            typeModel.setDocs(configurationDocsManager.getDocs(type));
        }

        return typeModel;
    }

    private void templateDecorateModel(String path, Record record, ConfigModel model)
    {
        if (record instanceof TemplateRecord)
        {
            TemplateRecord templateRecord = (TemplateRecord) record;
            String templateOriginator = getTemplateOriginator(templateRecord);

            model.decorateWithTemplateDetails(configurationTemplateManager.isConcrete(path), templateRecord.isSkeleton(), templateRecord.getOwner(), templateOriginator);
        }
    }

    private void templateDecorateForm(FormModel form, CompositeType type, TemplateRecord record)
    {
        TemplateRecord parentRecord = record.getParent();
        String templateOriginatorId = getTemplateOriginator(record);

        for (FieldModel field : form.getFields())
        {
            String fieldName = field.getName();

            // Note that if a field has both noInherit and noOverride,
            // noInherit takes precedence.
            if (!field.hasParameter(FormModelBuilder.PARAMETER_NO_INHERIT))
            {
                String ownerId = record.getOwner(fieldName);
                if (ownerId != null)
                {
                    if (!ownerId.equals(record.getOwner()))
                    {
                        if (fieldHasAnnotation(type, fieldName, NoOverride.class))
                        {
                            // This field should be read-only.
                            field.addParameter("noOverride", Boolean.toString(true));
                        }
                        else if (!ownerId.equals(templateOriginatorId))
                        {
                            field.addParameter("inheritedFrom", ownerId);
                        }
                    }
                    else if (parentRecord != null)
                    {
                        // Check for override
                        String parentOwnerId = parentRecord.getOwner(fieldName);
                        if (parentOwnerId != null)
                        {
                            field.addParameter("overriddenOwner", parentOwnerId);
                            field.addParameter("overriddenValue", parentRecord.get(fieldName));
                        }
                    }
                }
            }
        }
    }

    private String getTemplateOriginator(TemplateRecord templateRecord)
    {
        TemplateRecord originator = templateRecord;
        while (originator.getParent() != null)
        {
            originator = originator.getParent();
        }
        return originator.getOwner();
    }

    private boolean fieldHasAnnotation(CompositeType type, String fieldName, Class<? extends Annotation> annotationClass)
    {
        return type.getProperty(fieldName).getAnnotation(annotationClass) != null;
    }

    private void addActions(CompositeModel model, String path, CompositeType type, Configuration instance)
    {
        final Messages messages = Messages.getInstance(type.getClazz());
        List<String> actionNames = actionManager.getActions(instance, true, true);

        String key = null;
        Record parentRecord = null;
        String parentPath = PathUtils.getParentPath(path);
        boolean templateItem = false;
        ComplexType parentType = parentPath == null ? null : configurationTemplateManager.getType(parentPath);
        if (parentType != null && parentType instanceof MapType)
        {
            templateItem = configurationTemplateManager.isTemplatedCollection(parentPath);
            parentRecord = configurationTemplateManager.getRecord(parentPath);
            key = PathUtils.getBaseName(path);
        }

        for (String actionName: actionNames)
        {
            List<ActionVariant> variants = null;
            if (instance != null)
            {
                variants = actionManager.getVariants(actionName, instance);
            }

            if (variants == null)
            {
                String resolvedActionName = ToveUiUtils.resolveActionName(actionName, parentRecord, key);
                String label = ToveUiUtils.format(messages, resolvedActionName + ConventionSupport.I18N_KEY_SUFFIX_LABEL);
                model.addAction(new ActionModel(actionName, label, null, actionManager.hasArgument(actionName, type)));
            }
            else
            {
                for (ActionVariant variant: variants)
                {
                    model.addAction(new ActionModel(actionName, variant.getName(), variant.getName(), variant.hasArgument()));
                }
            }
        }

        List<String> descendantPaths = configurationTemplateManager.getDescendantPaths(path, true, true, false);
        configurationSecurityManager.filterPaths("", descendantPaths, AccessManager.ACTION_VIEW);
        if (descendantPaths.size() > 0)
        {
            Set<String> actionSet = new HashSet<>();
            for (String descendantPath: descendantPaths)
            {
                Configuration descendantInstance = configurationTemplateManager.getInstance(descendantPath);
                if (descendantInstance != null)
                {
                    actionSet.addAll(actionManager.getActions(descendantInstance, false, false));
                }
            }

            for (String actionName: actionSet)
            {
                String resolvedActionName = ToveUiUtils.resolveActionName(actionName, parentRecord, key);
                String label = ToveUiUtils.format(messages, resolvedActionName + ConventionSupport.I18N_KEY_SUFFIX_LABEL);
                model.addDescendantAction(new ActionModel(actionName, label, null, false));
            }
        }

        if (templateItem && configurationSecurityManager.hasPermission(parentPath, AccessManager.ACTION_CREATE))
        {
            if (configurationRefactoringManager.canIntroduceParentTemplate(path))
            {
                model.addRefactoringAction(new ActionModel("introduceParent", "introduce parent template", null, true));
            }
            if (configurationRefactoringManager.canSmartClone(path))
            {
                model.addRefactoringAction(new ActionModel("smartClone", "smart clone", null, true));
            }
        }
    }

    public TransientModel buildTransientModel(Class<? extends Configuration> clazz, String[] filters)
    {
        CompositeType type = typeRegistry.getType(clazz);
        if (type == null)
        {
            throw new IllegalArgumentException("Request for model of unregistered class '" + clazz + "'");
        }

        List<String> paths = configurationPersistenceManager.getConfigurationPaths(type);
        if (paths.size() != 1)
        {
            throw new IllegalArgumentException("No unambiguous path found for type '" + type.getSymbolicName() + "'");
        }

        String path = paths.get(0);
        ComplexType parentType = configurationTemplateManager.getType(PathUtils.getParentPath(path));
        return createTransientModel(path, type, ToveUiUtils.getDisplayName(path, type, parentType, null), filters);
    }

    private TransientModel createTransientModel(String path, CompositeType compositeType, String label, String[] filters)
    {
        TransientModel model = new TransientModel(PathUtils.getBaseName(path), label);
        model.setType(buildCompositeTypeModel(compositeType, new FormContext((String) null), filters));
        return model;
    }

    private List<ConfigModel> getNested(final String[] filters, final String path, final ComplexType type, final Record record, final int depth)
    {
        List<String> order;
        if (type instanceof CompositeType)
        {
            CompositeType compositeType = (CompositeType) type;
            order = compositeType.getNestedPropertyNames();
        }
        else
        {
            CollectionType collectionType = (CollectionType) type;
            order = collectionType.getOrder(record);
            configurationSecurityManager.filterPaths(path, order, AccessManager.ACTION_VIEW);
        }

        List<ConfigModel> children = Lists.newArrayList(Lists.transform(order, new Function<String, ConfigModel>()
        {
            @Override
            public ConfigModel apply(String key)
            {
                Record value = (Record) record.get(key);
                ComplexType propertyType = (ComplexType) type.getActualPropertyType(key, value);
                try
                {
                    return buildModel(filters, PathUtils.getPath(path, key), propertyType, type, value, depth);
                }
                catch (TypeException e)
                {
                    throw new ToveRuntimeException(e);
                }
            }
        }));

        if (type instanceof CompositeType)
        {
            // We do this last as it will use the labels we've looked up when creating the children
            // for default ordering.
            sortCompositeChildren((CompositeType)type, children);
        }

        return children;
    }

    private void sortCompositeChildren(CompositeType type, List<ConfigModel> children)
    {
        final Sort.StringComparator stringComparator = new Sort.StringComparator();
        Collections.sort(children, new Comparator<ConfigModel>()
        {
            @Override
            public int compare(ConfigModel c1, ConfigModel c2)
            {
                return stringComparator.compare(c1.getLabel(), c2.getLabel());
            }
        });

        Listing annotation = type.getAnnotation(Listing.class, true);
        if (annotation != null)
        {
            String[] definedOrder = annotation.order();
            int targetIndex = 0;
            for (final String key: definedOrder)
            {
                int sourceIndex = Iterables.indexOf(children, new Predicate<ConfigModel>()
                {
                    @Override
                    public boolean apply(ConfigModel c)
                    {
                        return c.getKey().equals(key);
                    }
                });

                // Note sourceIndex will never be less than targetIndex as we've already filled up
                // to targetIndex - 1 with other items (unless the Listing has dupes I guess).
                if (sourceIndex > targetIndex)
                {
                    children.add(targetIndex, children.remove(sourceIndex));
                }

                targetIndex++;
            }
        }
    }

    public CleanupTaskModel buildCleanupTask(RecordCleanupTask task)
    {
        CleanupTaskModel model = new CleanupTaskModel(task.getAffectedPath(), Messages.getInstance(task).format("summary"));
        for (RecordCleanupTask child: task.getCascaded())
        {
            if (configurationSecurityManager.hasPermission(child.getAffectedPath(), AccessManager.ACTION_VIEW))
            {
                model.addChild(buildCleanupTask(child));
            }
        }

        return model;
    }

    public void setActionManager(ActionManager actionManager)
    {
        this.actionManager = actionManager;
    }

    public void setConfigurationSecurityManager(ConfigurationSecurityManager configurationSecurityManager)
    {
        this.configurationSecurityManager = configurationSecurityManager;
    }

    public void setConfigurationTemplateManager(ConfigurationTemplateManager configurationTemplateManager)
    {
        this.configurationTemplateManager = configurationTemplateManager;
    }

    public void setFormModelBuilder(FormModelBuilder formModelBuilder)
    {
        this.formModelBuilder = formModelBuilder;
    }

    public void setTableModelBuilder(TableModelBuilder tableModelBuilder)
    {
        this.tableModelBuilder = tableModelBuilder;
    }

    public void setConfigurationRegistry(ConfigurationRegistry configurationRegistry)
    {
        this.configurationRegistry = configurationRegistry;
    }

    public void setLinkManager(LinkManager linkManager)
    {
        this.linkManager = linkManager;
    }

    public void setTypeRegistry(TypeRegistry typeRegistry)
    {
        this.typeRegistry = typeRegistry;
    }

    public void setConfigurationPersistenceManager(ConfigurationPersistenceManager configurationPersistenceManager)
    {
        this.configurationPersistenceManager = configurationPersistenceManager;
    }

    public void setConfigurationDocsManager(ConfigurationDocsManager configurationDocsManager)
    {
        this.configurationDocsManager = configurationDocsManager;
    }

    public void setStateDisplayManager(StateDisplayManager stateDisplayManager)
    {
        this.stateDisplayManager = stateDisplayManager;
    }

    public void setConfigPropertyFormatter(ConfigPropertyFormatter configPropertyFormatter)
    {
        this.configPropertyFormatter = configPropertyFormatter;
    }

    public void setConfigurationRefactoringManager(ConfigurationRefactoringManager configurationRefactoringManager)
    {
        this.configurationRefactoringManager = configurationRefactoringManager;
    }

    private static class NodeIdComparator implements Comparator<TemplateNode>
    {
        private static final Sort.StringComparator DELEGATE = new Sort.StringComparator();

        public int compare(TemplateNode n1, TemplateNode n2)
        {
            return DELEGATE.compare(n1.getId(), n2.getId());
        }
    }
}
