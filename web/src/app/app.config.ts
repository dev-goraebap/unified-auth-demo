import { ApplicationConfig, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideClientHydration } from '@angular/platform-browser';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './shared/session/auth-interceptor';
import { restoreSession } from './shared/session/session-restore';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideClientHydration(),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    // 앱 부팅 시 RFT 쿠키로 세션 복원(브라우저에서만).
    provideAppInitializer(restoreSession),
  ],
};
