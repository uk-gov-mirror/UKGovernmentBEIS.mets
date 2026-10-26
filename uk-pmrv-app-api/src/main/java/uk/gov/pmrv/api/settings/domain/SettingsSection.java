package uk.gov.pmrv.api.settings.domain;

import java.util.Set;
import uk.gov.pmrv.api.common.domain.enumeration.AccountType;

public enum SettingsSection {

    EMISSION_FACTORS(Set.of(AccountType.INSTALLATION)),
    FEES(Set.of(AccountType.INSTALLATION, AccountType.AVIATION));
    // GLOBAL_WARMING_POTENTIALS(Set.of(AccountType.INSTALLATION))

    private final Set<AccountType> supportedAccountTypes;

    SettingsSection(Set<AccountType> supportedAccountTypes) {
        this.supportedAccountTypes = supportedAccountTypes;
    }

    public boolean supportsAccountType(AccountType accountType) {
        return supportedAccountTypes.contains(accountType);
    }
}
