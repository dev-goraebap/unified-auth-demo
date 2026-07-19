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

export interface SocialLoginResponse {
  status: 'AUTHENTICATED' | 'VERIFICATION_REQUIRED';
  user: AuthResponse | null;
}

export interface SocialLinkResponse {
  user: AuthResponse;
  outcome: 'CREATED' | 'MERGED' | 'ALREADY_LINKED';
}

export interface AccountsResponse {
  userId: string;
  name: string;
  local: { loginId: string } | null;
  socials: { provider: SocialProvider }[];
}
