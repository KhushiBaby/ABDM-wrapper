/* (C) 2024 */
package in.nha.abdm.wrapper.v1.hip.hrp.dataTransfer.requests;

import in.nha.abdm.wrapper.v1.hiu.hrp.consent.requests.ConsentCareContexts;
import in.nha.abdm.wrapper.v1.hiu.hrp.consent.requests.DateRange;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class HealthInformationBundleRequest {
  private String hipId;
  private List<ConsentCareContexts> careContextsWithPatientReferences;

  /**
   * What the consent allows to be shared. The HIP must return only records of these HI types that
   * fall inside this date range. Both are null when the consent carries none.
   */
  private List<String> hiTypes;

  private DateRange dateRange;
}
