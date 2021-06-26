/*
 * Copyright 2015-2019 Netflix, Inc.
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

package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.plugin.LintRuleRegistry
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codenarc.rule.AbstractAstVisitorRule
import org.codenarc.rule.AstVisitor
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.codenarc.source.SourceCode
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.text.ParseException

abstract class GradleLintRule extends GroovyAstVisitor implements Rule {
    Project project // will be non-null if type is GradleModelAware, otherwise null
    BuildFiles buildFiles
    SourceCode sourceCode
    List<GradleViolation> gradleViolations = []
    boolean critical = false

    /**
     * Shared state of the method nesting context between gradleAstVisitor and our rule definition.
     */
    Stack<MethodCallExpression> callStack = new Stack()

    // a little convoluted, but will be set by LintRuleRegistry automatically so that name is derived from
    // the properties file resource that makes this rule available for use
    String ruleId

    @Override
    final String getName() {
        return ruleId
    }

    abstract String getDescription()

    private Map<String, ASTNode> bookmarks = [:]

    // Gradle DSL specific visitor methods
    void visitApplyPlugin(MethodCallExpression call, String plugin) {}

    void visitApplyFrom(MethodCallExpression call, String from) {}

    void visitBuildScriptDependency(MethodCallExpression call, String conf, GradleDependency dep) {}

    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {}

    void visitSubprojectGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {}

    void visitAllprojectsGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {}

    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {}

    void visitAnyObjectDependency(MethodCallExpression call, String conf, Object dep) {}

    void visitAnySubmoduleDependency(MethodCallExpression call, String conf, String projectName) {}

    void visitGradlePlugin(MethodCallExpression call, String conf, GradlePlugin plugin) {}

    void visitConfigurationExclude(MethodCallExpression call, String conf, GradleDependency exclude) {}

    void visitExtensionProperty(ExpressionStatement expression, String extension, String prop, String value) {}

    void visitExtensionProperty(ExpressionStatement expression, String extension, String prop) {}

    void visitDependencies(MethodCallExpression call) {}

    void visitAllprojects(MethodCallExpression call) {}

    void visitSubprojects(MethodCallExpression call) {}

    void visitPlugins(MethodCallExpression call) {}

    void visitTask(MethodCallExpression call, String name, Map<String, String> args) {}

    void visitBuildscript(MethodCallExpression call) {}

    void visitGradleResolutionStrategyForce(MethodCallExpression call, String conf, Map<GradleDependency, Expression> forces) {
    }

    protected boolean isIgnored() {
        callStack.any { call ->
            def methodName = call.methodAsString
            if ((methodName == 'ignore' || methodName == 'fixme') && call.objectExpression.text == 'gradleLint') {
                // for fixm-e, the first argument is the predicate that determines whether to fail the build or not
                def ruleNameExpressions = methodName == 'ignore' ? call.arguments.expressions : call.arguments.expressions.drop(1)

                List<String> rulesToIgnore = ruleNameExpressions.findAll { it instanceof ConstantExpression }.collect {
                    it.text
                }
                if (rulesToIgnore.isEmpty())
                    true
                else {
                    rulesToIgnore.collect { LintRuleRegistry.findRules(it) }.flatten().contains(ruleId)
                }
            } else false
        }
    }

    final Expression parentNode() {
        callStack.isEmpty() ? null : callStack.peek()
    }

    final List<String> dslStack() {
        dslStack(callStack)
    }

    final List<String> dslStack(List<MethodCallExpression> calls) {
        def _dslStack
        _dslStack = { Expression expr ->
            if (expr instanceof PropertyExpression)
                _dslStack(expr.objectExpression) + expr.propertyAsString
            else if (expr instanceof MethodCallExpression)
                _dslStack(expr.objectExpression) + expr.methodAsString
            else if (expr instanceof VariableExpression)
                expr.text == 'this' ? [] : [expr.text]
            else []
        }

        calls.collect { call -> _dslStack(call) }.flatten() as List<String>
    }

    private final String containingConfiguration(MethodCallExpression call) {
        def stackStartingWithConfName = dslStack(callStack + call).dropWhile { it != 'configurations' }.drop(1)
        stackStartingWithConfName.isEmpty() ? null : stackStartingWithConfName[0]
    }

    /**
     * Used to preserve the location of a block of code so that it can be affected in some way
     * later in the AST visit
     */
    void bookmark(String label, ASTNode node) {
        bookmarks[label] = node
    }

    ASTNode bookmark(String label) {
        bookmarks[label]
    }

    GradleViolation addBuildLintViolation(String message, ASTNode node) {
        def v = new GradleViolation(buildFiles, rule, node?.lineNumber, sourceCode(node), message)
        if (!isIgnored())
            gradleViolations.add(v)
        return v
    }

    GradleViolation addBuildLintViolation(String message) {
        addBuildLintViolation(message, null)
    }

    GradleViolation addLintViolation(String message, File file, Integer lineNumber) {
        def v = new GradleViolation(new BuildFiles([file]), rule, lineNumber, null, message)
        if (!isIgnored())
            gradleViolations.add(v)
        return v
    }

    /**
     * Allows a rule to perform one-off processing before a rule is applied.
     */
    protected void beforeApplyTo() {}

    @Override
    final List<Violation> applyTo(SourceCode sourceCode) {
        this.sourceCode = sourceCode
        beforeApplyTo()
        rule.applyTo(sourceCode)
        gradleViolations
    }

    /**
     * @param node
     * @return a single or multi-line code snippet stripped of indentation, code that exists on the starting line
     * prior to the starting column, and code that exists on the last line after the ending column
     */
    private final String sourceCode(ASTNode node) {
        if (!node) return null

        // make a copy of violating lines so they can be formatted for display in a report
        def violatingLines = new ArrayList<String>(sourceCode.lines.subList(node.lineNumber - 1, node.lastLineNumber))

        violatingLines[0] = violatingLines[0][(node.columnNumber - 1)..-1]
        if (node.lineNumber != node.lastLineNumber) {
            violatingLines[-1] = violatingLines[-1][0..(node.lastColumnNumber - 2)]
        }

        // taken from the internal implementation of stripIndent()
        def findMinimumLeadingSpaces = { Integer count, String line ->
            int index
            for (index = 0; index < line.length() && index < count && Character.isWhitespace(line.charAt(index)); ++index) {
            }
            index
        }

        def indentFirst = violatingLines.size() > 1 ? violatingLines.drop(1).inject(Integer.MAX_VALUE, findMinimumLeadingSpaces) : 0
        violatingLines[0] = violatingLines[0].padLeft(violatingLines[0].length() + indentFirst)
        violatingLines.join('\n').stripIndent()
    }

    /**
     * See the comment on compositeVisitor below for why we are visiting the AST separately independently of our rule definition.
     */
    @Delegate
    final Rule rule = new AbstractAstVisitorRule() {
        @Override
        AstVisitor getAstVisitor() {
            new CompositeGroovyAstVisitor(visitors: [gradleAstVisitor, GradleLintRule.this], callStack: callStack)
        }

        private Logger logger = LoggerFactory.getLogger(GradleLintRule)

        GroovyAstVisitor gradleAstVisitor = new GroovyAstVisitor() {

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                def methodName = call.methodAsString
                def expressions = call.arguments.expressions
                def objectExpression = call.objectExpression.text

                if (methodName == 'ignore' && objectExpression == 'gradleLint') {
                    return // short-circuit ignore calls
                }

                if (methodName == 'fixme' && objectExpression == 'gradleLint') {
                    visitFixme(call)
                }

                def inMethod = { name -> dslStack(callStack + call).contains(name) }

                if (inMethod('dependencies')) visitMethodCallInDependencies(call)
                if (inMethod('configurations')) visitMethodCallInConfigurations(call)
                if (inMethod('plugins')) visitMethodCallInPlugins(call)
                if (inMethod('resolutionStrategy')) visitMethodCallInResolutionStrategy(call)

                if (methodName == 'buildscript') {
                    GradleLintRule.this.visitBuildscript(call)
                } else if (methodName == 'dependencies') {
                    GradleLintRule.this.visitDependencies(call)
                } else if (methodName == 'plugins' && callStack.isEmpty()) {
                    GradleLintRule.this.visitPlugins(call)
                } else if (methodName == 'apply') {
                    if (expressions.any { it instanceof MapExpression }) {
                        def entries = GradleAstUtil.collectEntryExpressions(call)
                        if (entries.plugin) {
                            visitApplyPlugin(call, entries.plugin)
                        }
                        if (entries.from) {
                            visitApplyFrom(call, entries.from)
                        }
                    }
                } else if (methodName == 'task' || (objectExpression == 'tasks' && methodName == 'create')) {
                    visitPossibleTaskDefinition(call, expressions as List)
                } else if (methodName == 'allprojects') {
                    visitAllprojects(call)
                } else if (methodName == 'subprojects') {
                    visitSubprojects(call)
                }
            }

            /***
             * Invokes visitTask upon encountering a task definition in the gradle script
             * Supports the following definition forms:
             * task(t1)
             * task('t2')
             * task(t3) {}* task('t4') {}* task t5
             * task t6 {}* task (t7,type: Wrapper)
             * task ('t8',type: Wrapper)
             * task t9(type: Wrapper)
             * task t10(type: Wrapper) {}* task([:], t11)
             * task([type: Wrapper], t12)
             * task([type: Wrapper], t13) {}* tasks.create([name: 't14'])
             * tasks.create([name: 't15']) {}* tasks.create('t16') {}* tasks.create('t17')
             * tasks.create('t18', Wrapper) {}* tasks.create('t19', Wrapper.class)
             *
             * @author Boaz Jan
             * @param call
             * @param expressions
             */
            private void visitPossibleTaskDefinition(MethodCallExpression call, List expressions) {
                def taskName = null
                def taskArgs = [:] as Map<String, String>
                def possibleName = expressions.find {
                    !(it instanceof MapExpression || it instanceof ClosureExpression)
                }
                if (possibleName == null) {
                    taskArgs = GradleAstUtil.collectEntryExpressions(call)
                    taskName = taskArgs['name']
                } else if (possibleName instanceof VariableExpression) {
                    taskName = possibleName.variable
                    taskArgs = GradleAstUtil.collectEntryExpressions(call)
                } else if (possibleName instanceof ConstantExpression) {
                    taskName = possibleName.value
                    taskArgs = GradleAstUtil.collectEntryExpressions(call)
                    if (taskArgs.isEmpty() && expressions.size() > 1) {
                        if (expressions[1] instanceof VariableExpression) {
                            taskArgs['type'] = expressions[1].variable
                        } else if (expressions[1] instanceof PropertyExpression) {
                            taskArgs['type'] = expressions[1].objectExpression.variable
                        }
                    }
                } else if (possibleName instanceof MethodCallExpression) {
                    taskName = possibleName.methodAsString
                    taskArgs = GradleAstUtil.collectEntryExpressions(possibleName)
                }
                if (taskName != null) {
                    GradleLintRule.this.visitTask(call, taskName as String, taskArgs)
                }
            }

            @Override
            void visitExpressionStatement(ExpressionStatement statement) {
                def expression = statement.expression
                if (!callStack.isEmpty()) {
                    def closureName = null
                    switch (callStack.peek()) {
                        case MethodCallExpression: closureName = callStack.peek().methodAsString; break
                        case PropertyExpression: closureName = callStack.peek().text; break
                    }

                    if (expression instanceof BinaryExpression) {
                        if (expression.rightExpression instanceof ConstantExpression) {
                            // STYLE: nebula { moduleOwner = 'me' }
                            // if the right side isn't a constant expression, we won't be able to evaluate it through just the AST
                            visitExtensionProperty(statement, closureName, expression.leftExpression.text,
                                    expression.rightExpression.text)
                        }

                        // otherwise, still give a rule the opportunity to check the value of the extension property from a
                        // resolved Gradle model and react accordingly

                        // STYLE: nebula { moduleOwner = trim('me') }
                        visitExtensionProperty(statement, closureName, expression.leftExpression.text)
                    } else if (expression instanceof MethodCallExpression) {
                        if (expression.arguments instanceof ArgumentListExpression) {
                            def args = expression.arguments.expressions as List<Expression>
                            if (args.size() == 1) {
                                if (args[0] instanceof ConstantExpression) { // STYLE: nebula { moduleOwner 'me' }
                                    visitExtensionProperty(statement, closureName, expression.methodAsString, args[0].text)
                                }
                                // STYLE: nebula { moduleOwner trim('me') }
                                visitExtensionProperty(statement, closureName, expression.methodAsString)
                            }
                        }
                    }
                } else if (expression instanceof BinaryExpression && expression.leftExpression instanceof PropertyExpression) {
                    def extension = expression.leftExpression.objectExpression.text
                    def prop = expression.leftExpression.property.text
                    if (expression.rightExpression instanceof ConstantExpression) {
                        // STYLE: nebula.moduleOwner = 'me'
                        visitExtensionProperty(statement, extension, prop, expression.rightExpression.text)
                    }
                    // STYLE: nebula.moduleOwner trim('me')
                    visitExtensionProperty(statement, extension, prop)
                }
            }

            private void visitMethodCallInConfigurations(MethodCallExpression call) {
                def methodName = call.methodAsString
                def conf = containingConfiguration(call)

                // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/ModuleDependency.html#exclude(java.util.Map)
                if ((hasConfiguration(conf) || conf == 'all') && methodName == 'exclude') {
                    def entries = GradleAstUtil.collectEntryExpressions(call)
                    visitConfigurationExclude(call, conf, new GradleDependency(entries.group, entries.module))
                }
            }

            private void visitMethodCallInDependencies(MethodCallExpression call) {
                // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
                def methodName = call.methodAsString
                def args = call.arguments.expressions as List
                if (!args.empty && (hasConfiguration(methodName) || methodName == 'classpath')) {
                    def dependency = null

                    if (call.arguments.expressions.any { it instanceof MapExpression }) {
                        def entries = GradleAstUtil.collectEntryExpressions(call, sourceCode)
                        dependency = new GradleDependency(
                                entries.group,
                                entries.name,
                                entries.version,
                                entries.classifier,
                                entries.ext,
                                entries.conf,
                                GradleDependency.Syntax.MapNotation)
                    } else if (call.arguments.expressions.any {
                        it instanceof ConstantExpression || it instanceof GStringExpression
                    }) {
                        def expr = call.arguments.expressions.findResult {
                            if (it instanceof ConstantExpression)
                                return it.value
                            if (it instanceof GStringExpression)
                                if (it.lineNumber == it.lastLineNumber)
                                    return sourceCode.lines.get(it.lineNumber - 1).substring(it.columnNumber, it.lastColumnNumber - 2)
                                else
                                    return it.text
                            return null
                        }
                        dependency = GradleDependency.fromConstant(expr)
                    } else if (call.arguments.expressions.any { it instanceof MethodCallExpression && it.methodAsString == 'project'}) {
                        MethodCallExpression projectMethodCall = call.arguments.expressions
                                .find { it instanceof MethodCallExpression && it.methodAsString == 'project'} as MethodCallExpression
                        ConstantExpression projectName =
                                projectMethodCall.arguments.expressions.
                                find { it instanceof ConstantExpression} as ConstantExpression
                        if (projectName != null)
                            visitAnySubmoduleDependency(call, methodName, projectName.value.toString())
                        else if (projectMethodCall.arguments.expressions.any { it instanceof MapExpression }) {
                            def entries = GradleAstUtil.collectEntryExpressions(projectMethodCall, sourceCode)
                            def path = entries.get("path")
                            if (path != null)
                                visitAnySubmoduleDependency(call, methodName, path)
                        } else {
                            visitAnySubmoduleDependency(call, methodName, null)
                        }
                    } else if (project != null) {
                        Object dep
                        def shell = new GroovyShell()
                        shell.setVariable('project', project as Project)
                        try {
                            dep = shell.evaluate('project.' + sourceCode(call.arguments))
                            dependency = GradleDependency.fromConstant(dep)
                            if (dependency != null) {
                                dependency.syntax = GradleDependency.Syntax.EvaluatedArbitraryCode
                            }
                        } catch (Throwable t) {
                            // if we cannot evaluate this expression, just give up
                            logger.debug("Unable to evaluate dependency expression ${sourceCode(call.arguments)}", t)
                            dep = new NotEvaluatedObject(call.arguments)
                        }
                        if (dependency == null) {
                            visitAnyObjectDependency(call, methodName, dep)
                        }
                    }

                    if (dependency) {
                        def top = dslStack().isEmpty() ? "" : dslStack().first()
                        if (top == 'allprojects') {
                            visitAllprojectsGradleDependency(call, methodName, dependency)
                        } else if (top == 'subprojects') {
                            visitSubprojectGradleDependency(call, methodName, dependency)
                        } else if (top == 'buildscript') {
                            visitBuildScriptDependency(call, methodName, dependency)
                        } else {
                            visitGradleDependency(call, methodName, dependency)
                        }
                        visitAnyGradleDependency(call, methodName, dependency)
                    }
                }
            }

            private void visitMethodCallInPlugins(MethodCallExpression call) {
                // https://docs.gradle.org/current/javadoc/org/gradle/plugin/use/PluginDependenciesSpec.html
                def args = call.arguments.expressions as List
                if (!args.empty) {
                    def plugin = null
                    if (args.any {
                        it instanceof ConstantExpression || it instanceof GStringExpression
                    }) {
                        def expr = args.findResult {
                            if (it instanceof ConstantExpression)
                                return it.value
                            if (it instanceof GStringExpression)
                                return it.text
                            return null
                        }
                        if (expr instanceof String || (expr instanceof Boolean && call.methodAsString == 'apply'))
                            plugin = new GradlePlugin(expr.toString())
                    }

                    if (plugin) {
                        visitGradlePlugin(call, call.methodAsString, plugin)
                    }
                }
            }

            private void visitMethodCallInResolutionStrategy(MethodCallExpression call) {
                // https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.ResolutionStrategy.html
                if (call.methodAsString == 'force') {
                    def forces = (call.arguments.expressions as List).findResults {
                        if (it instanceof ConstantExpression)
                            return [GradleDependency.fromConstant(it.value), it]
                        if (it instanceof GStringExpression)
                            return [GradleDependency.fromConstant(it.text), it]
                        return null
                    }.flatten()

                    def conf = containingConfiguration(call)
                    if (conf)
                        visitGradleResolutionStrategyForce(call, conf, forces.toSpreadMap())
                }
            }

            private boolean hasConfiguration(String name) {
                if (!project) {
                    return Collections.emptySet()
                }
                def configurations
                if (!dslStack().isEmpty() && (dslStack().first() == 'subprojects' || callStack.first().methodAsString == 'project')) {
                    def expr = callStack.first().arguments.expressions[0]
                    def subproject
                    if (expr instanceof ConstantExpression || expr instanceof GStringExpression) {
                        def path = expr instanceof ConstantExpression ? expr.value : expr.text
                        subproject = project.childProjects.values().find { it.path == path }
                    }
                    //in cases of dynamically declared names we won't be able to find project in previous step
                    //fall back to a first project from children list
                    if (subproject == null) {
                        subproject = project.childProjects.values().first()
                    }
                    configurations = subproject.configurations
                } else {
                    configurations = project.configurations
                }
                // contains() causes an NPE on the TreeSet that comes from the configuration, thus the extra toSet()
                return configurations.names.toSet().contains(name)
            }

            private void visitFixme(MethodCallExpression call) {
                def predicate = call.arguments.expressions[0]
                switch (predicate) {
                    case ConstantExpression:
                        def successfullyComparedDate = ['yyyy-M-d', 'M/d/yy', 'M/d/yyyy'].any { pattern ->
                            try {
                                def date = Date.parse(pattern, predicate.value as String)
                                if (!date) false
                                else if (date < new Date()) {
                                    gradleViolations.add(new GradleViolation(buildFiles, new FixmeRule(), call?.lineNumber, sourceCode(call),
                                            'this fixme has expired -- remove it and address the underlying lint issue that caused it to be added'))
                                    true
                                } else true
                            } catch (ParseException ignored) {
                                false
                            }
                        }

                        if (!successfullyComparedDate) {
                            gradleViolations.add(new GradleViolation(buildFiles, new FixmeRule(), call?.lineNumber, sourceCode(call),
                                    'this fixme contains an unparseable date, use the yyyy-M-d format'))
                        }
                        break
                }
            }
        }

        @Override
        String getName() {
            GradleLintRule.this.name
        }

        @Override
        void setName(String name) {
            // unused
        }

        @Override
        int getPriority() {
            critical ? 1 : 2
        }

        @Override
        void setPriority(int priority) {
            // unused
        }
    }

    static class NotEvaluatedObject {
        ASTNode objectAst

        NotEvaluatedObject(ASTNode objectAst) {
            this.objectAst = objectAst
        }
    }
}
