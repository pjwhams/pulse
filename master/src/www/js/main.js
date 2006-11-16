function getElement(id)
{
    var element;

    if (document.getElementById)
    {
        element = document.getElementById(id);
    }
    else if (document.all)
    {
        element = document.all[id];
    }
    else
    {
        element = document.layers[id];
    }

    return element;
}

// Function to toggle the enable state of a control based on the state of a
// checkbox.
function setEnableState(id, checkboxId, inverse)
{
    var element = getElement(id);
    var disabled = !getElement(checkboxId).checked;

    if(inverse)
    {
        disabled = !disabled;
    }

    element.disabled = disabled;
}

// Sets the enabled state of all controls in a form (excepting the checkbox
// itself and submits (buttons)) based on the state of a checkbox.
function setFormEnableState(formId, checkboxId, includeSubmit, inverse)
{
    var disabled = !getElement(checkboxId).checked;

    if(inverse)
    {
        disabled = !disabled;
    }

    var form = getElement(formId);
    var fields = form.elements;

    for(var i = 0; i < fields.length; i++)
    {
        var field = fields[i];
        if(field.id != checkboxId && (includeSubmit || field.type && field.type != "submit"))
        {
            fields[i].disabled = disabled;
        }
    }
}

function confirmUrl(message, url)
{
    if (confirm(message))
    {
        location.href = url;
    }
}

// function to select the 'next' submit action in a wizard when
// enter is pressed in a form field. Without this, the first submit (previous)
// button would always be selected.
// How?: it sends a hidden field called submit with the details. 
function submitenter(field, evt, value)
{
    // provide backward compatibility. The value may not be specified, in which case default to 'next'.
    if (!value)
    {
        value = "next"; // the default value.
    }

    var keycode;
    if (window.event)
    {
        keycode = window.event.keyCode;
    }
    else if (evt)
    {
        keycode = evt.which;
    }
    else
    {
        return true;
    }

    if (keycode == 13)
    {
        // submit the next button.
        field.form.submit.value = value;
        field.form.submit();
        return false;
    }
    else
    {
        return true;
    }
}

// Function for opening an SCM browse window used on several forms.
//   - contextPath: the web app context path.
//   - selectDir: set to true for selecting a directory, false for a file
//   - elementId: ID of textbox to set the value of to the selected path
//   - extraArgs: optional extra arguments for param string (e.g. "&prefix=foo")
function openBrowseWindow(contextPath, selectDir, elementId, extraArgs)
{
    var browseWindow = window.open(contextPath + "/popups/browseScm.action?selectDir=" + selectDir + "&elementId=" + elementId + extraArgs, "browse scm", 'status=yes,resizable=yes,top=100,left=100,width=600,height=600,scrollbars=yes');
    browseWindow.opener = self;
    browseWindow.focus();
}

// Function for opening a resource browse window used on several forms.
//   - resourceId: ID of textbox to to receive the resource name
//   - versionId: ID of textbox to receive the resource version
function openResourceBrowser(contextPath, resourceId, versionId)
{
    var browseWindow = window.open(contextPath + "/popups/browseResources.action?resourceId=" + resourceId + "&versionId=" + versionId, "resource browser", 'status=yes,resizable=yes,top=100,left=100,width=600,height=600,scrollbars=yes');
    browseWindow.opener = self;
    browseWindow.focus();
}

function openFileDialog(path, formname, fieldname, root, showFiles, showHidden, showToolbar)
{
    var browseWindow = window.open(path + '?formname=' + formname +
                                   '&fieldname=' + fieldname + '&root=' + root +
                                   '&showFiles=' + showFiles + '&showHidden=' + showHidden +
                                   '&showToolbar=' + showToolbar,
            'browse_popup', 'width=400, height=550, resizable=yes', false);
    browseWindow.opener = self;
    browseWindow.focus();
}

// @deprecated. Use the Prototype function Element.toggle instead.
function toggleElementDisplay(element)
{
    if(element.style.display == 'none')
    {
        element.style.display = '';
    }
    else
    {
        element.style.display = 'none';
    }
}

function toggleDisplay(id)
{
    toggleElementDisplay(getElement(id));
}

// Toggles the display of a nested list and switched the correcsponding image
//   - if a second argument is given, it is used as the image for a "closed"
//     element
//   - if a third argument is given, it is used as the image for a "open"
//     element
function toggleList(id)
{
    var element = getElement(id);
    var header = getElement(id + '_header');

    if(!element.style.display)
    {
        var closedClass = 'collapsed-list';
        if(arguments.length > 1)
        {
            closedClass = arguments[1];
        }

        element.style.display = 'none';
        header.className = closedClass;
    }
    else
    {
        var openClass = 'expanded-list';
        if(arguments.length > 2)
        {
            openClass = arguments[2];
        }

        element.style.display = '';
        header.className = openClass;
    }
}

function toggleFolderList(id)
{
    toggleList(id, 'closed-folder', 'open-folder');
}

// Changes display style for all lists under the given node
function styleAllLists(id, style, headerClass)
{
    var node = getElement(id);
    var childLists = node.getElementsByTagName("ul");

    for(var i = 0; i < childLists.length; i++)
    {
        if(childLists[i].id != '')
        {
            childLists[i].style.display = style;
            var header = getElement(childLists[i].id + '_header');
            if(header)
            {
                header.className = headerClass(childLists[i]);
            }
        }
    }
}

// Expands all lists under the given node
function expandAllLists(id)
{
    styleAllLists(id, '', function(element) { if(element.parentNode.className == "dir-artifact") { return 'open-folder'; } else { return 'expanded-list'; } });
}

// Collapses all lists under the given node
function collapseAllLists(id)
{
    styleAllLists(id, 'none', function(element) { if(element.parentNode.className == "dir-artifact") { return 'closed-folder'; } else { return 'collapsed-list'; } });
}

// Hides all children of the element with the given id
function hideChildren(id)
{
    var element = getElement(id);

    $A(element.childNodes).each(function(child)
    {
        if (child.nodeType == 1)
        {
            Element.hide(child);
        }
    });
}

// Toggle display for all success rows under the given table, identified by
// CSS class "successful".
function toggleSuccessfulTestRows(tableId, successfulShowing)
{
    var table = getElement(tableId);
    var rows = table.getElementsByTagName('tr');

    for(var i = 0; i < rows.length; i++)
    {
        var successfulRow = rows[i].className.indexOf('successful') == 0;
        if(successfulRow)
        {
            rows[i].style.display = successfulShowing ? '' : 'none';
        }
    }
}

function setText(id, text)
{
    var element = getElement(id);
    element.innerHTML = text;
}

function setClass(id, className)
{
    var element = getElement(id);
    element.className = className;
}

// Used in left/right pane navigation to handle selection of a new node in
// the left pane.
function selectNode(id)
{
    setClass("nav_" + selectedNode, "");
    setClass("nav_" + id, "active");

    var rightPane = getElement("node_" + selectedNode);
    rightPane.style.display = "none";
    rightPane = getElement("node_" + id);
    rightPane.style.display = "block";
    selectedNode = id;
}

/*
 * Return the dimensions of the window.
 *
 *    width: the window width.
 *    height: the window height.
 */
function windowSize()
{
    var myWidth = 0, myHeight = 0;
    if (typeof( window.innerWidth ) == 'number')
    {
        //Non-IE
        myWidth = window.innerWidth;
        myHeight = window.innerHeight;
    }
    else if (document.documentElement && ( document.documentElement.clientWidth || document.documentElement.clientHeight ))
    {
        //IE 6+ in 'standards compliant mode'
        myWidth = document.documentElement.clientWidth;
        myHeight = document.documentElement.clientHeight;
    }
    else if (document.body && ( document.body.clientWidth || document.body.clientHeight ))
    {
        //IE 4 compatible
        myWidth = document.body.clientWidth;
        myHeight = document.body.clientHeight;
    }
    return {"width":myWidth, "height":myHeight};
}

/**
 * Return an array containing the query string parameters.
 *
 *
 */
function qs()
{
    var qsParm = new Array();
    var query = window.location.search.substring(1);
    var parms = query.split('&');
    for (var i=0; i<parms.length; i++) {
        var pos = parms[i].indexOf('=');
        if (pos > 0) {
            var key = parms[i].substring(0,pos);
            var val = parms[i].substring(pos+1);
            qsParm[key] = val;
        }
    }
    return qsParm;
}


function openDebugAlert(obj)
{
    if (obj)
    {
        var temp = "";
        for (var x in obj)
        {
            temp += x + ": " + obj[x] + "\n";
        }
        alert (temp);
    }
}

/**
 * Show / Hide the commit comment popup.  Requires that the following be added to the page.
 *
 *    <div id="commentsWindow" class="floating" style="display: none;">
 *       <div id="commentsWindowContent">
 *       </div>
 *   </div>
 */
function showHideCommentsWindow(id)
{
    var window = getElement('commentsWindow');
    var link = getElement(id + "_link");

    if(window.style.display == 'none')
    {
        var element = getElement(id);
        var cell = getElement(id + "_cell");
        var windowContent = getElement('commentsWindowContent');

        windowContent.innerHTML = element.innerHTML;
        
        var row = cell.parentNode;
        var position = Position.positionedOffset(cell);
        var rowPosition = Position.positionedOffset(row);

        window.style.visibility = 'hidden';
        window.style.display = '';

        var desiredLeft = position[0] + cell.offsetWidth - window.offsetWidth + 20;
        var maxLeft = rowPosition[0];
        if (desiredLeft < maxLeft)
        {
            window.style.left = maxLeft + "px";
        }
        else
        {
            window.style.left = desiredLeft + "px";
        }

        window.style.visibility = 'visible';
        window.style.top = position[1] + 20 + "px";
    }
    else
    {
        window.style.display = 'none';
    }
}

/**
 * A slight variation on the above.
 */
function showHideBuildsFloat(id)
{
    var window = getElement('buildsWindow');
    var link = getElement(id + "_link");

    if(window.style.display == 'none')
    {
        var element = getElement(id);
        var cell = getElement(id + "_cell");
        var windowContent = getElement('buildsWindowContent');

        windowContent.innerHTML = element.innerHTML;
        var position = Position.positionedOffset(cell);
        window.style.visibility = 'hidden';
        window.style.display = '';
        window.style.left = position[0] + cell.offsetWidth - window.offsetWidth - 20 + "px";
        window.style.visibility = 'visible';
        window.style.top = position[1] + 20 + "px";
    }
    else
    {
        window.style.display = 'none';
    }
}