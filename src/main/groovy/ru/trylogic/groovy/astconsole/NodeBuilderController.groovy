package ru.trylogic.groovy.astconsole

import groovy.json.JsonBuilder
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.syntax.Token
import org.springframework.web.bind.annotation.*

@CompileStatic
@RestController
class NodeBuilderController {

    @RequestMapping('/build')
    String index(@RequestParam String script, @RequestParam CompilePhase phase, @RequestParam boolean filter) {
        def adapter = new ExtendedScriptToTreeNodeAdapter(null, true, false, true, new MapNodeMaker());

        MapNode root = adapter.compile(script, phase.phaseNumber) as MapNode

        StringWriter writer = new StringWriter();
        IndentPrinter printer = new IndentPrinter(writer, "   ");
        generateBuilder(printer, root.getChildren().get(0).node as ASTNode)
        
        if (filter) {
            filterNode(root);
        }
        
        return new JsonBuilder([
                nodes : root.children,
                builder : "//TODO Use at your own risk, still under development.\n" + writer.toString()
        ]).toString();
    }

    @ExceptionHandler(Exception.class)
    public String error(Exception e) {
        return new JsonBuilder([
                        error: e.message
        ]).toString();
    }

    void filterNode(MapNode node) {
        node.getChildren().removeAll {
            def mod = Integer.parseInt(it.getAttribute('modifiers') ?: '0');
            return (mod & Opcodes.ACC_SYNTHETIC) != 0
        }

        node.getChildren()?.each(this.&filterNode);
    }

    @CompileDynamic
    void generateBuilder(IndentPrinter printer, ASTNode node) {
        if(node == null) {
            return;
        }

        printer.print("new ${node.getClass().simpleName}(");
        switch(node) {
            case BlockStatement:
                printer.println();
                printer.incrementIndent();
                printer.printIndent();
                printer.println("Arrays.<Statement>asList(");
                
                printer.incrementIndent();
                (node as BlockStatement).statements.each {
                    printer.printIndent();
                    generateBuilder(printer, it);
                    printer.println();
                }
                printer.decrementIndent();
                printer.printIndent();
                printer.println("),");
                printer.printIndent();
                printer.print("new VariableScope()");
                printer.decrementIndent();
                printer.println();
                printer.printIndent();
                break;
            case ExpressionStatement:
                generateBuilder(printer, (node as ExpressionStatement).expression);
                break;
            case ForStatement:
                def forStatement = (ForStatement) node;
                printer.println();
                printer.incrementIndent();
                printer.printIndent();
                if(forStatement.variable == ForStatement.FOR_LOOP_DUMMY) {
                    printer.print("ForStatement.FOR_LOOP_DUMMY");
                } else {
                    generateBuilder(printer, forStatement.variable)
                }
                printer.println(",");
                printer.printIndent();
                generateBuilder(printer, forStatement.collectionExpression);
                printer.println(",");
                printer.printIndent();
                generateBuilder(printer, forStatement.loopBlock);
                printer.println();
                printer.decrementIndent();
                printer.printIndent();
                break;
            case ClosureListExpression:
                def closureListExpression = (ClosureListExpression) node;

                printer.println("Arrays.asList(");
                printer.incrementIndent();
                
                def iterator = closureListExpression.expressions.iterator();
                while(iterator.hasNext()) {
                    printer.printIndent();
                    generateBuilder(printer, iterator.next());
                    if(iterator.hasNext()) {
                        printer.println(",")
                    };
                }
                
                printer.println();
                printer.decrementIndent();
                printer.printIndent();
                printer.print(")");
                break;
            case DeclarationExpression:
            case BinaryExpression:
                printer.println();
                def declarationExpression = (BinaryExpression) node;
                printer.incrementIndent();
                
                printer.printIndent();
                generateBuilder(printer, declarationExpression.leftExpression);
                printer.println(",");
                
                printer.printIndent();
                generateBuilder(printer, (Token) declarationExpression.getOperation());
                printer.println(",");
                
                printer.printIndent();
                generateBuilder(printer, declarationExpression.rightExpression);
            
                printer.println();
                printer.decrementIndent();
                printer.printIndent();
                break;
            case ClosureExpression:
                printer.println();
                def closureExpression = (ClosureExpression) node;

                printer.incrementIndent();
                printer.printIndent();
                printer.println("new Parameter[] {");

                printer.incrementIndent();
                def iterator = closureExpression.parameters.iterator();
                while(iterator.hasNext()) {
                    printer.printIndent();
                    generateBuilder(printer, iterator.next());
                    if(iterator.hasNext()) {
                        printer.println(",")
                    }
                }
                printer.println();
                printer.decrementIndent();
                printer.printIndent();
                printer.println("},");
                printer.printIndent();
                generateBuilder(printer, closureExpression.code);
                printer.println();
            
                printer.decrementIndent();

                printer.printIndent();
                break;
            case Parameter:
                def param = (Parameter) node;
                
                printClassHelper(printer, param.type);
                printer.print(', ');
                printer.print("\"${param.name}\"");
                break;
            case MethodCallExpression:
                def call = (MethodCallExpression) node;

                printer.println();
                printer.incrementIndent();
                printer.printIndent();
                generateBuilder(printer, call.objectExpression)
                printer.println(",");
                printer.printIndent();
                generateBuilder(printer, call.method);
                printer.println(",");
                printer.printIndent();
                generateBuilder(printer, call.arguments);
                printer.println();
                printer.decrementIndent();
                printer.printIndent();
                break;
            case ConstantExpression:
                def constantExpression = (ConstantExpression) node;
                if(constantExpression.value == null) {
                    printer.print("null");
                } else {
                    if (ClassHelper.STRING_TYPE == constantExpression.type) {
                        printer.print("\""+constantExpression.text.replaceAll("\"", "\\\\\"")+"\"")
                    } else if (ClassHelper.char_TYPE == constantExpression.type) {
                        printer.print("'${constantExpression.text}'")
                    } else {
                        printer.print(constantExpression.text)
                    }
                }
                break;
            case VariableExpression:
                def variableExpression = (VariableExpression) node;
                
                printer.print("\"${variableExpression.name}\"")
                printer.print(', ');
                printClassHelper(printer, variableExpression.type);
                break;
            case ArgumentListExpression:
                def args = (ArgumentListExpression) node;

                def iterator = args.expressions.iterator();
                
                if(!iterator.hasNext()) {
                    break;
                }
                printer.println("Arrays.<Expression>asList(");
                printer.incrementIndent();

                while(iterator.hasNext()) {
                    printer.printIndent();
                    generateBuilder(printer, iterator.next());
                    if(iterator.hasNext()) {
                        printer.println(",")
                    };
                }

                printer.println();
                printer.decrementIndent();
                printer.printIndent();
                printer.print(")");
                break;
            case TernaryExpression:
                def ternaryExpression = (TernaryExpression) node;

                printer.println();
                printer.incrementIndent();
                printer.printIndent();
                generateBuilder(printer, ternaryExpression.booleanExpression)
                printer.println(",");
                printer.printIndent();
                generateBuilder(printer, ternaryExpression.trueExpression);
                printer.println(",");
                printer.printIndent();
                generateBuilder(printer, ternaryExpression.falseExpression);
                printer.println();
                printer.decrementIndent();
                printer.printIndent();
                
                break;
            case BooleanExpression:
                def booleanExpression = (BooleanExpression) node;
                
                generateBuilder(printer, booleanExpression.expression);
                break;
            default:
                printer.print("/*unknown node*/");
        }
        printer.print(")");
    }


    @CompileDynamic
    void generateBuilder(IndentPrinter printer, Token token) {
        printer.print("new Token(${token.type}, \"${token.text}\", -1, -1)");
    }
    
    @CompileDynamic
    void printClassHelper(IndentPrinter printer, ClassNode type) {
        printer.print("ClassHelper.make(\"${type.name}\")");
    }
}

