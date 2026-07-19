import { isPlatformBrowser } from '@angular/common';
import { Component, PLATFORM_ID, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../shared/api/auth-api';
import { AuthResponse, SocialProvider } from '../../shared/api/dto';
import { SessionStore } from '../../shared/session/session-store';
import { MockVerificationDialog, VerifiedResult } from '../../features/mock-verification/mock-verification-dialog';

const PROVIDER_LABEL: Record<SocialProvider, string> = { KAKAO: '카카오', GOOGLE: 'Google', NAVER: '네이버' };

/**
 * 소셜 OAuth 콜백 처리(카카오·구글). 제공자가 리다이렉트한 code/state를 백엔드에 넘겨:
 * <ul>
 *   <li>이미 연결된 소셜 → 로그인 완료</li>
 *   <li>미연결 + 로그인 상태 → "현재 계정에 연결하시겠습니까?" 확인 후 연결</li>
 *   <li>미연결 + 비로그인 → PASS 본인인증
 *      → 기존 회원: "○○ 님 계정에 연동하시겠습니까?" 확인 후 연결
 *      → 신규: 일반 회원가입과 동일하게 ID/PW 입력 후 가입(소셜정보 + ID/PW)</li>
 * </ul>
 */
@Component({
  selector: 'app-oauth-callback-page',
  imports: [FormsModule, RouterLink, MockVerificationDialog],
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

    @if (phase() === 'need-link-confirm') {
      <div class="mx-auto max-w-sm text-center">
        <div class="mb-2 text-3xl">🔗</div>
        <h1 class="mb-2 text-xl font-bold text-gray-900">{{ providerLabel() }} 연동</h1>
        <p class="mb-6 text-sm text-gray-600">{{ confirmMessage() }}</p>
        @if (error()) {
          <p class="mb-3 text-sm text-red-600">{{ error() }}</p>
        }
        <div class="flex gap-2">
          <button type="button" (click)="cancel()" [disabled]="loading()"
                  class="flex-1 rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            취소
          </button>
          <button type="button" (click)="confirmLink()" [disabled]="loading()"
                  class="flex-1 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">
            {{ loading() ? '처리 중…' : '연동하기' }}
          </button>
        </div>
      </div>
    }

    @if (phase() === 'need-signup') {
      <div class="mx-auto max-w-sm">
        <h1 class="mb-1 text-2xl font-bold text-gray-900">회원가입</h1>
        <p class="mb-6 text-sm text-gray-500">처음 오셨네요. 아이디·비밀번호를 설정하면 소셜 계정과 함께 가입됩니다.</p>

        <div class="mb-4 rounded-lg bg-green-50 px-3 py-2 text-sm text-green-700">
          ✓ <b>{{ verifiedName() }}</b> 님, 본인인증 완료
        </div>
        <form (ngSubmit)="signup()" class="space-y-3">
          <input name="loginId" [(ngModel)]="loginId" placeholder="아이디"
                 class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none" />
          <input name="password" type="password" [(ngModel)]="password" placeholder="비밀번호 (8자 이상)"
                 class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none" />
          @if (error()) {
            <p class="text-sm text-red-600">{{ error() }}</p>
          }
          <button type="submit" [disabled]="loading()"
                  class="w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">
            {{ loading() ? '처리 중…' : '가입 완료' }}
          </button>
        </form>
      </div>
    }
  `,
})
export class OAuthCallbackPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionStore);

  readonly phase = signal<'processing' | 'need-pass' | 'need-link-confirm' | 'need-signup' | 'error'>('processing');
  readonly error = signal<string | null>(null);
  private provider: SocialProvider = 'KAKAO';
  private ticket: string | null = null;

  // 연동 확인 단계
  private linkMode: 'merge' | 'loggedin' = 'merge';
  readonly confirmMessage = signal<string>('');
  readonly providerLabel = signal<string>('');

  // 신규 소셜 회원가입 단계
  private reference: string | null = null;
  readonly verifiedName = signal<string | null>(null);
  loginId = '';
  password = '';
  readonly loading = signal(false);

  constructor() {
    if (isPlatformBrowser(inject(PLATFORM_ID))) {
      this.process();
    }
  }

  private process(): void {
    this.provider = (this.route.snapshot.paramMap.get('provider') ?? '').toUpperCase() as SocialProvider;
    this.providerLabel.set(PROVIDER_LABEL[this.provider] ?? this.provider);
    const code = this.route.snapshot.queryParamMap.get('code');
    const state = this.route.snapshot.queryParamMap.get('state');
    const oauthError = this.route.snapshot.queryParamMap.get('error');

    if (oauthError || !code || !state) {
      this.fail(oauthError ? '소셜 로그인이 취소되었습니다.' : '잘못된 콜백 요청입니다.');
      return;
    }

    this.authApi.socialCallback(this.provider, code, state).subscribe({
      next: (res) => {
        if (res.status === 'AUTHENTICATED' && res.user) {
          this.done(res.user);
        } else if (res.ticket) {
          this.ticket = res.ticket;
          if (this.session.isAuthenticated()) {
            // 로그인 상태 → 현재 계정에 연결할지 확인받는다(#2).
            this.linkMode = 'loggedin';
            this.confirmMessage.set(`${this.providerLabel()} 계정을 현재 로그인한 계정에 연결하시겠습니까?`);
            this.phase.set('need-link-confirm');
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
      next: (res) => {
        if (res.status === 'AUTHENTICATED' && res.user) {
          this.done(res.user); // 이미 연결된 소셜(멱등)
        } else if (res.status === 'LINK_REQUIRED') {
          // 기존 회원 → 병합 확인받는다(#1).
          this.reference = result.reference;
          this.linkMode = 'merge';
          this.confirmMessage.set(`이미 '${res.name}' 님 계정이 있습니다. 이 계정에 ${this.providerLabel()}를 연동하시겠습니까?`);
          this.phase.set('need-link-confirm');
        } else {
          // 신규 → ID/PW 입력받아 소셜 회원가입
          this.reference = result.reference;
          this.verifiedName.set(result.name);
          this.phase.set('need-signup');
        }
      },
      error: (err) => this.fail(err?.error?.message ?? '소셜 연결에 실패했습니다.'),
    });
  }

  confirmLink(): void {
    if (!this.ticket) return;
    this.loading.set(true);
    this.error.set(null);
    const request$ =
      this.linkMode === 'loggedin'
        ? this.authApi.socialLink(this.ticket)
        : this.authApi.socialConfirmLink(this.ticket, this.reference!);
    request$.subscribe({
      next: (auth) => {
        this.loading.set(false);
        this.done(auth);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? '연동에 실패했습니다.');
      },
    });
  }

  signup(): void {
    if (!this.ticket || !this.reference) return;
    if (!this.loginId || this.password.length < 8) {
      this.error.set('아이디와 8자 이상 비밀번호를 입력해 주세요.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.authApi
      .socialSignup({ ticket: this.ticket, reference: this.reference, loginId: this.loginId, password: this.password })
      .subscribe({
        next: (auth) => {
          this.loading.set(false);
          this.done(auth);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.error?.message ?? '가입에 실패했습니다.');
        },
      });
  }

  cancel(): void {
    this.router.navigate([this.session.isAuthenticated() ? '/accounts' : '/login']);
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
