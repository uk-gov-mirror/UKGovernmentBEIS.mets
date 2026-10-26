import { Pipe, PipeTransform } from '@angular/core';

import { RequestDetailsDTO } from 'pmrv-api';

@Pipe({
  name: 'workflowStatus',
  standalone: false,
})
export class WorkflowStatusPipe implements PipeTransform {
  transform(type: RequestDetailsDTO['requestStatus']): string {
    switch (type) {
      case 'APPROVED':
        return 'Approved';
      case 'CANCELLED':
        return 'Cancelled';
      case 'COMPLETED':
        return 'Completed';
      case 'IN_PROGRESS':
        return 'In Progress';
      case 'REJECTED':
        return 'Rejected';
      case 'WITHDRAWN':
        return 'Withdrawn';
      case 'CLOSED':
        return 'Closed';
      case 'DEEMED_WITHDRAWN':
        return 'Deemed withdrawn';
      case 'EXEMPT':
        return 'Exempt';
      case 'MIGRATED':
        return 'Migrated';
      case 'NOT_REQUIRED':
        return 'Not required';
      default:
        return null;
    }
  }
}
