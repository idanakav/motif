package motif.intellij.provider

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import motif.ast.intellij.IntelliJClass
import motif.core.ScopeEdge

internal fun ScopeEdge.navigateToParent() {
    val navigationElement: PsiElement =
            (parent.clazz as IntelliJClass).psiClass.navigationElement
    if (navigationElement is Navigatable && (navigationElement as Navigatable).canNavigate()) {
        navigationElement.navigate(true)
    }
}

internal class ParentScopeSelectorPopup(
        edges: List<ScopeEdge>) : BaseListPopupStep<ScopeEdge>(
        "Select Parent Scope",
        edges.toMutableList()
) {
    override fun getTextFor(value: ScopeEdge): String {
        return value.parent.clazz.simpleName
    }

    override fun onChosen(
            selectedValue: ScopeEdge?,
            finalChoice: Boolean
    ): PopupStep<*>? {
        selectedValue?.navigateToParent()
        return super.onChosen(selectedValue, finalChoice)
    }

    companion object {
        fun createListPopup(
                edges: List<ScopeEdge>,
        ): ListPopup {
            return JBPopupFactory.getInstance().createListPopup(ParentScopeSelectorPopup(edges))
        }
    }
}

