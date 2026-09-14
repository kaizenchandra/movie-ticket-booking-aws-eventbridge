package com.kaizenchandra.awseventbridgedemo;

import org.junit.jupiter.api.Test;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

class ArchitectureTest {
    @Test
    void domainIsFrameworkIndependent() {
        var c = new ClassFileImporter().importPackages("com.kaizenchandra.awseventbridgedemo");
        noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "software.amazon..", "reactor..", "..application..", "..adapter..", "..bootstrap..").check(c);
    }

    @Test
    void applicationOnlyDependsInward() {
        var c = new ClassFileImporter().importPackages("com.kaizenchandra.awseventbridgedemo");
        noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..bootstrap..", "org.springframework..", "jakarta..", "software.amazon..", "reactor..").check(c);
    }

    @Test
    void persistenceEntitiesStayOutsideDomain() {
        var c = new ClassFileImporter().importPackages("com.kaizenchandra.awseventbridgedemo");
        classes().that().areAnnotatedWith(jakarta.persistence.Entity.class).should().resideInAPackage("..adapter.out..").check(c);
    }
    @Test
    void adaptersDoNotDependOnBootstrap() {
        var classes=new ClassFileImporter().importPackages("com.kaizenchandra.awseventbridgedemo");
        noClasses().that().resideInAPackage("..adapter..").should().dependOnClassesThat().resideInAPackage("..bootstrap..").check(classes);
    }
}
