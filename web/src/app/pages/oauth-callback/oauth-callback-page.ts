import { isPlatformBrowser } from '@angular/common';
import { Component, PLATFORM_ID, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../shared/api/auth-api';
import { AuthResponse, SocialProvider } from '../../shared/api/dto';
import { SessionStore } from '../../shared/session/session-store';
import { MockVerificationDialog, VerifiedResult } from '../../features/mock-verification/mock-verification-dialog';

/**
 * 소셜 OAuth 콜백 처리(카카오·구글). 제공자가 리다이렉트한 code/state를 백엔드에 넘겨:
 * <ul>
 *   <li>이미 연결된 소셜 → 로그인 완료</li>
 *   <li>미연결 + 로그인 상태 → 현재 계정에 바로 연결</li>
 *   <li>미연결 + 비로그인 → PASS 본인인증 후 DI로 연결/가입</li>
 * </ul>
 */
@Component({
  selector: 'app-oauth-callback-page',
  imports: [RouterLink, MockVerificationDialog],
  template: `
    <div class="mx-auto max-w-sm text-center">
      @if (phase() === 'processing') {
        <p class="text-sm text-gray-500">소셜 로그인 처리 중…</p>
      } @else if (phase() === 'error') {
        <p class="mb-3 text-sm text-red-600">{{ error() }}</p>
        <a routerLink="/login" class="text-sm font-semibold text-indigo-600 hover:underline">로그인으로 돌아가기</a>
      }
    </div>

    @if (phase() === 'need-pass') {
      <app-mock-verification-dialog
        (verified)="onVerified($event)"
        (cancelled)="cancel()" />
    }
  `,
})
export class OAuthCallbackPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionStore);

  readonly phase = signal<'processing' | 'need-pass' | 'error'>('processing');
  readonly error = signal<string | null>(null);
  private ticket: string | null = null;

  constructor() {
    if (isPlatformBrowser(inject(PLATFORM_ID))) {
      this.process();
    }
  }

  private process(): void {
    const provider = (this.route.snapshot.paramMap.get('provider') ?? '').toUpperCase() as SocialProvider;
    const code = this.route.snapshot.queryParamMap.get('code');
    const state = this.route.snapshot.queryParamMap.get('state');
    const oauthError = this.route.snapshot.queryParamMap.get('error');

    if (oauthError || !code || !state) {
      this.fail(oauthError ? '소셜 로그인이 취소되었습니다.' : '잘못된 콜백 요청입니다.');
      return;
    }

    this.authApi.socialCallback(provider, code, state).subscribe({
      next: (res) => {
        if (res.status === 'AUTHENTICATED' && res.user) {
          this.done(res.user);
        } else if (res.ticket) {
          this.ticket = res.ticket;
          if (this.session.isAuthenticated()) {
            // 로그인 상태 → 현재 계정에 바로 연결
            this.authApi.socialLink(res.ticket).subscribe({
              next: (auth) => this.done(auth),
              error: (err) => this.fail(err?.error?.message ?? '소셜 연결에 실패했습니다.'),
            });
          } else {
            // 비로그인 → PASS 본인인증 필요
            this.phase.set('need-pass');
          }
        } else {
          this.fail('소셜 로그인 처리에 실패했습니다.');
        }
      },
      error: (err) => this.fail(err?.error?.message ?? '소셜 로그인 처리에 실패했습니다.'),
    });
  }

  onVerified(result: VerifiedResult): void {
    if (!this.ticket) return;
    this.phase.set('processing');
    this.authApi.socialComplete(this.ticket, result.reference).subscribe({
      next: (auth) => this.done(auth),
      error: (err) => this.fail(err?.error?.message ?? '소셜 연결에 실패했습니다.'),
    });
  }

  cancel(): void {
    this.router.navigate(['/login']);
  }

  private done(auth: AuthResponse): void {
    this.session.set(auth);
    this.router.navigate(['/accounts']);
  }

  private fail(message: string): void {
    this.error.set(message);
    this.phase.set('error');
  }
}
