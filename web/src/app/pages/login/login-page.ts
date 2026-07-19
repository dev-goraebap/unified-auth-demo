import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../shared/api/auth-api';
import { SocialProvider } from '../../shared/api/dto';
import { SessionStore } from '../../shared/session/session-store';
import { MockVerificationDialog, VerifiedResult } from '../../features/mock-verification/mock-verification-dialog';

const SOCIALS: { provider: SocialProvider; label: string; classes: string }[] = [
  { provider: 'KAKAO', label: '카카오로 로그인', classes: 'bg-yellow-300 text-yellow-950 hover:bg-yellow-400' },
  { provider: 'NAVER', label: '네이버로 로그인', classes: 'bg-green-600 text-white hover:bg-green-700' },
  { provider: 'GOOGLE', label: 'Google로 로그인', classes: 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50' },
];

/** 로그인 — 로컬(ID/PW) + 소셜. 소셜이 미연결이면 본인인증 후 연결/가입한다. */
@Component({
  selector: 'app-login-page',
  imports: [FormsModule, RouterLink, MockVerificationDialog],
  template: `
    <div class="mx-auto max-w-sm">
      <h1 class="mb-1 text-2xl font-bold text-gray-900">로그인</h1>
      <p class="mb-6 text-sm text-gray-500">통합 인증 데모</p>

      <form (ngSubmit)="loginLocal()" class="space-y-3">
        <input name="loginId" [(ngModel)]="loginId" placeholder="아이디"
               class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none" />
        <input name="password" type="password" [(ngModel)]="password" placeholder="비밀번호"
               class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none" />
        @if (error()) {
          <p class="text-sm text-red-600">{{ error() }}</p>
        }
        <button type="submit" [disabled]="loading()"
                class="w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">
          {{ loading() ? '처리 중…' : '로그인' }}
        </button>
      </form>

      <div class="my-5 flex items-center gap-3 text-xs text-gray-400">
        <div class="h-px flex-1 bg-gray-200"></div>
        소셜 로그인
        <div class="h-px flex-1 bg-gray-200"></div>
      </div>

      <div class="space-y-2">
        @for (s of socials; track s.provider) {
          <button type="button" (click)="loginSocial(s.provider)" [disabled]="loading()"
                  class="w-full rounded-lg px-4 py-2 text-sm font-semibold disabled:opacity-50 {{ s.classes }}">
            {{ s.label }}
          </button>
        }
      </div>

      <p class="mt-6 text-center text-sm text-gray-500">
        계정이 없으신가요?
        <a routerLink="/signup" class="font-semibold text-indigo-600 hover:underline">회원가입</a>
      </p>
    </div>

    @if (pendingProvider()) {
      <app-mock-verification-dialog
        (verified)="onVerified($event)"
        (cancelled)="pendingProvider.set(null)" />
    }
  `,
})
export class LoginPage {
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);

  readonly socials = SOCIALS;

  constructor() {
    // 부팅 시 refresh로 세션이 복원됐다면(새로고침 등) 로그인 화면 대신 계정으로.
    if (this.session.isAuthenticated()) {
      this.router.navigate(['/accounts']);
    }
  }

  loginId = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pendingProvider = signal<SocialProvider | null>(null);

  private providerUserId(provider: SocialProvider): string {
    // 데모: OAuth 대신 provider별 고정 식별자. 실제로는 OAuth 콜백이 채운다.
    return `${provider.toLowerCase()}-demo`;
  }

  loginLocal(): void {
    if (!this.loginId || !this.password) {
      this.error.set('아이디와 비밀번호를 입력해 주세요.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.authApi.login({ loginId: this.loginId, password: this.password }).subscribe({
      next: (auth) => this.onAuthenticated(auth),
      error: () => {
        this.loading.set(false);
        this.error.set('아이디 또는 비밀번호가 올바르지 않습니다.');
      },
    });
  }

  loginSocial(provider: SocialProvider): void {
    this.loading.set(true);
    this.error.set(null);
    this.authApi.socialLogin(provider, this.providerUserId(provider)).subscribe({
      next: (res) => {
        if (res.status === 'AUTHENTICATED' && res.user) {
          this.onAuthenticated(res.user);
        } else {
          // 미연결 → 본인인증 후 연결/가입.
          this.loading.set(false);
          this.pendingProvider.set(provider);
        }
      },
      error: () => {
        this.loading.set(false);
        this.error.set('소셜 로그인에 실패했습니다.');
      },
    });
  }

  onVerified(result: VerifiedResult): void {
    const provider = this.pendingProvider();
    if (!provider) return;
    this.loading.set(true);
    this.authApi.socialLink(provider, this.providerUserId(provider), result.reference).subscribe({
      next: (res) => {
        this.pendingProvider.set(null);
        this.onAuthenticated(res.user);
      },
      error: () => {
        this.loading.set(false);
        this.pendingProvider.set(null);
        this.error.set('소셜 계정 연결에 실패했습니다.');
      },
    });
  }

  private onAuthenticated(auth: { userId: string; name: string; accessToken: string; accessTokenExpiresAt: string }): void {
    this.session.set(auth);
    this.loading.set(false);
    this.router.navigate(['/accounts']);
  }
}
