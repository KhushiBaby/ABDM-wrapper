/* (C) 2024 */
package in.nha.abdm.wrapper.v3.hip.hrp.consent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.nha.abdm.wrapper.v1.common.GatewayConstants;
import in.nha.abdm.wrapper.v1.hip.hrp.consent.requests.HIPNotification;
import in.nha.abdm.wrapper.v1.hip.hrp.consent.requests.HIPNotifyRequest;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.helpers.RequestStatus;
import in.nha.abdm.wrapper.v3.common.RequestV3Manager;
import in.nha.abdm.wrapper.v3.common.models.GenericV3Response;
import in.nha.abdm.wrapper.v3.database.mongo.services.RequestLogV3Service;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The HIP request log saved by hipNotify is what the later health-information request reads. It
 * must be saved whatever the gateway answers to the on-notify acknowledgement.
 */
class ConsentV3ServiceTest {
  private RequestV3Manager requestV3Manager;
  private RequestLogV3Service requestLogV3Service;
  private ConsentV3Service service;
  private HIPNotifyRequest notifyRequest;
  private HttpHeaders headers;

  @BeforeEach
  void setUp() {
    requestV3Manager = mock(RequestV3Manager.class);
    requestLogV3Service = mock(RequestLogV3Service.class);
    service = new ConsentV3Service(requestV3Manager);
    service.requestLogV3Service = requestLogV3Service;
    // No consentDetail, so hipNotify builds its own acknowledgement and touches no other store.
    notifyRequest =
        HIPNotifyRequest.builder()
            .notification(
                HIPNotification.builder().status("GRANTED").consentId("consent-1").build())
            .build();
    headers = new HttpHeaders();
    headers.add(GatewayConstants.X_HIP_ID, "HIP-1");
    headers.add(GatewayConstants.REQUEST_ID, "req-1");
  }

  @Test
  void savesRequestLogWhenGatewayAcknowledgementTimesOut() throws Exception {
    when(requestV3Manager.fetchResponseFromGateway(any(), any(), any()))
        .thenThrow(ReadTimeoutException.INSTANCE);

    service.hipNotify(notifyRequest, headers);

    verify(requestLogV3Service)
        .dataTransferNotify(
            eq(notifyRequest), eq(RequestStatus.HIP_ON_NOTIFY_ERROR), any(), eq(headers));
  }

  @Test
  void savesRequestLogAsSuccessWhenGatewayAccepts() throws Exception {
    when(requestV3Manager.fetchResponseFromGateway(any(), any(), any()))
        .thenReturn(new ResponseEntity<GenericV3Response>(HttpStatus.ACCEPTED));

    service.hipNotify(notifyRequest, headers);

    verify(requestLogV3Service)
        .dataTransferNotify(
            eq(notifyRequest), eq(RequestStatus.HIP_ON_NOTIFY_SUCCESS), any(), eq(headers));
  }
}
