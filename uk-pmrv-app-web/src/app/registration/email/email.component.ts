import { Component, OnInit, signal } from '@angular/core';
import { UntypedFormBuilder } from '@angular/forms';

import { EMPTY } from 'rxjs';

import { catchTooManyRequests } from '@error/business-errors';

import { GovukValidators } from 'govuk-components';

import { OperatorUsersRegistrationService } from 'pmrv-api';

import { BackLinkService } from '../../shared/back-link/back-link.service';

// eslint-disable-next-line @angular-eslint/prefer-on-push-component-change-detection
@Component({
  selector: 'app-email',
  standalone: false,
  templateUrl: './email.component.html',
})
export class EmailComponent implements OnInit {
  isSummaryDisplayed = signal(false);
  isVerificationSent = signal(false);
  isSubmitDisabled = signal(false);

  form = this.fb.group({
    email: [
      null,
      [
        GovukValidators.required('Enter your email address'),
        GovukValidators.email('Enter an email address in the correct format, like name@example.com'),
        GovukValidators.maxLength(255, 'Enter an email address with a maximum of 255 characters'),
      ],
    ],
  });

  constructor(
    private readonly operatorUsersRegistrationService: OperatorUsersRegistrationService,
    private readonly fb: UntypedFormBuilder,
    private readonly backLinkService: BackLinkService,
  ) {}

  ngOnInit(): void {
    this.backLinkService.show();
  }

  submitEmail(): void {
    // clears the error of a previous rejected request so that the same email can be submitted again
    this.form.get('email').updateValueAndValidity();

    if (this.form.valid) {
      this.isSubmitDisabled.set(true);
      this.operatorUsersRegistrationService
        .sendVerificationEmail({ email: this.form.get('email').value })
        .pipe(
          catchTooManyRequests(() => {
            this.form
              .get('email')
              .setErrors({ tooManyRequests: 'You have requested too many verification emails. Try again later' });
            this.isSummaryDisplayed.set(true);
            this.isSubmitDisabled.set(false);
            return EMPTY;
          }),
        )
        .subscribe(() => {
          this.isVerificationSent.set(true);
          this.backLinkService.hide();
        });
    } else {
      this.isSummaryDisplayed.set(true);
    }
  }
}
