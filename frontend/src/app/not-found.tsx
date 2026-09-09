import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 text-center">
      <div className="mx-auto max-w-md space-y-4">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-primary">
          404 Not Found
        </p>
        <h1 className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Trang không tồn tại
        </h1>
        <p className="text-sm text-muted-foreground">
          Đường dẫn bạn yêu cầu không tồn tại hoặc đã được chuyển sang vị trí mới.
          The page you requested does not exist or has been moved.
        </p>
        <div className="pt-2">
          <Link
            href="/"
            className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-5 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          >
            Quay lại trang chủ / Back to Home
          </Link>
        </div>
      </div>
    </div>
  );
}
