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

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiClassReferenceType
import motif.core.ResolvedGraph
import motif.intellij.MotifService
import motif.intellij.MotifService.Companion.TOOL_WINDOW_ID
import motif.intellij.ScopeHierarchyUtils.Companion.getParentScopes
import motif.intellij.ScopeHierarchyUtils.Companion.isMotifScopeClass
import motif.intellij.analytics.AnalyticsService
import motif.intellij.analytics.MotifAnalyticsActions
import motif.intellij.toPsiClass
import org.jetbrains.kotlin.idea.highlighter.markers.LineMarkerInfos
import java.awt.event.MouseEvent

/*
 * {@LineMarkerProvider} used to display icon in gutter to navigate to motif scope ancestors hierarchy.
 */
class ScopeHierarchyLineMarkerProvider : LineMarkerProvider, MotifService.Listener {

  companion object {
    const val LABEL_ANCESTORS_SCOPE: String = "View Scope Ancestors"
  }

  private var graph: ResolvedGraph? = null

  override fun onGraphUpdated(graph: ResolvedGraph) {
    this.graph = graph
  }

  override fun collectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos) {
    val graph: ResolvedGraph = graph ?: return

    for (element in elements) {
      ProgressManager.checkCanceled()

      if (!isMotifScopeClass(element)) {
        return
      }

      val classElement = element.toPsiClass() as PsiClass
      if (getParentScopes(element.project, graph, classElement).isNullOrEmpty()) {
        return
      }

      val identifier: PsiIdentifier = classElement.nameIdentifier ?: return
      result.add(
              LineMarkerInfo(
                      identifier,
                      identifier.textRange,
                      AllIcons.Hierarchy.Supertypes,
                      { LABEL_ANCESTORS_SCOPE },
                      ScopeHierarchyHandler(element.project),
                      LEFT,
                      { LABEL_ANCESTORS_SCOPE }
              )
      )
    }
  }

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
    return null
  }

  private class ScopeHierarchyHandler(val project: Project) :
      GutterIconNavigationHandler<PsiElement> {
      private val analyticsService = project.service<AnalyticsService>()
      private val motifService = project.service<MotifService>()
    override fun navigate(event: MouseEvent?, element: PsiElement?) {
      val toolWindow: ToolWindow =
          ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
      if (element is PsiClass) {
        toolWindow.activate {
            motifService.onSelectedAncestorScope(element)
        }
      } else if (element is PsiMethod) {
        if (element.returnType is PsiClassReferenceType) {
          val returnElementClass: PsiClass =
              (element.returnType as PsiClassReferenceType).resolve() ?: return
          toolWindow.activate {
              motifService.onSelectedAncestorScope(returnElementClass)
          }
        }
      }
        analyticsService.logEvent(MotifAnalyticsActions.ANCESTOR_GUTTER_CLICK)
    }
  }
}
