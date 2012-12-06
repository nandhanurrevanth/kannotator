package funDependency

import java.io.File
import java.util.Comparator
import kotlinlib.*
import org.jetbrains.kannotator.funDependecy.FunctionNode
import org.jetbrains.kannotator.funDependecy.buildFunctionDependencyGraph
import org.junit.Test
import util.ClassPathDeclarationIndex
import util.ClassesFromClassPath
import util.assertEqualsOrCreate
import org.jetbrains.kannotator.declarations.Method

private val PATH = "testData/funDependency/"

class FunDependencyGraphTest {
    Test fun callDependencyForNonAnnotativeMethod() {
        doTest("callDependencyForNonAnnotativeMethod/callDependencyForNonAnnotativeMethod.txt",
                "funDependency.callDependencyForNonAnnotativeMethod.CallDependencyForNonAnnotativeMethod")
    }

    Test fun funInDifferentClassesTest() {
        doTest("funInDifferentClasses/funInDifferentClasses.txt", "fundependency.funInDifferentClasses.First", "fundependency.funInDifferentClasses.Second")
    }

    Test fun multiplyInvokeOfMethod() {
        doTest("multiplyInvokeOfMethod/multiplyInvokeOfMethod.txt", "fundependency.multiplyInvokeOfMethod.First", "fundependency.multiplyInvokeOfMethod.Second")
    }

    Test fun noAnnotatedMethods() {
        doTest("noAnnotatedMethods/noAnnotatedMethods.txt", "fundependency.noAnnotatedMethods.First")
    }

    Test fun recursiveFunTest() {
        doTest("recursiveFun/recursiveFun.txt", "fundependency.recursiveFun.First", "fundependency.recursiveFun.Second")
    }

    Test fun simpleTest() {
        doTest("simple/simple.txt", "fundependency.simple.Simple")
    }

    Test fun dependOnConstructorBecauseOfFields() {
        doTest("dependOnConstructorBecauseOfFields/dependOnConstructorBecauseOfFields.txt", "fundependency.dependOnConstructorBecauseOfFields.dependOnConstructorBecauseOfFields")
    }

    fun doTest(expectedResultPath: String, vararg canonicalNames: String) {
        val classSource = ClassesFromClassPath(*canonicalNames)
        val graph = buildFunctionDependencyGraph(ClassPathDeclarationIndex, classSource)

        val functionNodeComparator = object : Comparator<FunctionNode<Method>> {
            public override fun compare(o1: FunctionNode<Method>?, o2: FunctionNode<Method>?): Int {
                return o1?.data.toString().compareTo(o2?.data.toString())
            }

            public override fun equals(obj: Any?): Boolean {
                throw IllegalStateException()
            }
        }

        val actual = buildString { sb ->
            sb.println("== All Nodes == ")
            for (node in graph.functions.sort(functionNodeComparator)) {
                printFunctionNode(sb, node)
            }

            sb.println()
            sb.println("== No Outgoing Nodes == ")

            for (node in graph.noOutgoingNodes.sort(functionNodeComparator)) {
                printFunctionNode(sb, node)
            }
        }.trim()

        val expectedFile = File(PATH + expectedResultPath)
        assertEqualsOrCreate(expectedFile, actual)
    }

    fun printFunctionNode(sb: StringBuilder, node: FunctionNode<Method>) {
        sb.println(node.data)
        if (node.outgoingEdges.size() > 0) sb.println("    outgoing edges:")
        for (edge in node.outgoingEdges.sortByToString()) {
            sb.println("        $edge")
        }

        if (node.incomingEdges.size() > 0) sb.println("    incoming edges:")
        for (edge in node.incomingEdges.sortByToString()) {
            sb.println("        $edge")
        }
    }
}

