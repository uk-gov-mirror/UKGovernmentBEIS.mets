package uk.gov.pmrv.api.settings.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.netz.api.authorization.core.domain.AppUser;
import uk.gov.pmrv.api.common.domain.enumeration.AccountType;
import uk.gov.pmrv.api.settings.domain.SettingsSection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @InjectMocks
    private SettingsService service;

    @Mock
    private SettingsFeeAuthorizationService settingsFeeAuthorizationService;

    @Mock
    private SettingsEmissionFactorsAuthorizationService settingsEmissionFactorsAuthorizationService;

    @Mock
    private AppUser appUser;

    @Test
    void getAccessibleSections_installation_withBothPermissions_returnsBothSections() {
        when(settingsEmissionFactorsAuthorizationService.canView(appUser)).thenReturn(true);
        when(settingsFeeAuthorizationService.canView(appUser)).thenReturn(true);

        List<SettingsSection> result = service.getAccessibleSections(appUser, AccountType.INSTALLATION);

        assertThat(result).containsExactly(SettingsSection.EMISSION_FACTORS, SettingsSection.FEES);
    }

    @Test
    void getAccessibleSections_installation_withEmissionFactorsPermissionOnly_returnsEmissionFactors() {
        when(settingsEmissionFactorsAuthorizationService.canView(appUser)).thenReturn(true);
        when(settingsFeeAuthorizationService.canView(appUser)).thenReturn(false);

        List<SettingsSection> result = service.getAccessibleSections(appUser, AccountType.INSTALLATION);

        assertThat(result).containsExactly(SettingsSection.EMISSION_FACTORS);
    }

    @Test
    void getAccessibleSections_installation_withFeePermissionOnly_returnsFees() {
        when(settingsEmissionFactorsAuthorizationService.canView(appUser)).thenReturn(false);
        when(settingsFeeAuthorizationService.canView(appUser)).thenReturn(true);

        List<SettingsSection> result = service.getAccessibleSections(appUser, AccountType.INSTALLATION);

        assertThat(result).containsExactly(SettingsSection.FEES);
    }

    @Test
    void getAccessibleSections_aviation_withEmissionFactorsPermission_excludesEmissionFactors() {
        when(settingsEmissionFactorsAuthorizationService.canView(appUser)).thenReturn(true);
        when(settingsFeeAuthorizationService.canView(appUser)).thenReturn(true);

        List<SettingsSection> result = service.getAccessibleSections(appUser, AccountType.AVIATION);

        assertThat(result).containsExactly(SettingsSection.FEES);
    }

    @Test
    void getAccessibleSections_withNoPermissions_returnsEmpty() {
        when(settingsEmissionFactorsAuthorizationService.canView(appUser)).thenReturn(false);
        when(settingsFeeAuthorizationService.canView(appUser)).thenReturn(false);

        List<SettingsSection> result = service.getAccessibleSections(appUser, AccountType.INSTALLATION);

        assertThat(result).isEmpty();
    }
}
