/**
 * Simple node object that allows us to create a node tree.
 */
MyNode = function(){};
MyNode.prototype = {

    /**
     * Constructor.
     */
    initialize: function() {
        this.children = new $A(),
        this.parent = null
    },

    /**
     * Get the nodes children.
     */
    getChildren: function()
    {
        return this.children;
    },

    /**
     * Add a new child to this node.
     */
    addChild: function(child)
    {
        // check that the child is not already a child.
        this.children.push(child);
        child.parent = this;
    },

    /**
     * Returns true if this node instance has children, false otherwise.
     */
    hasChildren: function()
    {
        return this.children.length > 0;
    },

    /**
     * Returns the parent of this node, or null if this is the root node.
     */
    getParent: function()
    {
        return this.parent;
    }
};

/**
 * Initialise the tree control.
 *
 */
function init(event)
{
    var anchorId = getConfig().anchor;

    // need to find a way to extract the id of the root tree node from this file.
    var anchorDiv = document.getElementById(anchorId);

    // ensure that the model is correctly initialised. Use the root of this model as the first location
    // to be displayed.
    if (!getConfig().model)
    {
        getConfig().model = new MyNode();
        getConfig().model.initialize();
    }

    // LOADING FEEDBACK.
    var ul = document.createElement("ul");
    ul.appendChild(createDomNode(createTemporaryNode("Loading...", "loading", "")));
    anchorDiv.appendChild(ul);

    // TRIGGER LOAD OF THE ROOT NODE.
    requestUpdate("");
}

/**
 * Event handler.
 */
function load(event)
{
    var currentTarget = getCurrentTarget(event);

    if (this == currentTarget)
    {
        // WARNING: using innerHTML directly clears out any event handlers.
        // insert another level of the tree. <ul>loading...</ul>

        var ul = document.createElement("ul");
        ul.appendChild(createDomNode(createTemporaryNode("Loading...", "loading", "")));

        currentTarget.appendChild(ul);

        // now we change the onclick handler so that it handles toggling instead of loading.
        currentTarget.onclick = toggle;

        // open current target.
        Element.removeClassName(currentTarget, "folder");
        Element.addClassName(currentTarget, "openfolder");

        // send off the xml http request.
        requestUpdate(currentTarget.id);
    }
}

/**
 * Simple encapsulation of the configuration object. This should simplify the fixing how
 * the configuration details are passed around.
 */
function getConfig()
{
    return window.myTree;
}

function requestUpdate(id)
{
    var url = getConfig().url;

    var ajax = new Ajax.Request(
        url,
        {
            method: 'get',
            onComplete: updateModel,
            onFailure: handleFailure,
            onException: handleException,
            parameters:"uid=" + id
        }
    );
}

/**
 * Update the data model.
 */
function updateModel(originalRequest)
{
    var jsonText = originalRequest.responseText;
    var jsonObjs = eval("(" + jsonText + ")");

    var jsonObj = jsonObjs.results[0];

    // locate where in the tree this update belongs.
    var rootNode = getConfig().model;
    var node = locateNode(rootNode, jsonObj.uid);
    if (!node)
    {
        // the default.
        node = rootNode;
    }

    // UPDATE THE MODEL WITH THE NEW DATA.
    for (var i = 0; i < jsonObj.listing.length; i++)
    {
        // sanity check that we do not add components to the model a second time. This should be
        // caught at an earlier stage.
        var existingNode = node.getChildren().find(function(child)
        {
            return (child.data.uid == jsonObj.listing[i].uid);
        });
        if (existingNode)
        {
            console.log("skipping adding node a second time.");
            continue;
        }

        var childNode = new MyNode();
        childNode.initialize();
        childNode.data = jsonObj.listing[i];
        node.addChild(childNode);
    }

    // TRIGGER AN UPDATE OF THE UI. SHOULD THIS BE HANDLED VIA AN EVENT?
    updateTree(jsonObj);
}

//TODO: this traversal is too slow. Should generate a map of uid to nodes and use that instead.
function locateNode(parentNode, uid)
{
    var node = null;
    parentNode.getChildren().each(function(childNode)
    {
        if (childNode.data.uid == uid)
        {
            node = childNode;
        }
        var n = locateNode(childNode, uid);
        if (n)
        {
            node = n;
        }
    });
    return node;
}

/**
 * A callback handler to process the response from a 'listing' request to the
 * server. This handler constructs a full navigable directory tree.
 *
 * This handler expected a response in the format:
 *
 *    listing:  {file, type, id}
 *    path: 'parent id'
 *
 * where
 *    file - is the name of the file
 *    type - is the type of the file (folder, file, txt etc)
 *    id - is the unique identifier for this file.
 *
 * and
 *
 *    path - represents the unique id of the parent.
 */
function updateTree(jsonObj)
{
    // LOCATE THE POINT IN THE DOM THAT WE WILL BE UPDATING.
    var target = document.getElementById(jsonObj.uid);
    if (!target)
    {
        target = document.getElementById(getConfig().anchor);
    }

    // REMOVE ANY EXISTING LOADING MESSAGE
    var ul = locateFirstChild(target, "UL");
    removeAllChildren(ul);

    var rootNode = locateNode(getConfig().model, jsonObj.uid);
    if (!rootNode)
    {
        rootNode = getConfig().model;

        // add the '.' directory so that it can be selected. However, we do not want it to be
        // reloaded since it is a special case that clears out all existing content...
        var thisDirectory = createDomNode(createTemporaryNode(".", "root", ""));
        ul.appendChild(thisDirectory);
    }

    // convert the data into a dom tree representation.
    rootNode.getChildren().each(function(child)
    {
        var listItem = createDomNode(child);
        ul.appendChild(listItem);
    });

    updateDisplayPath(jsonObj);
}

function updateFlat(jsonObj)
{
    var folder = document.getElementById(getConfig().anchor);

    // lookup the root node.
    var rootNode = locateNode(getConfig().model, jsonObj.uid);
    if (!rootNode)
    {
        rootNode = getConfig().model;
    }

    removeChild(folder);
    clearSelection();

    var ul = document.createElement("ul");
    folder.appendChild(ul);

    // add the links to the current directory.
    var uid = "";
    if (rootNode.data)
    {
        uid = rootNode.data.uid;
    }

    var thisDirectory = createDomNode(createTemporaryNode(".", "folder", uid));
    ul.appendChild(thisDirectory);

    // show link to the parent whenever we are not at the root.
    if (rootNode.getParent())
    {
        var puid = ""; // value for the root.
        if (rootNode.getParent().data)
        {
            puid = rootNode.getParent().data.uid;
        }
        var parentDirectory = createDomNode(createTemporaryNode("..", "folder", puid));
        ul.appendChild(parentDirectory);
    }

    // convert the data into a dom tree representation.
    rootNode.getChildren().each(function(child)
    {
        var listItem = createDomNode(child);
        ul.appendChild(listItem);
    });

    updateDisplayPath(jsonObj);
}

function updateDisplayPath(jsonObj)
{
    // display path if it is available.
    getConfig().displayPath = jsonObj.displayPath;

    var currentPathDisplay = document.getElementById('path');
    if (currentPathDisplay)
    {
        removeAllChildren(currentPathDisplay);
        // update the current node status.
        if (jsonObj.displayPath)
        {
            currentPathDisplay.appendChild(document.createTextNode(jsonObj.displayPath));
        }
    }
}

function createTemporaryNode(file, type, uid)
{
    var tmpNode = new MyNode();
    tmpNode.data = {"file":file, "type":type, "uid":uid}
    return tmpNode;
}

/**
 * Create a visual representation of a node.
 *
 *    data: an associative array with fields id, file and type.
 *
 */
function createDomNode(node)
{
    var data = node.data;

    var domNode = document.createElement("li");
    domNode.appendChild(document.createTextNode(data.file));
    domNode.setAttribute("id", data.uid);

    Element.addClassName(domNode, data.type);
    if (data.type == "folder")
    {
        domNode.onclick = load;
    }
    else if (data.type == "loading")
    {
        // do nothing here..
    }
    else
    {
        domNode.onclick = select;
    }
    return domNode;
}

/**
 * Select the element that is the target of this event.
 *
 * Selecting an element will add the 'selected' class to its list of classes.
 * Only a single element can be selected at a time.
 */
function select(event)
{
    var currentTarget = getCurrentTarget(event);
    if (this == currentTarget)
    {
        // locate the selected class.
        clearSelection();

        // record selection.
        getConfig().selectedNode = currentTarget.id;

        Element.addClassName(currentTarget, "selected");
        if (Element.hasClassName(currentTarget, "folder"))
        {
            return;
        }

        getConfig().selectedValue = extractText(currentTarget);

        // update selected display.
        // - what is the currently selected name?
        var selectedDisplay = document.getElementById('selected');
        if (selectedDisplay)
        {
            removeAllChildren(selectedDisplay);
            selectedDisplay.value = extractText(currentTarget);
        }
    }
}

function currentSelectionValue()
{
    if (!getConfig().selectedNode)
    {
        return "";
    }
    var node = locateNode(getConfig().model, getConfig().selectedNode);

    // construct the selection value by walking up the node hierarchy.
    var value = "";
    var sep = "";
    while (node)
    {
        // not all nodes currently have data details.
        if (node.data && node.data.file)
        {
            value = node.data.file + sep + value;
            sep = "/"; //TODO: this is dependant on the filesystems separator char.
        }
        node = node.getParent();
    }
    return value;
}

function clearSelection()
{
    var selectedNodes = document.getElementsByClassName("selected");

    // remove 'selected' from the list of classes.
    if (selectedNodes)
    {
        for (var i = 0; i < selectedNodes.length; i++)
        {
            var node = selectedNodes[i];
            Element.removeClassName(node, "selected");
        }
    }
    clearBrowserTextSelection();
}

function extractText(element)
{
    return element.innerHTML;
}

/**
 * Toggle the state of the element that is the target of this event.
 *
 * NOTE: It only makes sense for the target node to represent a 'folder'.
 */
function toggle(event)
{
    var currentTarget = getCurrentTarget(event);
    if (this == currentTarget)
    {
        var node = this;
        var ul = locateFirstChild(node, "UL");
        Element.toggle(ul);
        if (Element.visible(ul))
        {
            replaceClassName(node, "folder", "openfolder");
        }
        else
        {
            replaceClassName(node, "openfolder", "folder");
        }
    }
}

function replaceClassName(element, oldClassName, newClassName)
{
    Element.removeClassName(element, oldClassName);
    Element.addClassName(element, newClassName);
}

function removeChild(element)
{
    var children = element.childNodes;
    for (var j = 0; j < children.length; j++)
    {
        var child = children[j];
        if (child.nodeType == 1 && (child.tagName.toUpperCase() == "UL"))
        {
            Element.remove(child);
        }
    }
}

/**
 * Remove all of the child nodes from the specified element.
 */
function removeAllChildren(element)
{
    var children = $A(element.childNodes);
    children.each(function(child)
    {
        // remove the child element from the document.
        Element.remove(child);
    });
}

function getCurrentTarget(event)
{
    return Event.element(getCurrentEvent(event));
}

function getCurrentEvent(event)
{
    return event || window.event;
}

function clearBrowserTextSelection()
{
    if (document.selection)
    {
        document.selection.empty();
    }
}

/**
 * Locate and return the first child of the specified element that has a nodeName property
 * that matches the specified nodeName.
 *
 */
function locateFirstChild(elem, nodeName)
{
    nodeName = nodeName.toUpperCase();

    // add Enumerable to childNodes
    var children = $A(elem.childNodes);

/* old implementation
    var res = null;
    children.each(function(child) {
        var name = child.nodeName;
        if (name && name.toUpperCase() == nodeName)
        {
            res = child;
            return;
        }
    });
    return res;
*/
    var res = children.find(function(child)
    {
        var name = child.nodeName;
        return (name && name.toUpperCase() == nodeName);
    });
    return res;
}

function debug(element)
{
    var debugging = "";
    var i = 0;
    for (var propName in element)
    {
        var sep = "\n"
        if (i < 5)
        {
            sep = ", "
        }
        else
        {
            i = 0;
        }
        debugging += sep + propName;
        i++;
    }
    alert(debugging);
}

/**
 * Basic failure handler.
 *
 */
function handleFailure(resp)
{
    alert("onFailure");
}

/**
 * Basic exception handler.
 *
 */
function handleException(resp, e)
{
    alert("onException: " + e);
}