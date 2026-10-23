import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { Button, Card, Field, ScreenShell, UiText } from '../../components/Ui';
import {
  ApiClientError,
  apiClient,
  campusApi,
  isTwoFactorChallenge,
  type AssistantLocale,
} from '../../api/client';
import { tokens } from '../../design/tokens';
import type { UserRole } from '../../navigation/routes';
import type { MobileScreenProps } from '../../navigation/types';

const TWO_FACTOR_CODE_LENGTH = 6;
const STUDENT_ONLY_MESSAGE = 'The mobile course demo currently supports the student role. Use the web portal for lecturer or admin work.';

type TwoFactorCopy = {
  title: string;
  intro: string;
  codeLabel: string;
  codePlaceholder: string;
  verify: string;
  back: string;
  invalidCode: string;
  expiredChallenge: string;
  locked: string;
  rateLimited: string;
  failed: string;
};

const twoFactorCopy: Record<AssistantLocale, TwoFactorCopy> = {
  vi: {
    title: 'Xác thực hai bước',
    intro: 'Nhập mã gồm 6 chữ số đã gửi đến email của bạn.',
    codeLabel: 'Mã xác thực',
    codePlaceholder: '123456',
    verify: 'Xác nhận',
    back: 'Quay lại đăng nhập',
    invalidCode: 'Mã xác thực không đúng. Kiểm tra lại mã trong email rồi thử lại.',
    expiredChallenge: 'Phiên xác thực đã hết hạn. Đăng nhập lại để nhận mã mới.',
    locked: 'Bạn đã nhập sai quá nhiều lần. Đăng nhập lại để nhận mã mới.',
    rateLimited: 'Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại.',
    failed: 'Không thể xác thực mã. Vui lòng thử lại.',
  },
  en: {
    title: 'Two-step verification',
    intro: 'Enter the 6-digit code sent to your email.',
    codeLabel: 'Verification code',
    codePlaceholder: '123456',
    verify: 'Verify',
    back: 'Back to sign in',
    invalidCode: 'The code is not correct. Check the code in your email and try again.',
    expiredChallenge: 'This verification session has expired. Sign in again to get a new code.',
    locked: 'Too many incorrect attempts. Sign in again to get a new code.',
    rateLimited: 'Too many attempts. Wait a moment and try again.',
    failed: 'The code could not be verified. Please try again.',
  },
};

function twoFactorErrorMessage(error: unknown, copy: TwoFactorCopy): string {
  if (error instanceof ApiClientError) {
    if (error.status === 429) {
      return copy.rateLimited;
    }
    switch (error.code) {
      case 'TWO_FACTOR_CODE_INVALID':
        return copy.invalidCode;
      case 'TWO_FACTOR_CODE_LOCKED':
        return copy.locked;
      case 'TWO_FACTOR_CODE_EXPIRED':
      case 'TWO_FACTOR_CHALLENGE_INVALID':
        return copy.expiredChallenge;
      default:
        return error.message || copy.failed;
    }
  }
  return copy.failed;
}

function resolveRole(roles: readonly string[] | undefined): UserRole | null {
  const normalizedRoles = new Set((roles ?? []).map((role) => role.toUpperCase()));
  if (normalizedRoles.has('ADMIN')) {
    return 'admin';
  }
  if (normalizedRoles.has('LECTURER') || normalizedRoles.has('FACULTY') || normalizedRoles.has('TEACHER')) {
    return 'lecturer';
  }
  if (normalizedRoles.has('STUDENT')) {
    return 'student';
  }
  return null;
}

export function SignInScreen({ navigation }: MobileScreenProps) {
  const [email, setEmail] = useState('student@campuscore.edu');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [challenge, setChallenge] = useState<{ challengeId: string; email: string | null } | null>(null);
  const [otpCode, setOtpCode] = useState('');
  const [isVerifying, setIsVerifying] = useState(false);
  const [locale, setLocale] = useState<AssistantLocale>('vi');
  const tf = twoFactorCopy[locale];
  const isPreview = apiClient.mode === 'preview';
  const canSubmit = isPreview || (email.trim().length > 0 && password.length > 0);

  const completeStudentSignIn = async (roles: readonly string[] | undefined) => {
    const role = resolveRole(roles);
    if (role !== 'student') {
      await campusApi.logout().catch(() => undefined);
      apiClient.clearAccessToken();
      setError(STUDENT_ONLY_MESSAGE);
      return;
    }
    navigation.completeSignIn(role);
  };

  const handleSubmit = async () => {
    setError(null);
    if (isPreview) {
      navigation.enterPreview();
      return;
    }

    if (!email.trim() || !password) {
      setError('Enter your university email and password to continue.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await campusApi.login(email.trim(), password);
      if (isTwoFactorChallenge(response)) {
        setChallenge({ challengeId: response.challengeId, email: response.email });
        setOtpCode('');
        return;
      }
      if (!response.accessToken) {
        throw new Error('The Java auth response did not include an access token.');
      }
      apiClient.setSessionTokens(response.accessToken, response.refreshToken);
      await completeStudentSignIn(response.user?.roles);
    } catch (signInError) {
      apiClient.clearAccessToken();
      setError(
        signInError instanceof ApiClientError
          ? signInError.message
          : 'Live sign in could not be completed against the Java auth contract.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleVerify = async () => {
    if (!challenge || isVerifying) {
      return;
    }
    const code = otpCode.replace(/\D/g, '');
    if (code.length !== TWO_FACTOR_CODE_LENGTH) {
      setError(tf.invalidCode);
      return;
    }

    setError(null);
    setIsVerifying(true);
    try {
      const response = await campusApi.verifyTwoFactor(challenge.challengeId, code);
      setChallenge(null);
      setOtpCode('');
      await completeStudentSignIn(response.user?.roles);
    } catch (verifyError) {
      setError(twoFactorErrorMessage(verifyError, tf));
    } finally {
      setIsVerifying(false);
    }
  };

  const handleBackToCredentials = () => {
    setChallenge(null);
    setOtpCode('');
    setPassword('');
    setError(null);
  };

  return (
    <ScreenShell
      eyebrow="CampusCore mobile"
      title="Welcome back"
      subtitle="Sign in with your university account."
      contentStyle={styles.content}
    >
      <View style={styles.brandRow}>
        <View style={styles.brandMark}>
          <MaterialCommunityIcons color={tokens.colors.text} name="school-outline" size={22} />
        </View>
        <View style={styles.brandCopy}>
          <UiText variant="headlineSmall">CampusCore</UiText>
          <UiText variant="bodySmall" tone="muted">Academic information portal</UiText>
        </View>
      </View>

      <Card style={styles.formCard}>
        {challenge ? (
          <>
            <View style={styles.localeRow}>
              {(['vi', 'en'] as const).map((option) => (
                <Button
                  key={option}
                  label={option.toUpperCase()}
                  onPress={() => setLocale(option)}
                  variant={locale === option ? 'primary' : 'secondary'}
                  accessibilityLabel={option === 'vi' ? 'Use Vietnamese' : 'Use English'}
                  style={styles.localeButton}
                />
              ))}
            </View>
            <UiText variant="headlineSmall">{tf.title}</UiText>
            <UiText variant="bodySmall" tone="muted" style={styles.formIntro}>
              {challenge.email ? `${tf.intro} (${challenge.email})` : tf.intro}
            </UiText>
            <Field
              autoCapitalize="none"
              autoComplete="one-time-code"
              keyboardType="number-pad"
              label={tf.codeLabel}
              maxLength={TWO_FACTOR_CODE_LENGTH}
              onChangeText={(value) => setOtpCode(value.replace(/\D/g, ''))}
              placeholder={tf.codePlaceholder}
              textContentType="oneTimeCode"
              value={otpCode}
            />
            <Button
              disabled={otpCode.length !== TWO_FACTOR_CODE_LENGTH}
              label={tf.verify}
              loading={isVerifying}
              onPress={handleVerify}
              style={styles.submit}
            />
            <Button
              disabled={isVerifying}
              label={tf.back}
              onPress={handleBackToCredentials}
              variant="text"
              style={styles.backButton}
            />
            {error ? (
              <View accessibilityLiveRegion="assertive" accessibilityRole="alert" style={styles.errorPanel}>
                <UiText variant="bodySmall" tone="error">
                  {error}
                </UiText>
              </View>
            ) : null}
          </>
        ) : (
          <>
            <UiText variant="headlineSmall">Sign in</UiText>
            <UiText variant="bodySmall" tone="muted" style={styles.formIntro}>
              Use your CampusCore account to continue.
            </UiText>
            <Field
              autoCapitalize="none"
              autoComplete="email"
              keyboardType="email-address"
              label="University email"
              onChangeText={setEmail}
              placeholder="you@university.edu.vn"
              value={email}
            />
            <Field
              autoCapitalize="none"
              autoComplete="password"
              label="Password"
              onChangeText={setPassword}
              placeholder="Enter your password"
              secureTextEntry
              value={password}
            />
            <Button
              disabled={!canSubmit}
              label={isPreview ? 'Explore preview' : 'Sign in'}
              loading={isSubmitting}
              onPress={handleSubmit}
              style={styles.submit}
            />
            {error ? (
              <View accessibilityLiveRegion="assertive" accessibilityRole="alert" style={styles.errorPanel}>
                <UiText variant="bodySmall" tone="error">
                  {error}
                </UiText>
              </View>
            ) : null}
          </>
        )}
      </Card>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  content: { paddingTop: tokens.spacing.sm },
  brandRow: { alignItems: 'center', borderBottomColor: tokens.colors.outlineVariant, borderBottomWidth: 1, flexDirection: 'row', marginBottom: tokens.spacing.lg, paddingBottom: tokens.spacing.md },
  brandMark: { alignItems: 'center', backgroundColor: tokens.colors.accent, borderRadius: tokens.radii.control, height: 44, justifyContent: 'center', width: 44 },
  brandCopy: { flex: 1, marginLeft: tokens.spacing.sm },
  formCard: { padding: tokens.spacing.lg },
  formIntro: { marginBottom: tokens.spacing.lg, marginTop: tokens.spacing.xs },
  submit: { marginTop: tokens.spacing.sm },
  localeRow: { flexDirection: 'row', gap: tokens.spacing.xs, marginBottom: tokens.spacing.md },
  localeButton: { flex: 1 },
  backButton: { alignSelf: 'center', marginTop: tokens.spacing.xs },
  errorPanel: { backgroundColor: '#FFF1F0', borderColor: '#F3C7C3', borderLeftColor: tokens.colors.error, borderLeftWidth: 3, borderRadius: tokens.radii.control, borderWidth: 1, marginTop: tokens.spacing.md, padding: tokens.spacing.sm },
});
