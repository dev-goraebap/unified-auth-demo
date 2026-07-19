import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  AuthResponse,
  AuthorizeResponse,
  LoginRequest,
  SignupRequest,
  SocialCallbackResponse,
  SocialProvider,
} from './dto';

/** 인증 API(로컬·소셜·세션). 서버 /api/auth. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/auth`;

  signup(body: SignupRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/signup`, body);
  }

  login(body: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/login`, body);
  }

  refresh(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/refresh`, {});
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/logout`, {});
  }

  /** 소셜 인가 URL 요청(브라우저를 여기로 보낸다). */
  socialAuthorizeUrl(provider: SocialProvider): Observable<AuthorizeResponse> {
    return this.http.get<AuthorizeResponse>(`${this.base}/social/${provider.toLowerCase()}/authorize`);
  }

  /** 콜백 code를 백엔드로 전달 → 로그인 또는 연결 티켓. */
  socialCallback(provider: SocialProvider, code: string, state: string): Observable<SocialCallbackResponse> {
    return this.http.post<SocialCallbackResponse>(`${this.base}/social/callback`, { provider, code, state });
  }

  /** (비로그인) PASS 완료 후 티켓으로 소셜 연결/가입. */
  socialComplete(ticket: string, reference: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/social/complete`, { ticket, reference });
  }

  /** (로그인 상태) 티켓의 소셜을 현재 계정에 연결. */
  socialLink(ticket: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/social/link`, { ticket });
  }
}
