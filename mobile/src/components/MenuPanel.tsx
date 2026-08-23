import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { tokens } from '../design/tokens';
import {
  getScreenDefinition,
  menuSections,
  screenRegistry,
  type ScreenName,
  type UserRole,
} from '../navigation/routes';
import { Button, UiText } from './Ui';

interface MenuPanelProps {
  activeRoute: ScreenName;
  role: UserRole;
  onClose(): void;
  onNavigate(route: ScreenName): void;
  onSwitchRole(role: UserRole): void;
  allowRoleSwitch: boolean;
}

export function MenuPanel({ activeRoute, role, onClose, onNavigate, onSwitchRole, allowRoleSwitch }: MenuPanelProps) {
  return (
    <View style={styles.overlay}>
      <Pressable accessibilityLabel="Close menu" accessibilityRole="button" onPress={onClose} style={styles.backdrop} />
      <View accessibilityViewIsModal importantForAccessibility="yes" style={styles.panel}>
        <View style={styles.panelHeader}>
          <View style={styles.headerCopy}>
            <UiText variant="meta" tone="primary">
              CAMPUSCORE
            </UiText>
            <UiText accessibilityRole="header" variant="headlineSmall">Complete menu</UiText>
          </View>
          <Button label="Close" onPress={onClose} variant="text" />
        </View>

        {allowRoleSwitch ? (
          <><UiText variant="label" tone="muted" style={styles.roleLabel}>
            Preview role
          </UiText>
          <View style={styles.roleRow}>
            {(['student', 'lecturer', 'admin'] as const).map((candidate) => (
            <Pressable
              key={candidate}
              accessibilityRole="button"
              accessibilityState={{ selected: candidate === role }}
              onPress={() => onSwitchRole(candidate)}
              style={[styles.roleButton, candidate === role ? styles.roleButtonActive : undefined]}
            >
              <UiText variant="meta" tone={candidate === role ? 'onPrimary' : 'muted'}>
                {candidate[0].toUpperCase() + candidate.slice(1)}
              </UiText>
            </Pressable>
            ))}
          </View></>
        ) : null}

        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.menuContent}>
          {menuSections.map((section) => {
            const screens = screenRegistry.filter(
              (screen) =>
                section.families.some((family) => family === screen.family) &&
                screen.roles.includes(role),
            );

            return (
              <View key={section.title} style={styles.section}>
                <UiText accessibilityRole="header" variant="label" tone="muted" style={styles.sectionTitle}>
                  {section.title}
                </UiText>
                {screens.map((screen) => {
                  const active = screen.name === activeRoute;

                  return (
                    <Pressable
                      key={screen.name}
                      accessibilityLabel={screen.title}
                      accessibilityRole="button"
                      accessibilityState={{ selected: active }}
                      onPress={() => onNavigate(screen.name)}
                      style={[styles.menuItem, active ? styles.menuItemActive : undefined]}
                    >
                      <View style={styles.menuIcon}>
                        <MaterialCommunityIcons
                          color={active ? tokens.colors.primary : tokens.colors.textMuted}
                          name={screen.icon}
                          size={19}
                        />
                      </View>
                      <UiText variant="bodySmall" style={styles.menuLabel}>
                        {screen.title}
                      </UiText>
                      <MaterialCommunityIcons color={tokens.colors.textMuted} name="chevron-right" size={20} />
                    </Pressable>
                  );
                })}
              </View>
            );
          })}
          <UiText variant="bodySmall" tone="muted" style={styles.menuNote}>
            {getScreenDefinition(activeRoute)?.family === 'staff'
              ? 'Staff screens are available only in explicit preview mode.'
              : 'Choose a screen to continue your academic workflow.'}
          </UiText>
        </ScrollView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { ...StyleSheet.absoluteFillObject, justifyContent: 'flex-end', zIndex: 10 },
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(15, 27, 44, 0.52)' },
  panel: {
    backgroundColor: tokens.colors.card,
    borderColor: tokens.colors.outlineVariant,
    borderWidth: 1,
    borderTopLeftRadius: tokens.radii.card,
    borderTopRightRadius: tokens.radii.card,
    maxHeight: '86%',
    paddingHorizontal: tokens.layout.mobileGutter,
    paddingTop: tokens.spacing.md,
  },
  panelHeader: { alignItems: 'center', borderBottomColor: tokens.colors.outlineVariant, borderBottomWidth: 1, flexDirection: 'row', justifyContent: 'space-between', marginBottom: tokens.spacing.md, paddingBottom: tokens.spacing.sm },
  headerCopy: { flex: 1 },
  roleLabel: { marginBottom: tokens.spacing.xs },
  roleRow: { flexDirection: 'row', marginBottom: tokens.spacing.md },
  roleButton: { alignItems: 'center', backgroundColor: tokens.colors.surfaceLow, borderColor: tokens.colors.outlineVariant, borderRadius: tokens.radii.control, borderWidth: 1, justifyContent: 'center', marginRight: tokens.spacing.sm, minHeight: tokens.layout.touchTarget, paddingHorizontal: tokens.spacing.md },
  roleButtonActive: { backgroundColor: tokens.colors.primary },
  menuContent: { paddingBottom: tokens.spacing.xl },
  section: { marginBottom: tokens.spacing.lg },
  sectionTitle: { marginBottom: tokens.spacing.xs },
  menuItem: { alignItems: 'center', borderBottomColor: tokens.colors.outlineVariant, borderBottomWidth: StyleSheet.hairlineWidth, flexDirection: 'row', minHeight: tokens.layout.touchTarget, paddingHorizontal: tokens.spacing.sm, paddingVertical: tokens.spacing.xs },
  menuItemActive: { backgroundColor: tokens.colors.primaryFixed, borderLeftColor: tokens.colors.primary, borderLeftWidth: 3 },
  menuIcon: { alignItems: 'center', width: 32 },
  menuLabel: { flex: 1, marginLeft: tokens.spacing.sm },
  menuNote: { paddingTop: tokens.spacing.sm },
});
