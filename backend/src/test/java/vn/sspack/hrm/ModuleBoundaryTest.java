package vn.sspack.hrm;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import org.junit.jupiter.api.Test;

/**
 * Ép quy tắc modular monolith trong CLAUDE.md: một module KHÔNG dùng {@code domain} (entity) hay
 * {@code infrastructure} (repository) của module khác; chỉ gọi qua {@code api}/{@code application}
 * hoặc sự kiện.
 */
class ModuleBoundaryTest {

    static final String ROOT = "vn.sspack.hrm";

    static final List<String> MODULES = List.of(
            "platform", "org", "employee", "leave", "attendance", "payroll", "recruitment", "dashboard");

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void modulesDoNotTouchOtherModulesInternals() {
        List<ArchRule> rules = MODULES.stream()
                .map(module -> noClasses()
                        .that().resideInAPackage(ROOT + "." + module + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(internalsOfOtherModules(module))
                        .allowEmptyShould(true)
                        .because("module " + module + " chỉ được gọi module khác qua api/application hoặc sự kiện"))
                .map(ArchRule.class::cast)
                .toList();

        CompositeArchRule.of(rules).check(CLASSES);
    }

    @Test
    void sharedDoesNotDependOnBusinessModules() {
        noClasses()
                .that().resideInAPackage(ROOT + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        MODULES.stream().map(m -> ROOT + "." + m + "..").toArray(String[]::new))
                .allowEmptyShould(true)
                .because("shared không chứa nghiệp vụ và không phụ thuộc module nghiệp vụ")
                .check(CLASSES);
    }

    private static String[] internalsOfOtherModules(String module) {
        return MODULES.stream()
                .filter(other -> !other.equals(module))
                .flatMap(other -> java.util.stream.Stream.of(
                        ROOT + "." + other + ".domain..",
                        ROOT + "." + other + ".infrastructure.."))
                .toArray(String[]::new);
    }
}
