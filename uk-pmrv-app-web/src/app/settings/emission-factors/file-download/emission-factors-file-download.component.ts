import { AsyncPipe } from '@angular/common';
import { AfterViewChecked, ChangeDetectionStrategy, Component, ElementRef, inject, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { expand, map, switchMap, timer } from 'rxjs';

import { LinkDirective } from 'govuk-components';

import { FileAttachmentsService, SettingsService } from 'pmrv-api';

@Component({
  selector: 'app-emission-factors-file-download',
  imports: [AsyncPipe, LinkDirective],
  template: `
    <h1 class="govuk-heading-l">Your download has started</h1>
    <p class="govuk-body">You should see your downloads in the downloads folder.</p>
    <a govukLink [href]="url$ | async" download #anchor>Click to restart download if it fails</a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmissionFactorsFileDownloadComponent implements AfterViewChecked {
  private readonly route = inject(ActivatedRoute);
  private readonly settingsService = inject(SettingsService);
  private readonly fileAttachmentsService = inject(FileAttachmentsService);

  private readonly anchor = viewChild.required<ElementRef<HTMLAnchorElement>>('anchor');
  private readonly fileDownloadAttachmentPath = `${this.fileAttachmentsService.configuration.basePath}/v1.0/file-attachments/`;
  private hasDownloadedOnce = false;

  readonly url$ = this.route.paramMap.pipe(
    map((params) => this.settingsService.generateGetEmissionFactorsFileToken(Number(params.get('referenceYear')))),
    switchMap((request) =>
      request.pipe(
        expand((response) => timer(response.tokenExpirationMinutes * 1000 * 60).pipe(switchMap(() => request))),
      ),
    ),
    map((fileToken) => `${this.fileDownloadAttachmentPath}${encodeURIComponent(String(fileToken.token))}`),
  );

  ngAfterViewChecked(): void {
    const anchor = this.anchor().nativeElement;

    if (anchor.href.includes(this.fileDownloadAttachmentPath) && !this.hasDownloadedOnce) {
      anchor.click();
      this.hasDownloadedOnce = true;
      onfocus = () => close();
    }
  }
}
