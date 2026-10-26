import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { RouterTestingModule } from '@angular/router/testing';

import { of, throwError } from 'rxjs';

import { GovukComponentsModule } from 'govuk-components';

import { OperatorUsersRegistrationService } from 'pmrv-api';

import { SharedModule } from '../../shared/shared.module';
import { VerificationSentComponent } from '../verification-sent/verification-sent.component';
import { EmailComponent } from './email.component';

describe('EmailComponent', () => {
  let component: EmailComponent;
  let fixture: ComponentFixture<TestComponent>;

  @Component({
    standalone: false,
    template: '<app-email></app-email>',
  })
  class TestComponent {}

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GovukComponentsModule, SharedModule, ReactiveFormsModule, RouterTestingModule],
      declarations: [TestComponent, EmailComponent, VerificationSentComponent],
      providers: [
        {
          provide: OperatorUsersRegistrationService,
          useValue: { sendVerificationEmail: jest.fn((email) => of(email)) },
        },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TestComponent);
    component = fixture.debugElement.query(By.directive(EmailComponent)).componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should submit only valid emails', () => {
    const element: HTMLElement = fixture.nativeElement;
    const emailInput = fixture.debugElement.query(By.css('input'));
    const button = element.querySelector<HTMLButtonElement>('button');
    const getVerificationSent = () => fixture.debugElement.query(By.directive(VerificationSentComponent));

    const setValueAndSubmit = (value) => {
      emailInput.triggerEventHandler('input', { target: { value } });
      button.click();
      fixture.detectChanges();
    };

    setValueAndSubmit('');
    setValueAndSubmit('asd');
    setValueAndSubmit('a'.repeat(300));
    expect(getVerificationSent());

    setValueAndSubmit('test@example.com');
    expect(getVerificationSent());
  });

  it('should display an error when too many emails have been requested and allow to request again', () => {
    const operatorUsersRegistrationService = TestBed.inject(OperatorUsersRegistrationService);
    jest
      .spyOn(operatorUsersRegistrationService, 'sendVerificationEmail')
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 429 })));
    const element: HTMLElement = fixture.nativeElement;
    const button = element.querySelector<HTMLButtonElement>('button');
    const getVerificationSent = () => fixture.debugElement.query(By.directive(VerificationSentComponent));

    fixture.debugElement.query(By.css('input')).triggerEventHandler('input', { target: { value: 'test@example.com' } });
    button.click();
    fixture.detectChanges();

    const tooManyRequestsError = 'You have requested too many verification emails. Try again later';
    const errorSummaryList = Array.from(element.querySelectorAll('.govuk-error-summary__list a')).map((a) =>
      a.textContent.trim(),
    );
    expect(errorSummaryList).toEqual([tooManyRequestsError]);
    expect(element.querySelector('.govuk-error-message').textContent).toContain(tooManyRequestsError);
    expect(button.disabled).toBe(false);
    expect(getVerificationSent()).toBeNull();

    button.click();
    fixture.detectChanges();

    expect(operatorUsersRegistrationService.sendVerificationEmail).toHaveBeenCalledTimes(2);
    expect(getVerificationSent()).not.toBeNull();
  });
});
