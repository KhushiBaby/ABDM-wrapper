/* (C) 2024 */
package in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.ConsentCipherMapping;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.helpers.FieldIdentifiers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class ConsentCipherMappingService {
  static final String FETCH_KEYS_COLLECTION = "fetch-cipher-key-mappings";
  static final String FETCH_REQUEST_ID = "requestId";

  private final MongoTemplate mongoTemplate;

  @Autowired
  public ConsentCipherMappingService(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public void saveConsentPrivateKeyMapping(String consentId, String privateKey, String nonce) {
    MongoCollection<Document> collection =
        mongoTemplate.getCollection("consent-cipher-key-mappings");
    UpdateOptions updateOptions = new UpdateOptions().upsert(true);
    collection.updateOne(
        Filters.eq(FieldIdentifiers.CONSENT_ID, consentId),
        Updates.combine(
            Updates.set(FieldIdentifiers.PRIVATE_KEY, privateKey),
            Updates.set(FieldIdentifiers.NONCE, nonce)),
        updateOptions);
  }

  public void updateTransactionId(String consentId, String transactionId) {
    MongoCollection<Document> collection =
        mongoTemplate.getCollection("consent-cipher-key-mappings");
    UpdateOptions updateOptions = new UpdateOptions().upsert(true);
    collection.updateOne(
        Filters.eq(FieldIdentifiers.CONSENT_ID, consentId),
        Updates.combine(Updates.set(FieldIdentifiers.TRANSACTION_ID, transactionId)),
        updateOptions);
  }

  public ConsentCipherMapping getConsentCipherMapping(String transactionId) {
    Query query = new Query(Criteria.where(FieldIdentifiers.TRANSACTION_ID).is(transactionId));
    ConsentCipherMapping perFetch =
        mongoTemplate.findOne(query, ConsentCipherMapping.class, FETCH_KEYS_COLLECTION);
    if (perFetch != null) {
      return perFetch;
    }
    // Fetches started before per-fetch keys existed are only in the per-consent collection.
    return mongoTemplate.findOne(query, ConsentCipherMapping.class);
  }

  /**
   * Keys of one health information request, stored under that request's id.
   *
   * <p>The per-consent collection holds a single key per consent, so a second fetch for the same
   * consent replaced the key of the first and the records the first one delivered could no longer
   * be decrypted. Each fetch now also keeps its own key here. The per-consent document is still
   * written, so an older wrapper build can read fetches made by this one.
   */
  public void saveFetchKey(String requestId, String consentId, String privateKey, String nonce) {
    mongoTemplate
        .getCollection(FETCH_KEYS_COLLECTION)
        .updateOne(
            Filters.eq(FETCH_REQUEST_ID, requestId),
            Updates.combine(
                Updates.set(FieldIdentifiers.CONSENT_ID, consentId),
                Updates.set(FieldIdentifiers.PRIVATE_KEY, privateKey),
                Updates.set(FieldIdentifiers.NONCE, nonce)),
            new UpdateOptions().upsert(true));
    saveConsentPrivateKeyMapping(consentId, privateKey, nonce);
  }

  /** Records the transaction id the gateway assigned to the request made under requestId. */
  public void updateFetchTransactionId(String requestId, String consentId, String transactionId) {
    // No upsert: a request started before per-fetch keys existed has no document here, and
    // creating one without a key would shadow the per-consent key that can still decrypt it.
    mongoTemplate
        .getCollection(FETCH_KEYS_COLLECTION)
        .updateOne(
            Filters.eq(FETCH_REQUEST_ID, requestId),
            Updates.set(FieldIdentifiers.TRANSACTION_ID, transactionId));
    updateTransactionId(consentId, transactionId);
  }

  @PostConstruct
  void ensureFetchKeyIndexes() {
    IndexOperations indexes = mongoTemplate.indexOps(FETCH_KEYS_COLLECTION);
    indexes.ensureIndex(new Index().on(FETCH_REQUEST_ID, Sort.Direction.ASC).unique());
    indexes.ensureIndex(new Index().on(FieldIdentifiers.TRANSACTION_ID, Sort.Direction.ASC));
  }
}
