package vn.sspack.hrm;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import vn.sspack.hrm.platform.application.audit.AuditLogService;
import vn.sspack.hrm.shared.audit.NoAudit;

/**
 * PLT-04 "mọi thao tác thêm/sửa/xóa đều có nhật ký": mọi method {@code @Transactional} ghi dữ liệu trong tầng
 * {@code application} phải gọi {@link AuditLogService#record} (trực tiếp hoặc qua method khác của cùng lớp)
 * hoặc mang {@link NoAudit} kèm lý do.
 *
 * <p>Thao tác chạy qua {@code TransactionTemplate} (đăng nhập, làm mới phiên) không nằm trong phạm vi quét;
 * chúng tự ghi audit và có test tích hợp riêng.
 */
class AuditCoverageTest {

    static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("vn.sspack.hrm");

    static final DescribedPredicate<JavaMethod> WRITE_TRANSACTION =
            DescribedPredicate.describe("là transaction ghi dữ liệu", method -> {
                if (!method.isAnnotatedWith(Transactional.class)) {
                    return false;
                }
                Object readOnly = method.getAnnotationOfType(Transactional.class).readOnly();
                return !Boolean.TRUE.equals(readOnly);
            });

    static final ArchCondition<JavaMethod> RECORD_AUDIT_OR_EXPLAIN =
            new ArchCondition<>("gọi AuditLogService.record hoặc có @NoAudit") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean ok = method.isAnnotatedWith(NoAudit.class) || reachesAudit(method, new HashSet<>());
                    if (!ok) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                + " ghi dữ liệu nhưng không ghi audit log; gọi AuditLogService.record "
                                + "hoặc thêm @NoAudit(\"lý do\")"));
                    }
                }
            };

    static final ArchRule RULE = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("vn.sspack.hrm..application..")
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(AuditLogService.class.getName())
            .and(WRITE_TRANSACTION)
            .should(RECORD_AUDIT_OR_EXPLAIN)
            .allowEmptyShould(true);

    /** Theo các lời gọi tới method khác của cùng lớp (helper private, lambda) cho tới khi gặp record. */
    static boolean reachesAudit(JavaMethod method, Set<JavaMethod> visited) {
        if (!visited.add(method)) {
            return false;
        }
        JavaClass owner = method.getOwner();
        for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
            if (call.getTargetOwner().isEquivalentTo(AuditLogService.class)
                    && call.getName().equals("record")) {
                return true;
            }
            if (call.getTargetOwner().equals(owner)) {
                for (JavaMethod target : call.getTarget().resolveMember().stream().toList()) {
                    if (reachesAudit(target, visited)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void everyWriteTransactionRecordsAudit() {
        RULE.check(PRODUCTION);
    }

    @Test
    void ruleIsNotVacuous() {
        JavaClasses fixtures = new ClassFileImporter().importClasses(
                vn.sspack.hrm.archfixture.application.AuditFixtureService.class);

        EvaluationResult result = RULE.evaluate(fixtures);

        assertThat(result.getFailureReport().getDetails())
                .hasSize(1)
                .first().asString().contains("forgotAudit");
    }
}
