package ru.trylogic.groovy.astconsole

import groovy.inspect.swingui.ScriptToTreeNodeAdapter
import groovy.inspect.swingui.TreeNodeBuildingNodeOperation
import org.codehaus.groovy.GroovyBugError
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.DynamicVariable
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration

class ExtendedScriptToTreeNodeAdapter extends ScriptToTreeNodeAdapter {

    ExtendedScriptToTreeNodeAdapter(Object classLoader, Object showScriptFreeForm, Object showScriptClass, Object showClosureClasses, Object nodeMaker) {
        super(classLoader, showScriptFreeForm, showScriptClass, showClosureClasses, nodeMaker)
    }

    def compile(String script, int compilePhase) {
        def scriptName = 'script' + System.currentTimeMillis() + '.groovy'
        GroovyCodeSource codeSource = new GroovyCodeSource(script, scriptName, '/groovy/script')
        CompilationUnit cu = new CompilationUnit(CompilerConfiguration.DEFAULT, codeSource.codeSource, classLoader)
        cu.setClassgenCallback(classLoader.createCollector(cu, null))

        TreeNodeBuildingNodeOperation operation = new TreeNodeBuildingNodeOperation(this, showScriptFreeForm, showScriptClass, showClosureClasses)
        cu.addPhaseOperation(operation, compilePhase)
        cu.addSource(codeSource.getName(), script)
        cu.compile(compilePhase)
        
        return operation.root
    }

    def make(node) {
        nodeMaker.makeNodeWithProperties(node, getPropertyTable(node))
    }

    def make(MethodNode node) {
        def table = getPropertyTable(node)
        extendMethodNodePropertyTable(table, node)

        nodeMaker.makeNodeWithProperties(node, table)
    }

    private List<List<String>> getPropertyTable(node) {
        node.metaClass.properties?.
                findAll { it.getter }?.
                collect {
                    def name = it.name.toString()
                    def value
                    try {
                        // multiple assignment statements cannot be cast to VariableExpression so
                        // instead reference the value through the leftExpression property, which is the same
                        if (node instanceof DeclarationExpression &&
                                (name == 'variableExpression' || name == 'tupleExpression')) {
                            value = node.leftExpression.toString()
                        } else {
                            value = it.getProperty(node).toString()
                        }
                    } catch (GroovyBugError reflectionArtefact) {
                        // compiler throws error if it thinks a field is being accessed
                        // before it is set under certain conditions. It wasn't designed
                        // to be walked reflectively like this.
                        value = null
                    }
                    def type = it.type.simpleName.toString()
                    [name, value, type]
                }
    }
}
