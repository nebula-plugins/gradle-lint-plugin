package com.netflix.nebula.lint

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

    StyledTextService withStyle(Styling styling) {
        Class<?> styleClass
        try {
            styleClass = Class.forName('org.gradle.internal.logging.text.StyledTextOutput$Style')
        } catch(ClassNotFoundException ignore) {
            styleClass = Class.forName('org.gradle.logging.StyledTextOutput$Style')
        }
        
        styleClass.enumConstants
        def styleByName = { String name -> styleClass.enumConstants.find { it.name() == name } }
        
        switch (styling) {
            case Styling.Bold:
                textOutput.withStyle(styleByName('UserInput'))
                break
            case Styling.Green:
                textOutput.withStyle(styleByName('Identifier'))
                break
            case Styling.Yellow:
                textOutput.withStyle(styleByName('Description'))
                break
            case Styling.Red:
                textOutput.withStyle(styleByName('Failure'))
                break
        }
        return this
    }

    def text = { String text ->
        textOutput.text(text)
        return this
    }

    StyledTextService println(String text) {
        textOutput.println(text)
        return this
    }

    static enum Styling {
        Bold, Green, Yellow, Red
    }
}
