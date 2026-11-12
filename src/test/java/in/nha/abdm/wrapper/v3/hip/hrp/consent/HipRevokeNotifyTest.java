/* (C) 2024 */
package in.nha.abdm.wrapper.v3.hip.hrp.consent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.nha.abdm.wrapper.v1.common.GatewayConstants;
import in.nha.abdm.wrapper.v1.hip.hrp.consent.requests.HIPNotification;
import in.nha.abdm.wrapper.v1.hip.hrp.consent.requests.HIPNotifyRequest;
import in.nha.abdm.wrapper.v1.hip.hrp.consent.requests.HIPOnNotifyRequest;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.ConsentPatient;
import in.nha.abdm.wrapper.v3.common.RequestV3Manager;
import in.nha.abdm.wrapper.v3.database.mongo.services.ConsentPatientV3Service;
import in.nha.abdm.wrapper.v3.database.mongo.services.PatientV3Service;
import in.nha.abdm.wrapper.v3.database.mongo.services.RequestLogV3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;

/** A revocation carries only the consent id and status; it must be recorded and acknowledged OK. */
class HipRevokeNotifyTest {
  private RequestV3Manager gateway;
  private PatientV3Service patients;
  private ConsentPatientV3Service consentPatients;
  private ConsentV3Service service;
  private HttpHeaders headers;

  @BeforeEach
  void setUp() {
    gateway = mock(RequestV3Manager.class);
    patients = mock(PatientV3Service.class);
    consentPatients = mock(ConsentPatientV3Service.class);
    service = new ConsentV3Service(gateway);
    service.patientService = patients;
    service.consentPatientService = consentPatients;
    service.requestLogV3Service = mock(RequestLogV3Service.class);
    headers = new HttpHeaders();
    headers.add(GatewayConstants.X_HIP_ID, "HIP-1");
    headers.add(GatewayConstants.REQUEST_ID, "req-1");
    headers.add(GatewayConstants.TIMESTAMP, "2026-11-12T14:02:31.000Z");
  }

  private static HIPNotifyRequest revoke(String consentId) {
    return HIPNotifyRequest.builder()
        .notification(HIPNotification.builder().status("REVOKED").consentId(consentId).build())
        .build();
  }

  private String acknowledgementSent() {
    ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
    verify(gateway).fetchResponseFromGateway(any(), body.capture(), any());
    return ((HIPOnNotifyRequest) body.getValue()).getAcknowledgement().getStatus();
  }

  @Test
  void revocationWithoutConsentDetailIsRecordedAndAcknowledgedOk() throws Exception {
    ConsentPatient mapping = new ConsentPatient();
    mapping.setAbhaAddress("someone@sbx");
    when(consentPatients.findMappingByConsentId("c-1", GatewayConstants.HIP, "HIP-1"))
        .thenReturn(mapping);

    service.hipNotify(revoke("c-1"), headers);

    verify(patients)
        .updatePatientConsent("someone@sbx", "c-1", "REVOKED", "2026-11-12T14:02:31.000Z", "HIP-1");
    assertEquals("OK", acknowledgementSent());
  }

  @Test
  void revocationOfAnUnknownConsentIsAcknowledgedAsFailure() throws Exception {
    service.hipNotify(revoke("unknown"), headers);

    verify(patients, never()).updatePatientConsent(any(), any(), any(), any(), any());
    assertEquals("FAILURE", acknowledgementSent());
  }

  @Test
  void grantStillGoesThroughTheGrantPath() throws Exception {
    // A GRANTED notification without consentDetail is invalid and must not reach the revoke path.
    HIPNotifyRequest grant =
        HIPNotifyRequest.builder()
            .notification(HIPNotification.builder().status("GRANTED").consentId("c-2").build())
            .build();
    service.hipNotify(grant, headers);
    verify(patients, never()).updatePatientConsent(any(), any(), any(), any(), eq("HIP-1"));
    assertEquals("FAILURE", acknowledgementSent());
  }
}
