import { expect, test, type APIRequestContext } from '@playwright/test';
import {
  apiUrl,
  buildCookieHeaders,
  buildMutatingSessionHeaders,
  expectOkResponse,
  getSharedSessionArtifacts,
  seedBrowserSession,
} from './helpers';

function escapeForRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

type InvoiceSummary = {
  id: string;
  invoiceNumber: string;
  status: string;
  balance: number;
  semesterId?: string;
};

async function listStudentInvoices(
  studentApi: APIRequestContext,
  session: Awaited<ReturnType<typeof getSharedSessionArtifacts>>,
) {
  const invoicesResponse = await studentApi.get(apiUrl('/finance/my/invoices'), {
    headers: buildCookieHeaders(session),
  });
  await expectOkResponse(invoicesResponse, 'GET /finance/my/invoices');
  return (await invoicesResponse.json()) as InvoiceSummary[];
}

async function ensureCheckoutInvoice(
  playwright: Parameters<typeof getSharedSessionArtifacts>[0],
  studentApi: APIRequestContext,
) {
  const session = await getSharedSessionArtifacts(playwright, 'student');
  const invoices = await listStudentInvoices(studentApi, session);
  const outstandingInvoice = invoices.find((invoice) => invoice.balance > 0);

  if (outstandingInvoice) {
    return { session, targetInvoice: outstandingInvoice };
  }

  const studentId = session.authData.user.studentId as string | undefined;
  const semesterId = invoices.find((invoice) => invoice.semesterId)?.semesterId;
  expect(
    studentId,
    'Expected seeded student session to expose a student profile id',
  ).toBeTruthy();
  expect(
    semesterId,
    'Expected seeded finance data to expose at least one semester id',
  ).toBeTruthy();

  const adminSession = await getSharedSessionArtifacts(playwright, 'admin');
  const createInvoiceResponse = await studentApi.post(apiUrl('/finance/invoices'), {
    headers: buildMutatingSessionHeaders(adminSession, {
      Authorization: `Bearer ${adminSession.authData.accessToken}`,
    }),
    data: {
      studentId,
      semesterId,
      dueDate: new Date(Date.now() + 1000 * 60 * 60 * 24 * 30).toISOString(),
      notes:
        'E2E checkout fixture: recreated when a previous run paid every outstanding seeded invoice.',
      items: [
        {
          description: 'E2E checkout regression balance',
          quantity: 1,
          unitPrice: 42,
        },
      ],
    },
  });
  await expectOkResponse(
    createInvoiceResponse,
    'POST /finance/invoices checkout fixture',
  );

  const targetInvoice = (await createInvoiceResponse.json()) as InvoiceSummary;
  expect(targetInvoice.balance).toBeGreaterThan(0);

  return { session, targetInvoice };
}

test('student checkout can switch providers before completing the sandbox handoff', async ({
  page,
  playwright,
}) => {
  const studentApi = await playwright.request.newContext();

  try {
    const { session, targetInvoice } = await ensureCheckoutInvoice(
      playwright,
      studentApi,
    );

    await seedBrowserSession(page, playwright, 'student', { shared: true });
    await page.goto('/dashboard/invoices');

    await page
      .getByRole('button', {
        name: new RegExp(
          `View details for invoice ${escapeForRegExp(
            targetInvoice.invoiceNumber,
          )}`,
          'i',
        ),
      })
      .click();

    await expect(
      page.getByRole('heading', { name: /Invoice details|Chi tiết hóa đơn/i }),
    ).toBeVisible();

    const providerChecks = [
      { button: /^MoMo$/i, action: /Continue to provider/i },
      { button: /^ZaloPay$/i, action: /Open QR confirmation/i },
      { button: /^VNPay$/i, action: /Continue to provider/i },
      { button: /^PayPal$/i, action: /Continue to approval/i },
      {
        button: /^Visa \/ international card$/i,
        action: /Open secure card checkout/i,
      },
    ];

    for (const provider of providerChecks) {
      await page.getByRole('button', { name: provider.button }).click();
      await expect(
        page.getByRole('button', { name: provider.action }),
      ).toBeVisible();
    }

    await page
      .getByRole('button', { name: /Open secure card checkout/i })
      .click();
    await expect(page).toHaveURL(
      /\/api\/v1\/finance\/payment-providers\/card\/handoff\//,
    );
    await expect(
      page.getByRole('link', { name: /Complete payment/i }),
    ).toBeVisible();

    await page.getByRole('link', { name: /Complete payment/i }).click();
    await expect(page).toHaveURL(/dashboard\/invoices/);
    await expect(
      page.getByRole('heading', { name: /Invoice details|Chi tiết hóa đơn/i }),
    ).toBeVisible();
    await expect(
      page.getByText(/Confirmed|Paid|Đã xác nhận|Đã thanh toán/i).first(),
    ).toBeVisible();

    const detailResponse = await studentApi.get(
      apiUrl(`/finance/my/invoices/${targetInvoice.id}`),
      {
        headers: buildCookieHeaders(session),
      },
    );
    await expectOkResponse(detailResponse, 'GET /finance/my/invoices/:id');
    const detail = await detailResponse.json();

    expect(detail.status).toBe('PAID');
    expect(Array.isArray(detail.payments)).toBe(true);
    expect(detail.payments.length).toBeGreaterThan(0);
  } finally {
    await studentApi.dispose();
  }
});

test('student invoice surface stays localized on the Vietnamese route', async ({
  page,
  playwright,
}) => {
  const studentApi = await playwright.request.newContext();

  try {
    const session = await getSharedSessionArtifacts(playwright, 'student');
    const invoicesResponse = await studentApi.get(apiUrl('/finance/my/invoices'), {
      headers: buildCookieHeaders(session),
    });
    await expectOkResponse(invoicesResponse, 'GET /finance/my/invoices');
    const invoices = (await invoicesResponse.json()) as Array<{
      invoiceNumber: string;
      balance: number;
    }>;

    await seedBrowserSession(page, playwright, 'student', { shared: true });
    await page.goto('/vi/dashboard/invoices');

    await expect(page).toHaveURL(/\/vi\/dashboard\/invoices$/);
    await expect(page.getByRole('heading', { name: /Hóa đơn/ })).toBeVisible();
    await expect(
      page.getByRole('combobox', { name: /Chọn học kỳ cho hóa đơn/ }),
    ).toBeVisible();
    await expect(page.getByText(/Số dư cần theo dõi/)).toBeVisible();

    const preferredInvoice = invoices.find((invoice) => invoice.balance > 0);
    const detailButton = preferredInvoice
      ? page.getByRole('button', {
          name: new RegExp(
            `Xem chi tiết hóa đơn ${escapeForRegExp(
              preferredInvoice.invoiceNumber,
            )}`,
            'i',
          ),
        })
      : page.getByRole('button', { name: /Xem chi tiết hóa đơn/i }).first();

    await expect(detailButton).toBeVisible();
    await detailButton.click();

    await expect(
      page.getByRole('heading', { name: /Chi tiết hóa đơn/ }),
    ).toBeVisible();
    await expect(page.getByText(/Phương thức thanh toán/)).toBeVisible();

    if (preferredInvoice) {
      await expect(page.getByText(/MoMo/)).toBeVisible();
      await expect(page.getByText(/ZaloPay/)).toBeVisible();
      await expect(page.getByText(/VNPay/)).toBeVisible();
      await expect(page.getByText(/PayPal/)).toBeVisible();
      await expect(page.getByText(/Visa|thẻ quốc tế/i)).toBeVisible();
    }
  } finally {
    await studentApi.dispose();
  }
});
