import { ChangeDetectionStrategy, Component, EventEmitter, Input, numberAttribute, Output } from '@angular/core';

import { FileUploadEvent } from '../file-input/file-upload-event';

@Component({
  selector: 'app-file-upload-list',
  standalone: false,
  templateUrl: './file-upload-list.component.html',
  styleUrl: '../multiple-file-input/multiple-file-input.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileUploadListComponent {
  @Input() headerSize: 'm' | 's' = 'm';
  @Input({ transform: numberAttribute }) headingLevel = 2;
  @Input() listTitle: string;
  @Input() files: FileUploadEvent[] = [];
  @Input() isDisabled = false;
  @Output() readonly fileDelete = new EventEmitter<number>();
}
