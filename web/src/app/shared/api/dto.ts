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

export interface AccountsResponse {
  userId: string;
  name: string;
  local: { loginId: string } | null;
  socials: { provider: SocialProvider }[];
}
