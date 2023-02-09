/*
 * Copyright (c) 2018-2019 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package motif.intellij.provider

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import motif.core.ResolvedGraph
import motif.core.ScopeEdge
import motif.intellij.MotifService
import motif.intellij.ScopeHierarchyUtils.Companion.getParentScopes
import motif.intellij.ScopeHierarchyUtils.Companion.isMotifChildScopeMethod
import motif.intellij.ScopeHierarchyUtils.Companion.isMotifScopeClass
import motif.intellij.toPsiClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.highlighter.markers.LineMarkerInfos

/*
 * {@LineMarkerProvider} used to display navigation icons in gutter to navigate to parent/children of Motif scopes.
 */
class ScopeNavigationLineMarkerProvider : LineMarkerProvider, MotifService.Listener {

    companion object {
        const val LABEL_NAVIGATE_PARENT_SCOPE: String = "Navigate to parent Scope"
        const val LABEL_NAVIGATE_CHILD_SCOPE: String = "Navigate to child Scope"
        const val MESSAGE_NAVIGATION_NO_SCOPE: String =
                "Provided class doesn't have a corresponding Motif scope. Please refresh graph manually and try again."
        const val MESSAGE_NAVIGATION_PARENT_ROOT: String =
                "Can't navigate to parent scope because scope is a root scope."
        const val MESSAGE_TITLE: String = "Motif"
    }

    private var graph: ResolvedGraph? = null

    override fun onGraphUpdated(graph: ResolvedGraph) {
        this.graph = graph
    }

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos) {
        val graph: ResolvedGraph = graph ?: return

        for (element in elements) {
            ProgressManager.checkCanceled()

            if (isMotifScopeClass(element)) {
                val psiClass = element.toPsiClass()
                if (psiClass !is PsiClass) continue
                val scopeEdges: Array<ScopeEdge>? = getParentScopes(element.project, graph, psiClass)
                if (scopeEdges.isNullOrEmpty()) continue

                val identifier: PsiIdentifier = psiClass.nameIdentifier ?: continue
                result.add(
                        LineMarkerInfo(
                                identifier,
                                identifier.textRange,
                                AllIcons.Actions.PreviousOccurence,
                                { LABEL_NAVIGATE_PARENT_SCOPE },
                                ScopeClassNavigationHandler(element.project, graph),
                                LEFT,
                                { LABEL_NAVIGATE_PARENT_SCOPE }
                        )
                )
            } else if (isMotifChildScopeMethod(element)) {
                val identifier: PsiIdentifier = element.toLightMethods().firstOrNull()?.nameIdentifier ?: continue
                result.add(
                        LineMarkerInfo(
                                identifier,
                                identifier.textRange,
                                AllIcons.Actions.NextOccurence,
                                { LABEL_NAVIGATE_CHILD_SCOPE },
                                ScopeMethodNavigationHandler(element.project, graph),
                                LEFT,
                                { LABEL_NAVIGATE_CHILD_SCOPE }
                        )
                )
            }
        }
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
        return null
    }
}
