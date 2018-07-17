package com.uber.motif.compiler.graph

import com.uber.motif.compiler.model.Dependency
import com.uber.motif.compiler.model.ScopeClass
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement

class ResolvedGraph(val resolvedScopes: Set<ResolvedScope>) {

    private val pretty: String by lazy {
        val scopes = resolvedScopes.map { it.scope.type }.toString()
        "ResolvedGraph$scopes"
    }

    override fun toString(): String {
        return pretty
    }

    companion object {

        fun resolve(env: ProcessingEnvironment, scopeTypes: List<TypeElement>): ResolvedGraph {
            return GraphResolver(env, scopeTypes).resolve()
        }
    }
}

private class GraphResolver(private val env: ProcessingEnvironment, scopeTypes: List<TypeElement>) {

    private val scopes: Map<TypeElement, ScopeClass> = scopeTypes
            .map { ScopeClass.fromClass(env, it) }
            .associateBy { it.type }

    private val resolvedParents: MutableMap<TypeElement, ResolvedParent> = mutableMapOf()

    fun resolve(): ResolvedGraph {
        val validatedScopes = scopes.map { (key, scope) ->
            val parent: ResolvedParent = resolveParent(key)
            val children: List<ResolvedChild> = scope.childMethods.map { childMethod ->
                val childParent: ResolvedParent = resolvedParents[childMethod.scopeType]!!
                ResolvedChild(childMethod, childParent)
            }
            ResolvedScope(scope, parent, children)
        }.toSet()
        return ResolvedGraph(validatedScopes)
    }

    private fun resolveParent(scopeType: TypeElement): ResolvedParent {
        return resolveParent(setOf(), scopeType)!!
    }

    // TODO Currently, dynamic dependencies are public by default. This is due to the fact that a child doesn't know
    // whether its external dependencies are provided "normally" or via a dynamic dependency. This is convenient in
    // order to support multiple parents each passing different dynamic dependencies to the child. Ideally, dynamic
    // dependencies would only be public if listed explicitly on the Objects class as public.
    private fun resolveParent(visited: Set<TypeElement>, scopeType: TypeElement): ResolvedParent? {
        // Circular scope dependencies are allowed. Stop recursion when we hit a cycle.
        if (scopeType in visited) return null
        return resolvedParents.computeIfAbsent(scopeType) {
            // If scope implementation is already generated, don't recalculate dependencies. This is the case for child
            // scopes that live in a different Gradle module.
            ResolvedParent.fromGenerated(env, scopeType)?.let { return@computeIfAbsent it }

            val scope = scopes[scopeType]
            // Child scope isn't being processed by the annotation processor and its implementation hasn't been
            // generated yet. This can happen if a child Gradle module didn't run this annotation processor.
                    ?: throw RuntimeException("Scope implementation not found for $scopeType. This can happen if a child " +
                            "Gradle module didn't the Motif annotation processor.")

            val newVisited = visited + scopeType

            // Collect child dependencies.
            val childExternalDependencies: Set<Dependency> = scope.childMethods.flatMap {
                val dependencies: List<Dependency> = resolveParent(newVisited, it.scopeType)?.methods?.map { it.dependency } ?: listOf()
                dependencies - it.dynamicDependencies
            }.toSet()

            val requiredFromParentBySelf = scope.dependenciesRequiredBySelf - scope.providedDependencies
            // We can only pass public dependencies to children so use scope.providedPublicDependencies here.
            val requiredFromParentByChildren = childExternalDependencies - scope.providedPublicDependencies
            val externalDependencies = requiredFromParentBySelf + requiredFromParentByChildren

            // If developer defined a parent interface explicitly, ensure that the dependencies declared on the parent
            // interface cover what this scope requires from its parent. If this scope requires more from its parent
            // than what's defined on the parent interface, throw a missing dependencies error.
            scope.parentInterface?.let { explicitParentInterface ->
                val expectedDependenciesProvidedByParent = explicitParentInterface.methods.map { it.dependency }.toSet()
                val missingDependencies = externalDependencies - expectedDependenciesProvidedByParent
                if (missingDependencies.isNotEmpty()) {
                    throw RuntimeException("Scope $scopeType is missing dependencies: $missingDependencies")
                }
                // Make sure to use the explicitly declared external dependencies instead of calculated ones.
                return@computeIfAbsent ResolvedParent.fromExplicit(explicitParentInterface)
            }

            ResolvedParent.fromCalculated(scopeType, externalDependencies)
        }
    }
}