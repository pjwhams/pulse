// dependency: ./namespace.js
// dependency: ext/package.js
// dependency: zutubi/ActivePanel.js
// dependency: zutubi/Pager.js
// dependency: zutubi/pulse/project/BuildSummaryTable.js

/**
 * The content of the project history page.  Expects data of the form:
 *
 * {
 *     builds: [ BuildModels ],
 *     pager: PagingModel
 * }
 *
 * @cfg {Array}  columns     Columns to display in the builds table.
 * @cfg {String} pagerUrl    URL to use as the basis for links in the pager.
 * @cfg {String} stateFilter State filter value.
 */
Zutubi.pulse.project.browse.ProjectHistoryPanel = Ext.extend(Zutubi.ActivePanel, {
    border: false,
    autoScroll: true,
    
    dataKeys: ['builds', 'pager'],
    
    initComponent: function(container, position)
    {
        var panel = this;
        Ext.apply(this, {
            items: [{
                xtype: 'panel',
                border: false,
                id: this.id + '-inner',
                style: 'padding: 17px',
                layout: 'vtable',
                contentEl: 'center',
                tbar: {
                    id: 'build-toolbar',
                    style: 'margin: 0',
                    items: [{
                        xtype: 'label',
                        text: 'state filter:'
                    }, ' ', {
                        xtype: 'combo',
                        id: 'state-filter',
                        editable: false,
                        forceSelection: true,
                        triggerAction: 'all',
                        store: [
                            ['', '[any]'],
                            ['broken', '[any broken]'],
                            ['error', 'error'],
                            ['failure', 'failure'],
                            ['terminated', 'terminated'],
                            ['success', 'success']
                        ],
                        value: this.stateFilter,
                        listeners: {
                            select: function(combo) {
                                panel.setFilter(combo.getValue());
                            }
                        }
                    }, ' ', {
                        xtype: 'xztblink',
                        id: 'state-filter-clear',
                        text: 'clear',
                        icon: window.baseUrl + '/images/config/actions/clean.gif',
                        listeners: {
                            click: function() {
                                panel.setFilter();
                            }
                        }
                    }, '->', {
                        xtype: 'xztblink',
                        icon: window.baseUrl + '/images/feed-icon-16x16.gif',
                        url: window.baseUrl + '/rss.action?projectId=' + this.projectId
                    }]
                },
                items: [{
                    id: this.id + '-builds',
                    xtype: 'xzbuildsummarytable',
                    title: 'build history',
                    selectedColumns: this.columns,
                    emptyMessage: 'no builds found',
                    customisable: !this.anonymous
                }, {
                    id: this.id + '-pager',
                    xtype: 'xzpager',
                    itemLabel: 'build',
                    url: this.pagerUrl, 
                    extraParams: this.stateFilter == '' ? '' : 'stateFilter/' + this.stateFilter + '/',
                    labels: {
                        first: 'latest',
                        previous: 'newer',
                        next: 'older',
                        last: 'oldest'
                    }
                }]
            }]
        });

        Zutubi.pulse.project.browse.ProjectHistoryPanel.superclass.initComponent.apply(this, arguments);
    },
    
    setFilter: function(filter)
    {
        var location = this.pagerUrl + this.data.pager.currentPage + '/';
        if (filter)
        {
            location += 'stateFilter/' + filter + '/';
        }
        
        window.location.href = location;
    }
});