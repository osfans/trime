# Voice Input Module for Trime

This document explains the voice input functionality that has been added to fix the issue where the voice input button was only outputting "voice input" text instead of activating speech recognition.

## What was fixed

The original issue was that when a voice input button was clicked, it would only output the text "voice input" instead of actually starting the system's speech recognition. This happened because the button action was not properly mapped to the voice input functionality.

## Implementation

### 1. VoiceInputManager Class
A new `VoiceInputManager` class has been created in `/app/src/main/java/com/osfans/trime/ime/voice/VoiceInputManager.kt` that:

- Uses Android's `SpeechRecognizer` API
- Handles voice recognition callbacks
- Provides error handling for various recognition failures
- Automatically commits recognized text to the input field

### 2. Integration with TrimeInputMethodService
The voice input manager is now integrated into the main input method service:

- Initialized in `onCreate()`
- Cleaned up in `onDestroy()`
- Provides a public `startVoiceInput()` method

### 3. Button Action Handling
The `CommonKeyboardActionListener` now supports the `voice_input` command:

- When a button with `command: voice_input` is pressed, it calls `service.startVoiceInput()`
- This replaces the previous behavior of just outputting text

### 4. Permissions
Added the required `RECORD_AUDIO` permission to `AndroidManifest.xml`

## How to Configure Voice Input Button

To configure a voice input button in your keyboard layout, use this configuration:

```yaml
VoiceInput: {label: 🎤, send: function, command: voice_input}
```

This creates a button with:
- Label: 🎤 (microphone emoji)
- Action: Calls the voice input functionality
- Command: `voice_input`

### Example Usage in Keyboard Layout

You can add the voice input button to any keyboard layout by referencing it:

```yaml
# In your keyboard layout
- {click: VoiceInput}
```

Or you can define it inline:

```yaml
- {label: 🎤, send: function, command: voice_input}
```

## Troubleshooting

### Permission Issues
If voice input doesn't work, ensure that:
1. The app has microphone permission granted
2. The device has Google speech services or similar speech recognition service installed

### No Speech Recognition Service
If the device doesn't have speech recognition available, the voice input will show an error in the logs.

### Error Handling
The implementation includes comprehensive error handling for:
- No speech recognition service available
- Audio recording errors
- Network timeouts
- No speech detected
- Insufficient permissions

## Testing

The voice input functionality can be tested by:
1. Adding a voice input button to your keyboard layout
2. Tapping the button
3. Speaking when prompted
4. Verifying that the recognized text appears in the input field

## Technical Details

### API Integration
- Uses `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`
- Supports partial results for real-time feedback
- Automatically uses the device's default language
- Configured for free-form speech recognition

### Lifecycle Management
- Voice input manager is properly initialized and destroyed with the input method service
- Ongoing recognition is stopped when the service is destroyed
- Memory leaks are prevented through proper cleanup

## Backward Compatibility

This implementation is fully backward compatible. Existing button configurations that don't use the `voice_input` command will continue to work as before.

## Future Enhancements

Potential future improvements could include:
- Visual feedback during recording
- Language selection options
- Custom recognition models
- Voice input timeout configuration