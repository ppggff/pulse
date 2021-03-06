// dependency: ./namespace.js
// dependency: ./Button.js
// dependency: ./Checkbox.js
// dependency: ./ComboBox.js
// dependency: ./ControllingCheckbox.js
// dependency: ./DropDownList.js
// dependency: ./ItemPicker.js
// dependency: ./PasswordField.js
// dependency: ./StringList.js
// dependency: ./TemplateIcon.js
// dependency: ./TextArea.js
// dependency: ./TextField.js

(function($)
{
    var ui = kendo.ui,
        Widget = ui.Widget,
        ns = ".kendoForm",
        CLICK = "click" + ns,
        KEYUP = "keyup" + ns,
        SUBMIT = "submit" + ns,
        SELECTOR_FIELD_WRAPPER = ".k-field-wrapper",
        SELECTOR_FIELD_ACTIONS = ".k-field-action-cell",
        SELECTOR_FIELD_HELP = ".k-field-help",
        ACTION = "action",
        CREATED = "created",
        BUTTON_CLICKED = "buttonClicked",
        ENTER_PRESSED = "enterPressed",
        NAVIGATE = "navigate",
        DEFAULT_SUBMITS = ["apply", "reset"],
        FIELD_TYPES = {
            checkbox: "kendoZaCheckbox",
            "controlling-checkbox": "kendoZaControllingCheckbox",
            "controlling-select": "kendoZaControllingDropDownList",
            combobox: "kendoZaComboBox",
            dropdown: "kendoZaDropDownList",
            itempicker: "kendoZaItemPicker",
            password: "kendoZaPasswordField",
            stringlist: "kendoZaStringList",
            text: "kendoZaTextField",
            textarea: "kendoZaTextArea"
        };

    Zutubi.config.Form = Widget.extend({
        init: function(element, options)
        {
            var that = this;

            Widget.fn.init.call(this, element, options);

            that._create();
        },

        events: [
            ACTION,
            CREATED,
            BUTTON_CLICKED,
            ENTER_PRESSED,
            NAVIGATE
        ],

        options: {
            name: "ZaForm",
            formName: "form",
            template: '<form name="#: id #" id="#: id #"><table class="k-form"><tbody></tbody></table></form>',
            hiddenTemplate: '<input type="hidden" id="#: id #" name="#: name #">',
            fieldTemplate: '<tr>' +
                               '<td id="#: id #-decorations" class="k-field-decorations-cell"></td>' +
                               '<th><label id="#: id #-label" for="#: id #">#: label #</label></th>' +
                               '<td><span id="#: id #-wrap" class="k-field-wrapper"></span></td>' +
                               '<td id="#: id #-actions" class="k-field-action-cell"></td>' +
                           '</tr>',
            helpTemplate: '<div class="k-builtin-help k-field-help k-collapsed"><div class="k-field-help-brief">#= brief #</div><div class="k-field-help-verbose">#= verbose #</div></div>',
            exampleTemplate: '<li class="k-field-help-example">#= blurb #<div class="k-field-help-example-value">#= value #</div></li>',
            buttonTemplate: '<button id="#: id #" type="button" value="#: value #">#: name #</button>',
            errorTemplate: '<li>#: message #</li>',
            markRequired: true
        },

        _create: function()
        {
            var structure = this.options.structure,
                fields = structure.fields,
                submits = this.options.submits || DEFAULT_SUBMITS,
                fieldOptions,
                submitCell,
                i;

            this.id = "zaf-" + this.options.formName;

            this.fields = [];
            this.submits = [];
            this.template = kendo.template(this.options.template);
            this.hiddenTemplate = kendo.template(this.options.hiddenTemplate);
            this.fieldTemplate = kendo.template(this.options.fieldTemplate);
            this.helpTemplate = kendo.template(this.options.helpTemplate);
            this.exampleTemplate = kendo.template(this.options.exampleTemplate);
            this.buttonTemplate = kendo.template(this.options.buttonTemplate);
            this.errorTemplate = kendo.template(this.options.errorTemplate);

            this.docs = new Zutubi.config.Docs(this.options.docs);
            this.helpShown = false;

            this.element.html(this.template({id: this.id}));
            this.formElement = this.element.find("form");
            this.formElement.on(SUBMIT, jQuery.proxy(this._formSubmit, this));

            this.tableBodyElement = this.formElement.find("tbody");

            for (i = 0; i < fields.length; i++)
            {
                fieldOptions = fields[i];
                this._appendField(fieldOptions);
            }

            if (!this.options.readOnly)
            {
                this.tableBodyElement.append('<tr><td class="k-submit" colspan="3"></td></tr>');
                submitCell = this.tableBodyElement.find(".k-submit");
                for (i = 0; i < submits.length; i++)
                {
                    this._addSubmit(submits[i], submitCell);
                }
            }

            if (this.options.values)
            {
                this.bindValues(this.options.values);
            }

            this.updateButtons();
            this.trigger(CREATED);
        },

        destroy: function()
        {
            var that = this;

            that.formElement.off(ns);
            that.tableBodyElement.find(SELECTOR_FIELD_WRAPPER).off(ns);

            Widget.fn.destroy.call(that);
            kendo.destroy(that.element);

            that.element = null;
        },

        _fieldParameter: function(fieldOptions, name)
        {
            var parameters = fieldOptions.parameters;
            if (parameters && typeof parameters[name] !== "undefined")
            {
                return parameters[name];
            }

            return null;
        },

        _appendField: function(fieldOptions)
        {
            var rowElement, fieldElement, fieldType, field;

            // HTML5 ids can contain most anything, but not spaces.  Our names can't include
            // slashes, so use them as a safe substitute.
            fieldOptions.id = this.id + "-" + fieldOptions.name.replace(/ /g, '/');

            if (fieldOptions.type === "hidden")
            {
                this.formElement.append(this.hiddenTemplate(fieldOptions));
            }
            else
            {
                fieldOptions.label = fieldOptions.label || fieldOptions.name;
                fieldOptions.type = fieldOptions.type || "text";

                rowElement = $(this.fieldTemplate(fieldOptions));
                fieldElement = rowElement.appendTo(this.tableBodyElement).find(SELECTOR_FIELD_WRAPPER);

                if (fieldOptions.name === "name" || (this.options.markRequired && fieldOptions.required))
                {
                    rowElement.find("label").addClass("k-required");
                }

                fieldType = FIELD_TYPES[fieldOptions.type];
                if (fieldType)
                {
                    field = fieldElement[fieldType]({
                        structure: fieldOptions,
                        parentForm: this
                    }).data(fieldType);

                    fieldElement.on(KEYUP, jQuery.proxy(this._keyUp, this));
                    if (this.options.readOnly)
                    {
                        field.enable(false);
                    }
                    else
                    {
                        if (this.options.dirtyChecking)
                        {
                            fieldElement.on(CLICK, jQuery.proxy(this.updateButtons, this));
                            field.bind("change", jQuery.proxy(this.updateButtons, this));
                        }
                    }

                    this.fields.push(field);
                }

                this._addFieldDecorations(field, fieldOptions, rowElement.find(".k-field-decorations-cell"));

                if (!this.options.readOnly)
                {
                    this._addFieldActions(field, fieldOptions, rowElement.find(".k-field-action-cell"));
                }

                this._addFieldScripts(field, fieldOptions);
                this._addFieldDocs(fieldOptions.name, fieldElement);
            }
        },

        _addFieldDecorations: function(field, fieldOptions, decorationsElement)
        {
            var inheritedFrom = this._fieldParameter(fieldOptions, "inheritedFrom"),
                readOnly = this.options.readOnly,
                overriddenOwner,
                el,
                items,
                icon;

            if (inheritedFrom)
            {
                el = $('<span></span>').appendTo(decorationsElement);
                icon = el.kendoZaTemplateIcon({
                    spriteCssClass: "fa fa-arrow-circle-up",
                    items: [{
                        text: "inherits value defined in " + kendo.htmlEncode(inheritedFrom),
                        action: "navigate",
                        owner: inheritedFrom
                    }]
                }).data("kendoZaTemplateIcon");
            }
            else
            {
                overriddenOwner = this._fieldParameter(fieldOptions, "overriddenOwner");
                if (overriddenOwner)
                {
                    items = [{
                        text: "overrides value defined in " + kendo.htmlEncode(overriddenOwner),
                        action: "navigate",
                        owner: overriddenOwner
                    }];

                    if (!readOnly)
                    {
                        items.push({
                            text: "revert to inherited value",
                            action: "revert",
                            field: field
                        });
                    }

                    el = $('<span></span>').appendTo(decorationsElement);
                    icon = el.kendoZaTemplateIcon({
                        spriteCssClass: "fa fa-arrow-circle-right",
                        items: items
                    }).data("kendoZaTemplateIcon");
                }
            }

            if (icon)
            {
                icon.bind("select", jQuery.proxy(this._decorationSelect, this));
            }
        },

        _decorationSelect: function(e)
        {
            var action = e.item.action,
                field;
            if (action === "navigate")
            {
                this.trigger(NAVIGATE, {owner: e.item.owner});
            }
            else if (action === "revert")
            {
                field = e.item.field;
                field.bindValue(field.options.structure.parameters.overriddenValue);
                this.updateButtons();
            }
        },

        _addFieldActions: function(field, fieldOptions, actionsElement)
        {
            var actions = fieldOptions.actions, i;
            if (actions)
            {
                for (i = 0; i < actions.length; i++)
                {
                    this._addFieldAction(field, actions[i], actionsElement);
                }
            }
        },

        _addFieldAction: function(field, action, actionsElement)
        {
            var id = this.id + "-action-" + action,
                buttonEl = $(this.buttonTemplate({name: action, value: action, id: id}));

            buttonEl.kendoZaButton({click: jQuery.proxy(this._actionClicked, this, field, action)});

            actionsElement.append(buttonEl);
        },

        _actionClicked: function(field, action)
        {
            this.trigger(ACTION, {
                field: field,
                action: action
            });
        },

        _addFieldScripts: function(field, fieldOptions)
        {
            var scripts = fieldOptions.scripts,
                i,
                result;

            if (scripts)
            {
                for (i = 0; i < scripts.length; i++)
                {
                    result = eval(scripts[i]);
                    if (typeof result === "function")
                    {
                        result(this, field);
                    }
                }
            }
        },

        _addFieldDocs: function(fieldName, fieldElement)
        {
            var fieldDocs = this.docs.getPropertyDocs(fieldName),
                helpElement,
                i,
                examplesList,
                example;

            if (fieldDocs)
            {
                helpElement = $(this.helpTemplate({
                    brief: fieldDocs.brief || "",
                    verbose: fieldDocs.verbose || ""
                }));

                helpElement.appendTo(fieldElement.closest("td"));

                if (fieldDocs.examples && fieldDocs.examples.length > 0)
                {
                    examplesList = $('<div class="k-field-help-examples"><h3>examples</h3><ul></ul></div>').appendTo(helpElement).find("ul");
                    for (i = 0; i < fieldDocs.examples.length; i++)
                    {
                        example = fieldDocs.examples[i];
                        examplesList.append(this.exampleTemplate({
                            blurb: example.blurb || "",
                            value: example.value
                        }));
                    }
                }
            }
        },

        _addSubmit: function(name, parentElement)
        {
            var that = this,
                id = this.id + "-submit-" + name,
                element,
                button;

            parentElement.append(this.buttonTemplate({name: name, value: name, id: id}));
            element = parentElement.find("button").last();
            button = element.kendoZaButton({
                click: jQuery.proxy(that._buttonClicked, that, name),
                value: name
            }).data("kendoZaButton");

            that.submits.push(button);
        },

        _formSubmit: function(e)
        {
            e.preventDefault();
        },

        _buttonClicked: function(value)
        {
            if (value === "reset")
            {
                this.resetValues();
            }
            else
            {
                this.clearMessages();
                this.trigger(BUTTON_CLICKED, {value: value});
            }
        },

        _keyUp: function(e)
        {
            var wrapper;

            if (this.options.dirtyChecking)
            {
                this.updateButtons();
            }

            if (e.which === 13)
            {
                wrapper = $(e.target).closest(SELECTOR_FIELD_WRAPPER);
                if (wrapper && this._enterSubmits(wrapper))
                {
                    if (this.submits.length > 0)
                    {
                        this._buttonClicked(this.options.defaultSubmit || this.submits[0].options.value);
                    }
                    else
                    {
                        this.trigger(ENTER_PRESSED);
                    }
                }
            }
        },

        _enterSubmits: function(wrapperEl)
        {
            return ["zatextfield", "zapasswordfield", "zacheckbox", "zacontrollingcheckbox"].indexOf(wrapperEl.attr("data-role")) >= 0;
        },

        updateButtons: function()
        {
            var i,
                enabled;

            if (this.options.dirtyChecking)
            {
                enabled = this.fields.length === 0 || this.isDirty();

                for (i = 0; i < this.submits.length; i++)
                {
                    this.submits[i].enable(enabled);
                }
            }
        },

        bindValues: function(values)
        {
            var i, field, name;

            if (typeof this.originalValues === "undefined")
            {
                this.originalValues = values;
            }

            for (i = 0; i < this.fields.length; i++)
            {
                field = this.fields[i];
                name = field.getFieldName();
                if (values.hasOwnProperty(name))
                {
                    field.bindValue(values[name]);
                }
            }

            this.updateButtons();
        },

        resetValues: function()
        {
            this.clearMessages();
            if (this.originalValues)
            {
                this.bindValues(this.originalValues);
            }
        },

        getValues: function()
        {
            var values = {}, i, field;

            for (i = 0; i < this.fields.length; i++)
            {
                field = this.fields[i];
                values[field.getFieldName()] = field.getValue();
            }

            return values;
        },

        _isEmptyValue: function(value)
        {
            return value === null || value === "";
        },

        _valuesEqual: function(v1, v2)
        {
            if (this._isEmptyValue(v1))
            {
                return this._isEmptyValue(v2);
            }

            if (Array.isArray(v1))
            {
                return Array.isArray(v2) && Zutubi.core.arraysEqual(v1, v2);
            }

            return String(v1) === String(v2);
        },

        isDirty: function()
        {
            var values, field, original, value;

            if (this.originalValues)
            {
                values = this.getValues();
                for (field in values)
                {
                    if (values.hasOwnProperty(field))
                    {
                        value = values[field];
                        if (this.originalValues.hasOwnProperty(field))
                        {
                            original = this.originalValues[field];
                            if (!this._valuesEqual(value, original))
                            {
                                return true;
                            }
                        }
                        else if (!this._isEmptyValue(value))
                        {
                            return true;
                        }
                    }
                }

                return false;
            }
            else
            {
                return true;
            }
        },

        getFields: function()
        {
            return this.fields;
        },

        getFieldNamed: function(name)
        {
            var i, field;

            for (i = 0; i < this.fields.length; i++)
            {
                field = this.fields[i];
                if (field.getFieldName() === name)
                {
                    return field;
                }
            }

            return null;
        },

        enableField: function(field, enable)
        {
            var buttons;
            if (field && field.enable)
            {
                field.enable(enable);
                buttons = field.element.closest("tr").find(SELECTOR_FIELD_ACTIONS).find("button");
                buttons.prop("disabled", !enable);
                if (enable)
                {
                    buttons.removeClass("k-state-disabled");
                }
                else
                {
                    buttons.addClass("k-state-disabled");
                }
            }
        },

        clearMessages: function()
        {
            this.element.find(".k-form-validation-errors").remove();
            this.element.find(".k-form-feedback").remove();
        },

        showStatus: function(success, message)
        {
            var status = $('<p class="k-form-feedback ' + (success ? 'k-form-success' : 'k-form-failure') + '"></p>').prependTo(this.element);
            status.text(message);
        },

        showValidationErrors: function(errors)
        {
            var field;

            if (errors)
            {
                for (field in errors)
                {
                    if (errors.hasOwnProperty(field))
                    {
                        if (field === "")
                        {
                            this._showInstanceErrors(errors[field]);
                        }
                        else
                        {
                            this._showFieldErrors(field, errors[field]);
                        }
                    }
                }
            }
        },

        hasHelp: function()
        {
            return this.element.find(SELECTOR_FIELD_HELP).length > 0;
        },

        isHelpShown: function()
        {
            return this.hasHelp() && this.helpShown;
        },

        toggleHelp: function(show)
        {
            var els = this.tableBodyElement.find(SELECTOR_FIELD_HELP);
            if (typeof show === "undefined")
            {
                show = !this.helpShown;
            }

            this.helpShown = show;
            els.toggleClass("k-expanded", show);
            els.toggleClass("k-collapsed", !show);
        },

        _showErrors: function(errorList, messages)
        {
            var i;

            for (i = 0; i < messages.length; i++)
            {
                errorList.append(this.errorTemplate({message: messages[i]}));
            }
        },

        _showInstanceErrors: function(messages)
        {
            var errorList = $('<ul class="k-form-validation-errors"></ul>').prependTo(this.element);
            this._showErrors(errorList, messages);
        },

        _showFieldErrors: function(fieldName, messages)
        {
            var field, fieldCell, errorList;

            if (messages.length)
            {
                field = this.getFieldNamed(fieldName);
                if (field)
                {
                    fieldCell = field.element.closest("td");
                    errorList = $('<ul class="k-form-validation-errors"></ul>').appendTo(fieldCell);
                    this._showErrors(errorList, messages);
                }
            }
        }
    });

    ui.plugin(Zutubi.config.Form);
}(jQuery));
