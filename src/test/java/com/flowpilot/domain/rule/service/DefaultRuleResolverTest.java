package com.flowpilot.domain.rule.service;

import com.flowpilot.cache.CaffeineRuleCache;
import com.flowpilot.cache.RuleCache;
import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.model.GrayPolicyStatus;
import com.flowpilot.domain.gray.repository.GrayPolicyRepository;
import com.flowpilot.domain.gray.service.GrayRouter;
import com.flowpilot.domain.gray.service.HashGrayRouter;
import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.model.RuleStatus;
import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import com.flowpilot.domain.rule.repository.RuleVersionRepository;
import com.flowpilot.exception.IllegalRuleStateException;
import com.flowpilot.exception.RuleNotFoundException;
import com.flowpilot.exception.RuleVersionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultRuleResolverTest {

    private RuleDefinitionRepository definitionRepository;
    private RuleVersionRepository versionRepository;
    private GrayPolicyRepository grayPolicyRepository;
    private RuleCache ruleCache;
    private DefaultRuleResolver resolver;

    @BeforeEach
    void setUp() {
        definitionRepository = mock(RuleDefinitionRepository.class);
        versionRepository = mock(RuleVersionRepository.class);
        grayPolicyRepository = mock(GrayPolicyRepository.class);
        ruleCache = new CaffeineRuleCache();
        GrayRouter grayRouter = new HashGrayRouter();
        resolver = new DefaultRuleResolver(
                definitionRepository,
                versionRepository,
                grayPolicyRepository,
                grayRouter,
                ruleCache
        );
    }

    @Test
    void resolvesCurrentVersionWhenNoGrayPolicyExists() {
        stubEnabledRule(1);
        when(grayPolicyRepository.findActiveByRuleId(1L)).thenReturn(Optional.empty());
        when(versionRepository.find(1L, 1)).thenReturn(Optional.of(publishedVersion(1)));

        RuleSnapshot snapshot = resolver.resolve("ORDER_FLOW", "user-1");

        assertThat(snapshot.version()).isEqualTo(1);
        assertThat(snapshot.ruleCode()).isEqualTo("ORDER_FLOW");
    }

    @Test
    void resolvesVersionSelectedByStableGrayRoute() {
        stubEnabledRule(1);
        GrayPolicy policy = new GrayPolicy(
                1L, 1L, 1, 2, 10, GrayPolicyStatus.ACTIVE, null, null
        );
        when(grayPolicyRepository.findActiveByRuleId(1L)).thenReturn(Optional.of(policy));

        String grayKey = findKeyRoutedToVersion(2);
        when(versionRepository.find(1L, 2)).thenReturn(Optional.of(publishedVersion(2)));

        RuleSnapshot snapshot = resolver.resolve("ORDER_FLOW", grayKey);

        assertThat(snapshot.version()).isEqualTo(2);
    }

    @Test
    void cacheHitAvoidsRepeatedRuleVersionLoad() {
        stubEnabledRule(1);
        when(grayPolicyRepository.findActiveByRuleId(1L)).thenReturn(Optional.empty());
        when(versionRepository.find(1L, 1)).thenReturn(Optional.of(publishedVersion(1)));

        RuleSnapshot first = resolver.resolve("ORDER_FLOW", "user-1");
        RuleSnapshot second = resolver.resolve("ORDER_FLOW", "user-1");

        assertThat(second).isSameAs(first);
        verify(versionRepository, times(1)).find(1L, 1);
    }

    @Test
    void preloadedCacheAvoidsRuleVersionRepository() {
        stubEnabledRule(1);
        when(grayPolicyRepository.findActiveByRuleId(1L)).thenReturn(Optional.empty());
        RuleSnapshot cached = new RuleSnapshot(
                1L, "ORDER_FLOW", 1, "THEN(cached)", "c".repeat(64)
        );
        ruleCache.put(cached);

        assertThat(resolver.resolve("ORDER_FLOW", "user-1")).isSameAs(cached);
        verify(versionRepository, never()).find(1L, 1);
    }

    @Test
    void failsWhenRuleDoesNotExist() {
        when(definitionRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("MISSING", "user-1"))
                .isInstanceOf(RuleNotFoundException.class)
                .hasMessageContaining("MISSING");
    }

    @Test
    void failsWhenResolvedVersionDoesNotExist() {
        stubEnabledRule(1);
        when(grayPolicyRepository.findActiveByRuleId(1L)).thenReturn(Optional.empty());
        when(versionRepository.find(1L, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("ORDER_FLOW", "user-1"))
                .isInstanceOf(RuleVersionNotFoundException.class)
                .hasMessageContaining("ORDER_FLOW@1");
    }

    @Test
    void rejectsInconsistentGrayPolicyInsteadOfFallingBack() {
        stubEnabledRule(2);
        GrayPolicy stalePolicy = new GrayPolicy(
                1L, 1L, 1, 3, 10, GrayPolicyStatus.ACTIVE, null, null
        );
        when(grayPolicyRepository.findActiveByRuleId(1L)).thenReturn(Optional.of(stalePolicy));

        assertThatThrownBy(() -> resolver.resolve("ORDER_FLOW", "user-1"))
                .isInstanceOf(IllegalRuleStateException.class)
                .hasMessageContaining("base version");
    }

    @Test
    void rejectsDraftVersionInsteadOfExecutingIt() {
        stubEnabledRule(1);
        when(grayPolicyRepository.findActiveByRuleId(1L)).thenReturn(Optional.empty());
        when(versionRepository.find(1L, 1)).thenReturn(Optional.of(new RuleVersion(
                1L, 1L, 1, "THEN(userCheck)", RuleVersionStatus.DRAFT,
                "a".repeat(64), "tester", null, null
        )));

        assertThatThrownBy(() -> resolver.resolve("ORDER_FLOW", "user-1"))
                .isInstanceOf(IllegalRuleStateException.class)
                .hasMessageContaining("not published");
    }

    private void stubEnabledRule(int currentVersion) {
        when(definitionRepository.findByCode("ORDER_FLOW")).thenReturn(Optional.of(
                new RuleDefinition(
                        1L, "ORDER_FLOW", "Order flow", currentVersion,
                        RuleStatus.ENABLED, null, null, null
                )
        ));
    }

    private static RuleVersion publishedVersion(int version) {
        return new RuleVersion(
                (long) version,
                1L,
                version,
                "THEN(userCheck,stockCheck,createOrder)",
                RuleVersionStatus.PUBLISHED,
                String.valueOf(version).repeat(64),
                "tester",
                null,
                null
        );
    }

    private static String findKeyRoutedToVersion(int targetVersion) {
        HashGrayRouter router = new HashGrayRouter();
        return java.util.stream.IntStream.range(0, 1_000)
                .mapToObj(index -> "gray-user-" + index)
                .filter(key -> router.route(key, 1, 2, 10) == targetVersion)
                .findFirst()
                .orElseThrow();
    }
}
