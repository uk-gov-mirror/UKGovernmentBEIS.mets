import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { AnalyticsService } from '@core/services/analytics.service';

import { CookiesService } from './cookies.service';

@Component({
  selector: 'app-cookies-container',
  standalone: false,
  template: `
    <govuk-cookies-pop-up
      cookiesExpirationTime="1"
      [areBrowserCookiesEnabled]="cookiesEnabled"
      [cookiesAccepted]="cookiesAccepted$ | async"
      (cookiesRejectedEmitter)="rejectCookies($event)"
      (cookiesAcceptedEmitter)="acceptCookies($event)" />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CookiesContainerComponent {
  private readonly cookiesService = inject(CookiesService);
  private readonly analyticsService = inject(AnalyticsService);
  cookiesEnabled = this.cookiesService.cookiesEnabled();
  cookiesAccepted$ = this.cookiesService.accepted$;

  acceptCookies(expired: string) {
    this.cookiesService.acceptAllCookies(+expired);
    this.analyticsService.enableGoogleTagManager();
  }

  rejectCookies(expired: string) {
    this.cookiesService.rejectAllCookies(+expired);
  }
}
