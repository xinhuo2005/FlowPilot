package com.flowpilot.application;

import com.flowpilot.cache.RuleCache;
import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.repository.GrayPolicyRepository;
import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import com.flowpilot.domain.rule.repository.RuleVersionRepository;
import com.flowpilot.domain.rule.service.RuleResolver;
import com.flowpilot.exception.IllegalRuleStateException;
import com.flowpilot.exception.RuleValidationException;
import com.flowpilot.infrastructure.util.ChecksumUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PhaseFourLifecycleIntegrationTest {

    private static final AtomicInteger RULE_SEQUENCE = new AtomicInteger();
    private static final String VERSION_ONE_CONTENT =
            "THEN(userCheck, stockCheck, createOrder)";
    private static final String VERSION_TWO_CONTENT =
            "THEN(userCheck, riskCheck, createOrder)";

    @Autowired
    private RuleApplicationService ruleApplicationService;

    @Autowired
    private PublishApplicationService publishApplicationService;

    @Autowired
    private GrayApplicationService grayApplicationService;

    @Autowired
    private RuleDefinitionRepository definitionRepository;

    @Autowired
    private RuleVersionRepository versionRepository;

    @Autowired
    private GrayPolicyRepository grayPolicyRepository;

    @Autowired
    private RuleCache ruleCache;

    @Autowired
    private RuleResolver ruleResolver;

    @Test
    void shouldCreateRuleAndIncrementDraftVersions() {
        String ruleCode = newRuleCode();

        Long ruleId = ruleApplicationService.createRule(
                new CreateRuleCommand(ruleCode, "Order flow", "phase four"));
        int versionOne = ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand(VERSION_ONE_CONTENT, "tester"));
        int versionTwo = ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand(VERSION_TWO_CONTENT, "tester"));

        assertThat(ruleId).isPositive();
        assertThat(versionOne).isEqualTo(1);
        assertThat(versionTwo).isEqualTo(2);
        List<RuleVersion> versions = versionRepository.findAll(ruleId);
        assertThat(versions).extracting(RuleVersion::status)
                .containsExactly(RuleVersionStatus.DRAFT, RuleVersionStatus.DRAFT);
        assertThat(versions.getFirst().checksum())
                .isEqualTo(ChecksumUtils.sha256(VERSION_ONE_CONTENT));
    }

    @Test
    void shouldPublishFirstVersionAndEvictCachedSnapshots() {
        RuleFixture fixture = createRuleWithVersions(1);
        ruleCache.put(new RuleSnapshot(
                fixture.ruleId(), fixture.ruleCode(), 1,
                VERSION_ONE_CONTENT, ChecksumUtils.sha256(VERSION_ONE_CONTENT)));

        publishApplicationService.publish(fixture.ruleCode(), 1);

        assertThat(currentVersion(fixture.ruleCode())).isEqualTo(1);
        assertThat(version(fixture, 1).status()).isEqualTo(RuleVersionStatus.PUBLISHED);
        assertThat(version(fixture, 1).publishedAt()).isNotNull();
        assertThat(ruleCache.get(fixture.ruleCode(), 1)).isNull();
    }

    @Test
    void shouldPublishNewVersionAndArchiveThePreviousOne() {
        RuleFixture fixture = createRuleWithVersions(2);
        publishApplicationService.publish(fixture.ruleCode(), 1);

        publishApplicationService.publish(fixture.ruleCode(), 2);

        assertThat(currentVersion(fixture.ruleCode())).isEqualTo(2);
        assertThat(version(fixture, 1).status()).isEqualTo(RuleVersionStatus.ARCHIVED);
        assertThat(version(fixture, 2).status()).isEqualTo(RuleVersionStatus.PUBLISHED);
    }

    @Test
    void shouldRejectRepeatedPublication() {
        RuleFixture fixture = createRuleWithVersions(1);
        publishApplicationService.publish(fixture.ruleCode(), 1);

        assertThatThrownBy(() -> publishApplicationService.publish(fixture.ruleCode(), 1))
                .isInstanceOf(IllegalRuleStateException.class);

        assertThat(currentVersion(fixture.ruleCode())).isEqualTo(1);
        assertThat(version(fixture, 1).status()).isEqualTo(RuleVersionStatus.PUBLISHED);
    }

    @Test
    void shouldRollbackWithoutDeletingVersionHistory() {
        RuleFixture fixture = createRuleWithVersions(2);
        publishApplicationService.publish(fixture.ruleCode(), 1);
        publishApplicationService.publish(fixture.ruleCode(), 2);

        publishApplicationService.rollback(fixture.ruleCode(), 1);

        assertThat(currentVersion(fixture.ruleCode())).isEqualTo(1);
        assertThat(version(fixture, 1).status()).isEqualTo(RuleVersionStatus.PUBLISHED);
        assertThat(version(fixture, 2).status()).isEqualTo(RuleVersionStatus.ARCHIVED);
        assertThat(versionRepository.findAll(fixture.ruleId())).hasSize(2);
    }

    @Test
    void shouldLeaveDatabaseUnchangedWhenValidationFails() {
        String ruleCode = newRuleCode();
        Long ruleId = ruleApplicationService.createRule(
                new CreateRuleCommand(ruleCode, "Invalid flow", null));
        ruleApplicationService.createVersion(
                ruleCode, new CreateRuleVersionCommand("THEN(userCheck, missingComponent)"));

        assertThatThrownBy(() -> publishApplicationService.publish(ruleCode, 1))
                .isInstanceOf(RuleValidationException.class);

        assertThat(currentVersion(ruleCode)).isNull();
        assertThat(versionRepository.find(ruleId, 1).orElseThrow().status())
                .isEqualTo(RuleVersionStatus.DRAFT);
    }

    @Test
    void shouldAllowOnlyOneConcurrentPublication() throws Exception {
        RuleFixture fixture = createRuleWithVersions(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(
                    () -> attemptPublish(fixture.ruleCode(), ready, start));
            Future<Boolean> second = executor.submit(
                    () -> attemptPublish(fixture.ruleCode(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(currentVersion(fixture.ruleCode())).isEqualTo(1);
        assertThat(version(fixture, 1).status()).isEqualTo(RuleVersionStatus.PUBLISHED);
    }

    @Test
    void shouldStartUpdateAndStopGrayRelease() {
        RuleFixture fixture = publishedRuleWithDraftVersion();

        grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(2, 10));

        assertThat(currentVersion(fixture.ruleCode())).isEqualTo(1);
        assertThat(version(fixture, 1).status()).isEqualTo(RuleVersionStatus.PUBLISHED);
        assertThat(version(fixture, 2).status()).isEqualTo(RuleVersionStatus.PUBLISHED);
        assertThat(grayApplicationService.getPolicy(fixture.ruleCode()).percentage()).isEqualTo(10);

        grayApplicationService.updatePercentage(fixture.ruleCode(), 30);
        assertThat(grayApplicationService.getPolicy(fixture.ruleCode()).percentage()).isEqualTo(30);

        grayApplicationService.stopGray(fixture.ruleCode());
        assertThat(currentVersion(fixture.ruleCode())).isEqualTo(1);
        assertThat(version(fixture, 2).status()).isEqualTo(RuleVersionStatus.ARCHIVED);
        assertThat(grayPolicyRepository.findActiveByRuleId(fixture.ruleId())).isEmpty();
    }

    @Test
    void shouldPromoteGrayVersionToStableVersion() {
        RuleFixture fixture = publishedRuleWithDraftVersion();
        grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(2, 50));

        grayApplicationService.promote(fixture.ruleCode());

        assertThat(currentVersion(fixture.ruleCode())).isEqualTo(2);
        assertThat(version(fixture, 1).status()).isEqualTo(RuleVersionStatus.ARCHIVED);
        assertThat(version(fixture, 2).status()).isEqualTo(RuleVersionStatus.PUBLISHED);
        assertThat(grayPolicyRepository.findActiveByRuleId(fixture.ruleId())).isEmpty();
    }

    @Test
    void shouldRejectASecondActiveGrayRelease() {
        RuleFixture fixture = createRuleWithVersions(3);
        publishApplicationService.publish(fixture.ruleCode(), 1);
        grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(2, 10));

        assertThatThrownBy(() -> grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(3, 20)))
                .isInstanceOf(IllegalRuleStateException.class);

        GrayPolicy active = grayApplicationService.getPolicy(fixture.ruleCode());
        assertThat(active.grayVersion()).isEqualTo(2);
        assertThat(version(fixture, 3).status()).isEqualTo(RuleVersionStatus.DRAFT);
    }

    @Test
    void shouldReuseDisabledPolicyForANewGrayVersion() {
        RuleFixture fixture = createRuleWithVersions(3);
        publishApplicationService.publish(fixture.ruleCode(), 1);
        grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(2, 10));
        grayApplicationService.stopGray(fixture.ruleCode());

        grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(3, 20));

        GrayPolicy active = grayApplicationService.getPolicy(fixture.ruleCode());
        assertThat(active.baseVersion()).isEqualTo(1);
        assertThat(active.grayVersion()).isEqualTo(3);
        assertThat(active.percentage()).isEqualTo(20);
        assertThat(version(fixture, 2).status()).isEqualTo(RuleVersionStatus.ARCHIVED);
        assertThat(version(fixture, 3).status()).isEqualTo(RuleVersionStatus.PUBLISHED);
    }

    @Test
    void shouldRejectFullPublicationWhileGrayIsActive() {
        RuleFixture fixture = createRuleWithVersions(3);
        publishApplicationService.publish(fixture.ruleCode(), 1);
        grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(2, 10));

        assertThatThrownBy(() -> publishApplicationService.publish(fixture.ruleCode(), 3))
                .isInstanceOf(IllegalRuleStateException.class)
                .hasMessageContaining("active gray policy");

        assertThat(currentVersion(fixture.ruleCode())).isEqualTo(1);
        assertThat(version(fixture, 3).status()).isEqualTo(RuleVersionStatus.DRAFT);
    }

    @Test
    void shouldResolveStableAndGraySnapshotsFromPersistedLifecycleState() {
        RuleFixture fixture = publishedRuleWithDraftVersion();

        assertThat(ruleResolver.resolve(fixture.ruleCode(), "stable-key").version()).isEqualTo(1);

        grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(2, 99));
        assertThat(ruleResolver.resolve(fixture.ruleCode(), grayRoutingKey()).version()).isEqualTo(2);

        grayApplicationService.stopGray(fixture.ruleCode());
        assertThat(ruleResolver.resolve(fixture.ruleCode(), grayRoutingKey()).version()).isEqualTo(1);
    }

    @Test
    void shouldReserveZeroForStopAndOneHundredForPromote() {
        RuleFixture fixture = publishedRuleWithDraftVersion();

        assertThatThrownBy(() -> grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(2, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> grayApplicationService.startGray(
                fixture.ruleCode(), new StartGrayCommand(2, 100)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(version(fixture, 2).status()).isEqualTo(RuleVersionStatus.DRAFT);
        assertThat(grayPolicyRepository.findActiveByRuleId(fixture.ruleId())).isEmpty();
    }

    private boolean attemptPublish(
            String ruleCode,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            publishApplicationService.publish(ruleCode, 1);
            return true;
        } catch (IllegalRuleStateException exception) {
            return false;
        }
    }

    private RuleFixture publishedRuleWithDraftVersion() {
        RuleFixture fixture = createRuleWithVersions(2);
        publishApplicationService.publish(fixture.ruleCode(), 1);
        return fixture;
    }

    private RuleFixture createRuleWithVersions(int count) {
        String ruleCode = newRuleCode();
        Long ruleId = ruleApplicationService.createRule(
                new CreateRuleCommand(ruleCode, "Order flow", null));
        for (int version = 1; version <= count; version++) {
            String content = version == 1 ? VERSION_ONE_CONTENT : VERSION_TWO_CONTENT;
            ruleApplicationService.createVersion(
                    ruleCode, new CreateRuleVersionCommand(content));
        }
        return new RuleFixture(ruleCode, ruleId);
    }

    private Integer currentVersion(String ruleCode) {
        return definitionRepository.findByCode(ruleCode)
                .orElseThrow()
                .currentVersion();
    }

    private RuleVersion version(RuleFixture fixture, int version) {
        return versionRepository.find(fixture.ruleId(), version).orElseThrow();
    }

    private String newRuleCode() {
        return "PHASE4_RULE_" + RULE_SEQUENCE.incrementAndGet();
    }

    private String grayRoutingKey() {
        for (int index = 0; index < 1_000; index++) {
            String candidate = "gray-key-" + index;
            if (Math.floorMod(candidate.hashCode(), 100) < 99) {
                return candidate;
            }
        }
        throw new IllegalStateException("unable to find gray routing key");
    }

    private record RuleFixture(String ruleCode, Long ruleId) {
    }
}
