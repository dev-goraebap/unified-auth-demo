/** 백엔드 DTO에 대응하는 타입(서버 application/auth·account 참고). */

export type Gender = 'M' | 'F';
export type SocialProvider = 'KAKAO' | 'NAVER' | 'GOOGLE';

/** 인증 성공 응답. accessToken은 메모리 보관, RFT는 httpOnly 쿠키(바디에 없음). */
export interface AuthResponse {
  userId: string;
  name: string;
  accessToken: string;
  accessTokenExpiresAt: string;
}

export interface MockVerificationStartRequest {
  name: string;
  birthDate: string; // yyyy-MM-dd
  gender: Gender;
  phone?: string;
}

export interface SignupRequest {
  reference: string;
  loginId: string;
  password: string;
}

export interface LoginRequest {
  loginId: string;
  password: string;
  /** 로그인 상태 유지 — true면 RFT 쿠키를 영속(브라우저 종료 후에도 유지)으로. 생략 시 세션 쿠키. */
  rememberMe?: boolean;
}

export interface AuthorizeResponse {
  authorizeUrl: string;
}

/** 소셜 OAuth 콜백 결과. AUTHENTICATED면 user(로그인 완료), 아니면 ticket(연결 필요). */
export interface SocialCallbackResponse {
  status: 'AUTHENTICATED' | 'VERIFICATION_REQUIRED';
  user: AuthResponse | null;
  ticket: string | null;
}

/**
 * 소셜 /complete 결과(이 단계에선 아직 연결/가입 안 함).
 * - AUTHENTICATED: 이미 연결된 소셜(멱등) → 로그인 완료(user)
 * - SIGNUP_REQUIRED: 신규 → ID/PW 입력 필요
 * - LINK_REQUIRED: 기존 회원 → "name 님 계정에 연동?" 확인 필요
 */
export interface SocialCompleteResponse {
  status: 'AUTHENTICATED' | 'SIGNUP_REQUIRED' | 'LINK_REQUIRED';
  user: AuthResponse | null;
  name: string | null;
}

/** 아이디 찾기 결과. */
export interface RecoverIdResponse {
  loginId: string;
}

/** 소셜 회원가입 요청 — 소셜정보(티켓) + 본인인증(reference) + ID/PW. */
export interface SocialSignupRequest {
  ticket: string;
  reference: string;
  loginId: string;
  password: string;
}

export interface AccountsResponse {
  userId: string;
  name: string;
  local: { loginId: string } | null;
  socials: { provider: SocialProvider }[];
}
