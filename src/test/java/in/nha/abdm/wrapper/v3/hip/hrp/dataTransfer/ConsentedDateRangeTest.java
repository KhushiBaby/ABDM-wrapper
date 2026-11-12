/* (C) 2024 */
package in.nha.abdm.wrapper.v3.hip.hrp.dataTransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import in.nha.abdm.wrapper.v1.common.models.ConsentDetail;
import in.nha.abdm.wrapper.v1.common.requests.HealthInformationRequest;
import in.nha.abdm.wrapper.v1.hip.hrp.dataTransfer.callback.HIPHealthInformationRequest;
import in.nha.abdm.wrapper.v1.hiu.hrp.consent.requests.DateRange;
import in.nha.abdm.wrapper.v1.hiu.hrp.consent.requests.Permission;
import org.junit.jupiter.api.Test;

/** The HIP is told which date range it may share from, so it can withhold records outside it. */
class ConsentedDateRangeTest {
  private static final DateRange REQUESTED =
      new DateRange("2025-02-01T00:00:00Z", "2025-03-01T00:00:00Z");
  private static final DateRange CONSENTED =
      new DateRange("2025-01-01T00:00:00Z", "2025-12-31T00:00:00Z");

  private static ConsentDetail consent(DateRange range) {
    Permission permission = new Permission();
    permission.setDateRange(range);
    return ConsentDetail.builder().permission(permission).build();
  }

  private static HIPHealthInformationRequest request(DateRange range) {
    HIPHealthInformationRequest request = new HIPHealthInformationRequest();
    HealthInformationRequest hiRequest = new HealthInformationRequest();
    hiRequest.setDateRange(range);
    request.setHiRequest(hiRequest);
    return request;
  }

  @Test
  void requestRangeIsUsedWhenPresent() {
    assertEquals(
        REQUESTED,
        HIPHealthInformationV3Service.consentedDateRange(request(REQUESTED), consent(CONSENTED)));
  }

  @Test
  void fallsBackToConsentRange() {
    assertEquals(
        CONSENTED,
        HIPHealthInformationV3Service.consentedDateRange(request(null), consent(CONSENTED)));
  }

  @Test
  void nullWhenNeitherCarriesARange() {
    assertNull(
        HIPHealthInformationV3Service.consentedDateRange(
            new HIPHealthInformationRequest(), ConsentDetail.builder().build()));
  }
}
