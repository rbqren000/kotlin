/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.diagnostics.rendering

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.resolve.multiplatform.K1ExpectActualCompatibility.Incompatible
import org.jetbrains.kotlin.resolve.multiplatform.K1ExpectActualMemberDiff
import java.text.MessageFormat

class PlatformIncompatibilityDiagnosticRenderer(
    private val mode: MultiplatformDiagnosticRenderingMode
) : DiagnosticParameterRenderer<Map<Incompatible<MemberDescriptor>, Collection<MemberDescriptor>>> {
    override fun render(
        obj: Map<Incompatible<MemberDescriptor>, Collection<MemberDescriptor>>,
        renderingContext: RenderingContext
    ): String {
        if (obj.isEmpty()) return ""

        return buildString {
            mode.newLine(this)
            renderIncompatibilityInformation(obj, "", renderingContext, mode)
        }
    }

    companion object {
        @JvmField
        val TEXT = PlatformIncompatibilityDiagnosticRenderer(MultiplatformDiagnosticRenderingMode())
    }
}

class IncompatibleExpectedActualClassScopesRenderer(
    private val mode: MultiplatformDiagnosticRenderingMode
) : DiagnosticParameterRenderer<List<Pair<MemberDescriptor, Map<Incompatible<MemberDescriptor>, Collection<MemberDescriptor>>>>> {
    override fun render(
        obj: List<Pair<MemberDescriptor, Map<Incompatible<MemberDescriptor>, Collection<MemberDescriptor>>>>,
        renderingContext: RenderingContext
    ): String {
        if (obj.isEmpty()) return ""

        return buildString {
            mode.newLine(this)
            renderIncompatibleClassScopes(obj, "", renderingContext, mode)
        }
    }

    companion object {
        @JvmField
        val TEXT = IncompatibleExpectedActualClassScopesRenderer(MultiplatformDiagnosticRenderingMode())
    }
}

class ExpectActualScopeDiffsRenderer(
    private val mode: MultiplatformDiagnosticRenderingMode,
) : DiagnosticParameterRenderer<Set<K1ExpectActualMemberDiff<CallableMemberDescriptor, ClassDescriptor>>> {
    override fun render(
        obj: Set<K1ExpectActualMemberDiff<CallableMemberDescriptor, ClassDescriptor>>,
        renderingContext: RenderingContext,
    ): String {
        check(obj.isNotEmpty())
        return buildString {
            mode.renderList(this, obj.toList().map { diff ->
                {
                    appendLine()
                    appendLine(ExpectActualScopeDiffRenderer.render(diff, renderingContext))
                }
            })
        }
    }

    companion object {
        @JvmField
        val TEXT = ExpectActualScopeDiffsRenderer(MultiplatformDiagnosticRenderingMode())
    }
}

object ExpectActualScopeDiffRenderer : DiagnosticParameterRenderer<K1ExpectActualMemberDiff<CallableMemberDescriptor, ClassDescriptor>> {
    override fun render(
        obj: K1ExpectActualMemberDiff<CallableMemberDescriptor, ClassDescriptor>,
        renderingContext: RenderingContext,
    ): String = MessageFormat.format(
        obj.kind.rawMessage,
        Renderers.DECLARATION_NAME_WITH_KIND.render(obj.actualMember, renderingContext),
        Renderers.NAME.render(obj.expectClass)
    )
}

class ListRenderer<T>(
    private val elementRenderer: DiagnosticParameterRenderer<T>,
    private val elemProcessor: (String) -> String = { it },
) : DiagnosticParameterRenderer<List<T>> {
    override fun render(obj: List<T>, renderingContext: RenderingContext): String =
        obj.joinToString { elemProcessor(elementRenderer.render(it, renderingContext)) }
}

open class MultiplatformDiagnosticRenderingMode {
    open fun newLine(sb: StringBuilder) {
        sb.appendLine()
    }

    open fun renderList(sb: StringBuilder, elements: List<() -> Unit>) {
        sb.appendLine()
        for (element in elements) {
            element()
        }
    }

    open fun renderDescriptor(sb: StringBuilder, descriptor: DeclarationDescriptor, context: RenderingContext, indent: String) {
        sb.append(indent)
        sb.append(INDENTATION_UNIT)
        sb.appendLine(Renderers.COMPACT_WITH_MODIFIERS.render(descriptor, context))
    }
}

private fun StringBuilder.renderIncompatibilityInformation(
    map: Map<Incompatible<MemberDescriptor>, Collection<MemberDescriptor>>,
    indent: String,
    context: RenderingContext,
    mode: MultiplatformDiagnosticRenderingMode
) {
    for ((incompatibility, descriptors) in map) {
        append(indent)
        append("The following declaration")
        if (descriptors.size == 1) append(" is") else append("s are")
        append(" incompatible")
        incompatibility.reason?.let { append(" because $it") }
        append(":")

        mode.renderList(this, descriptors.map { descriptor ->
            { mode.renderDescriptor(this, descriptor, context, indent) }
        })

        if (incompatibility is Incompatible.ClassScopes) {
            append(indent)
            append("No actual members are found for expected members listed below:")
            mode.newLine(this)
            renderIncompatibleClassScopes(incompatibility.unfulfilled, indent, context, mode)
        }
    }
}

private fun StringBuilder.renderIncompatibleClassScopes(
    unfulfilled: List<Pair<MemberDescriptor, Map<Incompatible<MemberDescriptor>, Collection<MemberDescriptor>>>>,
    indent: String,
    context: RenderingContext,
    mode: MultiplatformDiagnosticRenderingMode
) {
    mode.renderList(this, unfulfilled.indices.map { index ->
        {
            val (descriptor, mapping) = unfulfilled[index]
            mode.renderDescriptor(this, descriptor, context, indent)
            if (mapping.isNotEmpty()) {
                mode.newLine(this)
                renderIncompatibilityInformation(mapping, indent + INDENTATION_UNIT, context, mode)
            }
            if (index != unfulfilled.lastIndex) {
                mode.newLine(this)
            }
        }
    })
}

private const val INDENTATION_UNIT = "    "
