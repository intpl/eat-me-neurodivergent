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
| **Tomorrow** is 12h of silence, one silent step, then a meal gap | `Actions.tomorrow`, `Planner.tomorrowSilence` | `AppControllerTest.tomorrowSpeaksOnceSilentlyThenOnceAudiblyAMealGapLater` |
| The due alert offers **Ate** and **Not now** | `NotificationCenter.showAlert` | `NotificationCenterTest.theAlertStatesTheTimeTheGapAndTwoActions` |
| **Not now** pushes ten minutes, forever | `Actions.defer`, `Reducer` | `AppControllerTest.notNowPushesTheReminderTenMinutesAndKeepsTheRunSilent` |
| An ignored alert repeats on the same rhythm | `Reducer.planAfterAlert` | `ReducerTest.repeatsContinueOnTheSameRhythm` |
| Only the first alert of a run makes a sound | `NotificationCenter.showAlert` channel choice | `AppControllerTest.theDueAlarmIsTheOnlyAudibleOneOfARun` |
| The voice is flat | `Text` | `TextTest.noBannedWordEverReachesTheScreen`, `tools/wording.py` |
| A reminder speaks at the time it was set for, and not before | `Reducer.planForSet`, `Planner.overdueStep` | `AppControllerTest.anExplicitReminderSpeaksAtItsOwnTimeAndNotBefore`, `PlannerTest.nothingIsDueBeforeTheRemindersOwnInstant` |
| The app does not open without notifications, exact reminders and do-not-disturb access | `GatedActivity.gated`, `Accesses.missing` | `MainActivityTest.theFrontDoesNotOpenWithoutDoNotDisturbAccess`, `SettingsAndHistoryTest.theSettingsScreenIsBehindTheGateLikeEverythingElse` |
| The gate names each withheld switch and opens the phone's page for it | `AccessActivity`, `Accesses.settingsIntent` | `AccessTest.theGateNamesEveryWithheldSwitch`, `AccessTest.eachRowOpensThePhonesOwnPage` |
| The gate drops what the phone allows, and opens the app when nothing is withheld | `AccessActivity.check` | `AccessTest.theGateDropsTheSwitchesThePhoneAllows`, `AccessTest.theGateLetsTheAppThroughWhenNothingIsWithheld`, `MainActivityTest.theFrontOpensWhenThePhoneAllowsEverything` |
| A test notification proves the permission, the channel and the sound | `NotificationCenter.test`, `SettingsActivity.sendTestNotification` | `NotificationCenterTest.theTestNotificationLooksLikeAReminderAndUsesItsChannel` |
| A log written before 3.1.0 still replays its warning step | `Reducer.planAfterAlert` | `ReducerTest.aWarningFromAnOlderLogReplaysWithoutChangingAnything` |
| A reminder landing in the night hours warns and offers tomorrow | `Night`, `MainActivity.warnIfNight` | `AppControllerTest.theNightWarningOnlyFiresWhenTheAlertLandsInTheNightHours` |
| The night window is local clock time, not UTC | `Night.isNight` | `LocalTimeTest.theNightWindowFollowsTheLocalClockAcrossTheSpringForward` |
| Night hours can be changed from settings, both to a window that crosses midnight | `Settings.changed`, `SettingsActivity.editNightHours` | `SettingsTest.theNightHoursTheSettingsScreenWritesAreAccepted`, `SettingsTest.nightHoursWrittenAsBareMinutesStillReplay`, `AppControllerTest.theNightWindowFromTheSettingsScreenIsTheOneTheWarningUses`, `SettingsAndHistoryTest.theNightHoursRowShowsTheWindowThePickIsStoredAs` |
| The front shows last, next, the band, and links | `MainActivity.render` | `MainActivityTest.theFrontScreenFollowsTheLog` |
| The band is the last 24 hours, ending now | `TimelineBuilder.build` | `TimelineTest.theWindowIsTheLastTwentyFourHoursEndingNow` |
| The band replaces the Today list | `activity_main.xml`, `TimelineView` | `MainActivityTest.aFreshInstallShowsTheTwoButtonsAndSaysNothingElse` |
| Meals are filled dots, snacks hollow, times printed | `TimelineView.onDraw`, `TimelineView.drawTimes` | `MainActivityTest.theFrontScreenFollowsTheLog`, `ScreenshotTest.theFrontWithADayOnTheBand` |
| Night hours are shaded from the setting, including a window that crosses midnight | `TimelineBuilder.nights` | `TimelineTest.theNightHoursAreClippedToTheWindow`, `TimelineTest.theNightHoursFollowTheSetting`, `TimelineTest.aNightWindowThatCrossesMidnightIsOneStretch` |
| Midnight is a line inside the band, never its edge | `TimelineBuilder.midnights` | `TimelineTest.midnightIsMarkedInsideTheWindowNotAtItsEdge` |
| Hour labels follow the phone's clock across a DST change | `TimelineBuilder.hourMarks` | `TimelineTest.springForwardKeepsTheLabelsOnTheClock`, `TimelineTest.fallBackKeepsTheLabelsOnTheClock` |
| Nothing on the band is a judgement | `Text`, `TimelineView` | `TextTest.noBannedWordEverReachesTheScreen`, `tools/wording.py` |
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

Three lines in this table are the build's reading of a note that was silent, and all
three are recorded in the vault note's log: the reminder follows the **newest** meal, the
stale line is a fixed eight hours rather than a setting, and the band ends at now, which
is why the next meal is not on it — the Next line carries that.
