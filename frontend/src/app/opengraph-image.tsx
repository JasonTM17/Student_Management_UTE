import { ImageResponse } from 'next/og';

export const alt = 'CampusCore workspace overview';
export const size = {
  width: 1200,
  height: 630,
};
export const contentType = 'image/png';

export default function OpenGraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          height: '100%',
          width: '100%',
          display: 'flex',
          background: '#0f1724',
          color: '#f8fafc',
          padding: '64px',
          fontFamily: 'Inter, system-ui, sans-serif',
          position: 'relative',
        }}
      >
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background:
              'radial-gradient(circle at top left, rgba(86, 199, 255, 0.18), transparent 38%), radial-gradient(circle at bottom right, rgba(249, 115, 22, 0.18), transparent 34%)',
          }}
        />
        <div
          style={{
            position: 'relative',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            width: '100%',
            border: '1px solid rgba(148, 163, 184, 0.18)',
            borderRadius: 28,
            padding: '40px 44px',
            background: 'rgba(15, 23, 36, 0.82)',
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 16,
                color: '#cbd5e1',
                fontSize: 24,
                textTransform: 'uppercase',
                letterSpacing: '0.28em',
              }}
            >
              <span
                style={{
                  width: 10,
                  height: 10,
                  borderRadius: 999,
                  background: '#f97316',
                }}
              />
              CampusCore
            </div>
            <div
              style={{
                fontSize: 68,
                lineHeight: 1.03,
                fontWeight: 700,
                maxWidth: 760,
              }}
            >
              Academic work that stays clear from sign-in to thesis progress.
            </div>
            <div
              style={{
                fontSize: 28,
                lineHeight: 1.45,
                color: '#cbd5e1',
                maxWidth: 820,
              }}
            >
              Identity, academics, announcements, notifications, and thesis workflows in one course-ready portal.
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              gap: 18,
              flexWrap: 'wrap',
            }}
          >
            {[
              'Role-aware workspace',
              'One academic portal',
              'Campus records in one place',
            ].map((item) => (
              <div
                key={item}
                style={{
                  border: '1px solid rgba(148, 163, 184, 0.18)',
                  borderRadius: 18,
                  padding: '14px 18px',
                  fontSize: 24,
                  color: '#e2e8f0',
                  background: 'rgba(30, 41, 59, 0.6)',
                }}
              >
                {item}
              </div>
            ))}
          </div>
        </div>
      </div>
    ),
    size,
  );
}
