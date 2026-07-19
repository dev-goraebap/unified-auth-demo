import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../shared/api/auth-api';
import { SessionStore } from '../../shared/session/session-store';
import { MockVerificationDialog, VerifiedResult } from '../../features/mock-verification/mock-verification-dialog';

/** 회원가입 — PASS 우선. 본인인증으로 신원을 확정한 뒤 로컬(ID/PW) 계정을 만든다. */
@Component({
  selector: 'app-signup-page',
  imports: [FormsModule, RouterLink, MockVerificationDialog],
  template: `
    <div class="mx-auto max-w-sm">
      <h1 class="mb-1 text-2xl font-bold text-gray-900">회원가입</h1>
      <p class="mb-6 text-sm text-gray-500">본인인증 후 아이디·비밀번호를 설정합니다.</p>

      @if (!reference()) {
        <button type="button" (click)="showDialog.set(true)"
                class="w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700">
          본인인증 하기
        </button>
      } @else {
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
      }

      <p class="mt-6 text-center text-sm text-gray-500">
        이미 계정이 있으신가요?
        <a routerLink="/login" class="font-semibold text-indigo-600 hover:underline">로그인</a>
      </p>
    </div>

    @if (showDialog()) {
      <app-mock-verification-dialog
        (verified)="onVerified($event)"
        (cancelled)="showDialog.set(false)" />
    }
  `,
})
export class SignupPage {
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);

  constructor() {
    if (this.session.isAuthenticated()) {
      this.router.navigate(['/accounts']);
    }
  }

  loginId = '';
  password = '';
  readonly reference = signal<string | null>(null);
  readonly verifiedName = signal<string | null>(null);
  readonly showDialog = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  onVerified(result: VerifiedResult): void {
    this.reference.set(result.reference);
    this.verifiedName.set(result.name);
    this.showDialog.set(false);
  }

  signup(): void {
    const reference = this.reference();
    if (!reference) return;
    if (!this.loginId || this.password.length < 8) {
      this.error.set('아이디와 8자 이상 비밀번호를 입력해 주세요.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.authApi.signup({ reference, loginId: this.loginId, password: this.password }).subscribe({
      next: (auth) => {
        this.session.set(auth);
        this.loading.set(false);
        this.router.navigate(['/accounts']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? '가입에 실패했습니다.');
      },
    });
  }
}
