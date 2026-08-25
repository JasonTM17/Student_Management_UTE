import { useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { ApiClientError, campusApi } from '../../api/client';
import { Button, Card, Field, ScreenShell, StatePanel, UiText } from '../../components/Ui';
import { tokens } from '../../design/tokens';
import type { MobileScreenProps } from '../../navigation/types';

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiClientError && error.code === 'AUTH_RESEND_THROTTLED') {
    return 'Please wait before requesting another email.';
  }
  if (error instanceof ApiClientError && error.code === 'EMAIL_ALREADY_EXISTS') {
    return 'An account with this email already exists.';
  }
  return fallback;
}

type ChallengeFailureState = 'invalid' | 'expired' | 'attemptsExceeded' | 'unavailable';

function challengeFailure(error: unknown): ChallengeFailureState {
  if (!(error instanceof ApiClientError)) return 'unavailable';
  if (error.code === 'AUTH_CHALLENGE_EXPIRED') return 'expired';
  if (error.code === 'AUTH_CHALLENGE_ATTEMPTS_EXCEEDED') return 'attemptsExceeded';
  if (error.code === 'AUTH_CHALLENGE_INVALID') return 'invalid';
  // Network failures, retryable responses, and server errors are outages, not
  // proof that a one-time token is malformed. Keep the recovery path truthful.
  if (error.status === 0 || error.status >= 500 || error.retryable || !error.code) return 'unavailable';
  return 'invalid';
}

function AuthLinks({ navigation }: MobileScreenProps) {
  return (
    <View style={styles.links}>
      <Button label="Sign in" onPress={() => navigation.navigate('auth.signIn')} variant="text" />
      <Button label="Create account" onPress={() => navigation.navigate('auth.register')} variant="text" />
      <Button label="Forgot password" onPress={() => navigation.navigate('auth.forgotPassword')} variant="text" />
    </View>
  );
}

export function RegisterScreen({ navigation }: MobileScreenProps) {
  const [form, setForm] = useState({ email: '', password: '', confirm: '', firstName: '', lastName: '' });
  const [pending, setPending] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const update = (key: keyof typeof form) => (value: string) => setForm((current) => ({ ...current, [key]: value }));

  const submit = async () => {
    setError(null);
    if (!form.email.trim() || !form.firstName.trim() || !form.lastName.trim() || form.password.length < 8) {
      setError('Enter your name, email, and a password of at least 8 characters.');
      return;
    }
    if (form.password !== form.confirm) {
      setError('The passwords do not match.');
      return;
    }
    setPending(true);
    try {
      await campusApi.register({ email: form.email.trim(), password: form.password, firstName: form.firstName.trim(), lastName: form.lastName.trim() });
      setSuccess(true);
    } catch (requestError) {
      setError(errorMessage(requestError, 'Registration could not be completed right now.'));
    } finally {
      setPending(false);
    }
  };

  return (
    <ScreenShell eyebrow="CampusCore mobile" title={success ? 'Check your inbox' : 'Create account'} subtitle={success ? 'Verify your email, then sign in to create a mobile session.' : 'Student self-registration uses a one-time email verification link.'} contentStyle={styles.content}>
      {success ? <StatePanel kind="empty" title="Verification email sent" description="Open the latest message. The mobile app never logs or places the token in a URL." actionLabel="Verify email" onAction={() => navigation.navigate('auth.verifyEmail')} /> : <Card style={styles.card}>
        <Field label="First name" autoComplete="given-name" onChangeText={update('firstName')} value={form.firstName} />
        <Field label="Last name" autoComplete="family-name" onChangeText={update('lastName')} value={form.lastName} />
        <Field label="Email" autoCapitalize="none" autoComplete="email" keyboardType="email-address" onChangeText={update('email')} value={form.email} />
        <Field label="Password" autoCapitalize="none" autoComplete="password-new" onChangeText={update('password')} secureTextEntry value={form.password} />
        <Field label="Confirm password" autoCapitalize="none" autoComplete="password-new" onChangeText={update('confirm')} secureTextEntry value={form.confirm} />
        <Button label="Create account" loading={pending} onPress={() => void submit()} />
        {error ? <StatePanel kind="error" title={error} /> : null}
      </Card>}
      <AuthLinks navigation={navigation} role="student" selectedThesisTopicId={null} />
    </ScreenShell>
  );
}

export function VerifyEmailScreen({ navigation, authToken }: MobileScreenProps) {
  const [token, setToken] = useState(authToken ?? '');
  const [email, setEmail] = useState('');
  const [state, setState] = useState<'idle' | 'checking' | 'success' | ChallengeFailureState>('idle');
  const [resendState, setResendState] = useState<'idle' | 'resending' | 'success' | 'throttled' | 'unavailable'>('idle');
  const submittedToken = useRef<string | null>(null);

  useEffect(() => {
    if (!authToken || submittedToken.current === authToken) return;
    submittedToken.current = authToken;
    setToken(authToken);
    setState('checking');
    void campusApi.verifyEmail(authToken)
      .then(() => setState('success'))
      .catch((requestError) => setState(challengeFailure(requestError)));
  }, [authToken]);

  const verify = async () => {
    if (!token.trim()) return;
    setState('checking');
    try { await campusApi.verifyEmail(token.trim()); setState('success'); }
    catch (requestError) { setState(challengeFailure(requestError)); }
  };
  const resend = async () => {
    if (!email.trim()) return;
    setResendState('resending');
    try { await campusApi.resendVerification(email.trim()); setResendState('success'); }
    catch (requestError) {
      setResendState(requestError instanceof ApiClientError
        && (requestError.code === 'AUTH_RESEND_THROTTLED' || requestError.code === 'AUTH_RATE_LIMITED')
        ? 'throttled'
        : 'unavailable');
    }
  };

  return <ScreenShell eyebrow="Email verification" title="Verify your email" subtitle="Links expire after 24 hours and are single-use." contentStyle={styles.content}>
    <Card style={styles.card}>
      {state === 'success' ? <StatePanel kind="empty" title="Email verified" description="Return to sign in. Verification never creates a session automatically." actionLabel="Sign in" onAction={() => navigation.navigate('auth.signIn')} /> : <>
        <Field label="Verification token" autoCapitalize="none" autoCorrect={false} onChangeText={setToken} placeholder="Paste token from email" secureTextEntry value={token} />
        <Button label={state === 'checking' ? 'Verifying…' : 'Verify email'} disabled={!token.trim()} loading={state === 'checking'} onPress={() => void verify()} />
        {state === 'invalid' ? <StatePanel kind="error" title="This verification link is invalid" description="Request the latest link below." /> : null}
        {state === 'expired' ? <StatePanel kind="error" title="This verification link has expired" description="Links expire after 24 hours. Request a fresh one below." /> : null}
        {state === 'attemptsExceeded' ? <StatePanel kind="error" title="This verification link was disabled" description="Too many invalid attempts were made. Request a fresh link." /> : null}
        {state === 'unavailable' ? <StatePanel kind="error" title="Verification is temporarily unavailable" description="Try again in a moment. If the link already succeeded, return to sign in." /> : null}
        <View style={styles.divider} />
        <Field label="Email for a new link" autoCapitalize="none" autoComplete="email" keyboardType="email-address" onChangeText={setEmail} value={email} />
        <Button label={resendState === 'resending' ? 'Sending…' : 'Resend verification'} disabled={!email.trim() || resendState === 'resending'} loading={resendState === 'resending'} onPress={() => void resend()} variant="secondary" />
        {resendState === 'success' ? <StatePanel kind="empty" title="Request accepted" description="If the account is eligible, the latest verification email is on the way." /> : null}
        {resendState === 'throttled' ? <StatePanel kind="error" title="Please wait before requesting another email" /> : null}
        {resendState === 'unavailable' ? <StatePanel kind="error" title="Email delivery is temporarily unavailable" description="Try again in a moment." /> : null}
      </>}
    </Card>
    <AuthLinks navigation={navigation} role="student" selectedThesisTopicId={null} />
  </ScreenShell>;
}

export function ForgotPasswordScreen({ navigation }: MobileScreenProps) {
  const [email, setEmail] = useState('');
  const [pending, setPending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async () => {
    setPending(true); setError(null);
    try { await campusApi.requestPasswordReset(email.trim()); setSent(true); }
    catch (requestError) { setError(requestError instanceof ApiClientError && requestError.code === 'AUTH_RATE_LIMITED' ? 'Too many recovery requests were made. Please wait and try again.' : 'We could not start recovery right now.'); }
    finally { setPending(false); }
  };
  return <ScreenShell eyebrow="Password recovery" title="Forgot password" subtitle="The response stays generic whether an account exists or not." contentStyle={styles.content}>
    <Card style={styles.card}>{sent ? <StatePanel kind="empty" title="Check your inbox" description="If the email is eligible, a reset link is on the way. The link expires after 30 minutes." actionLabel="Enter reset token" onAction={() => navigation.navigate('auth.resetPassword')} /> : <><Field label="Email" autoCapitalize="none" autoComplete="email" keyboardType="email-address" onChangeText={setEmail} value={email} /><Button label="Send reset link" loading={pending} disabled={!email.trim()} onPress={() => void submit()} />{error ? <StatePanel kind="error" title={error} /> : null}</>}</Card>
    <AuthLinks navigation={navigation} role="student" selectedThesisTopicId={null} />
  </ScreenShell>;
}

export function ResetPasswordScreen({ navigation, authToken }: MobileScreenProps) {
  const [token, setToken] = useState(authToken ?? '');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [state, setState] = useState<'ready' | 'saving' | 'success' | ChallengeFailureState>('ready');
  useEffect(() => { if (authToken) setToken(authToken); }, [authToken]);
  const submit = async () => {
    if (!token.trim() || password.length < 8 || password !== confirm) { setState('invalid'); return; }
    setState('saving');
    try { await campusApi.resetPassword(token.trim(), password); setState('success'); }
    catch (requestError) { setState(challengeFailure(requestError)); }
  };
  return <ScreenShell eyebrow="Password recovery" title="Reset password" subtitle="Use a fresh password, then sign in again." contentStyle={styles.content}>
    <Card style={styles.card}>{state === 'success' ? <StatePanel kind="empty" title="Password reset complete" description="All refresh sessions were revoked. Sign in again on this device." actionLabel="Sign in" onAction={() => navigation.navigate('auth.signIn')} /> : <><Field label="Reset token" autoCapitalize="none" autoCorrect={false} onChangeText={(value) => { setToken(value); setState('ready'); }} placeholder="Paste token from email" secureTextEntry value={token} /><Field label="New password" autoCapitalize="none" autoComplete="password-new" onChangeText={setPassword} secureTextEntry value={password} /><Field label="Confirm password" autoCapitalize="none" autoComplete="password-new" onChangeText={setConfirm} secureTextEntry value={confirm} /><Button label={state === 'saving' ? 'Saving…' : 'Reset password'} disabled={state === 'saving'} loading={state === 'saving'} onPress={() => void submit()} />{state === 'invalid' ? <StatePanel kind="error" title="The reset token is invalid or the passwords do not match" /> : null}{state === 'expired' ? <StatePanel kind="error" title="The reset link has expired" description="Request a fresh link; reset links expire after 30 minutes." /> : null}{state === 'attemptsExceeded' ? <StatePanel kind="error" title="The reset link was disabled" description="Too many invalid attempts were made. Request a fresh link." /> : null}{state === 'unavailable' ? <StatePanel kind="error" title="Password reset is temporarily unavailable" description="Try again in a moment. Your current password is unchanged." /> : null}</>}</Card>
    <AuthLinks navigation={navigation} role="student" selectedThesisTopicId={null} />
  </ScreenShell>;
}

const styles = StyleSheet.create({
  content: { paddingTop: tokens.spacing.sm },
  card: { gap: tokens.spacing.sm, padding: tokens.spacing.lg },
  links: { alignItems: 'center', flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', marginTop: tokens.spacing.md },
  divider: { backgroundColor: tokens.colors.outlineVariant, height: 1, marginVertical: tokens.spacing.md },
});
