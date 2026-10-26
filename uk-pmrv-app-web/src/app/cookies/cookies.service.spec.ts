import { TestBed } from '@angular/core/testing';

import { CookiesService } from './cookies.service';

describe('CookiesService', () => {
  let service: CookiesService;

  const setPolicyCookie = (value: string) => (document.cookie = `uk_pmrv_cookies_policy=${value}; path=/`);

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CookiesService);
  });

  afterEach(() => {
    document.cookie = 'uk_pmrv_cookies_policy=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    document.cookie = 'uk_pmrv_cookies_preferences_set=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('should not have analytics consent when no policy cookie exists', () => {
    expect(service.hasAnalyticsConsent()).toBe(false);
  });

  it('should have analytics consent after accepting all cookies', () => {
    service.acceptAllCookies(1);

    expect(service.hasAnalyticsConsent()).toBe(true);
  });

  it('should not have analytics consent after rejecting all cookies', () => {
    service.rejectAllCookies(1);

    expect(service.hasAnalyticsConsent()).toBe(false);
  });

  it('should read a url encoded policy cookie', () => {
    setPolicyCookie(encodeURIComponent(JSON.stringify({ essential: true, usage: true })));

    expect(service.hasAnalyticsConsent()).toBe(true);
  });

  it('should not have analytics consent when the policy cookie is malformed', () => {
    setPolicyCookie('not-json');

    expect(service.hasAnalyticsConsent()).toBe(false);
  });
});
