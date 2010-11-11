// dependency: ./namespace.js
// dependency: ext/package.js

/**
 * A bar that allows paging through a set of information that is too long to
 * show on a single page.
 *
 * @cfg {String} id          id to use for the rendered element
 * @cfg {String} itemLabel   name of the type of items being shown (e.g. build)
 * @cfg {String} url         url that paging links point to (the page number is
 *                           appended to this url
 * @cfg {Object} extraParams extra parameters to append to the url after the
 *                           page number
 * @cfg {Object} labels      specifies labels to use for steps, should include
 *                           properties first, previous, next, last which map
 *                           to labels (by default the labels use these terms
 *                           themselves)
 * @cfg {Object} data        an object specifying: totalItems, itemsPerPage and
 *                           currentPage
 */
Zutubi.Pager = Ext.extend(Ext.BoxComponent, {
    labels: {
        first: 'first',
        previous: 'previous',
        next: 'next',
        last: 'last'
    },

    surroundingPages: 10,

    template: new Ext.XTemplate('<table id="{id}" class="pager content-table"><tbody></tbody></table>'),

    countTemplate: new Ext.XTemplate(
        '<tr>' +
            '<th id="{id}-total" class="leftmost rightmost" colspan="5">{totalItems} {itemLabel}<tpl if="totalItems != 1">s</tpl> found</th>' +
        '</tr>'
    ),

    pagingTemplate: new Ext.XTemplate(
        '<tr id="{id}-paging">' +
            '<td class="leftmost">' +
                '<tpl if="currentPage &gt; 0"><a href="{url}0/{extraParams}" id="{id}-first"></tpl>' +
                    '<img alt="{labelFirst}" src="{[window.baseUrl]}/images/resultset_first.gif"/> {labelFirst}' +
                '<tpl if="currentPage &gt; 0"></a></tpl>' +
            '</td>' +
            '<td>' +
                '<tpl if="currentPage &gt; 0"><a href="{url}{currentPage-1}/{extraParams}" id="{id}-previous"></tpl>' +
                    '<img alt="{labelPrevious}" src="{[window.baseUrl]}/images/resultset_previous.gif"/> {labelPrevious}' +
                '<tpl if="currentPage &gt; 0"></a></tpl>' +
            '</td>' +
            '<th>' +
                '<tpl for="pages">' +
                    ' &nbsp; ' +
                    '<tpl if="index != parent.currentPage"><a href="{parent.url}{index}/{parent.extraParams}" id="{id}-page-{index}"></tpl>' +
                        '{index+1}' +
                    '<tpl if="index != parent.currentPage"></a></tpl>' +
                '</tpl>' +
                ' &nbsp; ' +
            '</th>' +
            '<td>' +
                '<tpl if="currentPage &lt; lastPage"><a href="{url}{currentPage+1}/{extraParams}" id="{id}-next"></tpl>' +
                    '<img alt="{labelNext}" src="{[window.baseUrl]}/images/resultset_next.gif"/> {labelNext}' +
                '<tpl if="currentPage &lt; lastPage"></a></tpl>' +
            '</td>' +
            '<td class="rightmost">' +
                '<tpl if="currentPage &lt; lastPage"><a href="{url}{lastPage}/{extraParams}" id="{id}-last"></tpl>' +
                    '<img alt="{labelLast}" src="{[window.baseUrl]}/images/resultset_last.gif"/> {labelLast}' +
                '<tpl if="currentPage &lt; lastPage"></a></tpl>' +
            '</td>' +
        '</tr>'
    ),

    onRender: function(container, position) {
        if (position)
        {
            this.el = this.template.insertBefore(position, this, true);
        }
        else
        {
            this.el = this.template.append(container, this, true);
        }

        this.tbodyEl = this.el.down('tbody');
        this.renderRows();

        Zutubi.Pager.superclass.onRender.apply(this, arguments);
    },

    renderRows: function()
    {
        this.tbodyEl.select('tr').remove();

        var data = this.data || {totalItems: 0};
        
        this.countTemplate.append(this.tbodyEl, {
            id: this.id,
            totalItems: data.totalItems,
            itemLabel: this.itemLabel
        });

        var pageCount = Math.floor((data.totalItems + data.itemsPerPage - 1) / data.itemsPerPage);
        if (pageCount > 1)
        {
            var firstPage = this.getFirstPage(data.currentPage, pageCount);
            var lastPage = this.getLastPage(data.currentPage, pageCount);

            var args = Ext.apply({
                id: this.id,
                url: this.url,
                extraParams: this.extraParams,
                itemLabel: this.itemLabel,
                labelFirst: this.labels.first,
                labelPrevious: this.labels.previous,
                labelNext: this.labels.next,
                labelLast: this.labels.last,
                lastPage: lastPage,
                pages: []
            }, data);

            for (var i = firstPage; i <= lastPage; i++)
            {
                args.pages.push({index: i});
            }

            this.pagingTemplate.append(this.tbodyEl, args);
        }
    },

    getFirstPage: function(currentPage, pageCount)
    {
        var offset = Math.floor(this.surroundingPages / 2);
        if (currentPage + offset + 1 > pageCount)
        {
            offset += currentPage + offset + 1 - pageCount;
        }

        return Math.max(0, currentPage - offset);
    },

    getLastPage: function(currentPage, pageCount)
    {
        var offset = Math.floor(this.surroundingPages / 2);
        if (currentPage - offset < 0)
        {
            offset += offset - currentPage;
        }

        return Math.min(pageCount - 1, currentPage + offset);
    },

    update: function(data)
    {
        this.data = data;
        if (this.rendered)
        {
            this.renderRows();
        }
    }
});

Ext.reg('xzpager', Zutubi.Pager);