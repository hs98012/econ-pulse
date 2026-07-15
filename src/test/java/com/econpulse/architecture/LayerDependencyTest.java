package com.econpulse.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.econpulse.news.application.port.NewsProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.econpulse", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

    @ArchTest
    static final ArchRule apiDoesNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnApi = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..api..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule pureMappingMatcherDoesNotDependOnFrameworkOrApplicationLayers = noClasses()
            .that()
            .resideInAPackage("..mapping.domain..")
            .and()
            .haveNameMatching(".*\\.(TermNewsMatcher|TermMatch.*|NewsMatchContent|MatchedField)")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "..application..",
                    "..infrastructure.."
            );

    @ArchTest
    static final ArchRule applicationPortsDoNotDependOnSpringOrHttpClients = noClasses()
            .that()
            .resideInAPackage("..application.port..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "org.springframework.web.client..",
                    "org.springframework.web.reactive.function.client..",
                    "feign..",
                    "okhttp3..",
                    "java.net.http.."
            );

    @ArchTest
    static final ArchRule applicationAndDomainDoNotDependOnHttpClientTypes = noClasses()
            .that()
            .resideInAnyPackage("..application..", "..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.web.client..",
                    "org.springframework.http.client..",
                    "feign..",
                    "okhttp3.."
            );

    @ArchTest
    static final ArchRule newsApplicationDoesNotDependOnProviderImplementations = noClasses()
            .that()
            .resideInAPackage("..news.application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..news.infrastructure.provider..");

    @ArchTest
    static final ArchRule autoMappingDoesNotDependOnApiProvidersPopularOrSchedulers = noClasses()
            .that()
            .haveSimpleName("TermNewsAutoMappingService")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..api..",
                    "..news.application.port..",
                    "..news.infrastructure.provider..",
                    "..popular..",
                    "org.springframework.scheduling.."
            );

    @ArchTest
    static final ArchRule newsInfrastructureProvidersImplementNewsProviderPort = classes()
            .that()
            .resideInAPackage("..news.infrastructure.provider..")
            .and()
            .haveSimpleNameEndingWith("Provider")
            .should()
            .beAssignableTo(NewsProvider.class);

    @ArchTest
    static final ArchRule controllersDoNotDependOnRepositories = noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .or()
            .areAnnotatedWith(Controller.class)
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule mappingRebuildControllerDependsOnNoMappingInternals = noClasses()
            .that()
            .haveSimpleName("MappingRebuildController")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..infrastructure..",
                    "..domain..",
                    "..news.application.port.."
            );

    @ArchTest
    static final ArchRule apiMayDependOnApplication = classes()
            .that()
            .resideInAPackage("..api..")
            .should()
            .onlyDependOnClassesThat()
            .resideOutsideOfPackage("..infrastructure..");
}
