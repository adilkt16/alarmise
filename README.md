# Alarmise - Non-Stop Alarm with Math Puzzle Solution

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)

## 🚨 Project Overview

Alarmise is a specialized alarm application designed to ensure users wake up or respond to time-sensitive alerts through a unique **non-stop alarm mechanism** combined with **cognitive engagement via math puzzles**. Unlike traditional alarm apps that can be easily dismissed or snoozed, Alarmise creates a persistent alert system that requires active problem-solving to disable, ensuring the user is fully awake and mentally engaged.

## 🎯 Core Concept

The fundamental principle of Alarmise is simple yet effective:
- **Start Time**: When the alarm begins playing
- **End Time**: When the alarm automatically stops
- **Continuous Playback**: The alarm plays non-stop between start and end times
- **Single Dismissal Method**: Only solving a math puzzle can stop the alarm before the end time

## 📱 Key Features

### ✅ Persistent Alarm System
- Alarm continues playing regardless of app state (foreground/background/closed)
- No snooze functionality - preventing easy dismissal
- Protected from system interruptions and low power modes
- Automatic stop at user-defined end time

### 🧮 Math Puzzle Challenge
- **Three difficulty levels**: Easy, Medium, Hard
- **Randomized questions**: Addition, subtraction, multiplication, division
- **Cognitive engagement**: Ensures user is mentally alert before dismissal
- **Multiple attempts**: New puzzles generated for incorrect answers

### 🔧 Technical Architecture
- **MVVM Pattern**: Clean separation of concerns with ViewModel and Repository patterns
- **Room Database**: Local storage for alarms and usage logs
- **Foreground Services**: Ensures alarm continues even when app is closed
- **Dependency Injection**: Hilt for clean and testable architecture
- **Jetpack Compose**: Modern UI framework for responsive design

## 🏗️ Project Structure

```
app/
├── src/main/java/com/alarmise/app/
│   ├── data/
│   │   ├── database/          # Room database, DAOs, converters
│   │   ├── model/             # Data models (Alarm, MathPuzzle, AlarmLog)
│   │   └── repository/        # Repository pattern implementation
│   ├── di/                    # Dependency injection modules
│   ├── service/               # Background services for alarm functionality
│   ├── receiver/              # Broadcast receivers for system events
│   ├── ui/
│   │   ├── activity/          # Main and AlarmTrigger activities
│   │   ├── theme/             # Compose theme and styling
│   │   └── viewmodel/         # ViewModels for UI state management
│   └── utils/                 # Utility classes and helpers
├── src/main/res/
│   ├── layout/                # XML layouts (if any)
│   ├── values/                # Colors, strings, themes
│   ├── drawable/              # Icons and graphics
│   └── raw/                   # Audio files for alarm sounds
└── build.gradle               # App-level dependencies and configuration
```

## 🛠️ Technical Specifications

### Minimum Requirements
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin 100%
- **Architecture**: MVVM with Repository Pattern

### Key Dependencies
- **Jetpack Compose**: Modern UI toolkit
- **Room**: Local database for data persistence
- **Hilt**: Dependency injection framework
- **WorkManager**: Background task scheduling
- **ExoPlayer**: Advanced audio playback capabilities
- **Lifecycle Components**: ViewModel and LiveData

### Permissions Required
- `WAKE_LOCK`: Keep device awake during alarm
- `SCHEDULE_EXACT_ALARM`: Precise alarm scheduling
- `FOREGROUND_SERVICE`: Background alarm playback
- `POST_NOTIFICATIONS`: Alarm notifications
- `MODIFY_AUDIO_SETTINGS`: Audio control for alarm volume

## 🎮 User Flow

### 1. Setting an Alarm
1. User opens Alarmise app
2. Sets **Start Time** (when alarm should begin)
3. Sets **End Time** (when alarm should auto-stop)
4. Optionally adds a label for the alarm
5. Confirms alarm creation

### 2. Alarm Activation
1. At start time, alarm begins playing immediately
2. Foreground service ensures continuous playback
3. Notification appears with alarm status
4. App can be closed - alarm continues playing

### 3. Stopping the Alarm
1. User interacts with alarm notification or opens app
2. Math puzzle screen appears with red background
3. User must solve the puzzle correctly
4. Incorrect answers generate new puzzles
5. Correct answer stops the alarm immediately

### 4. Auto-Stop Safety
1. If user doesn't solve puzzle before end time
2. Alarm automatically stops at specified end time
3. This prevents indefinite alarm duration

## 🧮 Math Puzzle System

### Difficulty Levels

**Easy Mode**:
- Single digit arithmetic (1-9)
- Basic operations: +, -, ×, ÷
- Example: "7 + 3 = ?"

**Medium Mode** (Default):
- Double digit numbers (10-99)
- All basic operations
- Example: "45 × 8 = ?"

**Hard Mode**:
- Triple digit numbers (100-999)
- Complex operations with parentheses
- Example: "(15 + 28) × 6 = ?"

### Smart Generation
- Ensures clean division (no remainders)
- Prevents negative results in subtraction
- Randomized selection prevents memorization
- New puzzle generated for each incorrect attempt

## 🔒 Security & Reliability Features

### Background Execution Protection
- Foreground service with persistent notification
- Wake lock acquisition for critical periods
- Battery optimization exclusion requests
- System boot receiver for alarm rescheduling

### User Safety Measures
- **Mandatory end time**: Prevents infinite alarm duration
- **Maximum volume control**: Ensures alarm is heard
- **System override protection**: Cannot be easily disabled
- **Graceful degradation**: Fallback mechanisms for edge cases

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+
- Kotlin plugin enabled

### Setup Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/alarmise.git
   ```

2. Open in Android Studio and sync project

3. Build and run on device:
   ```bash
   ./gradlew assembleDebug
   ```

### First Launch Setup
1. Grant all requested permissions
2. Add app to battery optimization whitelist
3. Enable notification access for alarm functionality
4. Test with a short-duration alarm first

## 📊 Development Roadmap

### Version 1.0 (Current)
- [x] Basic alarm scheduling and playback
- [x] Math puzzle integration
- [x] Persistent background service
- [x] Room database implementation
- [x] MVVM architecture setup

### Version 1.1 (Planned)
- [ ] Custom alarm sounds
- [ ] Multiple alarm support
- [ ] Usage statistics and analytics
- [ ] Accessibility improvements
- [ ] Widget support

### Version 2.0 (Future)
- [ ] Cloud backup for alarms
- [ ] Advanced puzzle types (word problems, logic)
- [ ] Gradual volume increase
- [ ] Smart scheduling suggestions
- [ ] Integration with health apps

## 🧪 Testing Strategy

### Unit Tests
- Math puzzle generation algorithms
- Alarm time calculations and validations
- Repository and ViewModel logic

### Integration Tests
- Database operations and migrations
- Service lifecycle management
- Broadcast receiver functionality

### Manual Testing Checklist
- [ ] Alarm plays when app is closed
- [ ] Puzzle generation works correctly
- [ ] Auto-stop functionality at end time
- [ ] System boot alarm restoration
- [ ] Battery optimization scenarios

## 🐛 Known Limitations

1. **Android Doze Mode**: May affect alarm reliability on some devices
2. **OEM Battery Management**: Some manufacturers have aggressive power management
3. **Audio Focus**: Other apps may temporarily interrupt alarm audio
4. **Storage Permissions**: Required for custom alarm sound files

## 📖 Contributing

We welcome contributions to improve Alarmise! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

### Code Standards
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Maintain MVVM architecture patterns

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Support

For questions, bug reports, or feature requests:
- Create an issue on GitHub
- Email: support@alarmise.app
- Documentation: [Wiki Pages](wiki)

---

**⚠️ Important Notice**: Alarmise is designed to be a reliable wake-up tool. Please use responsibly and ensure you have alternative wake-up methods in critical situations. Always test the app thoroughly before relying on it for important events.

**🎯 Remember**: The goal is reliability through simplicity. Every feature should enhance the core alarm functionality without compromising its primary purpose.
