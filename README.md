# WatchTweak

A work-in-progress Android companion application primarily for the SG2 smart watch.
Watch communication based on reverse engineered communication protocol primarily obtained through packet capture.

Current state:

    - Will bond with a Bluetooth low energy device (only supports SG2 smart watch however).
    - Working foreground service for constant communication channel with watch.
    - Listens for notifications and sends notification alerts to watch.
    - Sets the time and date on watch.
    - Media playback and volume controls are functional.

Things to do:

    - Support for slightly older APIs. Currently some functions will only work with API 28+ devices.
    - Improve robustness of Bluetooth communication.
