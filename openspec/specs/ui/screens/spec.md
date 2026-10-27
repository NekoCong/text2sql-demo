# ui/screens Specification

## Purpose

Defines how each screen presents the Academic Clarity language while preserving the product's current information architecture, functionality, and role-based access.

## Requirements
### Requirement: Application shell

The authenticated application SHALL present a persistent top bar with the product brand, primary navigation, and the account menu; the chat screen SHALL additionally present the conversation history sidebar. The content area MUST provide a consistent page canvas and gutter for every route.

#### Scenario: Signed-in user lands on chat

- **WHEN** a signed-in user opens the application
- **THEN** the top bar, account menu, conversation sidebar, and chat canvas are visible with consistent spacing

#### Scenario: Switching to a data screen

- **WHEN** the user selects a non-chat navigation item
- **THEN** the top bar remains, the conversation sidebar is hidden, and the page content uses the shared canvas

### Requirement: About entry point removed

The application SHALL NOT expose an "关于" navigation item. The `/about` path MUST redirect to the chat screen so existing bookmarks and links do not render an empty page.

#### Scenario: Navigation without About

- **WHEN** a signed-in user views the primary navigation
- **THEN** the navigation lists chat, data sources, datasource access when authorized, and no About item

#### Scenario: Old About link

- **WHEN** a user opens `/about` directly
- **THEN** the application redirects to the chat screen

### Requirement: Login and registration presentation

The login and registration screen SHALL present the brand, a focused authentication card, and the same control styling as the rest of the product. Form errors and loading states MUST remain visible without shifting the card layout.

#### Scenario: Signing in

- **WHEN** a guest submits valid credentials
- **THEN** the submit control shows a loading state and the screen keeps its layout until navigation completes

#### Scenario: Validation error

- **WHEN** a guest submits an invalid form
- **THEN** the field-level error is readable and the card does not jump or overflow

### Requirement: Chat workspace hierarchy

The chat screen SHALL make the conversation hierarchy legible: the assistant identity and steps, the natural-language answer, the SQL block, the result table or chart, and the composer MUST be visually distinct. Streaming and error states MUST be distinguishable from completed answers.

#### Scenario: Answer with SQL and chart

- **WHEN** an assistant answer contains steps, explanation, SQL, and a chart
- **THEN** each part is visually separated and the SQL remains readable without competing with the chart

#### Scenario: Streaming answer

- **WHEN** an answer is still streaming
- **THEN** the transcript shows a clear in-progress state and the composer reflects that a request is running

### Requirement: SQL and result readability

SQL SHALL be rendered in a dedicated code surface with syntax-preserving whitespace and a copy affordance. Result tables SHALL remain horizontally scrollable without breaking the transcript layout, and charts MUST use the shared data-visualization palette.

#### Scenario: Wide result table

- **WHEN** a result contains more columns than fit the viewport
- **THEN** the table scrolls horizontally inside its own region and the page itself does not scroll sideways

#### Scenario: Copying SQL over plain HTTP

- **WHEN** the user copies SQL from a page served over plain HTTP
- **THEN** the copy action still succeeds through the fallback path

### Requirement: Data management screens

The data sources, model settings, and datasource access screens SHALL use the shared page title, action bar, card, table, form, and dialog treatments. Destructive actions MUST be visually distinct, and permission changes MUST show a clear saved or failed outcome.

#### Scenario: Editing a datasource

- **WHEN** a user opens the datasource dialog to edit an existing entry
- **THEN** the dialog uses the shared form styling and its primary and cancel actions are visually distinct

#### Scenario: Toggling datasource access

- **WHEN** an administrator grants or revokes a role on a datasource
- **THEN** the screen reports whether the change was saved and keeps the matrix aligned

### Requirement: Responsive layout

At widths below 768px, the application SHALL keep the primary navigation reachable, collapse or hide the conversation sidebar, and preserve readable chat and table content without requiring horizontal page scrolling.

#### Scenario: Narrow viewport chat

- **WHEN** the chat screen is opened on a narrow viewport
- **THEN** the transcript, composer, and SQL block remain usable and the page does not scroll horizontally

#### Scenario: Narrow viewport navigation

- **WHEN** the user opens a data screen on a narrow viewport
- **THEN** the primary navigation remains reachable and the content gutter stays consistent

### Requirement: Consistent loading, empty, and error states

Every screen SHALL use the shared presentation for loading, empty, and error states. A failed request MUST show a readable message and MUST NOT leave the screen in an ambiguous blank state.

#### Scenario: Empty conversation history

- **WHEN** a signed-in user has no saved conversations
- **THEN** the sidebar shows the shared empty state instead of an unstyled blank area

#### Scenario: Failed page load

- **WHEN** a data screen fails to load its content
- **THEN** the screen shows a readable error message and retains its page frame
