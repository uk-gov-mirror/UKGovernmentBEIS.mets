import { DOCUMENT } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/* eslint-disable @angular-eslint/prefer-on-push-component-change-detection */
@Component({
  selector: 'govuk-skip-link',
  imports: [RouterLink],
  template: `
    <a class="govuk-skip-link" [routerLink]="[]" [fragment]="anchor()" (click)="focusTarget()">Skip to main content</a>
  `,
})
export class SkipLinkComponent {
  readonly anchor = input('main-content');

  private readonly document = inject(DOCUMENT);

  focusTarget(): void {
    this.document.getElementById(this.anchor())?.focus();
  }
}
