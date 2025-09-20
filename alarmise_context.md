# Alarmise - App Context & Requirements

## Project Overview
Alarmise is a specialized alarm application designed to ensure users wake up or respond to time-sensitive alerts through a unique non-stop alarm mechanism combined with cognitive engagement via math puzzles.

## Core Concept
Unlike traditional alarm apps that can be easily dismissed or snoozed, Alarmise creates a persistent alert system that requires active problem-solving to disable, ensuring the user is fully awake and mentally engaged.

## Primary User Flow

### 1. Alarm Configuration
- User sets **Alarm Start Time** (when the alarm should begin)
- User sets **Alarm End Time** (when the alarm should automatically stop)
- The time window between start and end represents the maximum duration the alarm will play

### 2. Alarm Activation
- At the specified **start time**, the alarm begins playing immediately
- The alarm plays **continuously and non-stop** - no snooze function
- The alarm sound persists regardless of app state:
  - ✅ Foreground (app is open and visible)
  - ✅ Background (app is minimized or user switched to another app)
  - ✅ Closed (app has been terminated by the user or system)

### 3. Alarm Dismissal Process
- **ONLY** method to stop the alarm: solve a math puzzle
- The math puzzle should be:
  - Simple but require conscious thought (e.g., basic arithmetic)
  - Displayed clearly when user interacts with the alarm
  - Must be completed correctly to dismiss the alarm
- No alternative dismissal methods (no "dismiss" button, no volume controls bypass)

### 4. Automatic Alarm Termination
- If the user does **NOT** solve the math puzzle before the **end time**
- The alarm stops automatically at the specified end time
- This prevents indefinite alarm playing in case of user absence or inability to respond

## Critical Behavioral Requirements

### Non-Negotiable Rules
1. **Persistent Playback**: The alarm MUST continue playing regardless of app state
2. **No Easy Dismissal**: The ONLY way to stop the alarm before end time is solving the math puzzle
3. **Time-Bound Operation**: The alarm MUST automatically stop at the end time
4. **Cognitive Engagement**: Math puzzle must be solved correctly (no guessing bypass)

### Technical Considerations
- The app must handle background execution and system limitations
- Audio playback must be prioritized and protected from system interruption
- The app should gracefully handle device sleep, low power modes, and other system states
- Math puzzle generation should be randomized to prevent memorization

### User Experience Principles
- **Reliability**: The alarm must work as intended every time
- **Clarity**: Users must understand exactly when the alarm will start and stop
- **Fairness**: Math puzzles should be challenging enough to ensure wakefulness but not frustratingly difficult
- **Respect**: The alarm respects the user's end time boundary

## Scope Boundaries

### What This App IS:
- A reliable, persistent alarm system
- A wake-up tool that ensures mental alertness
- A time-bounded alert mechanism

### What This App IS NOT:
- A traditional alarm with snooze functionality
- A puzzle game or brain training app
- A flexible notification system with multiple dismissal options

## Success Criteria
The app is successful if:
1. It reliably starts playing at the specified start time
2. It continues playing uninterrupted until either puzzle completion or end time
3. It can only be dismissed by solving the math puzzle correctly
4. It automatically stops at the specified end time
5. It works consistently across all app states (foreground/background/closed)

## Development Reminders
- **Stay focused on the core concept**: Non-stop alarm + math puzzle dismissal + time boundaries
- **Resist feature creep**: Avoid adding unnecessary features that dilute the primary purpose
- **Prioritize reliability**: The alarm mechanism is the most critical component
- **Test thoroughly**: Verify behavior across all app states and system conditions
- **Respect the end time**: This is a crucial safety mechanism that must never be bypassed

---
*This document serves as the definitive reference for Alarmise requirements. Any development decisions should align with these specifications.*
