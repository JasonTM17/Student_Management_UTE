import { redirect } from 'next/navigation';

export default async function LocalizedAdminEditorRedirect({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { locale } = await params;
  const resolved = searchParams ? await searchParams : {};
  const query = new URLSearchParams();
  for (const [k, v] of Object.entries(resolved)) {
    if (typeof v === 'string') query.set(k, v);
    else if (Array.isArray(v)) v.forEach((val) => query.append(k, val));
  }
  const qs = query.toString();
  if (qs) {
    redirect(`/${locale}/dashboard/editor?${qs}`);
  }
  redirect(`/${locale}/dashboard/editor`);
}
