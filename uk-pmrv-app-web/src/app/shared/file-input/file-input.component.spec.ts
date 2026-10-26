import { HttpErrorResponse, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { RouterTestingModule } from '@angular/router/testing';

import { first, mergeMap, Subject, throwError } from 'rxjs';

import { HttpStatuses } from '@error/http-status';
import { BasePage } from '@testing';

import { GovukComponentsModule } from 'govuk-components';

import { FileUuidDTO } from 'pmrv-api';

import { FileUploadListComponent } from '../file-upload-list/file-upload-list.component';
import { FileInputComponent } from './file-input.component';
import { FileUploadService } from './file-upload.service';
import { FileValidators } from './file-validators';

describe('FileInputComponent', () => {
  let component: FileInputComponent;
  let fixture: ComponentFixture<TestComponent>;
  let hostComponent: TestComponent;
  let page: Page;
  let control: FormControl;

  @Component({
    standalone: false,
    template: `
      <form [formGroup]="form">
        <app-file-input
          formControlName="file"
          [downloadUrl]="getDownloadUrl"
          label="Upload the report"
          listTitle="Report added"
          [headerSize]="headerSize"
          [headingLevel]="headingLevel"></app-file-input>
      </form>
    `,
  })
  class TestComponent {
    form = new FormGroup({ file: new FormControl({ file: new File(['abc'], 'uploaded-file.txt'), uuid: '1234' }) });
    getDownloadUrl = jest.fn((uuid: string) => `/download/${uuid}`);
    headerSize: 'm' | 's' = undefined;
    headingLevel = 2;
  }

  class Page extends BasePage<TestComponent> {
    get fileText() {
      return this.query<HTMLDivElement>('.moj-multi-file-upload__message');
    }

    get downloadLink() {
      return this.query<HTMLAnchorElement>('.govuk-link');
    }

    get delete() {
      return this.query<HTMLButtonElement>('.moj-multi-file-upload__delete');
    }

    get file() {
      return this.query<HTMLInputElement>('input').files.item(0);
    }

    set file(file: File) {
      this.setInputValue('input', file ?? []);
    }

    get input() {
      return this.query<HTMLInputElement>('input');
    }

    get trigger() {
      return this.query<HTMLLabelElement>('label[role="link"]');
    }
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, GovukComponentsModule, RouterTestingModule],
      declarations: [FileInputComponent, TestComponent, FileUploadListComponent],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TestComponent);
    component = fixture.debugElement.query(By.directive(FileInputComponent)).componentInstance;
    hostComponent = fixture.componentInstance;
    page = new Page(fixture);
    control = hostComponent.form.get('file') as FormControl;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display current value', () => {
    expect(page.fileText.textContent.trim()).toEqual('uploaded-file.txt');
  });

  it('should replace the uploaded file', () => {
    page.file = new File(['test content'], 'New file');
    fixture.detectChanges();

    expect(page.fileText.textContent.trim()).toEqual('New file');
    expect(page.downloadLink.textContent.trim()).toEqual('New file');
  });

  it('should validate a file against max size', () => {
    const uploadSubject = new Subject<HttpEvent<FileUuidDTO>>();

    expect(control.touched).toBeFalsy();

    control.setValidators(FileValidators.maxFileSize(1));
    control.setAsyncValidators(TestBed.inject(FileUploadService).upload(() => uploadSubject));
    const file = new File(['test content'], 'Big file');
    jest.spyOn(file, 'size', 'get').mockReturnValue(1024 * 1024 * 1024);
    page.file = file;
    fixture.detectChanges();

    expect(control.touched).toBeTruthy();
    expect(page.fileText.textContent.trim()).toEqual('Big file must be smaller than 1MB');
    expect(control.invalid).toBeTruthy();
    expect(control.errors).toEqual({ 'maxFileSize-0': 'Big file must be smaller than 1MB' });
  });

  it('should display upload progress', () => {
    const uploadSubject = new Subject<HttpEvent<FileUuidDTO>>();

    control.setAsyncValidators(TestBed.inject(FileUploadService).upload(() => uploadSubject));

    control.setValue([{ file: new File(['test content'], 'Existing file'), uuid: 'abcdA' }]);
    page.file = new File(['test content'], 'New file');
    uploadSubject.next({ type: HttpEventType.UploadProgress, loaded: 5, total: 15 });
    fixture.detectChanges();
    expect(page.fileText.textContent.trim()).toEqual('New file33%');

    uploadSubject.next({ type: HttpEventType.UploadProgress, loaded: 10, total: 15 });
    fixture.detectChanges();

    expect(page.fileText.textContent.trim()).toEqual('New file67%');

    uploadSubject.next(new HttpResponse({ status: HttpStatuses.Ok, body: { uuid: 'abcd' } }));
    fixture.detectChanges();

    expect(page.fileText.textContent.trim()).toEqual('New file has been uploaded');
    expect(page.downloadLink.textContent.trim()).toEqual('New file');
  });

  it('should display upload errors', () => {
    const uploadSubject = new Subject<HttpEvent<FileUuidDTO>>();

    control.setAsyncValidators(
      TestBed.inject(FileUploadService).upload(() =>
        uploadSubject.pipe(
          first(),
          mergeMap(() =>
            throwError(
              () =>
                new HttpErrorResponse({
                  status: HttpStatuses.BadRequest,
                  error: { message: 'should not be txt' },
                }),
            ),
          ),
        ),
      ),
    );
    page.file = new File(['test content'], 'New file');
    uploadSubject.next({ type: HttpEventType.UploadProgress, loaded: 10, total: 15 });
    fixture.detectChanges();

    expect(control.invalid).toBeTruthy();
    expect(control.errors).toEqual({ upload: 'New file should not be txt' });
    expect(page.fileText.textContent.trim()).toEqual('New file should not be txt');
  });

  it('should delete a file', () => {
    page.delete.click();
    fixture.detectChanges();

    expect(page.fileText).toBeFalsy();
    expect(hostComponent.form.value.file).toBeNull();
    expect(page.file).toBeNull();

    page.file = new File(['content'], 'file.txt');
    fixture.detectChanges();

    page.delete.click();
    fixture.detectChanges();

    expect(page.fileText).toBeFalsy();
    expect(hostComponent.form.value.file).toBeNull();
    expect(page.file).toBeNull();
  });

  it('should retain the old file when the user cancels adding a new one', () => {
    const file = new File(['test content'], 'new-file.txt');
    page.file = file;
    fixture.detectChanges();

    page.file = null;
    fixture.detectChanges();

    expect(control.value).toEqual({ file, uuid: null, dimensions: null });
  });

  describe('title markup', () => {
    const input = () => page.query<HTMLInputElement>('input');
    const title = () => page.query<HTMLElement>(`[id="l.${input().id}"]`);

    it('should render a plain label when no header size is given', () => {
      expect(title().tagName).toEqual('LABEL');
      expect(title().getAttribute('for')).toEqual(input().id);
      expect(page.queryAll('h2').map((el) => el.textContent.trim())).toEqual(['Report added']);
    });

    it('should render an h2 heading referenced by the input when a header size is given', () => {
      hostComponent.headerSize = 's';
      fixture.detectChanges();

      expect(title().tagName).toEqual('H2');
      expect(title().className).toEqual('govuk-heading-s');
      expect(title().textContent.trim()).toEqual('Upload the report');
      expect(input().getAttribute('aria-labelledby')).toEqual(`l.${input().id} ld.${input().id}`);
    });

    it('should render the title and list title at the given heading level', () => {
      hostComponent.headerSize = 's';
      hostComponent.headingLevel = 3;
      fixture.detectChanges();

      expect(page.queryAll('h2')).toHaveLength(0);
      expect(page.queryAll('h3').map((el) => el.textContent.trim())).toEqual(['Report added', 'Upload the report']);
    });
  });

  describe('speech recognition support', () => {
    it('should expose a visible label as a focusable link that targets the hidden input', () => {
      expect(page.trigger.getAttribute('for')).toEqual(page.input.id);
      expect(page.trigger.getAttribute('tabindex')).toEqual('0');
      expect(page.trigger.getAttribute('aria-labelledby')).toEqual(`l.${page.input.id} ld.${page.input.id}`);
      expect(page.trigger.getAttribute('aria-describedby')).toEqual(`${page.input.id}-hints`);
      expect(page.trigger.textContent.trim()).toEqual('Choose file');
      expect(page.input.getAttribute('tabindex')).toEqual('-1');
    });

    it.each(['Enter', ' '])('should open the file picker on "%s" key', (key) => {
      const clickSpy = jest.spyOn(page.input, 'click').mockImplementation();
      const event = new KeyboardEvent('keydown', { key, cancelable: true });

      page.trigger.dispatchEvent(event);

      expect(clickSpy).toHaveBeenCalledTimes(1);
      expect(event.defaultPrevented).toBeTruthy();
    });

    it('should not be focusable or open the file picker when disabled', () => {
      control.disable();
      fixture.detectChanges();
      const clickSpy = jest.spyOn(page.input, 'click').mockImplementation();

      page.trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', cancelable: true }));

      expect(clickSpy).not.toHaveBeenCalled();
      expect(page.trigger.hasAttribute('tabindex')).toBeFalsy();
      expect(page.trigger.getAttribute('aria-disabled')).toEqual('true');
      expect(page.trigger.classList).toContain('govuk-button--disabled');
    });

    it('should move focus from the hidden input to the trigger', () => {
      page.input.focus();
      fixture.detectChanges();

      expect(document.activeElement).toBe(page.trigger);
      expect(page.trigger.classList).toContain('moj-multi-file-upload--focused');
    });

    it('should move focus to the trigger after deleting the file', () => {
      page.delete.click();
      fixture.detectChanges();

      expect(document.activeElement).toBe(page.trigger);
    });

    it('should mark the control as touched when the trigger loses focus', () => {
      page.trigger.focus();
      page.trigger.blur();

      expect(control.touched).toBeTruthy();
    });

    it('should describe the trigger with the error message when errors are displayed', () => {
      control.setErrors({ required: 'Select a file' });
      fixture.debugElement.query(By.css('form')).triggerEventHandler('submit', {});
      fixture.detectChanges();

      expect(page.trigger.getAttribute('aria-describedby')).toEqual(`${page.input.id}-hints ${page.input.id}-error`);
      expect(page.trigger.classList).toContain('app-file-input__trigger--error');
    });
  });
});
