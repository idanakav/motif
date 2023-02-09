package motif.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import motif.core.ResolvedGraph
import motif.intellij.analytics.AnalyticsService
import motif.intellij.analytics.MotifAnalyticsActions

class MotifStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val motifService = project.service<MotifService>()
        val analyticsService = project.service<AnalyticsService>()
        DumbService.getInstance(project).smartInvokeLater {
            ApplicationManager.getApplication().runReadAction {
                // Initialize plugin with empty graph to avoid IDE startup slowdown
                val emptyGraph: ResolvedGraph = ResolvedGraph.create(emptyList())
                motifService.onGraphUpdated(emptyGraph)

                analyticsService.logEvent(MotifAnalyticsActions.PROJECT_OPENED)
            }
        }
    }
}