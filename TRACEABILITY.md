# Where each promise is kept

Every behavioural line from the project note (`Projects/Eat-Me-Neurodivergent.md` in
the vault) and the code and test that carries it. Run `./verify.sh` and every test
named here is executed.

| Promise in the note | Code | Test |
| --- | --- | --- |
| Two actions on the front, nothing competing | `MainActivity` | `MainActivityTest.aFreshInstallShowsTheTwoButtonsAndSaysNothingElse` |
| **I ate** asks when: now, 30m, 1h to 5h | `MainActivity.askWhenLogging` | `MainActivityTest.threeTapsLogAMealNow` |
| Then meal or snack | `MainActivity.askWhich` | `MainActivityTest.theFrontScreenFollowsTheLog` |
| Logging sets the next reminder to meal + 4h, snack + 2h | `Planner.nextAfterMeal`, `Actions.logMeal` | `PlannerTest.aMealPlansTheNextOneByItsOwnGap` |
| The reminder follows the newest meal, not the last one logged | `Actions.logMeal` | `ActionsTest.loggingAnOlderMealDoesNotDragTheReminderBackwards` |
| The new time can be changed in one tap | `MainActivity.linkChange` | `MainActivityTest.theChangeLinkChangesTheReminderInOneTap` |
| **I will eat** only sets a reminder, never logs | `Actions.planAhead` | `MainActivityTest.iWillEatSetsAReminderAndLogsNothing` |
| **Tomorrow** is 8h of silence, one silent step, then a meal gap | `Actions.tomorrow`, `Planner.tomorrowSilence` | `AppControllerTest.tomorrowSpeaksOnceSilentlyThenOnceAudiblyAMealGapLater` |
| The due alert offers **Ate** and **Not now** | `NotificationCenter.showAlert` | `NotificationCenterTest.theAlertStatesTheTimeTheGapAndTwoActions` |
| **Not now** pushes ten minutes, forever | `Actions.defer`, `Reducer` | `AppControllerTest.notNowPushesTheReminderTenMinutesAndKeepsTheRunSilent` |
| An ignored alert repeats on the same rhythm | `Reducer.planAfterAlert` | `ReducerTest.repeatsContinueOnTheSameRhythm` |
| Only the first alert of a run makes a sound | `NotificationCenter.showAlert` channel choice | `AppControllerTest.theDueAlarmIsTheOnlyAudibleOneOfARun` |
| The voice is flat | `Text` | `TextTest.noBannedWordEverReachesTheScreen`, `tools/wording.py` |
| A reminder set explicitly warns silently five minutes before | `Reducer.planForSet`, `Planner.WARNING_LEAD_MS` | `AppControllerTest.anExplicitReminderGetsASilentWarningFiveMinutesBefore` |
| A reminder landing in the night hours warns and offers tomorrow | `Night`, `MainActivity.warnIfNight` | `AppControllerTest.theNightWarningOnlyFiresWhenTheAlertLandsInTheNightHours` |
| The night window is local clock time, not UTC | `Night.isNight` | `LocalTimeTest.theNightWindowFollowsTheLocalClockAcrossTheSpringForward` |
| The front shows last, next, today, and links | `MainActivity.render` | `MainActivityTest.theFrontScreenFollowsTheLog` |
| The stale line after eight hours | `Text.staleLine` | `TextTest.theStaleLineAppearsOnlyAfterEightHours` |
| Meals editable for 24 hours: time, kind, delete | `AppController.editableMeals`, `EditHistoryActivity` | `SettingsAndHistoryTest.deletingFromTheEditHistoryMovesTheCountdown`, `...theEditHistoryShowsOnlyTheLastDay` |
| Deleting the newest meal moves the reminder back | `Actions.replan` | `ActionsTest.deletingTheNewestOfTwoFallsBackToTheOlder` |
| Every action is an event, appended, in epoch millis | `Events`, `Reducer`, `FileEventStore` | `ReducerTest`, `EventCodecTest` |
| Exactly one notification exists at a time | `NotificationCenter.NOTIFICATION_ID` | `NotificationCenterTest.thereIsOnlyEverOneNotificationId` |
| Settings holds the seven things plus copy and erase | `Settings`, `SettingsActivity` | `SettingsAndHistoryTest.theSettingsScreenShowsEverySettingAndItsValue` |
| No network, no account, no INTERNET permission | `AndroidManifest.xml` | `verify.sh` permission gate |
| The sound is bundled and changeable | `res/raw`, `NotificationCenter.ensureChannels` | `NotificationCenterTest.aChangedSoundBecomesANewChannelAndTheOldOneGoes` |
| The app appears in do-not-disturb, silently | `NotificationCenter.doNotDisturbActive` | `ReceiverTest.theAlertIsSilentWhenThePhoneIsInDoNotDisturb` |
| Dark mode follows the phone | `values-night` palette | `ScreenshotNightTest` |
| Android 8.0 and newer | `minSdk 26` | `aapt2 dump badging` |
| Survives a reboot | `BootReceiver`, `AlarmScheduler` | `ReceiverTest.aRebootRearmsTheAlarmFromTheLog` |
| Everything since install exports as JSON | `Export` | `ExportTest.everyEventInTheExportParsesBackToItself` |
| The export carries a trace that it happened | `Actions.exportCopied` | `AppControllerTest.theExportCarriesEverythingAndLeavesATrace` |

Two lines in this table are the build's reading of a note that was silent, and both are
recorded in the vault note's log: the reminder follows the **newest** meal, and the stale
line is a fixed eight hours rather than a setting.
