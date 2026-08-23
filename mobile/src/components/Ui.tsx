import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleProp,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  TextProps,
  TextStyle,
  View,
  ViewStyle,
} from 'react-native';

import { tokens } from '../design/tokens';

export type TextVariant =
  | 'display'
  | 'headlineLarge'
  | 'headlineMedium'
  | 'headlineSmall'
  | 'bodyLarge'
  | 'bodyMedium'
  | 'bodySmall'
  | 'label'
  | 'meta';

export type TextTone = 'default' | 'muted' | 'primary' | 'success' | 'warning' | 'error' | 'onPrimary';

export interface UiTextProps extends Omit<TextProps, 'style'> {
  variant?: TextVariant;
  tone?: TextTone;
  style?: StyleProp<TextStyle>;
}

export function UiText({
  variant = 'bodyMedium',
  tone = 'default',
  style,
  ...props
}: UiTextProps) {
  return <Text {...props} style={[textStyles[variant], toneStyles[tone], style]} />;
}

export interface ScreenShellProps {
  title: string;
  eyebrow?: string;
  subtitle?: string;
  children: ReactNode;
  headerAction?: ReactNode;
  contentStyle?: StyleProp<ViewStyle>;
}

export function ScreenShell({
  title,
  eyebrow,
  subtitle,
  children,
  headerAction,
  contentStyle,
}: ScreenShellProps) {
  return (
    <View style={styles.screen}>
      <ScrollView
        contentContainerStyle={[styles.scrollContent, contentStyle]}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.headerRow}>
          <View style={styles.headerCopy}>
            {eyebrow ? (
              <UiText variant="meta" tone="primary" style={styles.eyebrow}>
                {eyebrow.toUpperCase()}
              </UiText>
            ) : null}
            <UiText accessibilityRole="header" variant="headlineLarge">{title}</UiText>
            {subtitle ? (
              <UiText variant="bodySmall" tone="muted" style={styles.subtitle}>
                {subtitle}
              </UiText>
            ) : null}
          </View>
          {headerAction}
        </View>
        {children}
      </ScrollView>
    </View>
  );
}

export interface CardProps {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
  tone?: 'card' | 'low' | 'primary';
}

export function Card({ children, style, tone = 'card' }: CardProps) {
  return <View style={[styles.card, cardTones[tone], style]}>{children}</View>;
}

export interface ButtonProps {
  label: string;
  onPress: () => void;
  accessibilityLabel?: string;
  variant?: 'primary' | 'secondary' | 'text';
  disabled?: boolean;
  loading?: boolean;
  style?: StyleProp<ViewStyle>;
}

export function Button({
  label,
  onPress,
  accessibilityLabel,
  variant = 'primary',
  disabled = false,
  loading = false,
  style,
}: ButtonProps) {
  const isDisabled = disabled || loading;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ busy: loading, disabled: isDisabled }}
      disabled={isDisabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        buttonVariants[variant],
        pressed && !isDisabled ? styles.buttonPressed : undefined,
        isDisabled ? styles.buttonDisabled : undefined,
        style,
      ]}
    >
      {loading ? (
        <View style={styles.buttonContent}>
          <ActivityIndicator color={variant === 'primary' ? tokens.colors.onPrimary : tokens.colors.primary} size="small" />
          <UiText variant="label" tone={variant === 'primary' ? 'onPrimary' : 'primary'}>
            Loading…
          </UiText>
        </View>
      ) : (
        <UiText variant="label" tone={variant === 'primary' ? 'onPrimary' : 'primary'}>
          {label}
        </UiText>
      )}
    </Pressable>
  );
}

export interface FieldProps extends TextInputProps {
  label: string;
  helperText?: string;
}

export function Field({ label, helperText, style, ...props }: FieldProps) {
  return (
    <View style={styles.fieldGroup}>
      <UiText variant="label" style={styles.fieldLabel}>
        {label}
      </UiText>
      <TextInput
        {...props}
        accessibilityLabel={props.accessibilityLabel ?? label}
        placeholderTextColor={props.placeholderTextColor ?? tokens.colors.outline}
        style={[styles.field, style]}
      />
      {helperText ? (
        <UiText variant="bodySmall" tone="muted" style={styles.helperText}>
          {helperText}
        </UiText>
      ) : null}
    </View>
  );
}

export interface BadgeProps {
  label: string;
  tone?: 'neutral' | 'primary' | 'success' | 'warning' | 'error';
}

export function Badge({ label, tone = 'neutral' }: BadgeProps) {
  return (
    <View style={[styles.badge, badgeTones[tone]]}>
      <UiText variant="meta" tone={badgeTextTones[tone]}>
        {label}
      </UiText>
    </View>
  );
}

export interface MetricCardProps {
  label: string;
  value: string;
  detail?: string;
  style?: StyleProp<ViewStyle>;
}

export function MetricCard({ label, value, detail, style }: MetricCardProps) {
  return (
    <Card style={[styles.metricCard, style]}>
      <UiText variant="bodySmall" tone="muted">
        {label}
      </UiText>
      <UiText variant="headlineMedium" tone="primary" style={styles.metricValue}>
        {value}
      </UiText>
      {detail ? (
        <UiText variant="meta" tone="muted">
          {detail}
        </UiText>
      ) : null}
    </Card>
  );
}

export interface SectionHeadingProps {
  title: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function SectionHeading({ title, actionLabel, onAction }: SectionHeadingProps) {
  return (
    <View style={styles.sectionHeading}>
      <UiText accessibilityRole="header" variant="headlineSmall">{title}</UiText>
      {actionLabel && onAction ? (
        <Button label={actionLabel} onPress={onAction} variant="text" />
      ) : null}
    </View>
  );
}

export interface ListRowProps {
  title: string;
  subtitle?: string;
  meta?: string;
  leading?: string;
  trailing?: ReactNode;
  onPress?: () => void;
  unread?: boolean;
}

export function ListRow({
  title,
  subtitle,
  meta,
  leading,
  trailing,
  onPress,
  unread = false,
}: ListRowProps) {
  const accessibilityLabel = [
    unread ? 'Unread' : null,
    title,
    subtitle,
    meta,
  ].filter(Boolean).join('. ');
  const content = (
    <View
      accessible={!onPress}
      accessibilityLabel={!onPress ? accessibilityLabel : undefined}
      style={styles.listRow}
    >
      {leading ? (
        <View style={[styles.leading, unread ? styles.leadingUnread : undefined]}>
          <UiText variant="label" tone={unread ? 'primary' : 'muted'}>
            {leading}
          </UiText>
        </View>
      ) : null}
      <View style={styles.listCopy}>
        <View style={styles.listTitleRow}>
          <UiText variant="bodyMedium" style={unread ? styles.unreadTitle : styles.listTitle}>
            {title}
          </UiText>
          {unread ? (
            <UiText variant="meta" tone="primary" style={styles.unreadLabel}>
              Unread
            </UiText>
          ) : null}
        </View>
        {subtitle ? (
          <UiText variant="bodySmall" tone="muted" numberOfLines={2}>
            {subtitle}
          </UiText>
        ) : null}
      </View>
      {meta ? (
        <UiText variant="meta" tone="muted" style={styles.listMeta}>
          {meta}
        </UiText>
      ) : null}
      {trailing}
    </View>
  );

  if (!onPress) {
    return content;
  }

  return (
    <Pressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [pressed ? styles.rowPressed : undefined]}
    >
      {content}
    </Pressable>
  );
}

export interface ProgressBarProps {
  value: number;
  label?: string;
  tone?: 'primary' | 'success' | 'warning';
}

export function ProgressBar({ value, label, tone = 'primary' }: ProgressBarProps) {
  const boundedValue = Math.max(0, Math.min(100, value));

  return (
    <View
      accessible
      accessibilityLabel={label ?? 'Progress'}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: Math.round(boundedValue) }}
      style={styles.progressGroup}
    >
      {label ? (
        <View style={styles.progressHeader}>
          <UiText variant="bodySmall" tone="muted">
            {label}
          </UiText>
          <UiText variant="meta" tone={tone === 'primary' ? 'primary' : tone}>
            {Math.round(boundedValue)}%
          </UiText>
        </View>
      ) : null}
      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, progressTones[tone], { width: `${boundedValue}%` }]} />
      </View>
    </View>
  );
}

export function Divider() {
  return <View style={styles.divider} />;
}

export function Avatar({ label, size = 48 }: { label: string; size?: number }) {
  return (
    <View style={[styles.avatar, { width: size, height: size, borderRadius: size / 2 }]}>
      <UiText variant="headlineSmall" tone="primary">
        {label.slice(0, 1).toUpperCase()}
      </UiText>
    </View>
  );
}

export function ScreenSpacer({ size = tokens.spacing.md }: { size?: number }) {
  return <View style={{ height: size }} />;
}

export interface StatePanelProps {
  kind: 'loading' | 'error' | 'empty';
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function StatePanel({
  kind,
  title,
  description,
  actionLabel,
  onAction,
}: StatePanelProps) {
  return (
    <View
      accessibilityLiveRegion={kind === 'error' ? 'assertive' : 'polite'}
      accessibilityRole={kind === 'error' ? 'alert' : undefined}
      style={[styles.statePanel, kind === 'error' ? styles.statePanelError : undefined]}
    >
      {kind === 'loading' ? (
        <ActivityIndicator color={tokens.colors.primary} size="small" />
      ) : null}
      <View style={styles.stateCopy}>
        <UiText variant="label" tone={kind === 'error' ? 'error' : 'default'}>
          {title}
        </UiText>
        {description ? (
          <UiText variant="bodySmall" tone="muted" style={styles.stateDescription}>
            {description}
          </UiText>
        ) : null}
      </View>
      {actionLabel && onAction ? (
        <Button label={actionLabel} onPress={onAction} variant="secondary" />
      ) : null}
    </View>
  );
}

export function ScrollSection({ children }: { children: ReactNode }) {
  return <View style={styles.scrollSection}>{children}</View>;
}

const textStyles = StyleSheet.create<Record<TextVariant, TextStyle>>({
  display: { ...tokens.typography.display, fontFamily: tokens.typography.family },
  headlineLarge: { ...tokens.typography.headlineLarge, fontFamily: tokens.typography.family },
  headlineMedium: { ...tokens.typography.headlineMedium, fontFamily: tokens.typography.family },
  headlineSmall: { ...tokens.typography.headlineSmall, fontFamily: tokens.typography.family },
  bodyLarge: { ...tokens.typography.bodyLarge, fontFamily: tokens.typography.family },
  bodyMedium: { ...tokens.typography.bodyMedium, fontFamily: tokens.typography.family },
  bodySmall: { ...tokens.typography.bodySmall, fontFamily: tokens.typography.family },
  label: { ...tokens.typography.label, fontFamily: tokens.typography.family },
  meta: { ...tokens.typography.meta, fontFamily: tokens.typography.family },
});

const toneStyles = StyleSheet.create<Record<TextTone, TextStyle>>({
  default: { color: tokens.colors.text },
  muted: { color: tokens.colors.textMuted },
  primary: { color: tokens.colors.primary },
  success: { color: tokens.colors.success },
  warning: { color: tokens.colors.warning },
  error: { color: tokens.colors.error },
  onPrimary: { color: tokens.colors.onPrimary },
});

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: tokens.colors.background },
  scrollContent: {
    paddingHorizontal: tokens.layout.mobileGutter,
    paddingTop: tokens.spacing.md,
    paddingBottom: tokens.spacing.xl + tokens.layout.bottomNavigationHeight,
  },
  headerRow: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: tokens.spacing.lg, paddingBottom: tokens.spacing.md, borderBottomColor: tokens.colors.outlineVariant, borderBottomWidth: 1 },
  headerCopy: { flex: 1, paddingRight: tokens.spacing.sm },
  eyebrow: { letterSpacing: 0, marginBottom: tokens.spacing.xs, textTransform: 'uppercase' },
  subtitle: { marginTop: tokens.spacing.xs },
  card: {
    backgroundColor: tokens.colors.card,
    borderColor: tokens.colors.outlineVariant,
    borderRadius: tokens.radii.card,
    borderWidth: 1,
    padding: tokens.spacing.md,
  },
  button: {
    alignItems: 'center',
    borderRadius: tokens.radii.control,
    justifyContent: 'center',
    minHeight: tokens.layout.touchTarget,
    paddingHorizontal: tokens.spacing.md,
  },
  buttonContent: { alignItems: 'center', flexDirection: 'row', gap: tokens.spacing.sm },
  buttonPressed: { opacity: 0.88 },
  buttonDisabled: { opacity: 0.48 },
  buttonPrimary: { backgroundColor: tokens.colors.primary },
  buttonSecondary: { backgroundColor: tokens.colors.card, borderColor: tokens.colors.outlineVariant, borderWidth: 1 },
  buttonText: { backgroundColor: 'transparent', minHeight: tokens.layout.touchTarget, paddingHorizontal: tokens.spacing.xs },
  fieldGroup: { marginBottom: tokens.spacing.md },
  fieldLabel: { marginBottom: tokens.spacing.xs },
  field: {
    backgroundColor: tokens.colors.card,
    borderColor: tokens.colors.outlineVariant,
    borderRadius: tokens.radii.control,
    borderWidth: 1,
    color: tokens.colors.text,
    fontFamily: tokens.typography.family,
    fontSize: tokens.typography.bodyMedium.fontSize,
    minHeight: tokens.layout.touchTarget,
    paddingHorizontal: tokens.spacing.md,
    paddingVertical: tokens.spacing.sm,
  },
  helperText: { marginTop: tokens.spacing.xs },
  badge: { alignSelf: 'flex-start', borderRadius: tokens.radii.pill, paddingHorizontal: tokens.spacing.sm, paddingVertical: tokens.spacing.xs },
  metricCard: { flex: 1, minHeight: 108, padding: tokens.spacing.sm + tokens.spacing.xs },
  metricValue: { marginVertical: tokens.spacing.xs },
  sectionHeading: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginBottom: tokens.spacing.sm, minHeight: tokens.layout.touchTarget },
  listRow: { alignItems: 'center', flexDirection: 'row', minHeight: tokens.layout.touchTarget, paddingVertical: tokens.spacing.sm },
  leading: { alignItems: 'center', backgroundColor: tokens.colors.surfaceLow, borderRadius: tokens.radii.control, height: 36, justifyContent: 'center', marginRight: tokens.spacing.sm, width: 36 },
  leadingUnread: { backgroundColor: tokens.colors.primaryFixed },
  listCopy: { flex: 1, paddingRight: tokens.spacing.sm },
  listTitle: { flex: 1 },
  listTitleRow: { alignItems: 'center', flexDirection: 'row', flexWrap: 'wrap', gap: tokens.spacing.xs },
  listMeta: { marginLeft: tokens.spacing.sm },
  unreadTitle: { color: tokens.colors.primary, fontWeight: '600' },
  unreadLabel: { backgroundColor: tokens.colors.primaryFixed, borderRadius: tokens.radii.pill, paddingHorizontal: tokens.spacing.xs, paddingVertical: 2 },
  rowPressed: { backgroundColor: tokens.colors.surfaceLow, borderRadius: tokens.radii.control },
  progressGroup: { marginTop: tokens.spacing.sm },
  progressHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginBottom: tokens.spacing.xs },
  progressTrack: { backgroundColor: tokens.colors.primaryFixed, borderRadius: tokens.radii.pill, height: tokens.spacing.sm, overflow: 'hidden' },
  progressFill: { borderRadius: tokens.radii.pill, height: '100%' },
  divider: { backgroundColor: tokens.colors.outlineVariant, height: 1, marginVertical: tokens.spacing.sm },
  avatar: { alignItems: 'center', backgroundColor: tokens.colors.primaryFixed, justifyContent: 'center' },
  scrollSection: { marginBottom: tokens.spacing.lg },
  statePanel: { alignItems: 'center', backgroundColor: tokens.colors.card, borderColor: tokens.colors.outlineVariant, borderLeftColor: tokens.colors.primary, borderLeftWidth: 3, borderRadius: tokens.radii.card, borderWidth: 1, flexDirection: 'row', padding: tokens.spacing.md },
  statePanelError: { borderLeftColor: tokens.colors.error },
  stateCopy: { flex: 1, marginHorizontal: tokens.spacing.sm },
  stateDescription: { marginTop: tokens.spacing.xs },
});

const cardTones = {
  card: { backgroundColor: tokens.colors.card },
  low: { backgroundColor: tokens.colors.surfaceLow },
  primary: { backgroundColor: tokens.colors.primaryFixed, borderColor: tokens.colors.primary },
} satisfies Record<NonNullable<CardProps['tone']>, ViewStyle>;

const buttonVariants = {
  primary: styles.buttonPrimary,
  secondary: styles.buttonSecondary,
  text: styles.buttonText,
} satisfies Record<NonNullable<ButtonProps['variant']>, ViewStyle>;

const badgeTones = {
  neutral: { backgroundColor: tokens.colors.surface },
  primary: { backgroundColor: tokens.colors.primaryFixed },
  success: { backgroundColor: '#D9F2DF' },
  warning: { backgroundColor: '#FFF0C7' },
  error: { backgroundColor: '#FBDAD7' },
} satisfies Record<NonNullable<BadgeProps['tone']>, ViewStyle>;

const badgeTextTones = {
  neutral: 'muted',
  primary: 'primary',
  success: 'success',
  warning: 'warning',
  error: 'error',
} satisfies Record<NonNullable<BadgeProps['tone']>, TextTone>;

const progressTones = {
  primary: { backgroundColor: tokens.colors.primaryContainer },
  success: { backgroundColor: tokens.colors.success },
  warning: { backgroundColor: tokens.colors.warning },
} satisfies Record<NonNullable<ProgressBarProps['tone']>, ViewStyle>;
