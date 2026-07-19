import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionStore } from './session-store';

/** 인증된 세션이 없으면 로그인으로 보낸다. */
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionStore);
  const router = inject(Router);
  return session.isAuthenticated() ? true : router.createUrlTree(['/login']);
};
