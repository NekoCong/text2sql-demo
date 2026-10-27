# Spec Delta

## Purpose

Defines the shared visual language that keeps every screen of the Smart Campus frontend coherent, readable, and accessible, so users can move between chat, data, and administration without relearning the interface.

## ADDED Requirements

### Requirement: One visual language across the product

Every screen, including login, chat, data sources, model settings, and datasource access, SHALL use the same Academic Clarity token set for color, typography, spacing, radii, and elevation. A screen MUST NOT introduce a competing primary hue or a divergent card style.

#### Scenario: Navigating between screens

- **WHEN** a signed-in user moves from the chat screen to a data or administration screen
- **THEN** the primary color, surface treatment, typography, and control styling remain visually consistent

#### Scenario: Login matches the product

- **WHEN** a guest opens the login screen
- **THEN** the screen uses the same brand, color, and control language as the authenticated application

### Requirement: Semantic color roles

The theme SHALL define deep campus blue as the primary action color, teal as the data and insight accent, amber as warning, and red as danger. Primary and accent colors MUST NOT be used interchangeably for semantic states.

#### Scenario: Data insight accent

- **WHEN** the interface highlights a successful query, a data insight, or a completed agent step
- **THEN** it uses the teal accent rather than the primary action blue

#### Scenario: Destructive action

- **WHEN** the interface offers a delete or revoke action
- **THEN** the action is presented with the danger color and is visually distinct from primary actions

### Requirement: Readable typography

The product SHALL use a Chinese-first font stack with a body size of at least 14px and a line height of at least 1.6 for prose. SQL and tabular identifiers MUST use a monospace face. Essential labels and values MUST NOT be rendered below 12px.

#### Scenario: Reading a long answer

- **WHEN** an assistant answer contains several paragraphs
- **THEN** the text is set at a comfortable body size and line height, with a clear separation between paragraphs

#### Scenario: Reading SQL

- **WHEN** a query result includes SQL
- **THEN** the SQL is rendered in a monospace face with sufficient contrast against its background

### Requirement: Consistent spacing and surfaces

Pages SHALL follow an 8pt spacing rhythm and use a shared surface treatment: light borders, restrained shadows, and consistent internal padding. Page sections MUST align to the same horizontal gutters at a given viewport width.

#### Scenario: Two data pages side by side

- **WHEN** a user compares the data sources screen and the model settings screen
- **THEN** page titles, action bars, cards, and table padding align to the same rhythm

### Requirement: Accessible interaction states

All interactive controls SHALL expose visible hover, focus, active, disabled, and loading states. Keyboard focus MUST be visible and MUST NOT be removed without an equivalent indicator. Text and essential controls MUST meet WCAG AA contrast.

#### Scenario: Keyboard navigation

- **WHEN** a user navigates the header, sidebar, and form controls with the keyboard
- **THEN** the focused element is clearly visible at every step

#### Scenario: Disabled action

- **WHEN** an action is unavailable because a request is in flight
- **THEN** the control shows a disabled or loading state and cannot be triggered repeatedly

### Requirement: Restrained motion

Theme transitions and feedback animations SHALL be short and subtle, and MUST respect the user's reduced-motion preference.

#### Scenario: Reduced motion enabled

- **WHEN** the operating system reports a reduced-motion preference
- **THEN** non-essential transitions and looping animations are suppressed

### Requirement: Theme consumption

Screens SHALL consume shared theme tokens rather than hardcoding brand colors. Data-visualization palettes MAY define their own categorical colors, but those colors MUST be documented as part of the theme.

#### Scenario: Adding a new page

- **WHEN** a developer adds a new screen using the shared components and tokens
- **THEN** the screen inherits the product palette, radii, and spacing without redefining them

### Requirement: Light appearance only

This change SHALL deliver the Academic Clarity light appearance. A dark theme is explicitly out of scope and MUST NOT be partially applied.

#### Scenario: System dark preference

- **WHEN** the operating system prefers a dark appearance
- **THEN** the application still renders the complete light Academic Clarity theme without mixed dark and light surfaces
