# SharePlate for Android Studio

SharePlate is a native Android app built with Kotlin and Android views. It does not use a WebView or load prefilled food listings. Features include account registration and sign-in, donor food listings with safety information, search and filters, recipient reservations, volunteer task claiming, pickup scheduling, collection and delivery status, recipient receipt confirmation, and history/impact.

## Open and run

1. In Android Studio, choose **Open** and select the `SharePlateAndroid` folder.
2. Select JDK 17 for the Gradle JDK and sync the project.
3. Select an emulator or connected Android phone, then press **Run**.

## Account workflow

1. Register a **Donor** account and publish a listing. Enter allergens (or “none known”) and storage instructions.
2. Sign out, register a **Recipient** account, and reserve that listing.
3. Sign out, register a **Volunteer** account, open **Activity**, and tap **Claim pickup task**.
4. Schedule a pickup time. Tap **Mark collected** when collecting the food from the donor.
5. Tap **Mark delivered** after handing the food to the recipient.
6. Sign in as the recipient and tap **Confirm received**. Only confirmed receipts count in **Impact**.

## Source and storage

- `app/src/main/java/com/shareplate/app/MainActivity.kt` builds the native screens, validates inputs, manages accounts and pickup status, and stores local data.
- `app/build.gradle.kts` and `app/src/main/AndroidManifest.xml` configure the Android app.
- Accounts, food listings and pickups use Android `SharedPreferences` on this device. Passwords are salted and hashed. Accounts do not synchronize between phones; multi-device service requires a shared backend.


