import { Component, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { VerificationApi } from '../../shared/api/verification-api';
import { Gender } from '../../shared/api/dto';

export interface VerifiedResult {
  reference: string;
  name: string;
}

/**
 * (데모) Mock 본인인증 창 — 애그리게이터(PASS) SDK 팝업을 흉내낸다(ADR-0004).
 * 이름·생년월일·성별·휴대폰을 받아 서버에 임시저장하고 reference를 발급받아 부모에 전달한다.
 * 실제 연동에서는 이 창이 통신사 PASS 화면으로 대체된다.
 */
@Component({
  selector: 'app-mock-verification-dialog',
  imports: [FormsModule],
  template: `
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
        <div class="mb-4 flex items-center gap-2">
          <span class="rounded-md bg-indigo-600 px-2 py-1 text-xs font-bold text-white">PASS</span>
          <h2 class="text-lg font-semibold text-gray-900">본인인증 (데모)</h2>
        </div>
        <p class="mb-4 text-sm text-gray-500">실제 서비스에서는 통신사 인증 화면이 열립니다.</p>

        <form (ngSubmit)="submit()" class="space-y-3">
          <label class="block text-sm">
            <span class="mb-1 block font-medium text-gray-700">이름</span>
            <input name="name" [(ngModel)]="name" required
                   class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none" />
          </label>
          <label class="block text-sm">
            <span class="mb-1 block font-medium text-gray-700">생년월일</span>
            <input name="birthDate" type="date" [(ngModel)]="birthDate" required
                   class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none" />
          </label>
          <label class="block text-sm">
            <span class="mb-1 block font-medium text-gray-700">성별</span>
            <select name="gender" [(ngModel)]="gender"
                    class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none">
              <option value="M">남성</option>
              <option value="F">여성</option>
            </select>
          </label>
          <label class="block text-sm">
            <span class="mb-1 block font-medium text-gray-700">휴대폰번호</span>
            <input name="phone" [(ngModel)]="phone" placeholder="01012345678"
                   class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none" />
          </label>

          @if (error()) {
            <p class="text-sm text-red-600">{{ error() }}</p>
          }

          <div class="flex gap-2 pt-2">
            <button type="button" (click)="cancelled.emit()" [disabled]="loading()"
                    class="flex-1 rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
              취소
            </button>
            <button type="submit" [disabled]="loading()"
                    class="flex-1 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">
              {{ loading() ? '인증 중…' : '인증하기' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
})
export class MockVerificationDialog {
  readonly verified = output<VerifiedResult>();
  readonly cancelled = output<void>();

  name = '';
  birthDate = '';
  gender: Gender = 'M';
  phone = '';

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor(private readonly verificationApi: VerificationApi) {}

  submit(): void {
    if (!this.name.trim() || !this.birthDate) {
      this.error.set('이름과 생년월일은 필수입니다.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.verificationApi
      .start({ name: this.name.trim(), birthDate: this.birthDate, gender: this.gender, phone: this.phone || undefined })
      .subscribe({
        next: (res) => {
          this.loading.set(false);
          this.verified.emit({ reference: res.reference, name: this.name.trim() });
        },
        error: () => {
          this.loading.set(false);
          this.error.set('본인인증에 실패했습니다. 다시 시도해 주세요.');
        },
      });
  }
}
