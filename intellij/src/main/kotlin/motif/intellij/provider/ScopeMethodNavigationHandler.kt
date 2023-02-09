package motif.intellij.provider

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTreeUtil
import motif.core.ResolvedGraph
import motif.intellij.ScopeHierarchyUtils
import motif.intellij.analytics.AnalyticsService
import motif.intellij.analytics.MotifAnalyticsActions
import java.awt.event.MouseEvent


internal class ScopeMethodNavigationHandler(project: Project, val graph: ResolvedGraph) :
        GutterIconNavigationHandler<PsiElement> {

    private val analyticsService = project.service<AnalyticsService>()

    override fun navigate(event: MouseEvent?, element: PsiElement?) {
        val methodElement = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        if (methodElement is PsiMethod) {
            val classElement = PsiTreeUtil.getParentOfType(methodElement, PsiClass::class.java)
            if (ScopeHierarchyUtils.isMotifScopeClass(classElement) &&
                    methodElement.returnType is PsiClassReferenceType) {
                val returnElementClass: PsiClass? =
                        (methodElement.returnType as PsiClassReferenceType).resolve()
                returnElementClass?.let {
                    val navigationElement = it.navigationElement
                    if (navigationElement is Navigatable &&
                            (navigationElement as Navigatable).canNavigate()) {
                        navigationElement.navigate(true)
                    }
                }
            }
        }
        analyticsService.logEvent(MotifAnalyticsActions.NAVIGATION_GUTTER_CLICK)
    }
}