import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { MockVerificationStartRequest } from './dto';

/**
 * (데모) Mock 본인인증 API. 실제 연동에서는 이 호출이 사라지고 프론트가 애그리게이터 SDK를
 * 직접 열어 reference(receipt_id/identityVerificationId)를 받는다(ADR-0004).
 */
@Injectable({ providedIn: 'root' })
export class VerificationApi {
  private readonly http = inject(HttpClient);

  /** 가짜 인증창 제출 → reference 발급. */
  start(body: MockVerificationStartRequest): Observable<{ reference: string }> {
    return this.http.post<{ reference: string }>(`${API_BASE_URL}/api/verification/mock/start`, body);
  }
}
