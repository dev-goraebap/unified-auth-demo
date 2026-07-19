import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    // 인증 상태에 따라 리다이렉트되는 동적 앱이라 사전렌더 대신 온디맨드 SSR.
    path: '**',
    renderMode: RenderMode.Server,
  },
];
