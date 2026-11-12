/* (C) 2024 */
package in.nha.abdm.wrapper.v3.hiu.hrp.consent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.RequestLog;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.helpers.FieldIdentifiers;
import in.nha.abdm.wrapper.v1.hiu.hrp.consent.requests.callback.ConsentStatus;
import java.time.LocalDateTime;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

/**
 * A request still waiting on the patient must be re-asked at the gateway after a while, so that an
 * expiry is seen; a final answer must never be re-asked.
 */
class OnStatusRefreshTest {
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 20, 0);

  private static RequestLog log(String status, LocalDateTime lastUpdated, boolean hasOnInit) {
    HashMap<String, Object> details = new HashMap<>();
    details.put(
        FieldIdentifiers.CONSENT_ON_STATUS_RESPONSE,
        ConsentStatus.builder().status(status).build());
    if (hasOnInit) {
      details.put(FieldIdentifiers.CONSENT_ON_INIT_RESPONSE, "consent-request-1");
    }
    RequestLog requestLog = new RequestLog();
    requestLog.setResponseDetails(details);
    requestLog.setLastUpdated(lastUpdated);
    return requestLog;
  }

  @Test
  void requestedAnswerOlderThanTheWindowIsRefreshed() {
    assertTrue(
        HIUConsentV3Service.onStatusIsStale(log("REQUESTED", NOW.minusMinutes(6), true), NOW));
  }

  @Test
  void freshRequestedAnswerIsReused() {
    assertFalse(
        HIUConsentV3Service.onStatusIsStale(log("REQUESTED", NOW.minusMinutes(1), true), NOW));
  }

  @Test
  void finalAnswersAreNeverRefreshed() {
    for (String status : new String[] {"EXPIRED", "DENIED", "GRANTED", "REVOKED"}) {
      assertFalse(
          HIUConsentV3Service.onStatusIsStale(log(status, NOW.minusDays(1), true), NOW), status);
    }
  }

  @Test
  void requestWithoutConsentRequestIdIsNotRefreshed() {
    assertFalse(
        HIUConsentV3Service.onStatusIsStale(log("REQUESTED", NOW.minusDays(1), false), NOW));
  }
}
