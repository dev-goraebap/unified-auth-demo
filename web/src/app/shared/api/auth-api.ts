import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  AuthResponse,
  AuthorizeResponse,
  LoginRequest,
  RecoverIdResponse,
  SignupRequest,
  SocialCallbackResponse,
  SocialCompleteResponse,
  SocialProvider,
  SocialSignupRequest,
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

  /** (비로그인) PASS 완료 후 판정 — 기존 회원이면 로그인, 신규면 SIGNUP_REQUIRED. */
  socialComplete(ticket: string, reference: string): Observable<SocialCompleteResponse> {
    return this.http.post<SocialCompleteResponse>(`${this.base}/social/complete`, { ticket, reference });
  }

  /** (비로그인) 신규 소셜 회원가입 — 소셜정보(티켓) + 본인인증 + ID/PW. */
  socialSignup(body: SocialSignupRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/social/signup`, body);
  }

  /** (비로그인) 확인 후 기존 회원 계정에 소셜을 연결하고 로그인. */
  socialConfirmLink(ticket: string, reference: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/social/link-confirm`, { ticket, reference });
  }

  /** (로그인 상태) 티켓의 소셜을 현재 계정에 연결. */
  socialLink(ticket: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/social/link`, { ticket });
  }

  /** (로그인 상태) 소셜 연동 해제. */
  socialUnlink(provider: SocialProvider): Observable<void> {
    return this.http.delete<void>(`${this.base}/social/${provider.toLowerCase()}`);
  }

  /** (비로그인) 아이디 찾기 — PASS 본인인증 reference로 로그인 아이디를 받는다. */
  recoverId(reference: string): Observable<RecoverIdResponse> {
    return this.http.post<RecoverIdResponse>(`${this.base}/recover`, { reference });
  }

  /** (비로그인) 비밀번호 재설정 — PASS 본인인증 reference + 새 비밀번호. */
  resetPassword(reference: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/reset-password`, { reference, newPassword });
  }
}
