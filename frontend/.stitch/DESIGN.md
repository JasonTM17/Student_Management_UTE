# Design System: Academic Continuity

**Stitch project:** `16486483525927292845` (Smart Student Management Portal)

## Visual theme

Academic Continuity is a calm, modern-corporate system for high-stakes academic workflows. It favors clear hierarchy, generous breathing room, tonal layering, and restrained professional blue accents. The experience should feel trustworthy and operationally precise across thesis registration, supervision, evaluation, teaching, and student self-service.

## Color roles

| Role | Hex | Use |
| --- | --- | --- |
| Surface / background | `#F9F9FF` | Page canvas and primary workspace background |
| Surface container low | `#F2F3FC` | Secondary panels and quiet grouping |
| Surface container | `#EDEDF6` | Selected navigation and filter surfaces |
| Surface container high | `#E7E8F0` | Hover and elevated tonal layers |
| Surface lowest | `#FFFFFF` | Cards, forms, and focused content |
| On-surface | `#191C21` | Headings and primary text |
| On-surface variant | `#424752` | Body copy and secondary labels |
| Primary | `#003F87` | Main actions, active navigation, focus, and brand anchor |
| Primary container | `#0056B3` | Filled emphasis and selected states |
| Primary fixed | `#D7E2FF` | Soft primary backgrounds and progress tracks |
| Secondary | `#505F76` | Supporting actions and neutral emphasis |
| Secondary container | `#D0E1FB` | Informational chips and supporting surfaces |
| Outline | `#727784` | Strong field and divider borders |
| Outline variant | `#C2C6D4` | Low-contrast borders |
| Error | `#BA1A1A` | Rejections, destructive actions, and blocking validation |

Semantic success uses a restrained green and warning uses amber. They communicate state only; blue remains the dominant product accent.

## Typography

Use **Be Vietnam Pro** for display, headings, labels, and body copy so Vietnamese diacritics remain clear and the web and mobile surfaces share one voice. Use the following hierarchy:

- Display: 36px / 44px / 700, tight tracking for major dashboard statements.
- Headline large: 28px / 36px / 600.
- Headline medium: 24px / 32px / 600.
- Headline small: 20px / 28px / 600.
- Body large: 18px / 28px / 400.
- Body medium: 16px / 24px / 400.
- Body small: 14px / 20px / 400.
- Labels: 12–14px / 16px / 500–600, with tracking only for compact status metadata.

## Shape, spacing, and elevation

Use a 4px baseline with common steps of 4, 8, 16, 24, and 32px. Desktop content caps at 1280px with 48px outer margins and 24px gutters; mobile uses a 16px gutter. Small controls use 4px radius, cards use 8px, and status badges use a pill radius. Avoid decorative oversized pills that compete with utility workflows.

Prefer tonal layering and low-contrast outlines over hard shadows. Cards use a subtle border; temporary menus and dialogs may use a soft ambient shadow. Focus states must have a visible 2px primary ring and all touch targets should be at least 44px.

## Component rules

- Primary buttons: professional blue fill, white text, 4–8px radius, minimum 40px height.
- Secondary buttons: white or low-tone surface with outline variant border.
- Cards: white surface, 8px radius, low-contrast outline, 24px desktop padding and 16px mobile padding.
- Forms: labels above fields; 1px outline variant border; 2px primary focus ring.
- Status badges: compact pills with explicit text and sufficient contrast; never rely on color alone.
- Data views: tables on desktop; stacked cards/lists on mobile, with no essential information hidden in horizontal scroll.
- Navigation: fixed desktop sidebar; mobile bottom navigation for the highest-frequency actions, with the complete menu available from the menu button.
- Notifications: unread items use a small blue emphasis and explicit unread label; read items remain visually distinguishable without disappearing.

## Responsive contract

At 767px and below, collapse multi-column layouts into one column, move core navigation to a bottom bar, keep 16px page gutters, stack actions, and preserve 44px touch targets. At 768–1023px use an 8-column fluid layout. At 1024px and above use the fixed sidebar plus a 12-column content grid. Remove decorative desktop-only connectors on mobile and keep reading order linear.

## Stitch screen atlas

The current Stitch project contains 22 relevant screens across web and mobile:

- Dashboard: desktop, mobile, and localized home variants.
- Authentication: mobile sign-in.
- Student profile: desktop and mobile.
- Registration rounds: desktop, mobile, and supporting list variants.
- Thesis lifecycle: topic list, topic detail, registration, progress, and evaluation on desktop/mobile.
- Faculty and operations: lecturer lists and dashboard surfaces on desktop/mobile.
- Notifications: desktop and mobile notification centers.

Every implementation change should name the corresponding screen family and verify both a desktop and a 390px/780px mobile viewport when the family has both references.
