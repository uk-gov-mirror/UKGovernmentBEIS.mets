package uk.gov.pmrv.api.settings.domain.enumeration;

public enum EmissionFactorsHistoryEntryStatus {
    /** Uploaded and parsed, waiting for the user to confirm the previewed data. */
    PENDING,
    /** Confirmed by the user, its data is the reference year's emission factors. */
    COMPLETED
}
