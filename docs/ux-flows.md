# Rivoo — UX Flows & Screen Inventory

## Navigation (Bottom Bar, 4 tabs)

| Tab | Icon | Label | Screen |
|-----|------|-------|--------|
| 1 | Calendar+check | Hoy | Today view — today's appointments + quick actions |
| 2 | Grid calendar | Citas | Day/week calendar with time grid |
| 3 | Users | Equipo | Staff list + services |
| 4 | Menu | Más | Settings, billing, public booking, account |

FAB (+) visible on Hoy and Citas tabs only.

## Screen Inventory (~40 screens)

### Auth (4)
- A-01: Splash / App Shell
- A-02: Login (triggers Keycloak PKCE)
- A-03: Keycloak Login WebView
- A-04: Onboarding Gate

### Onboarding (6)
- O-01: Welcome (3-step progress)
- O-02: Salon Profile Setup (name, address, phone, slug)
- O-03: Business Hours (7 days, toggle + time pickers)
- O-04: Add First Employee (skippable)
- O-05: Add First Service (skippable)
- O-06: Complete (celebration, share booking link)

### Main App (5 tabs)
- M-01: Today View — operational list of today's appointments
- M-02: Calendar — time-grid day/week view
- M-03: Clients — searchable client database
- M-04: Staff — employee list and management
- M-05: Settings — navigation hub

### Appointments (6)
- AP-01: Appointment Detail (bottom sheet, status actions)
- AP-02: Create Step 1 — Select Employee (grid cards)
- AP-02b: Create Step 2 — Select Service (list with duration+price)
- AP-03: Create Step 3 — Pick Date+Time (date strip + slot grid)
- AP-04: Create Step 4 — Select/Create Client (search + inline create)
- AP-05: Confirm (summary card + "Reservar")

### Staff (6)
- ST-01: Staff List
- ST-02: Employee Detail (profile, stats)
- ST-03: Add Employee
- ST-04: Edit Employee
- ST-05: Employee Working Hours (7 day rows)
- ST-06: Employee Services (multi-select checkboxes)

### Clients (6)
- CL-01: Client List (search, filters)
- CL-02: Client Detail (stats, history, GDPR actions)
- CL-03: Add Client
- CL-04: Edit Client
- CL-05: Appointment History
- CL-06: GDPR Export Confirmation

### Settings (8)
- S-01: Settings Home
- S-02: Salon Profile
- S-03: Business Hours
- S-04: Public Booking Page (toggle, link, QR)
- S-05: Billing & Plan (current plan, upgrade)
- S-06: Notification Settings
- S-07: Account (password via Keycloak, logout)
- S-08: Danger Zone (deactivate)

### Public Booking (6, no auth)
- PB-01: Public Salon Page (service list)
- PB-02: Select Employee (or "Any")
- PB-03: Select Date+Time (month calendar + slots)
- PB-04: Client Details Form (name, email, phone, GDPR consent)
- PB-05: Booking Confirmed (add to calendar)
- PB-06: Booking Error (slot taken, alternatives)

## Key UX Decisions

1. **Default view**: "Hoy" (operational list), NOT calendar
2. **Create appointment**: 4-step wizard on mobile, single modal on desktop
3. **Wizard order**: Employee → Service → Date/Time → Client (each depends on previous)
4. **Swipe-to-act**: Swipe left on appointment card → Confirm/No-show/Cancel (no detail screen needed)
5. **Calendar**: Day view default on mobile, week on desktop
6. **Public booking**: Separate route `/book/[slug]`, no auth, minimal design, conversion-focused
7. **Optimistic updates**: Status changes update UI immediately, revert on error
8. **Empty states**: Illustration + CTA, never blank screen

## Mobile vs Desktop

| Aspect | Mobile | Desktop |
|--------|--------|---------|
| Calendar | Day view, single column | Week view, employee columns |
| Create appointment | 4-step wizard (full screen each) | Single modal form |
| Today view | Full-screen scrollable list | Split: list left + detail right |
| Navigation | Bottom tab bar | Left sidebar |
| Inline actions | Swipe-to-reveal | Hover buttons |

## App Router Structure (Next.js 14)

```
src/app/
├── (auth)/login, callback
├── (onboarding)/welcome, salon-setup, business-hours, add-employee, add-service, complete
├── (app)/
│   ├── today/                  ← M-01 (default)
│   ├── calendar/               ← M-02
│   ├── clients/[id]/           ← CL-01, CL-02
│   ├── staff/[id]/             ← ST-01, ST-02
│   ├── appointments/new/       ← AP-02→AP-05 wizard
│   ├── appointments/[id]/      ← AP-01
│   └── settings/               ← S-01→S-08
└── book/[slug]/                ← PB-01→PB-06 (public)
```

## Tech Stack

- Next.js 14 (App Router, TypeScript)
- Shadcn/UI (component library)
- Tailwind CSS (styling)
- Keycloak OIDC (PKCE flow, client: salon-frontend)
- Zustand or React Context (state management)
- date-fns (date handling, Europe/Madrid timezone)
