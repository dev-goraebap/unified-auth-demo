import { isPlatformBrowser } from '@angular/common';
import { Component, PLATFORM_ID, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AccountApi } from '../../shared/api/account-api';
import { AuthApi } from '../../shared/api/auth-api';
import { AccountsResponse, SocialProvider } from '../../shared/api/dto';
import { SessionStore } from '../../shared/session/session-store';

const ALL_SOCIALS: { provider: SocialProvider; label: string }[] = [
  { provider: 'KAKAO', label: '카카오' },
  { provider: 'GOOGLE', label: 'Google' },
];

/** 내 계정 — DI 앵커로 묶인 로컬·소셜 계정을 한눈에 보고, 소셜을 추가 연결한다(실제 OAuth). */
@Component({
  selector: 'app-accounts-page',
  imports: [],
  template: `
    <div class="mx-auto max-w-md">
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">내 계정</h1>
          <p class="text-sm text-gray-500">{{ session.user()?.name }} 님</p>
        </div>
        <button type="button" (click)="logout()"
                class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
          로그아웃
        </button>
      </div>

      @if (error()) {
        <p class="mb-4 text-sm text-red-600">{{ error() }}</p>
      }

      @if (accounts(); as acc) {
        <section class="mb-4 rounded-xl border border-gray-200 p-4">
          <h2 class="mb-2 text-sm font-semibold text-gray-700">로컬 계정</h2>
          @if (acc.local) {
            <p class="text-sm text-gray-900">아이디: <b>{{ acc.local.loginId }}</b></p>
          } @else {
            <p class="text-sm text-gray-400">연결된 로컬 계정 없음</p>
          }
        </section>

        <section class="rounded-xl border border-gray-200 p-4">
          <h2 class="mb-3 text-sm font-semibold text-gray-700">소셜 계정</h2>
          <ul class="space-y-2">
            @for (s of allSocials; track s.provider) {
              <li class="flex items-center justify-between gap-2">
                <span class="text-sm text-gray-900">{{ s.label }}</span>
                @if (unlinkTarget() === s.provider) {
                  <span class="flex items-center gap-2">
                    <span class="text-xs text-gray-500">연동을 해제할까요?</span>
                    <button type="button" (click)="confirmUnlink(s.provider)" [disabled]="loading()"
                            class="rounded-lg bg-red-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50">
                      해제
                    </button>
                    <button type="button" (click)="unlinkTarget.set(null)"
                            class="rounded-lg border border-gray-300 px-2.5 py-1 text-xs text-gray-600 hover:bg-gray-50">
                      취소
                    </button>
                  </span>
                } @else if (isLinked(acc, s.provider)) {
                  <span class="flex items-center gap-2">
                    <span class="rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">연결됨</span>
                    <button type="button" (click)="unlinkTarget.set(s.provider)" [disabled]="loading()"
                            class="text-xs text-gray-400 hover:text-red-600 hover:underline disabled:opacity-50">
                      해제
                    </button>
                  </span>
                } @else {
                  <button type="button" (click)="linkSocial(s.provider)" [disabled]="loading()"
                          class="rounded-lg bg-indigo-600 px-3 py-1 text-xs font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">
                    연결
                  </button>
                }
              </li>
            }
          </ul>
        </section>
      } @else {
        <p class="text-sm text-gray-400">불러오는 중…</p>
      }
    </div>
  `,
})
export class AccountsPage {
  private readonly accountApi = inject(AccountApi);
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);
  readonly session = inject(SessionStore);

  readonly allSocials = ALL_SOCIALS;
  readonly accounts = signal<AccountsResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly unlinkTarget = signal<SocialProvider | null>(null);

  constructor() {
    if (isPlatformBrowser(inject(PLATFORM_ID))) {
      this.load();
    }
  }

  private load(): void {
    this.accountApi.myAccounts().subscribe({
      next: (acc) => this.accounts.set(acc),
      error: () => this.error.set('계정 정보를 불러오지 못했습니다.'),
    });
  }

  isLinked(acc: AccountsResponse, provider: SocialProvider): boolean {
    return acc.socials.some((s) => s.provider === provider);
  }

  /** 실제 제공자 인가 페이지로 리다이렉트. 콜백에서 현재 계정에 연결된다(/auth/callback). */
  linkSocial(provider: SocialProvider): void {
    this.loading.set(true);
    this.error.set(null);
    this.authApi.socialAuthorizeUrl(provider).subscribe({
      next: (res) => {
        window.location.href = res.authorizeUrl;
      },
      error: () => {
        this.loading.set(false);
        this.error.set('소셜 연결을 시작하지 못했습니다.');
      },
    });
  }

  /** 소셜 연동 해제. 로컬(ID/PW) 계정은 항상 남으므로 안전. */
  confirmUnlink(provider: SocialProvider): void {
    this.loading.set(true);
    this.error.set(null);
    this.authApi.socialUnlink(provider).subscribe({
      next: () => {
        this.unlinkTarget.set(null);
        this.loading.set(false);
        this.load();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? '연동 해제에 실패했습니다.');
      },
    });
  }

  logout(): void {
    this.authApi.logout().subscribe({
      next: () => this.afterLogout(),
      error: () => this.afterLogout(),
    });
  }

  private afterLogout(): void {
    this.session.clear();
    this.router.navigate(['/login']);
  }
}
