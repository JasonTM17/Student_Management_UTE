---
name: ak:payment-integration
description: Integrate payments with SePay (VietQR), Polar, and Stripe. Checkout, webhooks, subscriptions, QR codes, and multi-provider orders.
user-invocable: true
when_to_use: "Invoke for checkout, subscriptions, webhooks, or QR payments."
category: backend
keywords: [payments, stripe, polar, webhooks, qr]
license: MIT
argument-hint: "[provider] [task]"
metadata:
  author: agentkit
  version: "2.2.0"
---

# Payment Integration

Production-proven payment processing with SePay (Vietnamese banks), Polar (global SaaS), and Stripe (global infrastructure).

## When to Use

- Payment gateway integration (checkout, processing)
- Subscription management (trials, upgrades, billing)
- Webhook handling (notifications, idempotency)
- QR code payments (VietQR, NAPAS)
- Multi-provider order management

## Platform Selection

| Platform | Best For |
|----------|----------|
| **SePay** | Vietnamese market, VND, bank transfers, VietQR |
| **Polar** | Global SaaS, subscriptions, automated benefits (GitHub/Discord) |
| **Stripe** | Enterprise payments, Connect platforms, custom checkout |

## Quick Reference

### SePay
- `references/sepay/overview.md` - Auth, supported banks
- `references/sepay/api.md` - Endpoints, transactions
- `references/sepay/webhooks.md` - Setup, verification
- `references/sepay/sdk.md` - Node.js, PHP, Laravel
- `references/sepay/qr-codes.md` - VietQR generation
- `references/sepay/best-practices.md` - Production patterns

### Polar
- `references/polar/overview.md` - Auth, MoR concept
- `references/polar/products.md` - Pricing models
- `references/polar/checkouts.md` - Checkout flows
- `references/polar/subscriptions.md` - Lifecycle management
- `references/polar/webhooks.md` - Event handling
- `references/polar/benefits.md` - Automated delivery
- `references/polar/sdk.md` - Multi-language SDKs
- `references/polar/best-practices.md` - Production patterns

### Stripe
- `references/stripe/stripe-best-practices.md` - Integration design
- `references/stripe/stripe-sdks.md` - Server SDKs
- `references/stripe/stripe-js.md` - Payment Element
- `references/stripe/stripe-cli.md` - Local testing
- `references/stripe/stripe-upgrade.md` - Version upgrades
- External: https://docs.stripe.com/llms.txt

### Multi-Provider
- `references/multi-provider-order-management-patterns.md` - Unified orders, currency conversion

### Scripts
- `scripts/sepay-webhook-verify.js` - SePay webhook verification
- `scripts/polar-webhook-verify.js` - Polar webhook verification
- `scripts/checkout-helper.js` - Checkout session generator

## Key Capabilities

| Platform | Highlights |
|----------|------------|
| **SePay** | QR/bank/cards, 44+ VN banks, webhooks, 2 req/s |
| **Polar** | MoR, subscriptions, usage billing, benefits, 300 req/min |
| **Stripe** | CheckoutSessions, Billing, Connect, Payment Element |

## Implementation

**General flow:** auth → products → checkout → webhooks → events
