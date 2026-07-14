# System Context

**Status: mostly Planned.** Only `order-service` exists today. This document
describes the target system so future phases have a stable reference; it is
not a description of what currently runs.

## Actors and external systems

```mermaid
C4Context
title FulfillX — System Context (target)

Person(customer, "Customer", "Places and tracks orders")
Person(operator, "Operator", "Manages fulfillment")
Person(admin, "Administrator", "Manages products, inventory, refunds, audit")

System_Boundary(fulfillx, "FulfillX") {
  System(portal, "Web Portal", "Customer + admin UI")
  System(platform, "Order-Fulfillment Platform", "auth, inventory, order, payment, notification services")
}

System_Ext(paymentSim, "Payment Simulator", "In-repo, deterministic — never a real processor")

Rel(customer, portal, "Uses")
Rel(operator, portal, "Uses")
Rel(admin, portal, "Uses")
Rel(portal, platform, "HTTPS/JSON")
Rel(platform, paymentSim, "Authorizes/refunds (simulated)")
```

## Why this system exists

FulfillX is a controlled, realistic distributed order-fulfillment system
built specifically to give an automated quality-engineering platform
something worth protecting. See
`docs/business-risks/business-risk-register.md` for the specific risks each
part of the system is designed to expose and defend against.

## Current implementation status

| Component | Status |
|---|---|
| order-service (skeleton: health, `orders` table, entity) | **Implemented** |
| auth-service | Planned |
| inventory-service | Planned |
| payment-service (simulator) | Planned |
| notification-consumer | Planned |
| web-portal | Planned |
| order-service business API (create/reserve/authorize/confirm/cancel/refund) | Planned |
