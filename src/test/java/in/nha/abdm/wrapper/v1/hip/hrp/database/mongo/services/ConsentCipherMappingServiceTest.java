/* (C) 2024 */
package in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mongodb.client.MongoClients;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.ConsentCipherMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Needs a real MongoDB: set VERIFY_MONGO_URI (for example mongodb://127.0.0.1:27017) to run it.
 * Skipped otherwise.
 */
class ConsentCipherMappingServiceTest {
  private MongoTemplate mongoTemplate;
  private ConsentCipherMappingService service;

  @BeforeEach
  void setUp() {
    String uri = System.getenv("VERIFY_MONGO_URI");
    assumeTrue(uri != null && !uri.isBlank(), "VERIFY_MONGO_URI not set");
    mongoTemplate = new MongoTemplate(MongoClients.create(uri), "wrapper_cipher_key_test");
    mongoTemplate.getDb().drop();
    service = new ConsentCipherMappingService(mongoTemplate);
    service.ensureFetchKeyIndexes();
  }

  @Test
  void secondFetchOfAConsentKeepsTheFirstFetchDecryptable() {
    service.saveFetchKey("request-A", "consent-1", "private-A", "nonce-A");
    service.updateFetchTransactionId("request-A", "consent-1", "txn-A");
    service.saveFetchKey("request-B", "consent-1", "private-B", "nonce-B");
    service.updateFetchTransactionId("request-B", "consent-1", "txn-B");

    ConsentCipherMapping first = service.getConsentCipherMapping("txn-A");
    assertNotNull(first, "the first fetch lost its key");
    assertEquals("private-A", first.getPrivateKey());
    assertEquals("nonce-A", first.getNonce());
    assertEquals("consent-1", first.getConsentId());
    assertEquals("private-B", service.getConsentCipherMapping("txn-B").getPrivateKey());
  }

  @Test
  void fetchStartedBeforePerFetchKeysIsStillDecryptable() {
    // An older build wrote only the per-consent document.
    service.saveConsentPrivateKeyMapping("consent-old", "private-old", "nonce-old");
    // Its callback arrives after the upgrade.
    service.updateFetchTransactionId("request-old", "consent-old", "txn-old");

    ConsentCipherMapping mapping = service.getConsentCipherMapping("txn-old");
    assertNotNull(mapping);
    assertEquals("private-old", mapping.getPrivateKey());
  }
}
