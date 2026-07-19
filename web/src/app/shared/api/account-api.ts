import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { AccountsResponse } from './dto';

/** 내 계정(로컬+소셜 통합) 조회 API. 서버 /api/users/me/accounts (jOOQ 읽기). */
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);

  myAccounts(): Observable<AccountsResponse> {
    return this.http.get<AccountsResponse>(`${API_BASE_URL}/api/users/me/accounts`);
  }
}
