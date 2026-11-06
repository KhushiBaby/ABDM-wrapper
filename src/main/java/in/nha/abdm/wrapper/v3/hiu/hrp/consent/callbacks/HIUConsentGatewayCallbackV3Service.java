/* (C) 2024 */
package in.nha.abdm.wrapper.v3.hiu.hrp.consent.callbacks;

import in.nha.abdm.wrapper.v1.common.GatewayConstants;
import in.nha.abdm.wrapper.v1.common.Utils;
import in.nha.abdm.wrapper.v1.common.exceptions.IllegalDataStateException;
import in.nha.abdm.wrapper.v1.common.models.Consent;
import in.nha.abdm.wrapper.v1.common.models.ConsentAcknowledgement;
import in.nha.abdm.wrapper.v1.common.models.RespRequest;
import in.nha.abdm.wrapper.v1.common.responses.ErrorResponse;
import in.nha.abdm.wrapper.v1.common.responses.ErrorV3Response;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.repositories.LogsRepo;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.services.ConsentRequestService;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.services.RequestLogService;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.RequestLog;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.helpers.FieldIdentifiers;
import in.nha.abdm.wrapper.v1.hip.hrp.database.mongo.tables.helpers.RequestStatus;
import in.nha.abdm.wrapper.v1.hiu.hrp.consent.HIUConsentInterface;
import in.nha.abdm.wrapper.v1.hiu.hrp.consent.requests.FetchConsentRequest;
import in.nha.abdm.wrapper.v1.hiu.hrp.consent.requests.callback.*;
import in.nha.abdm.wrapper.v3.config.ErrorHandler;
import in.nha.abdm.wrapper.v3.database.mongo.services.ConsentPatientV3Service;
import in.nha.abdm.wrapper.v3.database.mongo.services.PatientV3Service;
import in.nha.abdm.wrapper.v3.database.mongo.services.RequestLogV3Service;
import in.nha.abdm.wrapper.v3.hiu.hrp.consent.HIUConsentV3Interface;
import in.nha.abdm.wrapper.v3.hiu.hrp.consent.requests.ConsentOnInitV3Request;
import in.nha.abdm.wrapper.v3.hiu.hrp.consent.requests.ConsentOnNotifyV3Request;
import in.nha.abdm.wrapper.v3.hiu.hrp.consent.requests.HIUConsentOnStatusV3Request;
import in.nha.abdm.wrapper.v3.hiu.hrp.consent.requests.OnFetchV3Request;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HIUConsentGatewayCallbackV3Service implements HIUConsentGatewayCallbackV3Interface {

  private final RequestLogV3Service requestLogV3Service;
  private final HIUConsentV3Interface hiuConsentInterface;
  private final PatientV3Service patientService;
  private final ConsentRequestService consentRequestService;
  private final LogsRepo logsRepo;
  private final ConsentPatientV3Service consentPatientService;

  private static final Logger log = LogManager.getLogger(HIUConsentGatewayCallbackV3Service.class);

  @Autowired
  public HIUConsentGatewayCallbackV3Service(
      RequestLogService requestLogService,
      RequestLogV3Service requestLogV3Service,
      HIUConsentInterface hiuConsentInterface,
      HIUConsentV3Interface hiuConsentInterface1,
      PatientV3Service patientService,
      ConsentRequestService consentRequestService,
      LogsRepo logsRepo,
      ConsentPatientV3Service consentPatientService) {
    this.requestLogV3Service = requestLogV3Service;
    this.hiuConsentInterface = hiuConsentInterface1;
    this.patientService = patientService;
    this.consentRequestService = consentRequestService;
    this.logsRepo = logsRepo;
    this.consentPatientService = consentPatientService;
  }

  @Override
  public HttpStatus onInitConsent(
      ConsentOnInitV3Request consentOnInitV3Request, HttpHeaders httpHeaders)
      throws IllegalDataStateException {
    if (Objects.nonNull(consentOnInitV3Request)
        && Objects.nonNull(consentOnInitV3Request.getConsentRequest())
        && Objects.nonNull(consentOnInitV3Request.getResponse())) {
      // This mapping needs to be persisted in database because when gateway issues hiu notify call,
      // it passes
      // consent request id and then there is no way to track original request other that looping
      // through all the requests
      // and checking their responses for consentRequestId.
      consentRequestService.saveConsentRequest(
          consentOnInitV3Request.getConsentRequest().getId(),
          consentOnInitV3Request.getResponse().getRequestId());
      requestLogV3Service.updateConsentResponse(
          consentOnInitV3Request.getResponse().getRequestId(),
          FieldIdentifiers.CONSENT_ON_INIT_RESPONSE,
          RequestStatus.CONSENT_ON_INIT_RESPONSE_RECEIVED,
          consentOnInitV3Request.getConsentRequest().getId());
      log.info(
          "onInitConsent saved consentRequestId={} -> gatewayRequestId={} mapping",
          consentOnInitV3Request.getConsentRequest().getId(),
          consentOnInitV3Request.getResponse().getRequestId());
    } else if (Objects.nonNull(consentOnInitV3Request)
        && Objects.nonNull(consentOnInitV3Request.getConsentRequest())) {
      // consentRequest present but response missing: the consentRequestId -> gatewayRequestId
      // mapping cannot be saved here, which will later make hiuNotify's lookup fail with
      // "No request found for consent request id". Logging so that failure is traceable back
      // to this cause instead of appearing unexplained downstream.
      log.error(
          "onInitConsent received consentRequest.id={} with no response — mapping not saved",
          consentOnInitV3Request.getConsentRequest().getId());
    } else if (Objects.nonNull(consentOnInitV3Request)
        && Objects.nonNull(consentOnInitV3Request.getError())) {
      requestLogV3Service.updateError(
          consentOnInitV3Request.getResponse().getRequestId(),
          Collections.singletonList(
              ErrorV3Response.builder()
                  .error(
                      ErrorResponse.builder()
                          .code(GatewayConstants.ERROR_CODE)
                          .message(consentOnInitV3Request.getError().getMessage())
                          .build())
                  .build()),
          RequestStatus.CONSENT_ON_INIT_ERROR);
    } else if (Objects.nonNull(consentOnInitV3Request)
        && Objects.nonNull(consentOnInitV3Request.getResponse())) {
      requestLogV3Service.updateError(
          consentOnInitV3Request.getResponse().getRequestId(),
          Collections.singletonList(
              ErrorV3Response.builder()
                  .error(
                      ErrorResponse.builder()
                          .code(GatewayConstants.ERROR_CODE)
                          .message("Something went wrong while executing consent on init")
                          .build())
                  .build()),
          RequestStatus.CONSENT_ON_INIT_ERROR);
      return HttpStatus.BAD_REQUEST;
    }
    return HttpStatus.ACCEPTED;
  }

  @Override
  public HttpStatus consentOnStatus(
      HIUConsentOnStatusV3Request hiuConsentOnStatusV3Request, HttpHeaders httpHeaders)
      throws IllegalDataStateException, IllegalDataStateException {
    if (Objects.nonNull(hiuConsentOnStatusV3Request)
        && Objects.nonNull(hiuConsentOnStatusV3Request.getConsentRequest())) {
      String gatewayRequestId = resolveOnStatusGatewayRequestId(hiuConsentOnStatusV3Request);
      requestLogV3Service.updateConsentResponse(
          gatewayRequestId,
          FieldIdentifiers.CONSENT_ON_STATUS_RESPONSE,
          RequestStatus.CONSENT_ON_STATUS_RESPONSE_RECEIVED,
          hiuConsentOnStatusV3Request.getConsentRequest());
    } else {
      // There is no way to track the gateway request id since gateway sent empty request. So we
      // will not be
      // able to update the error status in database.
      log.error("Something went wrong while executing consent on status");
      return HttpStatus.BAD_REQUEST;
    }
    return HttpStatus.ACCEPTED;
  }

  /**
   * The gateway's consent on-status callback identifies the request two ways: consentRequest.id
   * (the id issued at on-init, mapped in ConsentRequest) when it sends one, and response.requestId,
   * which echoes the REQUEST-ID of our consent-request/status call. The sandbox sends
   * consentRequest.id=null, so the echoed status request id is the reliable key.
   */
  private String resolveOnStatusGatewayRequestId(HIUConsentOnStatusV3Request request)
      throws IllegalDataStateException {
    String consentRequestId = request.getConsentRequest().getId();
    if (Objects.nonNull(consentRequestId) && !consentRequestId.isBlank()) {
      return consentRequestService.getGatewayRequestId(consentRequestId);
    }
    if (Objects.isNull(request.getResponse())
        || Objects.isNull(request.getResponse().getRequestId())) {
      throw new IllegalDataStateException(
          "Consent on-status carries neither consentRequest.id nor response.requestId");
    }
    return requestLogV3Service.getGatewayRequestIdByConsentStatusRequestId(
        request.getResponse().getRequestId());
  }

  @Override
  public HttpStatus hiuNotify(NotifyHIURequest notifyHIURequest, HttpHeaders httpHeaders)
      throws IllegalDataStateException, IllegalDataStateException {
    if (Objects.nonNull(notifyHIURequest)
        && Objects.nonNull(notifyHIURequest.getNotification())
        && Objects.isNull(notifyHIURequest.getError())) {
      // Get corresponding gateway request for the given consent request id.
      if (!notifyHIURequest.getNotification().getStatus().equalsIgnoreCase("GRANTED")) {
        List<ConsentArtefact> consentArtefacts =
            notifyHIURequest.getNotification().getConsentArtefacts();
        if (notifyHIURequest.getNotification().getStatus().equalsIgnoreCase("DENIED")) {
          String gatewayRequestId =
              consentRequestService.getGatewayRequestId(
                  notifyHIURequest.getNotification().getConsentRequestId());
          requestLogV3Service.updateConsentResponse(
              gatewayRequestId,
              FieldIdentifiers.CONSENT_ON_NOTIFY_RESPONSE,
              RequestStatus.CONSENT_ON_NOTIFY_RESPONSE_RECEIVED,
              notifyHIURequest);
        } else {
          for (ConsentArtefact consentArtefact : consentArtefacts) {
            patientService.updatePatientConsent(
                consentPatientService
                    .findMappingByConsentId(
                        consentArtefact.getId(),
                        "HIU",
                        httpHeaders.getFirst(GatewayConstants.X_HIU_ID))
                    .getAbhaAddress(),
                consentArtefact.getId(),
                notifyHIURequest.getNotification().getStatus(),
                notifyHIURequest.getTimestamp(),
                httpHeaders.getFirst(GatewayConstants.X_HIU_ID));
          }
        }
        List<ConsentAcknowledgement> consentAcknowledgements = new ArrayList<>();
        for (ConsentArtefact consentArtefact :
            notifyHIURequest.getNotification().getConsentArtefacts()) {
          consentAcknowledgements.add(
              ConsentAcknowledgement.builder()
                  .status("OK")
                  .consentId(consentArtefact.getId())
                  .build());
        }
        ConsentOnNotifyV3Request consentOnNotifyV3Request =
            ConsentOnNotifyV3Request.builder()
                .acknowledgement(consentAcknowledgements)
                .response(
                    RespRequest.builder()
                        .requestId(httpHeaders.getFirst(GatewayConstants.REQUEST_ID))
                        .build())
                .build();
        hiuConsentInterface.hiuOnNotify(consentOnNotifyV3Request, httpHeaders);
        return HttpStatus.OK;
      }
      String gatewayRequestId =
          consentRequestService.getGatewayRequestId(
              notifyHIURequest.getNotification().getConsentRequestId());
      requestLogV3Service.updateConsentResponse(
          gatewayRequestId,
          FieldIdentifiers.CONSENT_ON_NOTIFY_RESPONSE,
          RequestStatus.CONSENT_ON_NOTIFY_RESPONSE_RECEIVED,
          notifyHIURequest);

      RequestLog requestLog = logsRepo.findByGatewayRequestId(gatewayRequestId);

      List<ConsentAcknowledgement> consentAcknowledgements = new ArrayList<>();
      String status = notifyHIURequest.getNotification().getStatus();
      for (ConsentArtefact consentArtefact :
          notifyHIURequest.getNotification().getConsentArtefacts()) {
        consentAcknowledgements.add(
            ConsentAcknowledgement.builder()
                .status("OK")
                .consentId(consentArtefact.getId())
                .build());
        FetchConsentRequest fetchConsentRequest =
            FetchConsentRequest.builder().consentId(consentArtefact.getId()).build();
        hiuConsentInterface.fetchConsent(fetchConsentRequest, requestLog, httpHeaders);
      }
      ConsentOnNotifyV3Request onNotifyRequest =
          ConsentOnNotifyV3Request.builder()
              .acknowledgement(consentAcknowledgements)
              .response(
                  RespRequest.builder()
                      .requestId(httpHeaders.getFirst(GatewayConstants.REQUEST_ID))
                      .build())
              .build();
      hiuConsentInterface.hiuOnNotify(onNotifyRequest, httpHeaders);
    } else {
      if (notifyHIURequest.getError() != null) {
        String gatewayRequestId =
            consentRequestService.getGatewayRequestId(
                notifyHIURequest.getNotification().getConsentRequestId());
        requestLogV3Service.updateError(
            gatewayRequestId,
            Collections.singletonList(
                ErrorV3Response.builder()
                    .error(
                        ErrorResponse.builder()
                            .code(GatewayConstants.ERROR_CODE)
                            .message(RequestStatus.CONSENT_NOTIFY_ERROR.getValue())
                            .build())
                    .build()),
            RequestStatus.CONSENT_NOTIFY_ERROR);
        log.error("HIU Notify : " + notifyHIURequest.getError().toString());
        return HttpStatus.OK;
      }
      // There is no way to track the gateway request id since gateway sent empty request. So we
      // will not be
      // able to update the error status in database.
      log.error("Something went wrong while executing hiu notify");
      return HttpStatus.BAD_REQUEST;
    }
    return HttpStatus.ACCEPTED;
  }

  @Override
  public HttpStatus consentOnFetch(OnFetchV3Request onFetchRequest, HttpHeaders httpHeaders)
      throws IllegalDataStateException {
    if (Objects.nonNull(onFetchRequest)
        && Objects.nonNull(onFetchRequest.getConsent())
        && Objects.nonNull(onFetchRequest.getConsent().getConsentDetail())) {
      String patientId = onFetchRequest.getConsent().getConsentDetail().getPatient().getId();
      Consent consent =
          Consent.builder()
              .lastUpdatedOn(Utils.getCurrentTimeStamp())
              .consentDetail(onFetchRequest.getConsent().getConsentDetail())
              .status(onFetchRequest.getConsent().getStatus())
              .build();
      try {
        patientService.addConsent(
            patientId, consent, httpHeaders.getFirst(GatewayConstants.X_HIU_ID));
        consentPatientService.saveConsentPatientMapping(
            onFetchRequest.getConsent().getConsentDetail().getConsentId(),
            patientId,
            "HIU",
            httpHeaders.getFirst(GatewayConstants.X_HIU_ID));
      } catch (Exception ex) {
        Object error = ErrorHandler.getErrors(ex);
        requestLogV3Service.updateError(
            onFetchRequest.getResponse().getRequestId(), error, RequestStatus.CONSENT_FETCH_ERROR);
      }
    } else {
      return HttpStatus.BAD_REQUEST;
    }
    return HttpStatus.ACCEPTED;
  }
}
