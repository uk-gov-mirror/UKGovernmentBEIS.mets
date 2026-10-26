package uk.gov.pmrv.api.settings.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.netz.api.authorization.core.domain.AppUser;
import uk.gov.pmrv.api.common.domain.enumeration.AccountType;
import uk.gov.pmrv.api.settings.domain.SettingsSection;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsFeeAuthorizationService settingsFeeAuthorizationService;
    private final SettingsEmissionFactorsAuthorizationService settingsEmissionFactorsAuthorizationService;

    public List<SettingsSection> getAccessibleSections(AppUser appUser, AccountType accountType) {
        List<SettingsSection> sections = new ArrayList<>();
        if (settingsEmissionFactorsAuthorizationService.canView(appUser) && SettingsSection.EMISSION_FACTORS.supportsAccountType(accountType)) {
            sections.add(SettingsSection.EMISSION_FACTORS);
        }
        if (settingsFeeAuthorizationService.canView(appUser) && SettingsSection.FEES.supportsAccountType(accountType)) {
            sections.add(SettingsSection.FEES);
        }
        return sections;
    }
}
