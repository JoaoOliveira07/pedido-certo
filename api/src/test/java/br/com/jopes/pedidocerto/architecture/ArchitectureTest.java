package br.com.jopes.pedidocerto.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * As regras da arquitetura hexagonal, verificadas a cada build. Se alguém fizer o domínio
 * depender do Spring ou um caso de uso chamar um repositório JDBC direto, o CI quebra.
 */
@AnalyzeClasses(packages = "br.com.jopes.pedidocerto", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPlainJava = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "com.fasterxml..",
                    "..application..", "..adapter..", "..config..")
            .because("o domínio é Java puro, testável sem framework");

    @ArchTest
    static final ArchRule applicationIgnoresTechnicalDetails = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "..config..",
                    "org.springframework.web..", "org.springframework.jdbc..", "jakarta.servlet..",
                    "com.fasterxml..")
            .because("casos de uso falam com o mundo só através das portas");

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Adapters").definedBy("..adapter..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Config")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "Config");

    @ArchTest
    static final ArchRule adaptersDoNotTalkToEachOther = slices()
            .matching("..adapter.(*).(*)..")
            .should().notDependOnEachOther()
            .because("um adapter não deve saber como o outro funciona; a conversa passa pela aplicação");
}
