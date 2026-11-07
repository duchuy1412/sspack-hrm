package vn.sspack.hrm.platform.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.sspack.hrm.platform.domain.auth.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();

    List<Permission> findByCodeIn(Collection<String> codes);
}
