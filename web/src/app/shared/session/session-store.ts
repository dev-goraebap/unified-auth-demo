import { Injectable, computed, signal } from '@angular/core';
import { AuthResponse } from '../api/dto';

/**
 * 현재 세션(ADR-0006). Access Token은 <b>메모리</b>에만 둔다(localStorage 미사용, XSS 노출 최소화).
 * RFT는 httpOnly 쿠키라 JS가 접근하지 않는다. 새로고침 시 세션은 초기화되고, 앱 부팅 때
 * 조용한 refresh(쿠키 기반)로 복원한다.
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly _accessToken = signal<string | null>(null);
  private readonly _user = signal<{ userId: string; name: string } | null>(null);

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._accessToken() !== null);

  get accessToken(): string | null {
    return this._accessToken();
  }

  set(auth: AuthResponse): void {
    this._accessToken.set(auth.accessToken);
    this._user.set({ userId: auth.userId, name: auth.name });
  }

  clear(): void {
    this._accessToken.set(null);
    this._user.set(null);
  }
}
