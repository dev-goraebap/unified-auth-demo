import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SessionStore } from './session-store';

/**
 * 모든 API 요청에 자격증명(RFT httpOnly 쿠키)을 싣고, 세션에 Access Token이 있으면
 * {@code Authorization: Bearer}를 붙인다. refresh 요청엔 access를 붙이지 않는다(쿠키만 사용).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionStore);

  let request = req.clone({ withCredentials: true });

  const token = session.accessToken;
  if (token && !req.url.endsWith('/api/auth/refresh')) {
    request = request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(request);
};
