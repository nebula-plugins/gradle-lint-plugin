package com.netflix.nebula.lint

import groovy.transform.TupleConstructor
import org.gradle.internal.service.ServiceRegistry

/**
 * Bridges the internal Gradle 2 and 3 APIs for styled text output to provide
 * a single backwards-compatible interface.
 */
class StyledTextService {
    def textOutput

    StyledTextService(ServiceRegistry registry) {
        Class<?> factoryClass
        try {
            factoryClass = Class.forName('org.gradle.internal.logging.text.StyledTextOutputFactory')
        } catch(ClassNotFoundException ignore) {
            factoryClass = Class.forName('org.gradle.logging.StyledTextOutputFactory')
        }

        def textOutputFactory = registry.get(factoryClass)
        textOutput = textOutputFactory.create("gradle-lint")
    }

    VersionNeutralTextOutput withStyle(Styling styling) {
        Class<?> styleClass
        try {
            styleClass = Class.forName('org.gradle.internal.logging.text.StyledTextOutput$Style')
        } catch(ClassNotFoundException ignore) {
            styleClass = Class.forName('org.gradle.logging.StyledTextOutput$Style')
        }
        
        styleClass.enumConstants
        def styleByName = { String name ->
            styleClass.enumConstants.find { it.name() == name }
        }
        
        switch (styling) {
            case Styling.Bold:
                return new VersionNeutralTextOutput(textOutput.withStyle(styleByName('UserInput')))
            case Styling.Green:
                return new VersionNeutralTextOutput(textOutput.withStyle(styleByName('Identifier')))
            case Styling.Yellow:
                return new VersionNeutralTextOutput(textOutput.withStyle(styleByName('Description')))
            case Styling.Red:
                return new VersionNeutralTextOutput(textOutput.withStyle(styleByName('Failure')))
        }
    }

    StyledTextService text(String text) {
        textOutput.text(text)
        return this
    }

    StyledTextService println(String text) {
        textOutput.println(text)
        return this
    }

    StyledTextService println() {
        // the no-arg form is a dangerous overload on Groovy's println() metaclass extension of Object
        textOutput.println('')
        return this
    }

    static enum Styling {
        Bold, Green, Yellow, Red
    }
}

@TupleConstructor
class VersionNeutralTextOutput {
    def textOutput

    void text(Object v) { textOutput.text(v) }
    void println(Object v) { textOutput.println(v) }
    void println() { textOutput.println('') }
}