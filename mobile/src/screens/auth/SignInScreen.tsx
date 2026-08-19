import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Button, Card, Field, ScreenShell, UiText } from '../../components/Ui';
import { tokens } from '../../design/tokens';
import type { MobileScreenProps } from '../../navigation/types';

export function SignInScreen({ navigation }: MobileScreenProps) {
  const [email, setEmail] = useState('student@campuscore.local');
  const [password, setPassword] = useState('');

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
          label="Continue"
          onPress={() => navigation.navigate('dashboard.student')}
          style={styles.submit}
        />
        <Button label="Forgot password?" onPress={() => undefined} variant="text" />
      </Card>

      <View style={styles.footerNote}>
        <UiText variant="bodySmall" tone="muted" style={styles.centerText}>
          API seam: one Java REST API at `/api/v1`.
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
  footerNote: { alignItems: 'center', paddingHorizontal: tokens.spacing.md, paddingTop: tokens.spacing.lg },
  centerText: { textAlign: 'center' },
});

