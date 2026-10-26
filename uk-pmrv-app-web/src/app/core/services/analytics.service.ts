import { inject, Injectable } from '@angular/core';

import { ConfigStore } from '@core/config/config.store';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly configStore = inject(ConfigStore);
  private currentScript: HTMLScriptElement;

  enableGoogleTagManager() {
    const gtmContainerId = this.configStore.getState()?.analytics?.gtmContainerId;
    if (!gtmContainerId) {
      console.warn('No google tag manager container id found. Environment will not log google analytics.');
      return;
    }

    if (this.currentScript) {
      console.warn('GTM script already loaded in DOM - Aborting...');
      return;
    }

    console.log('Logging to Google Analytics - [GTM Container ID]:', gtmContainerId);

    (window as any).dataLayer = (window as any).dataLayer || [];
    (window as any).dataLayer.push({
      'gtm.start': new Date().getTime(),
      event: 'gtm.js',
    });

    const gtmScript = document.createElement('script');
    gtmScript.async = true;
    gtmScript.src = `https://www.googletagmanager.com/gtm.js?id=${gtmContainerId}`;
    const firstHeadChild = document.head.childNodes[0];
    document.head.insertBefore(gtmScript, firstHeadChild);
    this.currentScript = gtmScript;
  }
}
