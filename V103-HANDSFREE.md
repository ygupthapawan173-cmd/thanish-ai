# V103 Hands-free wake candidate
- V102 cloud-only AI, online speech recognition, streaming and cloud voice retained.
- Start Hey JARVIS in Wake Word settings while app is visible. Local Vosk detector recognizes only the wake phrase (no offline assistant); no idle audio is sent to cloud by this detector. After a match, foreground uses existing wake event / main assistant; minimized app uses existing bounded online Q&A (no phone actions). Background reply ends or times out and returns to wake; stop action ends the service.
- Mic lease, stale callback guard, bounded retry, foreground session yield, notification stop, and unlocked-screen privacy checks. Screen lock or privacy mute stops service; no auto restart after death/boot. No owner filtering. Initial setup requires microphone and notification permission. User must start again after lock or stop.
- Improved Voice Pack text contrast.
- 54/54 JVM policy assertions, 28/28 new static checks plus prior static checks passed. Android compilation and device verification pending. No assertion of wake accuracy or background reliability on specific devices.
