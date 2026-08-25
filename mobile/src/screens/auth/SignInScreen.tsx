import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { Button, Card, Field, ScreenShell, UiText } from '../../components/Ui';
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
  const [email, setEmail] = useState('student@campuscore.edu');
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
        throw new Error('The Java auth response did not include an access token.');
      }
      apiClient.setSessionTokens(response.accessToken, response.refreshToken);
      const role = resolveRole(response.user?.roles);
      if (role !== 'student') {
        await campusApi.logout().catch(() => undefined);
        apiClient.clearAccessToken();
        setError('The mobile course demo currently supports the student role. Use the web portal for lecturer or admin work.');
        return;
      }
      navigation.completeSignIn(role);
    } catch (signInError) {
      apiClient.clearAccessToken();
      if (signInError instanceof ApiClientError && signInError.code === 'EMAIL_VERIFICATION_REQUIRED') {
        navigation.navigate('auth.verifyEmail');
      }
      setError(
        signInError instanceof ApiClientError
          ? signInError.message
          : 'Live sign in could not be completed against the Java auth contract.',
      );
    } finally {
      setIsSubmitting(false);
    }
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
        <View style={styles.authLinks}>
          <Button label="Create account" onPress={() => navigation.navigate('auth.register')} variant="text" />
          <Button label="Forgot password" onPress={() => navigation.navigate('auth.forgotPassword')} variant="text" />
        </View>
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
  errorPanel: { backgroundColor: '#FFF1F0', borderColor: '#F3C7C3', borderLeftColor: tokens.colors.error, borderLeftWidth: 3, borderRadius: tokens.radii.control, borderWidth: 1, marginTop: tokens.spacing.md, padding: tokens.spacing.sm },
  authLinks: { alignItems: 'center', flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', marginTop: tokens.spacing.sm },
});
