package com.etheric.service;

import com.etheric.entity.UserConsent;
import com.etheric.model.ConsentData;
import com.etheric.repository.ConsentRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.etheric.testsupport.TestSupport.await;
import static com.etheric.testsupport.TestSupport.awaitVoid;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ConsentServiceTest {

    private static final String CLIENT_ID = "test-client";
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Inject
    ConsentService consentService;

    @Inject
    ConsentRepository consentRepository;

    @Inject
    CacheService cacheService;

    @BeforeEach
    void clearConsent() {
        awaitVoid(() -> consentRepository.delete(USER_ID, CLIENT_ID));
        awaitVoid(cacheService.deleteConsent(USER_ID.toString(), CLIENT_ID));
    }

    @Test
    void saveConsent_persistsAndCaches() {
        awaitVoid(() -> consentService.saveConsent(USER_ID.toString(), CLIENT_ID, List.of("openid", "profile")));

        ConsentData cached = await(cacheService.getConsent(USER_ID.toString(), CLIENT_ID));
        assertNotNull(cached);
        assertTrue(cached.getScopes().containsAll(List.of("openid", "profile")));

        ConsentData loaded = await(consentService.getConsent(USER_ID.toString(), CLIENT_ID));
        assertNotNull(loaded);
        assertTrue(loaded.getScopes().containsAll(List.of("openid", "profile")));
        assertTrue(loaded.getGrantedAt() > 0);
    }

    @Test
    void getConsent_loadsFromDatabaseOnCacheMiss() {
        awaitVoid(() -> consentRepository.upsertMergeScopes(USER_ID, CLIENT_ID, List.of("openid", "email")));

        ConsentData loaded = await(consentService.getConsent(USER_ID.toString(), CLIENT_ID));
        assertNotNull(loaded);
        assertTrue(loaded.getScopes().containsAll(List.of("openid", "email")));
        assertNotNull(await(cacheService.getConsent(USER_ID.toString(), CLIENT_ID)));
    }

    @Test
    void saveConsent_mergesScopesWithExisting() {
        awaitVoid(() -> consentService.saveConsent(USER_ID.toString(), CLIENT_ID, List.of("openid", "profile")));
        awaitVoid(() -> consentService.saveConsent(USER_ID.toString(), CLIENT_ID, List.of("profile", "email")));

        ConsentData loaded = await(consentService.getConsent(USER_ID.toString(), CLIENT_ID));
        assertNotNull(loaded);
        assertTrue(loaded.getScopes().containsAll(List.of("openid", "profile", "email")));
        assertEquals(3, loaded.getScopes().size());

        UserConsent entity = await(() -> consentRepository.find(USER_ID, CLIENT_ID));
        assertNotNull(entity);
        assertTrue(entity.getScopes().containsAll(List.of("openid", "profile", "email")));
        assertEquals(3, entity.getScopes().size());
    }

    @Test
    void getConsent_returnsNullWhenMissing() {
        assertNull(await(consentService.getConsent(USER_ID.toString(), CLIENT_ID)));
    }
}
