package com.netflix.nebula.lint;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.codenarc.analyzer.SourceAnalyzer;
import org.codenarc.analyzer.StringSourceAnalyzer;
import org.codenarc.results.Results;
import org.codenarc.ruleregistry.RuleRegistryInitializer;
import org.codenarc.ruleset.CompositeRuleSet;
import org.codenarc.ruleset.PropertiesFileRuleSetConfigurer;
import org.codenarc.ruleset.RuleSetUtil;

public class GradleLint {
    public static void main(String[] args) throws IOException {
        new RuleRegistryInitializer().initializeRuleRegistry();

        CompositeRuleSet ruleSet = new CompositeRuleSet();
        ruleSet.addRuleSet(RuleSetUtil.loadRuleSetFile("rulesets/basic.xml"));
        ruleSet.addRuleSet(RuleSetUtil.loadRuleSetFile("rulesets/unnecessary.xml"));
        ruleSet.addRuleSet(RuleSetUtil.loadRuleSetFile("rulesets/gradle.xml"));

        new PropertiesFileRuleSetConfigurer().configure(ruleSet);

        String source = new String(Files.readAllBytes(new File("build.gradle").toPath()));

        SourceAnalyzer sourceAnalyzer = new StringSourceAnalyzer(source);
        Results results = sourceAnalyzer.analyze(ruleSet);

        results.getViolations().forEach(System.out::println);
    }
}
