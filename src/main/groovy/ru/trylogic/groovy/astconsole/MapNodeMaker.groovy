package ru.trylogic.groovy.astconsole

import groovy.inspect.swingui.AstBrowserNodeMaker
import groovy.transform.CompileStatic

@CompileStatic
class MapNodeMaker implements AstBrowserNodeMaker<MapNode> {

    @Override
    MapNode makeNode(Object userObject) {
        return new MapNode(userObject);
    }

    @Override
    MapNode makeNodeWithProperties(Object userObject, List<List<String>> properties) {
        return new MapNode(userObject, properties);
    }
}
