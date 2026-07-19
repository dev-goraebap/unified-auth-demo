import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  AuthResponse,
  LoginRequest,
  SignupRequest,
  SocialLinkResponse,
  SocialLoginResponse,
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

  socialLogin(provider: SocialProvider, providerUserId: string): Observable<SocialLoginResponse> {
    return this.http.post<SocialLoginResponse>(`${this.base}/social/login`, { provider, providerUserId });
  }

  socialLink(provider: SocialProvider, providerUserId: string, reference: string): Observable<SocialLinkResponse> {
    return this.http.post<SocialLinkResponse>(`${this.base}/social/link`, { provider, providerUserId, reference });
  }
}
