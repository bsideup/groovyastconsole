package ru.trylogic.groovy.astconsole

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*

@CompileStatic
class MapNode extends HashMap<String, Object> {

    Object node;

    MapNode() {
    }

    MapNode(Object node) {
        this.node = node

        put('text', getStringForm(node));
        put('children', new ArrayList());
    }

    MapNode(Object node, List<List<String>> properties) {
        this(node);

        if (!properties.empty) {
            Map<String, String> attributes = [:];
            for (property in properties) {
                def propertyName = property[0];
                switch (propertyName) {
                    case "statements":
                    case "expression":
                    case "columnNumber":
                    case "lastColumnNumber":
                    case "lastLineNumber":
                    case "lineNumber":
                        break;
                    default:
                        def value = property[1]
                        if(value != null && value != "null" && !(value =~ /@[a-f0-9]{8}/)) {
                            attributes[propertyName] = value
                        }
                }
            }

            put("attributes", attributes);
        }
    }

    List<MapNode> getChildren() {
        return get("children") as List<MapNode>;
    }

    void add(MapNode child) {
        (get("children", []) as List<MapNode>).add(child);
    }

    void setParent(MapNode newParent) {
    }

    String getAttribute(String name) {
        (get('attributes') as Map<String, String>)?.get(name);
    }

    protected String getStringForm(node) {
        def simpleName = node.class.simpleName
        if (simpleName.endsWith("Expression")) {
            simpleName -= "Expression";
        }
        return simpleName + " - " + getNodeDescription(node);
    }

    @CompileDynamic
    protected String getNodeDescription(node) {
        switch (node.class) {
            case ClassNode:
            case InnerClassNode:
            case ConstructorNode:
            case MethodNode:
            case Parameter:
            case DynamicVariable:
                return node.name;

            case FieldNode: return node.name + " : " + node.type;
            case PropertyNode: return node.field?.name + " : " + node.field?.type;
            case AnnotationNode: return node.classNode?.name;

            case ReturnStatement: return node.text;
            case BlockStatement: return "(" + (node.statements ? node.statements.size() : 0) + ")";
            case ExpressionStatement: return node?.expression?.getClass()?.simpleName;
            case TryCatchStatement: return (node.catchStatements?.size ?: 0) + " catch, " + (node.finallyStatement ? 1 : 0) + " finally";
            case CatchStatement: return node.exceptionType;

            case VariableExpression: return node.name + " : " + node.type;
            case ConstantExpression: return node.value?.toString() + " : " + node.type;
            case PropertyExpression: return node.propertyAsString

            default:
                return node instanceof String ? node : node.text
        }
    }
}
