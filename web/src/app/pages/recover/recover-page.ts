import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../shared/api/auth-api';
import { MockVerificationDialog, VerifiedResult } from '../../features/mock-verification/mock-verification-dialog';

/**
 * 아이디 찾기 / 비밀번호 재설정 — PASS 우선. 본인인증으로 신원을 확정한 뒤
 * 로그인 아이디를 보여주고, 같은 화면에서 새 비밀번호로 재설정할 수 있다.
 */
@Component({
  selector: 'app-recover-page',
  imports: [FormsModule, RouterLink, MockVerificationDialog],
  template: `
    <div class="mx-auto max-w-sm">
      <h1 class="mb-1 text-2xl font-bold text-gray-900">아이디 찾기 · 비밀번호 재설정</h1>
      <p class="mb-6 text-sm text-gray-500">본인인증 후 아이디를 확인하고, 필요하면 비밀번호를 새로 설정합니다.</p>

      @if (!loginId()) {
        @if (error()) {
          <p class="mb-3 text-sm text-red-600">{{ error() }}</p>
        }
        <button type="button" (click)="showDialog.set(true)"
                class="w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700">
          본인인증 하기
        </button>
      } @else {
        <div class="mb-4 rounded-lg bg-green-50 px-3 py-3 text-sm text-green-800">
          회원님의 아이디는 <b class="text-base">{{ loginId() }}</b> 입니다.
        </div>

        @if (resetDone()) {
          <div class="mb-4 rounded-lg bg-indigo-50 px-3 py-2 text-sm text-indigo-700">
            ✓ 비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.
          </div>
          <a routerLink="/login"
             class="block w-full rounded-lg bg-indigo-600 px-4 py-2 text-center text-sm font-semibold text-white hover:bg-indigo-700">
            로그인하러 가기
          </a>
        } @else {
          <p class="mb-2 text-sm font-medium text-gray-700">비밀번호 재설정 (선택)</p>
          <form (ngSubmit)="resetPassword()" class="space-y-3">
            <input name="newPassword" type="password" [(ngModel)]="newPassword" placeholder="새 비밀번호 (8자 이상)"
                   class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none" />
            <input name="confirmPassword" type="password" [(ngModel)]="confirmPassword" placeholder="새 비밀번호 확인"
                   class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none" />
            @if (error()) {
              <p class="text-sm text-red-600">{{ error() }}</p>
            }
            <button type="submit" [disabled]="loading()"
                    class="w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">
              {{ loading() ? '처리 중…' : '비밀번호 변경' }}
            </button>
          </form>
        }
      }

      <p class="mt-6 text-center text-sm text-gray-500">
        <a routerLink="/login" class="font-semibold text-indigo-600 hover:underline">로그인으로 돌아가기</a>
      </p>
    </div>

    @if (showDialog()) {
      <app-mock-verification-dialog
        (verified)="onVerified($event)"
        (cancelled)="showDialog.set(false)" />
    }
  `,
})
export class RecoverPage {
  private readonly authApi = inject(AuthApi);

  private reference: string | null = null;
  readonly loginId = signal<string | null>(null);
  readonly showDialog = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly resetDone = signal(false);

  newPassword = '';
  confirmPassword = '';

  onVerified(result: VerifiedResult): void {
    this.showDialog.set(false);
    this.error.set(null);
    this.reference = result.reference;
    this.authApi.recoverId(result.reference).subscribe({
      next: (res) => this.loginId.set(res.loginId),
      error: (err) => this.error.set(err?.error?.message ?? '가입된 계정을 찾지 못했습니다.'),
    });
  }

  resetPassword(): void {
    if (!this.reference) return;
    if (this.newPassword.length < 8) {
      this.error.set('새 비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.error.set('비밀번호 확인이 일치하지 않습니다.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.authApi.resetPassword(this.reference, this.newPassword).subscribe({
      next: () => {
        this.loading.set(false);
        this.resetDone.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? '비밀번호 변경에 실패했습니다.');
      },
    });
  }
}
