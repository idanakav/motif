package motif.intellij.provider

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import motif.core.ResolvedGraph
import motif.core.ScopeEdge
import motif.intellij.ScopeHierarchyUtils
import motif.intellij.analytics.AnalyticsService
import motif.intellij.analytics.MotifAnalyticsActions
import java.awt.event.MouseEvent


internal class ScopeClassNavigationHandler(project: Project, val graph: ResolvedGraph) :
        GutterIconNavigationHandler<PsiElement> {

    private val analyticsService = project.service<AnalyticsService>()

    override fun navigate(event: MouseEvent?, element: PsiElement?) {
        val psiClassElement = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        if (psiClassElement is PsiClass) {
            val scopeEdges: Array<ScopeEdge>? =
                    ScopeHierarchyUtils.getParentScopes(psiClassElement.project, graph, psiClassElement)
            if (scopeEdges == null) {
                Messages.showInfoMessage(ScopeNavigationLineMarkerProvider.MESSAGE_NAVIGATION_NO_SCOPE, ScopeNavigationLineMarkerProvider.MESSAGE_TITLE)
                return
            }
            when (scopeEdges.size) {
                0 -> Messages.showInfoMessage(ScopeNavigationLineMarkerProvider.MESSAGE_NAVIGATION_PARENT_ROOT, ScopeNavigationLineMarkerProvider.MESSAGE_TITLE)
                1 -> scopeEdges[0].navigateToParent()
                else -> {
                    val mouseEvent: MouseEvent = event ?: return
                    val listPopup = ParentScopeSelectorPopup.createListPopup(scopeEdges.toList())
                    listPopup.show(RelativePoint(mouseEvent))
                }
            }
        }
        analyticsService.logEvent(MotifAnalyticsActions.NAVIGATION_GUTTER_CLICK)
    }
}