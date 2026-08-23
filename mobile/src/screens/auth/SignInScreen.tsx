import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Button, Card, ErrorState, Field, ScreenShell, UiText } from '../../components/Ui';
import { ApiClientError, apiClient, campusApi } from '../../api/client';
import { tokens } from '../../design/tokens';
import type { UserRole } from '../../navigation/routes';
import type { MobileScreenProps } from '../../navigation/types';

function resolveRole(roles: readonly string[] | undefined): UserRole {
  const normalizedRoles = new Set((roles ?? []).map((role) => role.toUpperCase()));
  if (normalizedRoles.has('ADMIN')) {
    return 'admin';
  }
  if (normalizedRoles.has('LECTURER') || normalizedRoles.has('FACULTY') || normalizedRoles.has('TEACHER')) {
    return 'lecturer';
  }
  return 'student';
}

export function SignInScreen({ navigation }: MobileScreenProps) {
  const [email, setEmail] = useState('student@campuscore.local');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const isPreview = apiClient.mode === 'preview';
  const canSubmit = isPreview || (email.trim().length > 0 && password.length > 0);

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
      if (!response.accessToken) {
        throw new Error('Sign-in could not be completed because the session was not created.');
      }
      apiClient.setSessionTokens(response.accessToken, response.refreshToken);
      navigation.completeSignIn(resolveRole(response.user?.roles));
    } catch (signInError) {
      apiClient.clearAccessToken();
      setError(
        signInError instanceof ApiClientError
          ? signInError.message
          : 'Live sign in could not be completed. Please try again.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <ScreenShell
      eyebrow="Academic continuity"
      title="Welcome back"
      subtitle="Keep your academic workflow clear, calm, and connected."
      contentStyle={styles.content}
    >
      <Card style={styles.brandCard} tone="primary">
        <UiText variant="meta" tone="primary">
          CAMPUSCORE MOBILE
        </UiText>
        <UiText variant="headlineSmall" style={styles.brandTitle}>
          One place for your semester.
        </UiText>
        <UiText variant="bodySmall" tone="muted">
          Schedule, grades, thesis milestones, and notices in a mobile-first workspace.
        </UiText>
      </Card>

      <Card style={styles.formCard}>
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
        <Button label="Forgot password?" onPress={() => undefined} variant="text" />
        {error ? <ErrorState title="Sign-in unavailable" description={error} /> : null}
        <UiText variant="bodySmall" tone="muted" style={styles.previewNotice}>
          {isPreview
            ? 'No account is authenticated until the Java auth contract is implemented and verified.'
            : 'Your account enters the app only after a bearer token is returned.'}
        </UiText>
      </Card>

      <View style={styles.footerNote}>
        <UiText variant="bodySmall" tone="muted" style={styles.centerText}>
          CampusCore course-project workspace.
        </UiText>
        <UiText variant="meta" tone="muted" style={styles.centerText}>
          Be Vietnam Pro · 44px targets · 4px spacing
        </UiText>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  content: { paddingTop: tokens.spacing.sm },
  brandCard: { marginBottom: tokens.spacing.lg },
  brandTitle: { marginBottom: tokens.spacing.xs, marginTop: tokens.spacing.sm },
  formCard: { padding: tokens.spacing.lg },
  formIntro: { marginBottom: tokens.spacing.lg, marginTop: tokens.spacing.xs },
  submit: { marginTop: tokens.spacing.sm },
  previewNotice: { marginTop: tokens.spacing.md, textAlign: 'center' },
  footerNote: { alignItems: 'center', paddingHorizontal: tokens.spacing.md, paddingTop: tokens.spacing.lg },
  centerText: { textAlign: 'center' },
});
