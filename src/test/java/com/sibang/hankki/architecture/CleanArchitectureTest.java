package com.sibang.hankki.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.sibang.hankki", importOptions = DoNotIncludeTests.class)
class CleanArchitectureTest {
    private static final SliceAssignment RESERVATION_LAYER = new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String packageName = javaClass.getPackageName();
            if (packageName.startsWith("com.sibang.hankki.reservation.adapter.in.")) {
                return SliceIdentifier.of("adapter", "in");
            }
            if (packageName.startsWith("com.sibang.hankki.reservation.adapter.out.")) {
                return SliceIdentifier.of("adapter", "out");
            }
            if (packageName.startsWith("com.sibang.hankki.reservation.application.")) {
                return SliceIdentifier.of("application");
            }
            if (packageName.startsWith("com.sibang.hankki.reservation.domain.")) {
                return SliceIdentifier.of("domain");
            }
            return SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
            return "reservation clean-architecture layers";
        }
    };

    private static final SliceAssignment RESTAURANT_LAYER = new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String packageName = javaClass.getPackageName();
            if (packageName.startsWith("com.sibang.hankki.restaurant.adapter.in.")) {
                return SliceIdentifier.of("adapter", "in");
            }
            if (packageName.startsWith("com.sibang.hankki.restaurant.adapter.out.")) {
                return SliceIdentifier.of("adapter", "out");
            }
            if (packageName.startsWith("com.sibang.hankki.restaurant.application.")) {
                return SliceIdentifier.of("application");
            }
            if (packageName.startsWith("com.sibang.hankki.restaurant.domain.")) {
                return SliceIdentifier.of("domain");
            }
            return SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
            return "restaurant clean-architecture layers";
        }
    };

    @ArchTest
    static final ArchRule restaurantDomainDoesNotDependOnApplicationOrAdapters = noClasses()
            .that().resideInAnyPackage("..restaurant.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..restaurant.application..", "..restaurant.adapter..");

    @ArchTest
    static final ArchRule restaurantDomainIsFrameworkIndependent = noClasses()
            .that().resideInAnyPackage("..restaurant.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.springframework.web..");

    @ArchTest
    static final ArchRule restaurantApplicationDoesNotDependOnInboundAdapters = noClasses()
            .that().resideInAnyPackage("..restaurant.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..restaurant.adapter.in.web..", "..restaurant.adapter.in.scheduling..");

    @ArchTest
    static final ArchRule restaurantApplicationDoesNotDependOnOutboundAdapters = noClasses()
            .that().resideInAnyPackage("..restaurant.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..restaurant.adapter.out..");

    @ArchTest
    static final ArchRule restaurantApplicationDoesNotDependOnPersistenceFrameworks = noClasses()
            .that().resideInAnyPackage("..restaurant.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.springframework.data..", "org.springframework.orm.jpa..", "org.hibernate..");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotDependOnApplicationServices = noClasses()
            .that().resideInAnyPackage("..restaurant.adapter.in..")
            .should().dependOnClassesThat().areAnnotatedWith(Service.class);

    @ArchTest
    static final ArchRule restaurantApplicationDoesNotDependOnSpringHttpOrWeb = noClasses()
            .that().resideInAnyPackage("..restaurant.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.http..", "org.springframework.web..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotDependOnOutboundAdapters = noClasses()
            .that().resideInAnyPackage("..restaurant.adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage("..restaurant.adapter.out..");

    @ArchTest
    static final ArchRule persistenceDoesNotDependOnWebAdapters = noClasses()
            .that().resideInAnyPackage("..restaurant.adapter.out.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("..restaurant.adapter.in.web..");

    @ArchTest
    static final ArchRule noLayerDependsOnWebControllers = noClasses()
            .that().resideInAnyPackage(
                    "..restaurant.domain..", "..restaurant.application..", "..restaurant.adapter.in.scheduling..",
                    "..restaurant.adapter.out.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("..restaurant.adapter.in.web..");

    @ArchTest
    static final ArchRule controllersAreOnlyInboundWebAdapters = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAnyPackage("..adapter.in.web..");

    @ArchTest
    static final ArchRule controllerAdviceIsOnlyInInboundWebAdapters = classes()
            .that().areAnnotatedWith(RestControllerAdvice.class)
            .should().resideInAnyPackage("..adapter.in.web..");

    @ArchTest
    static final ArchRule exceptionHandlersAreOnlyInInboundWebAdapters = methods()
            .that().areAnnotatedWith(ExceptionHandler.class)
            .should().beDeclaredInClassesThat().resideInAnyPackage("..adapter.in.web..");

    @ArchTest
    static final ArchRule scheduledMethodsAreOnlyInboundSchedulingAdapters = methods()
            .that().areAnnotatedWith(Scheduled.class)
            .should().beDeclaredInClassesThat().resideInAnyPackage("..adapter.in.scheduling..");

    @ArchTest
    static final ArchRule jpaEntitiesAreOnlyPersistenceEntities = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAnyPackage("..adapter.out.persistence.entity..");

    @ArchTest
    static final ArchRule repositoriesAreOnlyPersistenceRepositories = classes()
            .that().areAnnotatedWith(Repository.class)
            .or().areAssignableTo(JpaRepository.class)
            .should().resideInAnyPackage("..adapter.out.persistence.repository..");

    @ArchTest
    static final ArchRule reservationDomainDoesNotDependOnApplicationOrAdapters = noClasses()
            .that().resideInAnyPackage("..reservation.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..reservation.application..", "..reservation.adapter..");

    @ArchTest
    static final ArchRule reservationDomainIsFrameworkIndependent = noClasses()
            .that().resideInAnyPackage("..reservation.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..");

    @ArchTest
    static final ArchRule reservationApplicationDoesNotDependOnAdaptersOrPersistenceFrameworks = noClasses()
            .that().resideInAnyPackage("..reservation.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..reservation.adapter..", "jakarta.persistence..", "org.springframework.data..",
                    "org.springframework.orm.jpa..", "org.hibernate..");

    @ArchTest
    static final ArchRule reservationJpaEntitiesAreOnlyPersistenceEntities = classes()
            .that().resideInAnyPackage("..reservation..")
            .and().areAnnotatedWith(Entity.class)
            .should().resideInAnyPackage("..reservation.adapter.out.persistence.entity..");

    @ArchTest
    static final ArchRule reservationRepositoriesAreOnlyPersistenceRepositories = classes()
            .that().resideInAnyPackage("..reservation..")
            .and().areAssignableTo(JpaRepository.class)
            .should().resideInAnyPackage("..reservation.adapter.out.persistence.repository..");

    @ArchTest
    static final ArchRule reservationPackagesAreFreeOfCycles = slices()
            .assignedFrom(RESERVATION_LAYER)
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule restaurantPackagesAreFreeOfCycles = slices()
            .assignedFrom(RESTAURANT_LAYER)
            .should().beFreeOfCycles();
}
