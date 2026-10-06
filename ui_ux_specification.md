# IPXtream UI/UX Visual Specification

This document provides a language-agnostic layout and interaction specification for building a modern media streaming desktop application with the same visual hierarchy and user experience.

---

## 1. Global Themes & Styling Guidelines

- **Theme Engine**: The interface supports a dynamic accent color palette overlaid on a consistent dark background. Accent color presets include:
  - **Sleek Purple** (Default)
  - **Forest Green**
  - **Slate Blue**
  - **Amber Gold**
- **Core Palette**:
  - **Background Deep**: Solid or very dark blue-grey (`#080812`) to provide high contrast for media posters.
  - **Card Background**: Slightly lighter grey-blue (`#141422`) for panels, text boxes, and container cards.
  - **Text Primary**: Off-white (`#F0F0F8`) for high readability.
  - **Text Muted**: Lavender-grey (`#8E8E9F`) for subtitles, metadata, and secondary labels.
- **Visual Style**:
  - Rounded corners (`10px` to `12px` border radius) on all text fields, primary buttons, and containers.
  - Micro-animations: Smooth scale transitions (`1.05x`) and drop shadow glows on hover for clickable items.

---

## 2. Login Screen

A centered login card overlaying a deep gradient background.

### Visual Structure
1. **Background**: A radial gradient shifting from dark indigo to deep obsidian.
2. **Login Card**:
   - Prominent drop-shadow blur to separate it from the background.
   - Rounded border wraps the entire form.
   - **Header**:
     - Circular app icon/logo with a dual-color linear gradient.
     - Large, bold title: **IPXtream**.
   - **Form Fields**:
     - **Profile Name Input**: Plain text field.
     - **Username Input**: Plain text field.
     - **Password Input**: Secure password masking field.
     - **Server URL Input**: Combined dropdown and editable text field (for typing or selecting recently used host addresses).
   - **State Checkboxes**:
     - "Remember Me" checkbox.
     - "Auto-Login on Startup" checkbox.
   - **Primary Action Button**:
     - Bold text ("Login").
     - Filled with a vivid gradient.
     - Enlarges slightly on hover and displays an accent glow.
   - **Secondary Panels**:
     - **Loading Overlay**: Translucent overlay showing a spinning circular indicator when connecting.
     - **Error Banner**: Collapsible red-bordered notification box showing detailed authentication failure alerts.

---

## 3. Main Dashboard

Split into three main structural zones: Left Sidebar (Navigation), Top Header (System Controls), and Main Content Window (Dynamic Pages).

### 3.1 Top Header
Placed horizontally along the top of the main window.
- **Left**: Current section title or page indicator.
- **Right**:
  - **Library Cache Status Indicator**: Shows whether background caching is active.
    - If active: Shows a progress percentage bar and a red "Cancel Cache" button.
    - If idle: Shows a "Cache All" button with a cloud icon.
  - **Refresh Button**: A circular arrow vector icon; triggers reload of the active section.
  - **Settings Button**: A cogwheel icon; toggles settings options.
  - **Account Manager Button**: A profile outline icon; opens the multi-account selector drawer.

### 3.2 Left Sidebar (Navigation)
A vertical bar spanning the left edge of the dashboard.
- **Top**: Active account details (Name/Avatar/Expiry Date indicator).
- **Navigation Options** (Vertical list of items with vector icons):
  - **Home**: House icon.
  - **Live TV**: TV monitor icon.
  - **Movies**: Film reel icon.
  - **TV Series**: Stacked series cards icon.
  - **Currently Watching**: Clock icon with a circular arrow (history).
  - **My Library**: Heart icon (favorites and custom lists).
  - **Downloads**: Tray download arrow icon (active download manager).
- **Interaction Rules**:
  - Hovering over an option highlights it with a subtle backdrop color.
  - Selecting an option moves a vertical bar/indicator of the active theme color to its left edge.

### 3.3 Dynamic Content Pages
The large pane to the right of the sidebar, displaying content based on the navigation choice.

#### A. Home Page
- **Hero Carousel Banner**:
  - Large focal poster representing a featured title.
  - Title text, category tags, a description snippet, and a "Play Now" primary button.
  - Slides automatically or via left/right arrow buttons.
- **Category Shelves**:
  - Horizontal scrolling shelves (rows) with section headers:
    - **Hot Movies**
    - **Popular TV Series**
    - **Live TV Highlights**
  - Items are represented by rectangular poster cards with titles written beneath.
  - Cards scale up smoothly and cast a shadow when hovered.

#### B. Media Browsers (Live TV, Movies, TV Series)
- **Left Category Pane**:
  - Vertical list of categories/genres.
  - Clicking a category filters the item grid on the right.
  - Category list items show badge counts (number of items inside).
- **Right Item Grid**:
  - Search bar at the top with a magnifying glass icon and clear button.
  - Grid of poster cards (Uniform layout).
  - Cards display stream quality tags (e.g. `HD`, `FHD`, `4K`) or category badges.
  - Hovering a card shows overlay play icons and quick-favorite toggles.
- **Pagination Footer**:
  - Capsule-shaped navigation bar centered at the bottom.
  - Left/Right arrows for page switching.
  - Page numbers (active page is highlighted with the theme color).

#### C. Downloads Manager
- List of items currently downloading, paused, or completed.
- Each item shows:
  - Poster preview.
  - File name.
  - Progress bar.
  - Download speed (MB/s) and estimated time remaining.
  - Action buttons: Pause, Resume, Delete.
- Settings panel at the top to adjust maximum concurrent downloads and download speeds.

---

## 4. Media Player Overlay

A borderless, full-screen overlay for media playback. Controls automatically fade out after a brief period of mouse inactivity.

### 4.1 Header Controller (Top Bar)
- **Back Button**: Left chevron; exits the player.
- **Title Block**: Displays stream title (or Series Title - Season X - Episode Y).
- **Playback Options**:
  - **Speed Selector**: Dropdown menu for speed values (e.g. `0.5x`, `1.0x`, `1.5x`, `2.0x`).
  - **Audio/Language Selector (Dub)**: Dropdown to switch audio tracks.
  - **Subtitle Selector (Sub)**: Dropdown to toggle and choose subtitle files.

### 4.2 Footer Controller (Bottom Bar)
- **Timeline Seekbar**:
  - A horizontal slider stretching across the width.
  - Shows current elapsed time (left) and remaining time (right).
  - Moving the slider jumps to that timestamp.
- **Playback Controls**:
  - **Play/Pause Toggle**: Large center-left button.
  - **Skip Controls**: Forward/Rewind buttons (skip 10s).
  - **Next Episode Button**: Skip icon (only available for TV Series playback).
- **Audio Controls**:
  - Speaker icon (toggles mute).
  - Horizontal volume slider.
- **Screen Controls**:
  - Fullscreen toggle button.

---

## 5. Overlay Drawers & Modals

### 5.1 Account Manager Drawer
- Slides out from the right side.
- Displays a vertical list of saved credentials/profiles.
- Options:
  - Click a profile to switch accounts immediately.
  - Add New Account button (opens form).
  - Delete Account button (trash bin icon next to each profile).

### 5.2 Settings Dialog
- A modal pop-up containing tabs:
  - **Appearance**: Theme selection dropdown and scaling options.
  - **Player Options**: Select preferred default media engine or external player.
  - **Cache Settings**: Schedule background updates or clear cache.
