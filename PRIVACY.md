# NotifPlus privacy disclosure

NotifPlus is a local notification-history application for the consenting owner of the Android device.

- Notification content is read only through Android `NotificationListenerService` after the user grants Notification Access.
- Every delivered `onNotificationPosted()` payload is appended as an immutable snapshot. When a source app later edits or removes a notification, earlier snapshots remain available in the local history.
- Text, sensitive content, MessagingStyle/historic messages, and accessible media attachments are stored as plaintext in private app storage so the user can read the complete payload received by Android. No application-level content filter, package allowlist, redaction, or truncation is applied.
- Accessible notification images are copied immediately into `filesDir/notification_attachments` before optional auto-dismiss. Each attachment is limited to 50 MB; unavailable or oversized media is kept as metadata with its capture status.
- The database and preferences are stored in private app storage and excluded from Android backup.
- The app does not request Internet, Accessibility Service, SMS, broad storage, package visibility, or foreground-service access.
- Notification content is never written to logs.
- Archive retention defaults to 30 days and can be changed or disabled.
- Original-notification auto-dismiss is disabled by default and must be enabled per application. It only runs after the notification has been persisted, never for ongoing or non-clearable notifications, and never retroactively after reconnect. This is separate from archive retention, which controls deletion of NotifPlus's local copy.
- Export is user initiated and may transfer sensitive content to another application; users control the destination.

NotifPlus cannot receive data that Android or an OEM removes before delivery. Android 15 may redact OTP/sensitive notification content, and the app cannot bypass that system behavior. NotifPlus stores the exact payload it receives and does not claim to reconstruct unavailable content.
