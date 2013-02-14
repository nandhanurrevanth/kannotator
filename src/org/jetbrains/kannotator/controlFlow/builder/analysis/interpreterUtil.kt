package org.jetbrains.kannotator.controlFlow.builder.analysis

import org.jetbrains.kannotator.declarations.ClassName
import org.jetbrains.kannotator.controlFlow.builder.Method
import org.jetbrains.kannotator.asm.util.*

import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.*
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.*

import java.util.*
import com.gs.collections.impl.map.strategy.mutable.UnifiedMapWithHashingStrategy
import com.gs.collections.api.block.HashingStrategy

public fun <V: AbstractValue<V>> Frame<V>.copy(): Frame<V> {
    val frameCopy = Frame<V>(this)

    for (i in 0..getLocals() - 1) {
        val v = getLocal(i)
        if (v != null) {
            frameCopy.setLocal(i, v.copy())
        }
    }

    frameCopy.clearStack()
    for (i in 0..getStackSize() - 1) {
        val v = getStack(i)
        if (v != null) {
            frameCopy.push(v.copy())
        }
    }

    return frameCopy
}

fun <V: Value> Frame<V>.forEachValue(body: (value: V) -> Unit) {
    for (i in 0..this.getLocals() - 1) {
        val v = this.getLocal(i)
        if (v != null) {
            body(v)
        }
    }
    for (i in 0..this.getStackSize() - 1) {
        val v = this.getStack(i)
        if (v != null) {
            body(v)
        }
    }
}

fun <V: Value> Frame<V>.allValues(body: (value: V) -> Boolean): Boolean {
    for (i in 0..this.getLocals() - 1) {
        val v = this.getLocal(i)
        if (v != null && !body(v)) {
            return false
        }
    }

    for (i in 0..this.getStackSize() - 1) {
        val v = this.getStack(i)
        if (v != null && !body(v)) {
            return false
        }
    }

    return true
}

fun <Q: Qualifier> Qualifier.extract(qualifierSet: QualifierSet<Q>): Q? {
    if (qualifierSet.contains(this)) {
        return this as Q
    }
    if (this is MultiQualifier<*>) {
        return this.qualifiers[qualifierSet.id] as Q?
    }
    throw IllegalArgumentException("Can't extract qualifier")
}

fun <Q: Qualifier, I: Qualifier> QualifiedValue<Q>.copy(qualifierSet: QualifierSet<I>, qualifier: I): QualifiedValue<Q> {
    val thisQualifier = this.qualifier
    if (qualifierSet.contains(thisQualifier)) {
        return this.copy(qualifier as Q)
    }
    if (thisQualifier is MultiQualifier<*>) {
        val thisQualifierCopy = (thisQualifier as MultiQualifier<Qualifier>).copy(qualifierSet.id, qualifier)
        return this.copy(thisQualifierCopy as Q)
    }
    throw IllegalArgumentException("Can't extract qualifier")
}

class QualifiedValueHashingStrategy<Q: Qualifier, I: Qualifier>(
        val qualifierSet: QualifierSet<I>
) : HashingStrategy<QualifiedValue<Q>> {
    public override fun equals(object1: QualifiedValue<Q>?, object2: QualifiedValue<Q>?): Boolean {
        if (object1 identityEquals object2) return true
        if (object1 == null || object2 == null) return false

        if (object1.base.id != object2.base.id) return false
        return (object1.qualifier.extract<I>(qualifierSet) == object2.qualifier.extract<I>(qualifierSet))
    }

    public override fun computeHashCode(_object: QualifiedValue<Q>?): Int {
        if (_object == null) return 0
        return _object.base.id * (_object.qualifier.extract<I>(qualifierSet)?.hashCode() ?: 0)
    }
}

fun <Q: Qualifier, I: Qualifier> imposeQualifierOnFrameValues(
        frame: Frame<QualifiedValueSet<Q>>,
        frameValues: QualifiedValueSet<Q>?,
        qualifier: I,
        qualifierSet: QualifierSet<I>,
        updateOriginalValues: Boolean
): Frame<QualifiedValueSet<Q>> {
    if (frameValues != null) {
        val map = UnifiedMapWithHashingStrategy<QualifiedValue<Q>, QualifiedValue<Q>>(QualifiedValueHashingStrategy(qualifierSet))

        for (stackValue in frameValues.values) {
            val q1 = stackValue.qualifier.extract<I>(qualifierSet) ?: qualifierSet.initial
            val q2 = qualifierSet.impose(q1, qualifier)
            if (q2 != q1) {
                map.put(stackValue, stackValue.copy(qualifierSet, q2))
            }
        }

        if (updateOriginalValues) {
            val valueSet = frameValues.values as MutableSet<QualifiedValue<Q>>
            for ((origValue, newValue) in map.entrySet()) {
                valueSet.remove(origValue)
                valueSet.add(newValue)
            }
        }

        val replacedValues = HashSet<QualifiedValue<Q>>()
        frame.forEachValue { valueSet ->
            replacedValues.clear()
            for (value in valueSet.values) {
                if (map.containsKey(value)) {
                    replacedValues.add(value)
                }
            }
            for (value in replacedValues) {
                (valueSet.values as MutableSet<QualifiedValue<Q>>).remove(value)
                (valueSet.values as MutableSet<QualifiedValue<Q>>).add(map[value]!!)
            }
        }
    }

    return frame
}

open class BasicFrameTransformer<Q: Qualifier>: FrameTransformer<QualifiedValueSet<Q>> {
    public override fun getPostFrame(
            insnNode: AbstractInsnNode,
            edgeIndex: Int,
            preFrame: Frame<QualifiedValueSet<Q>>,
            executedFrame: Frame<QualifiedValueSet<Q>>,
            analyzer: Analyzer<QualifiedValueSet<Q>>): Frame<QualifiedValueSet<Q>>? {
        if (insnNode.getOpcode() == ASTORE && edgeIndex == 0) {
            val postFrame = executedFrame.copy()
            val varIndex = (insnNode as VarInsnNode).`var`
            val rhs = preFrame.getStackFromTop(0)
            postFrame.setLocal(varIndex, if (rhs != null) QualifiedValueSet(rhs._size, HashSet(rhs.values)) else null)
            return postFrame
        }
        return executedFrame
    }
}

fun <V: Value> Analyzer<V>.getInstructionFrame(insn: AbstractInsnNode): Frame<V>? {
    val m = getMethodNode()
    return if (m != null) getFrames()[m.instructions.indexOf(insn)] else null
}

fun <V: Value> Frame<V>.getStackFromTop(index: Int): V? =
        getStack(getStackSize() - index - 1)

fun <V: Value> MethodInsnNode.getReceiver(frame: Frame<V>): V? =
        frame.getStackFromTop(getArgumentCount())

public class QualifiedValuesAnalyzer<Q: Qualifier>(
        val owner: ClassName,
        val methodNode: MethodNode,
        val qualifierSet: QualifierSet<Q>,
        val frameTransformer: FrameTransformer<QualifiedValueSet<Q>>,
        val qualifierEvaluator: QualifierEvaluator<Q>
) : Analyzer<QualifiedValueSet<Q>>(QualifiedValuesInterpreter(Method(owner, methodNode), qualifierSet, qualifierEvaluator), frameTransformer)

public fun <Q: Qualifier> MethodNode.runQualifierAnalysis(
        owner: ClassName,
        qualifierSet: QualifierSet<Q>,
        frameTransformer: FrameTransformer<QualifiedValueSet<Q>>,
        qualifierEvaluator: QualifierEvaluator<Q>
): AnalysisResult<QualifiedValueSet<Q>> =
        QualifiedValuesAnalyzer(owner, this, qualifierSet, frameTransformer, qualifierEvaluator).analyze(owner.internal, this)