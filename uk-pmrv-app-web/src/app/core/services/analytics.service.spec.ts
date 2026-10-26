import { TestBed } from '@angular/core/testing';

import { ConfigStore } from '@core/config/config.store';

import { AnalyticsService } from './analytics.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let configStore: ConfigStore;

  const gtmScripts = () => document.head.querySelectorAll('script[src*="googletagmanager.com/gtm.js"]');

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AnalyticsService);
    configStore = TestBed.inject(ConfigStore);
    jest.spyOn(console, 'warn').mockImplementation();
    jest.spyOn(console, 'log').mockImplementation();
  });

  afterEach(() => {
    gtmScripts().forEach((script) => script.remove());
    delete (window as any).dataLayer;
    jest.restoreAllMocks();
  });

  it('should not load GTM when no container id is configured', () => {
    service.enableGoogleTagManager();

    expect(gtmScripts()).toHaveLength(0);
    expect((window as any).dataLayer).toBeUndefined();
  });

  it('should load GTM with the configured container id', () => {
    configStore.setState({ ...configStore.getState(), analytics: { gtmContainerId: 'GTM-TEST' } });

    service.enableGoogleTagManager();

    expect(gtmScripts()).toHaveLength(1);
    expect((gtmScripts()[0] as HTMLScriptElement).src).toEqual('https://www.googletagmanager.com/gtm.js?id=GTM-TEST');
    expect((window as any).dataLayer).toEqual([expect.objectContaining({ event: 'gtm.js' })]);
  });

  it('should load GTM only once', () => {
    configStore.setState({ ...configStore.getState(), analytics: { gtmContainerId: 'GTM-TEST' } });

    service.enableGoogleTagManager();
    service.enableGoogleTagManager();

    expect(gtmScripts()).toHaveLength(1);
    expect((window as any).dataLayer).toHaveLength(1);
  });
});
