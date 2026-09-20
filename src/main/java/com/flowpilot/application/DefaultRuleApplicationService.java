package com.flowpilot.application;

import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleStatus;
import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import com.flowpilot.domain.rule.repository.RuleVersionRepository;
import com.flowpilot.exception.IllegalRuleStateException;
import com.flowpilot.exception.RuleNotFoundException;
import com.flowpilot.infrastructure.util.ChecksumUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class DefaultRuleApplicationService implements RuleApplicationService {

    private final RuleDefinitionRepository definitionRepository;
    private final RuleVersionRepository versionRepository;

    public DefaultRuleApplicationService(
            RuleDefinitionRepository definitionRepository,
            RuleVersionRepository versionRepository
    ) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
    }

    @Override
    @Transactional
    public Long createRule(CreateRuleCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (definitionRepository.findByCode(command.ruleCode()).isPresent()) {
            throw new IllegalRuleStateException("Rule already exists: " + command.ruleCode());
        }
        try {
            RuleDefinition saved = definitionRepository.save(new RuleDefinition(
                    null, command.ruleCode(), command.ruleName(), null,
                    RuleStatus.ENABLED, command.description(), null, null));
            return saved.id();
        } catch (DuplicateKeyException exception) {
            throw new IllegalRuleStateException("Rule already exists: " + command.ruleCode());
        }
    }

    @Override
    @Transactional
    public Integer createVersion(String ruleCode, CreateRuleVersionCommand command) {
        requireText(ruleCode, "ruleCode");
        Objects.requireNonNull(command, "command must not be null");
        RuleDefinition definition = definitionRepository.findByCodeForUpdate(ruleCode)
                .orElseThrow(() -> new RuleNotFoundException(ruleCode));
        int nextVersion = versionRepository.findAll(definition.id()).stream()
                .map(RuleVersion::version)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
        RuleVersion saved = versionRepository.save(new RuleVersion(
                null, definition.id(), nextVersion, command.ruleContent(),
                RuleVersionStatus.DRAFT, ChecksumUtils.sha256(command.ruleContent()),
                command.createdBy(), null, null));
        return saved.version();
    }

    @Override
    @Transactional(readOnly = true)
    public RuleDetailResponse getRule(String ruleCode) {
        requireText(ruleCode, "ruleCode");
        RuleDefinition definition = definitionRepository.findByCode(ruleCode)
                .orElseThrow(() -> new RuleNotFoundException(ruleCode));
        return RuleDetailResponse.from(definition);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleVersionResponse> listVersions(String ruleCode) {
        requireText(ruleCode, "ruleCode");
        RuleDefinition definition = definitionRepository.findByCode(ruleCode)
                .orElseThrow(() -> new RuleNotFoundException(ruleCode));
        return versionRepository.findAll(definition.id()).stream()
                .sorted(Comparator.comparing(RuleVersion::version))
                .map(RuleVersionResponse::from)
                .toList();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
