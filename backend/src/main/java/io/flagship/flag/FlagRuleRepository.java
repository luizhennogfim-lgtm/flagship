package io.flagship.flag;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagRuleRepository extends JpaRepository<FlagRule, UUID> {

    List<FlagRule> findByFlagConfigIdOrderByPriorityAsc(UUID flagConfigId);
}
