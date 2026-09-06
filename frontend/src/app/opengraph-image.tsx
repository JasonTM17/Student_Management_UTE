import { ImageResponse } from 'next/og';

export const alt = 'CampusUTE - Cổng Thông Tin Đào Tạo & Quản Lý Sinh Viên HCMUTE';
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
          background: '#002047',
          color: '#f8fafc',
          padding: '54px',
          fontFamily: 'Inter, system-ui, sans-serif',
          position: 'relative',
        }}
      >
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background:
              'radial-gradient(circle at top left, rgba(56, 189, 248, 0.22), transparent 42%), radial-gradient(circle at bottom right, rgba(245, 158, 11, 0.22), transparent 38%)',
          }}
        />
        <div
          style={{
            position: 'relative',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            width: '100%',
            border: '2px solid rgba(245, 158, 11, 0.3)',
            borderRadius: 32,
            padding: '44px 50px',
            background: 'rgba(0, 37, 84, 0.88)',
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 16,
                color: '#fde047',
                fontSize: 26,
                textTransform: 'uppercase',
                fontWeight: 800,
                letterSpacing: '0.2em',
              }}
            >
              <span
                style={{
                  width: 14,
                  height: 14,
                  borderRadius: 999,
                  background: '#f59e0b',
                }}
              />
              CampusUTE · HCMUTE
            </div>
            <div
              style={{
                fontSize: 62,
                lineHeight: 1.08,
                fontWeight: 800,
                maxWidth: 820,
                color: '#ffffff',
              }}
            >
              Cổng Thông Tin Đào Tạo & Học Vụ Sinh Viên
            </div>
            <div
              style={{
                fontSize: 26,
                lineHeight: 1.45,
                color: '#cbd5e1',
                maxWidth: 860,
              }}
            >
              Trường Đại học Sư phạm Kỹ thuật TP.HCM — Đăng ký học phần, thời khóa biểu, điểm số, và đồ án tốt nghiệp.
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              gap: 16,
              flexWrap: 'wrap',
            }}
          >
            {[
              '🎓 Cổng Đào Tạo HCMUTE',
              '📚 Đăng Ký Môn Học',
              '📊 Tra Cứu Điểm Số',
              '🏆 Đồ Án Tốt Nghiệp',
            ].map((item) => (
              <div
                key={item}
                style={{
                  border: '1px solid rgba(245, 158, 11, 0.35)',
                  borderRadius: 16,
                  padding: '12px 22px',
                  fontSize: 22,
                  fontWeight: 600,
                  color: '#ffffff',
                  background: 'rgba(255, 255, 255, 0.08)',
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

