import { PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../api/auth-api';
import { SessionStore } from './session-store';

/**
 * 앱 부팅 시 조용한 세션 복원(ADR-0006). Access Token은 메모리라 새로고침에 사라지므로,
 * httpOnly RFT 쿠키로 refresh를 시도해 세션을 되살린다. 브라우저에서만 수행하고,
 * 실패(미로그인)해도 부팅을 막지 않는다.
 */
export function restoreSession(): Promise<void> | void {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformBrowser(platformId)) {
    return;
  }
  const authApi = inject(AuthApi);
  const session = inject(SessionStore);
  return firstValueFrom(authApi.refresh())
    .then((auth) => session.set(auth))
    .catch(() => {
      /* 유효한 RFT 없음 = 미로그인. 정상 흐름. */
    });
}
