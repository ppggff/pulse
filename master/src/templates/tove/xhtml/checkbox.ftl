<#include "/tove/xhtml/controlheader.ftl" />

(function()
{
<#if parameters.value?exists && parameters.value == "true">
    fieldConfig.checked = true;
</#if>
    fieldConfig.width = 14;
    fieldConfig.autoCreate = { tag: 'input', type: 'checkbox', value: 'true', id: fieldConfig.id };

    var checkbox = new Ext.form.Checkbox(fieldConfig);
    form.add(checkbox);
    checkbox.on('check', updateButtons);

    form.add(new Ext.form.Hidden({name: '${parameters.name}.default', value: 'false'}));
}());

<#include "/tove/xhtml/controlfooter.ftl" />