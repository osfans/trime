# Changelog

All notable changes to this project will be documented in this file.

## [3.3.5] - 2025-07-01

### 🚀 Features

- Implement Parcelable for theme classes

### 🐛 Bug Fixes

- Navigation bar blocked out the virtual keyboard (again)
- Fix crash on inline suggestions response and improve ui
- Malfunction of sound effect loading
- Incorrect sound effect on press of some keys
- Error on key custom color parsing
- Custom key colors were transparent sometimes

### 🚜 Refactor

- Polish InputFeedbackManager
- Enhance Kotlin-made config parser
- Mapping theme to data classes

### ⚙️ Miscellaneous Tasks

- Bump version to 3.3.5
- Upgrade gradle to 8.14.1
- Add 3.3.5 changelog

## [3.3.4] - 2025-05-01

### 🐛 Bug Fixes

- Couldn't load theme with over 200 anchors and aliases
- Frequent exception on color parsing
- Theme name in list might be empty
- Potential uninitialized theme property exception
- Fix the nightmode not working problems (#1633)
- Fix boost file check

### 🚜 Refactor

- Show clear exception when failed to eval valid color scheme

### ⚙️ Miscellaneous Tasks

- Bump version to 3.3.4
- Update librime-lua to e3912a4
- Upgrade boost to 1.88.0
- Upgrade runner to window-2025
- Add 3.3.4 changelog

## [3.3.3] - 2025-03-01

### 🚀 Features

- Clear text selection after copy
- Add switch to not reset shift state for arrow keys
- Set Trime to support inline suggestions for v30 onward
- Add "autofill" library
- Add logics & UIs for displaying inline suggestions
- Add `CandidateModule` to encapsulate different candidate module
- Add new `Suggestion` State to `QuickBarStateMachine`
- Handle `onInlineSuggestion()` in `QuickBar` with `suggestionUi`
- Request and handle inline suggestion in `TrimeInputMethodService`
- Page navigation using prevIcon and nextIcon components
- Add corner radius to highlighted candidate items in popup candidate window
- Add setting to hide quick bar when always show candidates window
- Notify on preference changes
- Avoid crash loop
- Allow users to trigger rime action via adb shell
- Add setting option, schema switches debounce interval
- Execute background sync work via WorkManager
- All trime modifier keys, support long-press lock

### 🐛 Bug Fixes

- Do Not Send Key Event for NumPad Key
- Notification/toast on RimeMessage will be popped up repeatedly
- Shift + arrow keys not working to select text
- Copy, cut keys not working when the shift key is locked
- Edit action interceptor not working
- Theme preset trime combination keys not working
- Liquid keyboard symbollist not working
- In drafts or collection tab of liquid keyboard, adapter data changes to clipboard after copying text
- Liquid keyboard not regenerate data as expected after related view cache was destroyed
- Clipboard update listener, not updating data as expected after copying existing text
- Clipboard view cache exists, switching clipboards after copying text not update data to show newly copied content
- Clipboard item text color not follow theme key text color
- Liquid keyboard not auto-scrolling to top after data update
- Composing key label not display and reset as expected
- Force show mode, toggle switch options cannot update view
- CurrentKeyboardView has not been initialized, causing crash
- Key sequence cannot switch keyboards and commit partial text
- Force show mode, candidates and window components show simultaneously (temp)
- Key sequence cannot guide symbol and lua script mapping
- Key sequence cannot parse theme preset keys with same name as key types
- Setting ascii_mode to true in composing state causes symbol to be repeated commits
- Labeled candidate item in CandidatesView couldn't break line correctly
- Keyboard hides once click input box when the candidates window is always shown
- NPE on key commands that to start activity with intent
- Key preview not dismissed as expected
- Schema switches would be processed twice
- Key sequence not handling `commit` and `text` preset keys and not processed in order
- Duplicated toast on deploy messaging (again)
- *(jni)* Recreate rime session when necessary
- Sending non-Android key events will commit "Not a Character" text
- In `ascii_mode` and `ascii_punct` modes, symbols for which mapping has not been set in the scheme config will cannot be committed
- *(jni)* Enabled schema list always showed the previous settings
- *(daemon)* Use distinct notification IDs for deploy start/success/failure states
- IMS didn't workaround null cursor anchor info correctly
- Navbar color didn't change after changing theme or color
- *(jni)* Potential crash on setting rime runtime options
- Notification might not pop on screen by default
- Key preview displays incorrect label
- Space, number, symbol keys hook shift not working
- No vibrate effect for candidates
- Preset keys abbreviation not working
- Key bindings not working for Shift + symbol
- Crash when selecting new theme/color
- Modifications to theme configs didn't take effect

### 🚜 Refactor

- Merge modifier state with the current keyboard's modifier state after sending combination keys
- Simplify PrefMainActivity
- Still handle rime deploy message in RimeDaemon
- Add inline suggestions handling in `broadcaster`
- Use `CandidateModule` instead and add `SuggestionCandidateModule` to receive event
- Remove inject annotation in `CompactCandidateModule`
- Remove rime keycode to unicode mapping
- Remove unused delegated rime api
- Remove redundant run state checking
- Migrate `Rime.setOption` and `Rime.setCaretPos` to new api usage
- Make clear how to get and expand active text for command express
- Merge `schemaItemCached` and `inputStatusCached` into `statusCached`
- Improve getting drawable from color schemes
- Restore the candidate window background to previous settings
- Polish candidates window settings wording
- Polish (virtual) keyboard settings wording
- Use rime dedicate api to change candidate page
- Improve touch event receiving of stock PreeditUi
- Improve touch event receiving of CandidatesView
- Makeup a universal TouchEventReceiverWindow
- Make sure CandidatesView positioning correct on first time showup
- Make sure CandidatesView will not display overflow the screen
- Update touchEventReceiverWindow's position after CandidatesView's
- Simplify the setup of PageCandidatesUi's listeners
- Extract CandidatesView's cursor anchor updating to IMS
- Re-register intent receiver for IMS
- Add helpers to manage levers api
- Add helpers to transform candidate list
- Pass version name to rime setup via JNI
- Remove unused stuffs from `jni-utils.h`
- Remove user config accesses when select schema or set option
- *(jni)* Pack rime proto marshaling as rime c api
- *(jni)* Use std::string_view as more as possible
- Reduce nesting of KeyMessage in TrimeInputMethodService
- New public createNotificationChannel util method
- Replace logcat DSL with `subprocess`
- Make custom proto apis comply with the original style
- Improve CandidatesView's positioning
- Adjust preedit ui setups and appearance
- Remove show status bar icon settings
- Remove deprecated setting fields in theme
- Improve color/drawable resolving
- *(config)* Add ConfigNull and ConfigTagged types
- Cancel showing mini keyboard when use physical keyboard
- Improve theme parsing
- Rebuild theme setting delegates
- Improve color scheme parsing
- Remove deprecated theme setting fields from data classes

### ⚙️ Miscellaneous Tasks

- Bump version to 3.3.3
- Bump librime to 1.13.0
- Introduce AndroidX Work library
- Update dependencies and toolchains
- Upgrade spotless to 7.0.2
- Upgrade gradle to 8.12.1
- Upgrade librime to 1.13.1
- Add 3.3.3 changelog

### Build

- Always overwrite files when install OpenCC data
- Remove UseZGC option for gradle
- Enable app shrinking for release build type
- Add more app shrinking settings
- Re-enable developers use file(s) to store sign key properties

### Reforce

- *(key)* Reduce redundant code, 'ascii' support 'send_bindings'

## [3.3.2] - 2025-01-01

### 🚀 Features

- Add Android keycode to scancode mapping
- Implement RimeKeyEvent
- Replace Composition (view) with CandidatesView
- Make new PreeditUi support moving cursor on touch
- Integrate UI creation for PreferenceDelegate
- Allow user to determine the candidates view mode
- Add PageinationUi to indicate if candidates page has prev or next
- Restore horizontal padding for candidate item in candidate window
- Restore vertical layout in candidate window
- Add keyval unicode mapping to process unhandled-by-librime key
- Enhance physical keyboard support with candidates window
- Show preedit ui on the top of bar when candidates window is disabled
- New deploy user experience
- Improve candidate item display
- Improve switch display
- Improve candidate window display

### 🐛 Bug Fixes

- Clipboard update not in time
- Main keyboard view would disappear after switching schema
- Back, Escape and Enter key action was handled before forward to librime
- Space key always showed current schema name
- Temporary workaround for duplicated return action (again)
- Only the candidates of the first page could be selected in popup window
- Reduce crash on flexboxlayout changing on candidates update
- Keyboard view would be disappear after recreating input view
- Workaround for some symbols cannot be committed
- Make sure the window view height can always follow current keyboard height
- Schema name on space bar didn't change after switching schema
- Workaround for some text pattern cannot be simulated as key sequence
- Wrong behavior on pressing return key on physical keyboard
- Candidates window blocked the bar at first time showup
- Regression that return key from physical keyboard would duplicate new line
- Add missing highlighted candidate background
- Crash on creating notification on deploy failure on Android 12+
- `KP_*` would be processed twice

### 🚜 Refactor

- Replace SimpleKeyItemBinding with SimpleItemUi
- Transform FlexibleAdapter with BaseDifferAdapter
- Remove deprecated and unused api
- Update key processing api usage
- Utilize scancode to improve key event handling
- Tell key processing API if the system key event is ACTION_UP
- Rename Event to KeyAction
- Polish the code of KeyAction
- Reduce redundant nesting during key processing
- Rename KeyEventType to KeyBehavior
- Remove unused override `onWindowShown/Hidden` in TrimeInputMethodService
- Relocate the files in candidates
- Rename InlinePreeditMode to ComposingTextMode and set DISABLED as default value
- Remove deprecated string res and preference items
- Try to clean up the code in KeyboardView
- Make Keyboard as KeyboardView a primary constructor's parameters
- Cleanup for keyboard drawing in KeyboardView
- Remove deprecated popup keyboard stuffs in KeyboardView
- Clean up the code of Key and Keyboard
- Replace LeakGuardHandlerWrapper with coroutines
- Improve cursor following of candidate window
- Remove scancode mapping
- Judge key up state by modifiers
- Remove deprecated GraphicUtils
- Make candidates window can show at fixed position perfectly
- Extract `showDialog` from InputView to IMS
- Clean up `ShortcutUtils`
- Split Utils.kt by function or receiver type
- Slightly refine NinePatchBitmapFactory.kt
- Remove unused resources
- Bundle core native lib version name into BuildConfig
- Improve user experience of settings pages
- Correct preedit view behavior and polish its appearance
- Remove librime charcode plugin
- Remove iconv dependency
- Merge RimeNotification and RimeEvent as RimeMessage
- Try to improve the showing of the preedit view
- Migrate DialogUtils to ProgressBarDialogIndeterminate
- Remove speech recognition
- Deprecate IMS instance getter
- Constraint the text views' height in CandidateItemUi
- Share the features of CandidateItemUi to SwitchUi

### ⚙️ Miscellaneous Tasks

- Bump version to 3.3.2
- Upgrade librime to 1.12.0
- Update librime to 1.12.0-1-gec40354
- Upgrade ktlint to 1.5.0
- Add 3.3.2 changelog

### Build

- Try to adjust gradle jvm arguments
- Refactor build process

## [3.3.1] - 2024-11-01

### 🚀 Features

- *(core)* Implements KeyValue and KeyModifier
- Implement RimeEvent to hold events created by this frontend
- Hide scroll bars of switcher view

### 🐛 Bug Fixes

- Switches weren't updated after switching schema
- Shift action could not be committed when ascii mode is off
- Unrolled candidates size was actually limited to about 144
- Could not unroll the candidates somehow
- Unrolled candidates size was still limited to about 144
- Some symbols would be committed twice in full and half shape
- Assets in sub directories ran out of its parent in dest path
- Data checksums descriptor didn't copy correctly
- Couldn't smart match the keyboard corresponding to the schema id
- Forgot to invoke response handlers in Rime itself
- Could not scroll down the unrolled candidates
- Metrics of strings in RimeProto were not completely converted
- Filter opencc data file
- Ime could not response key event from physical keyboard (#1485)
- Truncated composition view (#1479)
- Duplicated characters in ascii mode
- Popup composition view blocked the bar view at first show
- Duplicated line breaks

### 🚜 Refactor

- "pack" the text and comment view so that they are as centered as possible
- Slightly shorten the default animation duration
- Never fill the width of the candidate item view
- Make the candidate text always in center while ...
- Truncate the candidate at the end if the text is too long
- Improve key event forwarding
- Split out KeyboardActionListener from KeyboardView
- Split out CommonKeyboardActionListener from TextInputManager
- Make unrolled candidate view high customizable
- Remove useless/unused keyboard settings
- Split out EnterKeyLabelModule from KeyboardView
- Remove debounce when selecting candidates in the compat view
- *(api)* Update context in rime engine lifecycle looper
- *(ime)* Merge TextInputManager into TrimeInputMethodService
- Handle window switching in input(view) scope as more as possible
- *(ime)* Reduce redundant text committing functions
- Always pass the copy of the active theme to the views ...
- Enhance the process of theme switching
- Optimize timing background sync
- Move on result action into FolderPickerPreference
- Drop unnecessary data dir change listeners
- Cleanup the process of handling rime response inside Rime
- Enhance rime notification handling
- Merge notification flow and response flow as callback flow
- Move RimeResponse into RimeEvent as IpcResponse(Event)
- Improve the build of spanned composition
- Transform LiquidTabsUi with RecyclerView

### ⚙️ Miscellaneous Tasks

- Bump version to 3.3.1
- Remove google java format
- Upgrade ktlint to 1.3.1
- Format with ktlint 1.3.1
- Ignore code format patches within git blame
- Share copyright profile
- Update native dependencies
- Upgrade opencc data
- Update development guide [skip ci]
- Add OpenCC data install path to gitignore
- Update librime to 1.11.2-39-gb74f5fa0
- Switch to macOS 15 runner
- Add 3.3.1 changelog

### Build

- Implement OpenCCDataPlugin to install OpenCC data
- Remove rules for installing OpenCC data in Makefile
- Fix deprecated function usage

## [3.3.0] - 2024-09-01

### 🚀 Features

- Internal shared data directory
- Builtin prelude files
- Initial implementation of SwitchesUi
- Add runtime option setter and getter to new api interface
- *(jni)* Use NewString to create jstring
- *(jni)* Add getRimeCandidates API
- *(jni)* Add selectRimeCanidate and forgetRimeCandidate APIs
- *(api)* Initial implementation of emitting rime response via shared flow
- Update composing text via rime response flow
- Update candidates via rime response shared flow
- Update composition via rime response shared flow
- New (compact) candidate view using recyclerview
- *(utils)* Introduce EventStateMachine
- *(window)* Add default animation effect when enter or exit
- Implements unrolled candidate view
- Restore the highlight of the candidate

### 🐛 Bug Fixes

- Candidates are abnormally centered
- 输入状态下切换深色模式时，悬浮窗无法关闭
- Fix list is empty
- 输入状态下切换配色，悬浮窗无法关闭
- Error on access to user data dir especially on app first run
- Ime could not follow the keyboard's ascii mode after switching
- Switcher didn't update after switching to different ascii mode keyboard
- Keyboard layout didn't switch in time on device's orientation changed
- Couldn't back to appropriate keyboard layout from others at landscape mode
- Keyboard layout sometimes inadvertently backed to the default layout
- Timing sync (#1441)
- Candidate view in LiquidKeyboard didn't show all bulk candidates
- Key sequence could not be committed when ascii mode is on
- Inaccurate left offset before the compact candidate view ...

### 🚜 Refactor

- Hide composition view on input view detached from window
- Create main keyboard view without binding
- Cancel jvm overloads on keyboard view
- Rename SchemaListItem to SchemaItem
- Add `schemaItemCached` and `currentSchema()` to Rime(Api)
- Migrate KeyboardSwitcher features into KeyboardWindow
- Slightly change the base data syncing logic
- Move bar ui classes into ui package
- Restore the style for SwitchesUi
- Apply the new runtime option setter and getter as more as possible
- Add STOPPING state for RimeLifecycle
- Slightly improve the switches view
- Migrate rime out data class into RimeProto
- Adjust the data struct of RimeProto
- Rename CandidateListItem to CandidateItem
- Implements QuickBarStateMachine to drive UI update of QuickBar
- Remove obsolete candidate view
- Remove obsolete custom scroll view
- Remove unused api functions
- Rename CandidateAdapter to VarLengthAdapter
- Remove unused preference entries

### 📚 Documentation

- Add SPDX license header with reuse

### ⚙️ Miscellaneous Tasks

- Bump version to 3.3.0
- Use form for issue template
- Checkout submodules recursively on pull request and commit
- Disable layout update animations.
- Introduce BRAVH library
- Update librime to 1.11.2-27-gcdab8936
- Introduce AndroidX Paging library
- Upgrade gradle to 8.10
- Add 3.3.0 changelog

## [3.2.19] - 2024-06-30

### 🚀 Features

- Smarter and faster assets syncing
- *(event)* Add command set_color_scheme
- Add button to copy brief result logs to clipboard
- Custom liquid keyboard fixed key bar
- Fixed key bar support `round_corner` , `margin_x`
- Fixed key bar support image `key_back_color`
- Restore selected files only

### 🐛 Bug Fixes

- Crash on showing composition popup window somehow
- Crash on showing toast in LogActivity on some custom ROMs
- Data dir preferences didn't show default value on init
- Main keyboard view wouldn't show up after switching schema
- Attempt to correct the wrong popup window position on moving
- Index out of bounds when calculate offset in Composition view
- Candidate ascii_mode state mismatch
- The last candidate font size is abnormal
- Work with the old synced asset files hierarchy
- Liquid keyboard `key_height` , `margin_x` no work
- Fix missing debug stripped native libs
- `dbAdapter.type` not updated when switching tabs
- Theme file named xx.yy.trime.yaml cannot be selected
- Copy folders in `assets` correctly
- Use `/` as path separator for all OS
- Crash on access to split option enum constants
- `ascii_keyboard` tag was malfunctional
- Draw all computed candidates aligned with the first one in the Y-axis direction
- Use unified landscape mode
- Use local context instead of appContext

### 🚜 Refactor

- Move DataManager in a standalone package
- Utilize custom resource util methods to reset assets
- Split out composition stuffs from text package
- Adjust RimeComposition fields
- Simplify the logic to build composition view content
- Update composition view from external context data
- Move CompositionPopupWindow into InputComponent (and InputView)
- Rewrite ascii mode switch logic
- Refactors to picker dialog
- Use custom toast utils
- Use native apis to build notification
- Use native apis to get stuffs from system's resource instance
- Use native apis to build intent and start activity
- Use custom uri utils
- Access internal inset resources by self
- Implement PreferenceDelegate to simplify AppPrefs
- Move AppPrefs to prefs package
- Use enum class to list app ui mode
- Reimplement StringUtils as Strings and Regex
- Remove outdated licensing layout
- Add `Char.isAsciiPrintable` function
- Move `(commit/clear)Composition` to new api interface
- Simplify the code of Candidate view
- Move the composing logic to the view into InputView

### 📚 Documentation

- Add missing license header
- Add missing SPDX header
- Add 3.2.19 change log

### ⚡ Performance

- Make parseColor no longer check the parameter is null

### 🎨 Styling

- Format code

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.19
- Update trime-schema.json
- Upgrade to ubuntu 24.04 runner
- Fix broken jni cache source path for release
- Built-in theme name localization
- Remove androidutilcode from dependencies
- Migrate to rime_get_api
- Upgrade librime lua with rime_get_api
- Upgrade librime with rime_get_api
- Add .kotlin to git ignore
- Upgrade boost to 1.85.0
- Upgrade gradle to 8.8

### Build

- Upgrade kotlin to 2 and more

## [3.2.18] - 2024-05-01

### 🚀 Features

- Slightly enhance the handling of the old opencc dict format (ocd)
- Enhance the exception handling when build opencc dictionaries
- Add json schema
- Add operation area in LiquidKeyboard
- Show notification when restarting rime
- Add class & mapper to hold style parameters
- Add `GeneralStyle` to `Theme`
- Implements RimeDispatcher for running the rime backend solo on a single thread
- Make dialog use device default settings theme outside the app
- Override `toString()` method of custom config types
- Auto scroll to the activated liquid tab when it's out of the viewport
- Allow user to determine navigation bar background behavior
- Excerpt the text of clipboard, collection or draft entry ...

### 🐛 Bug Fixes

- App would crash on clipboard entry editing
- Bar could not be hidden correctly with corresponding rime option
- Fix composition window disappear
- Too large key popup preview and it didn't dismiss after pressing
- Keys were not all be invalidated when switching from smaller keyboard view size
- Set vibrate duration only if time > 0
- Clear previous liquid keyboard data before adding new one
- Retain current scheme ID when changing day/night theme
- Didn't actually enable iconv for boost locale
- Build failure with glog v0.7.0
- Wrong judge condition for opencc dict type [skip ci]
- Update to the latest librime to fix api's malfunction
- Disappear clipboard, collection and draft in LiquidKeyboard
- Random NPE when loading config in TrimeInputMethodService
- Move sign in the composition window was too small
- Crash on clicking blank area when enable auto split keyboard space
- Adjust `InitializationUi`'s inset to unblock the whole screen
- Random crash on composition popup window moving
- Notify data set changed using `notifyDataSetChanged()`
- Calculate size using `mBeansByRow`
- Do not set background color for `EventSpan` if `key_back_color` is not color
- Set key width to 0 if `width` not set
- Incorrect filled items' background when fast scroll the clipboard
- LiquidTabsUi didn't scroll to current selected tab on init
- Back button in LiquidKeyboard didn't work well with some themes
- Wrong tag name in release artifactory (#1289)
- Inaccurate command to get build version name
- Landscape keyboard's keys' position
- Crash on switching from other input method
- Create rime session by lazy in MainViewModel ...
- IntentReceiver was re-registered in TextInputManager
- Ensure to pass non-null input editor info to `startInput` of InputView ...
- Calculate scaled vertical gap to fit all keyboard height
- Forgot to require full check when manually deploy or sync user data
- Actions in dialog didn't actually launch sometimes
- Crash on typing after switching theme in the app settings
- Librime backend didn't log to logcat
- Emit ready state in instead of after `dispatcher.start()`
- Could only turn the candidate page once
- Add missing new line
- Error key label in parseAction
- Error on getting color or drawable from external map config
- Page Up/Down symbol text size
- Failed to load sound effects
- Incorrect real position got from the symbol board of `TABS` type
- Liquid keyboard could not switch to `tabs` tab ...
- LiquidKeyboardEditActivity didn't follow the night mode

### 🚜 Refactor

- *(keyboard)* Tidy KeyboardView
- *(symbol)* Tidy LiquidKeyboard and FlexibleAdapter
- *(symbol)* Tidy TabView, TabManager and SimpleKeyDao
- Remove deprecated dimension methods
- Make the variables in Key.kt comply with CamelCase
- *(enums)* Tidy enum classes
- Tidy the stuffs of input method service
- Merge EditorInstance into TrimeInputMethodService
- *(symbol)* Refine the way to get drawable for the adapters
- Try to introduce kotlin-inject
- Implement window manager to manage all kinds of keyboard window
- Wrap the message value of a rime notification into data class
- Handle rime option in sub input components ...
- Provide InputBroadcaster via InputComponent
- Slightly refactor to schema deserialization
- Make ConfigMap/ConfigList implement collection interfaces
- Apply custom yaml config parser to Theme
- Fix type
- Extract `DbAdapter` to standalone class so it can refresh data correctly
- Add BarBoardWindow class
- Replace TabView with new LiquidTabsUi
- Automatically switch bar view when board window attach or detach
- Comb and simplify the code logic of LiquidKeyboard
- Enhance exception handling of the config traversing
- Remove redundant parameters to show composition window
- Introduce daemon to manage sessions access to rime
- Enhance lifecycle management of Rime
- Adjust TextInputManager constructor method
- Replace with `GeneralStyle` in `ColorManager`
- Make all fields in `CompositionComponent` to non-nullable
- Replace with `GeneralStyle` in various classes
- Map font family from `GeneralStyle`
- Remove unnecessary condition checking
- Replace handler with main looper with custom RimeLifecycleScope
- Add schemata stuffs to RimeApi
- Enhance the UX of selecting/enabling schema(ta)
- Remove the judgment of whether the notification is handling
- Enhance handling of exception when built files are removed by user manually
- Use native notification builder api to notify restarting
- Make the code of parseAction in Event more neat
- Make the code of LiquidKeyboard more clean
- Move SymbolKeyboardType to symbol package and rename to SymbolBoardType
- Merge `candidateAdapter` into `varLengthAdapter ` in LiquidKeyboard
- Get database data in place on selecting
- Simplify the logic of the init of the fix data in LiquidKeyboard

### 📚 Documentation

- Generate changelog with cliff
- Add conventional commits in pull request template
- Add SPDX license header with reuse

### ⚡ Performance

- Reduce KeyboardView memory usage

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.18
- Tidy KeyboardView
- Upgrade spotless to 6.25.0
- Upgrade ktlint to 1.2.1
- Apply ktlint to kts
- Add kts target on ktlint
- Update to the latest librime
- Update librime plugins to their latest
- Add macos 14 m1 runner
- Upgrade librime to 1.11.0
- Chanage macos dependency step name
- Add git cliff config
- Bump jvm target to jdk 11
- Upgrade gradle to 8.7
- Downgrade java version to 17 on release and nightly build
- Remove unused `Theme.Style`
- SpotApply style
- Add resue target
- Upgrade librime to 1.11.1
- Update android gradle plugin to 8.3.2
- Add fastlane metadata for F-Droid
- Update version info of native license metadata
- Add changelog target
- Update changelog of 3.2.18

### Build

- *(jni)* Remove unneeded boost dependencies
- *(jni)* Remove unused compile definitions

### Build,refactor

- Drop koin dependency injection framework

## [3.2.17] - 2024-02-26

### 🚀 Features

- Respect `liquid_keyword/single_width` in `trime.yaml`
- Implement RimeDataProvider
- Request storage permission in setup
- Support font family
- Reveal the keyboard background when navigation bar is visible
- Support long press key vibration
- Built in default fallback colors
- Request notification permission on Android 13

### 🐛 Bug Fixes

- Display `SYMBOL` type using var-length style
- Only call `updateComposing()` if not using `LiquidKeyboard` so tab will not scroll back to the start
- Notify dataset changed after data updated
- Liquid keyboard `TABs`
- Reset to INIT state if `LiquidKeyboard` is reset
- Hide `LiquidKeyboard` first initially
- Show toolbar whenever `selectLiquidKeyboard()` is called
- Prevent NPE of `CompositionPopupWindow`
- Wrong functional key back color
- ObtainBoolean
- Reveal dialogs on Android P (#1196)
- Call `super.onTouchEvent` when in "Toolbar" mode
- Do not set zero-width non-clickable key with default width
- Crash on getLabel
- Try to fix potential crash when start input view
- Crash after switching to liquid keyboard
- Color didn't refresh immediately when users change it
- Color would be repeatedly updated on some custom ROMs
- Updated view in non-ui-thread
- Keyboard layout became cluttered after changing themes
- `_hide_candidate` didn't actually hide candidate bar
- Invalid text label for preset keys
- Fallback to unprocessed image path
- Some key colors are incorrect
- Composition popup window didn't update width and height in time
- Enter label was empty after changing theme or color in place
- Incorrect navigation bar appearance in the activities of the app
- Potential NPE crash on window shown
- Crash with Koin on LiquidKeyboard (#1231)
- The keyboard overlapping with the navigation bar
- Invalid keyboard lock
- Random crash on select LiquidKeyboard tab
- Apply correct unit for the text size of `Paint`s (#1252)
- No koin definitions found when recreating InputView
- Determine whether to follow the system's night mode switching color scheme
- *(jni)* Wrong integer type conversion
- *(setting)* Add missing value of candidate quantity
- Horizontal alignment of candidate items
- Space key label set as a scheme name
- Failed to load theme which include default theme's fallback_colors
- Keyboard layout mismatched
- Popup window cannot close
- Avoid reading ENABLED_INPUT_METHODS on 34+

### 🚜 Refactor

- Make `SimpleAdapter` faster
- Use `initVarLengthKeys()` for `TABS`
- Remove `dimens.xml`
- Justify content in `simpleAdapter` with `space_around`
- Further improve `LiquidKeyboard` performance by change implementation
- Extract logic of `mPopupWindow` to `CompositionPopupWindow`
- Directly read theme name instead of caching using local variable
- Get color/drawable
- Remove old input root layout
- Manage bar ui with QuickBar
- Manage keyboard ui with KeyboardWindow
- Use enum state instead of index to switch UI in InputView
- Key.java to kotlin
- Keyboard.java to kotlin
- KeyboardView.java to kotlin
- Event.java to kotlin
- Kotlinify Speech.java
- Kotlinify Trime.java
- Merge the features of DataDirectoryChangeListener into DataManager
- Fully manage Theme instance in ThemeManager
- Complete the migration to InputView of which included components
- Move LiquidKeyboard to InputView
- Handle option notifications about input view in InputView
- Mutable isDarkMode parameters
- Handle works of KeyboardView on start/finish input in InputView
- Reduce redundant operations in the input method service
- Bind composition view into its popup window
- Simplify reset processes when changing theme or keyboard settings
- Fill the entire keyboard view with keyboard background
- Enable edge-to-edge display for activities with builtin method
- Adjust app theme color palette
- Try to improve key press vibrate feedback
- Kotlinify symbol
- *(symbol)* Use data class to make life easier
- *(symbol)* Increase adapters' code quality
- *(symbol)* Singleton TabManager
- Kotlinify ScrollView.java
- Kotlinify Composition.java
- Kotlinify Candidate.java
- Tidy Candidate.kt
- Tidy Composition.kt
- Tidy ScrollView.kt
- Update APIs of the custom dimensions util
- Remove redundant variables in Trime service
- Separate color management from theme
- Migrate to continue using exact alarms above Android 13
- Other refactors to adapt Android 14
- Remove alias of the input method service

### 📚 Documentation

- Add telegram group to README

### ⚡ Performance

- ParseColor without underline
- Remember last dark/light color scheme
- Dynamically load and cache keyboard layout
- Reduce duplicate keyboard loading
- Split keyboard from the event
- Caching used fonts
- Cache used Event
- Reduce duplicate binding of keyboard to inputView
- Speed up recycler view Adapter
- Adjust the RecyclerView cache size

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.17
- Complete release ci configuration
- Create preprelease when ref name contains 'alpha' or 'beta' [skip ci]
- Create nightly release attached to the latest commit
- Use built-in generated release notes on nightly build
- Remove obsolete keystore tar
- Split out nightly build ci configuration
- Remove cache magic file
- Attempt to make nightly build has correct changelog
- Introduce Koin dependency injection library
- Update librime and librime-predict
- Upgrade google java format to 1.19.2
- Qualify import name
- Update pull request template
- Upgrade action to v4
- Add telegram link to About page
- Optimize build doc
- Add F-Droid to About page
- Update liquidkeyboard in assets theme
- *(commit-ci)* Add runner os name as the suffix of jni artifacts
- *(release-ci)* Fix wrong tag match pattern on push event
- *(README.md)* Remove community information
- Remove specified buildABI in gradle.properties
- Upgrade gradle to 8.6
- Rebase to dev branch
- Update targetSdk version to 34 (Android 14)

### Build

- Adjust gradle properties
- Update deprecated gradle feature usages
- Try to remove redundant guava dependency
- Implement native convention plugins
- Keep apks splitted by ABI with release build type
- Implement the gradle task to calculate native cache hash
- Remove unneeded scripts
- Try to make build reproduciable
- Change to archiveBaseName
- Support Java 21 compile
- Set builder as unknown on error or getting empty

### Build,ci

- Allow to specify a array of target ABIs

### Build,refactor

- Change the way to resolve keystore.properties file

## [3.2.16] - 2024-01-01

### 🚀 Features

- Add "Clear All Logs" & "Jump To Bottom" button
- Add time in logcat
- Display a loading screen as keyboard during deploying
- Add scrollbar style to candidate view

### 🐛 Bug Fixes

- Incorrect schemaId to resolve keyboard
- Incorrectly consume `keyEvent`
- Missing init values for `LandscapeInputUIMode`
- Mini keyboard toggle issue when plug/unplug the physical keyboard
- Float window covers the input text on top/bottom edge
- Reset to original state when restarting input in the same view
- Commit any composing text when cursor's position changed
- Cannot auto switch back between night mode & day mode
- Resolve color and image path correctly in themes
- Support reading non-compiled nine patch images
- Select first theme if selected theme was removed after deploy
- Revert the candidate window logic as before
- Modify text of `scroll_to_bottom`
- Rollback of dc27449
- Remove obsolete version of boost
- Fixed an implementation error in WeakHashSet
- Return default keyboard if no keyboard is matched
- Prevent NPE
- Set view's height so it won't be fullscreen at first
- Bind keyboard immediately so height won't jump up and down
- Check if external storage path is empty before starting `RimeWrapper`
- Instantiate property as requested instead of caching it
- Correct lock & release mutex in `deploy()` method
- Move `fontDir` to method so it always refers to the latest value
- Handle html hex code input
- Deploy in background thread when triggered by broadcast

### 🚜 Refactor

- *(jni)* Remove workaround for rime tools
- Fix coding style
- Fix compile error due to dependency update
- Add `RimeWrapper` to deploy rime in async manner
- Wait for rime deployment completed before doing any work
- Do not call `Rime.getInsance()` during init
- Use `RimeWrapper` to deploy instead of using rime directly
- Display loading dialog in preference screen when deploying in background
- Casts as a more generic `ViewGroup`
- Remove extra loading dialog
- Change loading text to "deploying"
- Dismiss `loadingDialog` to prevent leakage
- Standardize deploy process with a result dialog
- Add `InitialKeyboard` to display before deployment
- Add `canStart` to `RimeWrapper` to prevent auto startup
- Add `PermissionUtils` to check if all required permissions granted
- Set `RimeWrapper.canStart` if permissions granted
- Display `InitialKeyboard` before deployment or lacks of permissions
- Fix code style
- Remove `DataDirectoryChangeListener.Listener` from `DataManager`

### 📚 Documentation

- Add cmake format document

### 🧪 Testing

- Add unit tests

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.16
- Ignore boost build artifactory
- Update librime to latest commit
- Use change log builder action
- Remove obsolete change log script
- Unify job step name
- Upgrade setup android action to v3
- Change commit template style
- Upgrade gralde to 8.4
- Init convert map with enum values
- Build on Ubuntu, macOS and Windows
- Update build guides
- Improve job steps
- Update dependencies
- Upgrade gradle to 8.5
- Upgrade spotless to 6.23.3
- Upgrade google jave format to 1.18.1
- Add matrix os in cache key
- Rename code style job name
- Upgrade checkout action to v4 in release
- Remove duplicated gradle setup job
- Upgrade jdk to 21
- Rename build trime job name
- Fix sha256sum not found in macOS
- Check C++ files with style job
- Disable windows runner due to build issue
- Install clang-format in macOS
- Skim style check in windows
- Disable fast fail in matrix build
- Use macos to match matrix name
- Fix cache key placeholder in release
- Add .editorconfig file
- Upgrade boost to 1.84.0
- Format cmake files with cmake-format
- Format CMakeLists file
- Fix typo in cache hash script
- Add new contributors to author

### Build

- *(jni)* Try to replace boost git submodule with source tarball
- *(jni)* More neat way to find headers
- *(jni)* Use marisa vendored by (lib)rime
- *(jni)* Try to improve boost build
- *(jni)* Try to improve the build of rime and its plugins
- *(cmake)* Use cmake file command to create symlink
- Remove git branch info
- Refactor build logic
- Migrate build to version catalogs
- Add cmake format target
- Add missing cmake file

## [3.2.15] - 2023-11-01

### 🐛 Bug Fixes

- Timing sync crash above Android 12
- Update opencc asset

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.15
- Upgrade rime to 1.9.0
- Upgrade ktlint to 1.0.1
- Format code with ktlint 1.0.1

## [3.2.14] - 2023-08-31

### 🚀 Features

- *(data, ime, ui, res)* Add edit text function for liquid keyboard
- Add confirm delete all function

### 🐛 Bug Fixes

- Fix build error of missing resource
- Fix build config error
- Apply all kotlin code with ktlint
- Apply the ktlint rule
- After the pinned status changes, multiple items may be affected and all need to be updated.
- Update clipboard view when clipboard content changes.
- Fix composition window disappear
- Modify the wrong kaomoji keys.
- Fix liquid keyboard (#1052)
- Fix the crash that happens when the screen is rotated. (#1054)
- Fix two issues with temux
- Init flexbox by screen orientation
- Initialize the keyboardView.layoutManager every time in the liquid keyboard
- Update data directory on time
- Update tab manager when theme changes
- Add missing boost header
- Refresh liquid keyboard's candidates view

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.14
- Update .gitignore template
- Upgrade gradle to 8.2
- Upgrade ndk to 25.2.9519653
- Upgrade google java format to 1.17.0
- Upgrade AGP to 8.0.2
- Upgrade ktlint to 0.49.1
- Apply ktlint format
- Upgrade spotless to 6.20.0
- Upgrade ktlint to 0.50.0
- Upgrade gradle to 8.2.1
- Remove obsolete artwork
- Update trime author
- Upgrade rime to latest
- Upgrade boost to 1.83.0

## [3.2.13] - 2023-06-15

### 🐛 Bug Fixes

- Fix dynamic keyboard outdated data

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.13

### Add

- Monochrome icon for Android 13+ devices

## [3.2.12] - 2023-04-24

### 🚀 Features

- *(jni)* Add getRimeStateLabel api which returns the state label for UI display
- *(data)* New method to resolve compiled config file path
- *(util/config)* New config parser
- Custom UncaughtExceptionHandler
- Expose the static create method of Config
- *(ConfigTypes.kt)* Add contentToString method to each config types
- *(util/config)* Small improvements for Config and ConfigTypes
- *(data,util/config)* Add decode method to ConfigItem

### 🐛 Bug Fixes

- *(build)* Properly setup signing configs
- Remove self registered clean task
- Suppress enum-entry-name-case rule
- Fix trailing-comma-on-call-site rule
- Composition UI disappears after jump to liquid keyboard and then back
- Flicking screen when changing keyboard
- Fix some issues
- Flush layout with switching keyboard
- Move namespace to gradle file
- Avoid potential deployment failure
- *(data)* Enhance the exception handling when parsing schema file
- *(jni)* Add exception handling to prevent unexpected program crashes
- *(Config.java)* Deploy theme file every time
- Replace macros with inline funtction
- *(SchemaManager.kt)* Ensure all switch options' enable index is not less than 0
- *(Rime.java)* Ensure deploy opencc dictionaries each time startup librime (#960)
- *(method.xml)* Could not open setting page from system settings
- Set output of checksum

### 🚜 Refactor

- *(jni)* Split objconv.h from rime_jni.cc
- *(jni)* Slightly refactor CMake stuffs
- Convert old Config.java to Theme.kt
- *(util/config)* Polish new config parser
- *(TrimeApplication.kt)* Refactor logging format
- *(data)* Utilize new config parser to initialize schema stuffs
- *(text)* Move all candidates down once one of them has comment
- Merge the usages of ConfigGetter into CollectionUtils
- Some cleanups
- Adjust third party library summary display
- *(Trime.java)* Some cleanups
- *(rime_jni.cc)* Define notification handler in JNI function
- Convert Rime.java to Rime.kt
- Rename RimeEvent to RimeNotification
- *(core)* Get commit text in place

### 📚 Documentation

- Update pull request template with style lint

### 🎨 Styling

- Add clang format style
- Apply clang format for native file

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.12
- Update getting stared and third party library info in README
- Update targetSdkVersion to 33
- Bump version to 3.2.12
- Apply latest ktlint format
- Upgrade spotless to 6.16.0
- Upgrade google format to 1.16.0
- Upgrade ktlint to 0.48.2
- Hack workaround for gradle 8.0.2
- Upgrade gralde to 8.0.2
- Add clang format helper tool
- Remove spotless check
- Install clang-format package
- Add style lint and format task
- Use default clang format
- Migrate from cookpad/license-tools-plugin to mikepenz/AboutLibraries
- Add license for native dependency
- Update dependencies
- Set jdk version of kotlin for codegen module ...
- Upgrade AGP to 7.4.2
- Upgrade spotless to 6.17.0
- Update librime-lua to latest
- *(ci)* Replace cache check file
- Upgrade gralde to 8.1.1

### Build

- *(build.gradle,res)* Append suffix to debug type package
- Register clean up tasks

### Build,refactor

- Migrate build configuration from Groovy to KTS

## [3.2.11] - 2023-02-28

### 🚀 Features

- *(ime)* Reform how to handle the return (enter) key
- *(core,data)* Sync built-in data before setting up
- *(core,ui,jni)* Reimplement setting integer in custom config

### 🐛 Bug Fixes

- *(data)* Restore disappear key round corners (#895)
- *(data,util)* Parse color values from inputted map first to get drawable
- *(ui)* Initialize sound related stuffs on storage permission granted
- *(data)* Add detection of whether the custom file has modified ...
- *(ui)* Make sure sound package configs are showed in the picker
- *(jni)* Don't specify log dir since we don't really need the log files (#906)
- *(data,ime)* Eliminate the wired padding on the either side of liquid keyboard (#869)
- Unset ascii mode after switching keyboard
- *(core,data)* Build opencc dictionaries in the user data dir
- *(ui)* Display schema name instead of its id in the picker
- *(data)* Don't use librime's API to get the user data dir
- *(data)* Make all properties optional when deserializing a schema config
- *(ui)* Show loading dialog after confirming the schemas to enable

### 🚜 Refactor

- *(data,ime,ui,res)* Enhance key sound theme management
- *(data,ime,ui,util)* Replace SystemService with Splitties's one
- *(core,data,ime,...)* Structuralize the schema list item
- *(core,jni,ui)* Reform the native method of selecting schemas
- *(core,ime,jni,ui,util)* Shrink the native stuffs
- *(core,ime,jni,ui)* Migrate more C++ methods must to call ...
- *(core,jni)* Remove the the bridges of configuration APIs
- *(core,jni)* Remove redundant deployment APIs
- *(core,data,ime,jni,ui)* Move OpenCC APIs to OpenCCDictManager
- *(core,data,ime,...)* Make all native methods comply with Camel Case
- *(core,data,ime)* Split out schema parsing stuffs from Rime.java
- *(core,data,jni)* Bundle more data to schema data class
- *(keyboard,lifecycle)* Remove useless CoroutineScopeJava
- *(ime)* Reform how to initialize the local array
- *(ime)* Cancels TextInputManager's inheritance from MainCoroutineScope
- *(ime,util)* Move launchMainActivity to ShortcutUtils
- *(ime)* Optimize the enum classes related key event stuffs
- *(core,data,ime,jni)* Replace RimeCandidate with new data class
- *(core,jni)* Move some calculations to C++ side ...
- *(ime,util)* Convert GraphicUtils class to object

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.11
- *(res)* Update social media information
- Add kaml dependency ...
- Add several splitties modules
- Generate rime key val constants and keycode mapping using ksp
- Update dependencies and compile sdk version
- Upgrade librime to 1.8.5

### Build,refactor

- *(ime,ui,util)* Eliminate warnings during build

### Refacor

- *(data,ime)* Optimize the handling of key code/event somewhere

## [3.2.10] - 2022-12-25

### 🚀 Features

- *(jni)* Enable native logging
- *(ui)* Show warning or error log info when deploy from the main setting page
- *(data, ime, ui, res)* Add new preference screen for clipboard, ...
- *(core, ime)* Use kotlin flow to emit and handle rime notification
- *(Config)* New basic config type getter implementations
- *(core, data, jni)* Try to redeploy only after the theme config files have been modified

### 🐛 Bug Fixes

- *(ui)* Changing color scheme doesn't work
- *(Keyboard)* Keyboard definition doesn't fallback to default ...
- *(Rime)* Switches on the bar toggle to the opposite states as they show
- Fix crash in theme/color picker

### 🚜 Refactor

- *(data)* Refactor AppPrefs ...
- *(data)* Completely remove methods/variables should can only handle by AppPrefs from Config
- *(data)* Rename some variables to make it easier to understand their usages
- *(data)* Move Config to theme package
- *(data/ui/res)* Refactor database related sharedpeferences to use more proper types to store
- *(data)* Refactor LogView with RecyclerView to improve its performance
- *(jni)* Only call RimeSetup at first run
- *(jni)* Tweak with include_directories ...
- *(ui)* Change the log tag to the new app name of rime traits
- *(data, ui)* Remove old database stuffs
- *(util, ime)* Bundle more system services to SystemServices.kt ...
- *(jni)* Remove out-of-date test class
- *(jni)* Minor adjust to the headers
- *(core, data, ime, util, jni)* Optimize the process to get config map...
- *(Config)* Replace the most of the capture type with Object ...
- *(Config)* Tidy the code that how to parse color string
- *(data, ime)* Rename and simplify PopupPosition (PositionType previously)
- *(data, ime)* Reform the getters of the keyboard style parameters
- *(data, ime)* Reform how to get size parameters for theme layout
- *(data, ime, util)* Reform how to get typeface for the theme layout
- *(data, ime)* Reform the getters of the liquid keyboard parameters
- *(Config)* Reform how to apply sound package
- *(data, ui)* Migrate more theme related parameters to ThemeManager ...
- *(Config)* Minor refactor of the getter holder classes
- *(Config)* Optimize some loop implementations
- *(keyboard)* Remove the Context parameter
- *(data, ime, ...)* Reform some getters of the preset keyboard parameters
- *(data, ime)* Reform KeyboardSwitcher
- *(data, ime)* Update or remove some deprecated methods and parameters
- *(data, ime, util)* Migrate more parameters from Config ...
- *(data, ime, util)* Continue to shrink duplicated or similar parts in Config
- *(data, ime, ui, util)* Shrink the methods of parsing the color parameters in Config
- *(data, core, ime, ui)* Move the sound parameters from Config to SoundManager

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.10
- Update librime to 1.7.3-g9086de3
- Not to require capnproto dependency anymore
- Upgrade opencc to 1.1.5
- Upgrade opencc to 1.1.6

### Build

- *(jni)* Remove capnproto module
- *(jni)* Replace miniglog with glog bundled by librime

## [3.2.9] - 2022-10-22

### 🚀 Features

- Input symbol in liquid Keyboard
- *(ui)* Add animation when navigate between the fragments
- *(ime)* No permission required to show popups above the input view ...
- *(ui)* Simplify the picker creation with implementing CoroutineChoiceDialog class
- *(ui)* Basically implement FolderPickerPreference
- *(util)* Implement UriUtils
- *(ui)* Support to choose data directory via SAF
- *(util)* Add SystemServices to contain frequently used services
- *(data)* Initialize androidx room database stuffs
- *(data)* Initialize ClipboardHelper to enhance management of clipboard
- *(data)* Add and apply migration methods to database
- *(data)* Apply coroutine to room database stuffs
- *(ime)* Operate database beans on keyboard by popup menu
- *(ime)* Add delete all database beans menu action

### 🐛 Bug Fixes

- *(data)* Should list sound profiles in the subdir
- *(ui)* The buttons in the navigation bar were difficult to see
- *(ui/data)* Try to fix hardcoded data dir
- *(ui)* Navigation bar overlaid the last preference
- *(data)* Pinned beans don't move to the top of the list
- *(data/symbol)* Insert a unique bean failed when collect a bean
- *(ime)* Symbol: invisible key text in LiquidKeyboard

### 🚜 Refactor

- *(data)* Remove unused functions in Config.java
- *(ui)* Improve how to show license page
- *(ui)* Polish LiquidKeyboardActivity
- *(ui)* Reorder preferences in OtherFragment
- *(ui)* Fine tune DialogSeekBarPreference
- *(ime)* Move all show-dialog-related methods to Kotlin side ...
- *(ui)* Apply the new show picker methods in activity fragments
- *(ui)* Remove old picker implementations
- *(res)* Remove redundant layout files
- *(ui)* Apply XXPermission APIs in PerfMainActivity
- Remove redundant construct parameter of Rime and Config class
- *(ime/symbol)* Improve LiquidKeyboard UI logic
- Adjust input views
- *(data)* Complete new database stuffs
- *(data)* Adjust database migrate methods
- *(data)* Rewrite implementations for database bean displaying
- *(data)* Continue to improve LiquidKeyboard implementations

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.9
- Remove redundant items in manifest
- Add junit test implementations
- Polish methods to get custom build config fields
- Update gradle build tools plugin to 7.2.2
- Migrate to new gradle settings management
- Update cmake to 3.22.1
- Remove system alter window permission in manifest
- Update dependencies
- Minor reformat manifest
- Fix code style in manifest
- Introduce XXPermission to simplify permission request process
- Add andoridx room to manage database
- Mark argument to export room database architecture

## [3.2.8] - 2022-08-18

### 🚀 Features

- Long click to delete clipboard item
- Show variable length keys in liquid keyboard
- Ignore Shift locke for space, number and symbol
- Change key label when shift key on
- Not use shifted label when Shift lock ignored
- Ignore modifer keys when preset key has modifer
- *(data)* Implement data synchronization in DataManager
- Manage data in clipboard / draft / collection
- Config apps not save draft, increase save draft frequency
- Floating window for liquid keyboard

### 🐛 Bug Fixes

- Selected text not provide to presetkey option
- KP_0 - KP_9 could not input
- Wrong key background
- Missing default.custom.yaml
- Fix copy text crash issue
- Long click to delete wrong behaviour
- Bug in modify keys
- Some key with shift couldn't input in ascii mode
- Modifier state lost when longclick keys
- Commit char when librime not process key
- Shift_lock value ascii_long not work
- Num key not work, show shift label when alt/meta/ctrl on
- *(ui)* Fail to enable or disable schema(s) sometimes
- *(ui)* "Sync in background" preference never show the time and status of last sync
- *(config)* Endless loop in setTheme
- Hide_comment not works

### 🚜 Refactor

- Improve commit text logic and performance
- *(data)* Replace prepareRime with sync in Config
- *(components)* Optimize logic in ResetAssetsDialog
- *(components)* Optimize logic in SchemaPickerDialog
- *(ui)* Put all ui-related files into ui directory
- *(ui)* New MainViewModel
- *(ui)* Apply MainViewModel to PerfMainActivity and its fragments
- *(ui)* Reorganize preference screen
- *(ui)* Rename classes to be consistent with new preference names
- *(util)* Implement withLoadingDialog for lifecycleCoroutineScope
- *(ui)* Apply new progress dialog implementation
- *(ui)* Improve code logic in ThemePickerDialog
- *(ui)* Improve code logic in SoundPickerDialog
- *(ui)* Improve code logic in ColorPickerDialog
- Apply spotless check
- *(res)* Polish translations

### ⚡ Performance

- Show ascii label when ascii_punct on

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.8
- *(gradle)* Get git branch info from ci
- Upgrade gradle to 7.5
- Clean dead code in manifest
- Upgrade capnp to 0.10.2
- Set compileOptions
- Disable desugaring temporarily
- Move acticity part to activity alias

## [3.2.7] - 2022-07-02

### 🚀 Features

- Set keyboard_height in preset_keyboards
- Support switching from other IME
- Define enter key label for different sense
- *(liquidkeyboard)* Design which key in new row
- Add custom qq group in about activity
- Define page_size in perf activity
- Hook candidate commit to space key
- Show candidates in liquidkeyboard
- Design comment position in liquidkeyboard candidates
- Increase page size modes for candidates
- Swipe left candidates and show liquidkeyboard
- Record last pid for crash logs
- Enable custom crash activity
- *(settings)* Add entry to view real-time logs
- Long click to delete candidate
- Add mini keyboard for real keyboard (#765)

### 🐛 Bug Fixes

- Spelling correction
- Keyboard width error when orientation changed
- Liquidkeyboard keywidth not changed when new theme selected
- Real keyboard could not input words
- Braceleft and braceright keycode error, commitTextByChar count error
- Switch hide_comment not works
- Candidate in windows not hiden when liquidkeyboard shown
- *(drawable)* Adjust icon resources
- Remove duplicated string resource

### 🚜 Refactor

- *(util)* New Logcat
- *(components)* New LogView
- *(util)* New DeviceInfo
- *(settings)* New LogActivity
- *(fragments)* Separate PrefFragment from PrefMainActivity

### 📚 Documentation

- Update build guide (#783)
- Polish readme document [ci skip]
- Minor change words in readme

### ⚡ Performance

- Hide liquidKeyboard when popup keyboard
- Add perfs and evolve key swipe
- Improve dark mode compatibility
- Adjust candidate swipe action tigger
- Adjust LiquidKeyboard candidate UI

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.7
- Upgrade gradle to 7.4.2
- Upgrade librime to latest
- Upgrade action cache to v3
- Upgrade ndk to 24.0
- *(jni)* Add jni-util.h
- *(core)* Move Rime class into core package
- *(jni)* Re-registry native methods
- *(jni)* Simplification and Clang-Tidy
- *(jni)* Minor change for CMake project name
- Use Kotlin stdlib jdk8 version
- *(util)* Remove AndroidVersion.kt
- *(util)* Fine tune usages
- *(core)* Enhance handle for Rime notification
- *(util)* Move and fine tune InputMethodUtils
- *(util)* Rename YamlUtils to ConfigGetter
- *(setup)* Fine tune type to cast in Config.java
- *(data)* Move Config.java to data
- *(data)* Move Preferences.kt to data, rename it to AppPrefs.kt
- *(data)* Move DataUtils to data, rename it to DataManager
- Drop PrettyTime dependency
- *(data)* Move Daos of Clipboard and Draft and DbHelper to data
- *(symbol)* Move adapters of Clipboard and Draft to symbol
- *(data)* Extra opencc part from Config.java
- *(broadcast)* Move IntentReceiver to broadcast, convert it to Kotlin
- *(util)* Tidy up stray util class or object
- Apply spotless check result
- Solve conflicts
- Change action_label_type to enter_label_mode in theme
- Remove parse android_keys
- *(Key)* Move some event fileds to events and fix some error
- Show more build info in about preference
- Upgrade opencc to 1.1.4
- Update opencc phrases file
- Update build info in about preference
- Completely remove android_keys from the theme
- Update build time in about preference
- Remove help activity to about activity
- Upgrade boost to 1.79.0
- Upgrade capn to 0.10.0
- Upgrade capn to 0.10.1
- Add customactivityoncrash dependency
- Update librime
- Update cmakelists due the change of librime
- Click item in about activity to copy build info
- Change translation
- Replace name of submodule with repo name
- Upload arm64_v8a apk and librime_jni.so

### Pref

- Keyboard support dark/light mode
- Preference add auto dark switch
- Add hook keyboard preferences
- Improve compatibility for hook ctrl+c/v/x
- Add switch for long clicking to delete candidate

## [3.2.6] - 2022-04-21

### 🚀 Features

- Increase imeOptions support
- Add tab switch for LiquidKeyboard
- Add symbol switch and symbol label in theme
- Enhance modifier key

### 🐛 Bug Fixes

- Fix java format violations
- Error reset for sound progress
- `ascii_mode` not being set correctly
- Creash in SchemaPicker SoundPicker ThemePicker loading dialog
- Keyboard modifer not be used

### ⚡ Performance

- Reduse depoly time for theme, color and sound
- Improve key height and keyboard height
- Improve private protect for draft
- Improve adaptation of setup activity
- Enhance ascii mode

### ⚙️ Miscellaneous Tasks

- Bump version to 3.2.6
- Polish string values
- Remove workaround todo tag
- Upgrade google java format to 1.15.0
- Upgrade spotless to 6.3.0
- Upgrade ktlint to 0.44.0
- Upgrade action to v3
- SpotlessApply
- Print formated init log

## [3.2.5] - 2022-01-15

### 🚀 Features

- Enable manually trigger jni build with trick
- Add JDK 17 build support
- (settings): the restart after deploy is not required now
- *(settings,setup)* Deploy & sync: don't block the main dispatchers
- Add sticky_lines_land for candidate window
- Add sound package support
- Add sound package support
- Add sound param in theme
- Add draft manager
- Privacy protection for draft
- *(setup)* Introduce setup wizard
- *(setup)* Notify user when setup wizard on pause
- *(setup)* Add skip button

### 🐛 Bug Fixes

- 布局配置读取修改
- `key_text_size`，`symbol_text_size`，`key_long_text_size`在style节点下的配置不生效
- Shift选中时，光标移动不生效
- Key_text_size判断错误
- *(ci)* Extract essential elements for cache hit
- Use more secure SHA-2
- *(text)* Show OpenCC comment correctly
- *(util)* Close #617
- Fix shift lock regression in #619
- *(jni)* Fix the freeze after deploy
- *(settings)* Also apply keyboard UI changes while deploying
- *(settings)* Fix some crashes in settings when Trime is not the default IME
- *(liquidKeyboard)* Fix round corner and adjust tongwenfeng
- *(Sound)* Crash when soundpackage missing
- Crashes without sound yaml file
- Crashes when switch to ASCII keyboard(fix #624)
- Uncaught exception #657
- *(jni)* Fix librime third-party plugins

### 🚜 Refactor

- Vars in soundPicker
- Clipboard and draft
- *(settings)* Minor adjustment for DialogSeekBarPreference
- *(setup)* Apply spotless refactoring
- *(setup)* Fine tune layout of SetupFragment
- *(settings)* Fully drop deprecated ProgressDialog
- *(setup)* Try to fix out-of-scope buttons

### ⚙️ Miscellaneous Tasks

- Disable universal APK that includes all ABIs
- Upgrade gralde to 7.3.1 to support JDK 17
- Add workaround for google-java-format broken on JDK 16+
- *(ci)* Upgrade to JDK 17 for ci build
- Upgrade spotless gradle plugin to 6.0.2
- Bump version to 3.2.5 for next release
- *(ci)* Use SHA-1 algorithm for cache key is enough
- Upgrade boost to 1.78.0
- Replace wrong class doc
- *(doc)* Remove git clone folder
- Upgrade gradle to 7.3.3
- Upgrade android build to 7.0.4
- Upgrade spotless to 6.1.2
- Upgrade kotlin to 1.6.10
- *(jni)* Use the phony name of librime-octagram

## [3.2.4] - 2021-11-26

### 🚀 Features

- *(res)* Add default system subtype (slogan)

### 🐛 Bug Fixes

- *(core)* Unexpectedly select all action while typing
- *(KeyboardView)* Multpoint touch wrongly recognized as swipe
- *(jni)* Remove unnecessary CACHE entry
- *(KeyboardView)* IllegalFormatConversionException in debug build
- *(TextInputMangager)* Move the logic back to the right position
- *(jni)* Disable statx
- Fix broken build ci badge
- *(EditorInstance)* Fix UninitializedPropertyAccessException
- *(TextInputManager)* Fix the %3 argument of commands
- *(core)* Fix unexpected text clears after popup
- *(assets)* Fix the redo shortcut
- *(text)* Fix combined shortcuts
- *(core, util)* Fix commands that return non-String CharSequence
- *(util)* Clipboard command: do nothing if clipboard is empty
- *(jni)* Fix a null pointer check
- *(text)* Fix a stack overflow in TextInputManager.onText
- *(text)* Fix onText parsing
- *(ci)* Set 90 retention days in commit ci
- *(core)* Fix the position of candidate popup window
- *(core)* Fix popup position problem when composing text disabled
- *(core)* Ignore outdated onUpdateCursorAnchorInfo

### 🚜 Refactor

- *(text)* Reduce redundant code and normalize variable names
- *(text)* Split code related to font customization
- *(text)* Optimize how to recompute tab geometry
- *(text)* Introduce GraphicUtils to Candidate
- *(text)* Reduce redundant code and normalize variable names
- *(text)* Optimize how to recompute tab geometry
- *(lifecycle)* Enhance lifecycle management
- *(core, text)* Split most code related to text input in Trime
- *(util)* Fine tune ImeUtils (InputMethodUtils)
- *(components)* Migrate AlertDialog to AndroidX
- *(core)* Fine tune popup window
- *(core)* Fine tune AlertDialog
- *(core)* Split more code from Trime service
- *(settings, lifecycle)* Fine tune coroutines scope settings
- Apply spotless to unify style
- *(util)* Introduce AndroidVersion
- *(settings)* Enhance permissions request
- *(jni)* Refactor the cmake files
- *(jni)* Add the rime plugins back
- *(core, text)* Handle KEYCODE_MENU in Trime directly
- *(core, text)* Send shortcuts directly
- *(core)* Improve the calculation of cursor RectF

### 📚 Documentation

- Make clear that every commit should be in good state
- Update build guide of macOS
- Fix pull request markdown style
- Update pull request template

### ⚡ Performance

- Reduce keyboard and one_hand_mode loading time

### ⚙️ Miscellaneous Tasks

- Add theme in bug report template
- *(submodule)* Ignore changes that CMake makes
- Remove obsolete submodule config
- Bump version to 3.2.4 for next release cycle
- Polish gitignore by template
- *(jni)* Build everything into one library
- *(build)* Add more build variants
- *(jni)* Use dependencies from librime as much as possible
- *(jni)* Don't compile library tools
- *(jni)* Enable snappy
- Add name and version to artifactory
- *(jni)* Allow specifying prebuilt JNI libraries
- Remove obsolete gitattributes rules
- Enable building multiple apks per abi
- Upgrade code style tools
- Polish English README file
- *(CI)* Add JNI cache
- *(CI)* Move cache-hash.sh to script dir
- *(CI)* Add app/build.gradle to the JNI cache hash
- *(CI)* Enable JNI cache for release CI
- *(ci)* Skip to install dependency if hit cache
- *(ci)* Change multiple artifacts to 'trime.zip'
- Upgrade capnproto to 0.9.1
- *(ci)* Switch to submodule source code
- Upgrade prettytime to 5.0.2
- Upgrade opencc to 1.1.3
- Unify workflow name(ci skip)
- *(assets)* Add notes about margins
- Upgrade ndk to 23.1.7779620
- Update outdated authors file

### Pref

- *(core)* Remove unnecessary `finishComposingText`

## [3.2.3] - 2021-10-19

### 🚀 Features

- Add code of conduct file
- Add contribution guide
- Enhance haptic feedback

### 🐛 Bug Fixes

- Fix merge conflict and style
- Fix image align by change icon
- Fix fdroid build by remove unverified repo
- *(res)* Make Options Menu follow the UI mode (#521)
- Cannot seek progress of repeat interval setting
- Key properties should fallback to keyboard's
- Cannot display liquid keyboard view

### 🚜 Refactor

- Lower case package name and split
- *(keyboard)* Remove unused KeyboardManager
- *(keyboard)* Move and rename TrimeKeyEffects
- *(keyboard)* Improve KeyboardSwitch(er)
- *(clipboard)* Fix typos
- *(symbol)* Fix typos
- *(core)* Fix typos
- *(setup/Config)* Reduce context parameter usages
- *(keyboard/KeyboardView)* Introduce LeakGuardHandlerWrapper
- *(res/layout; core/Trime)* Reorganize layout resources
- *(core/Trime; keyboard/KeyboardView)* Remove unusable swipe action
- Try to meet spotless styling

### 📚 Documentation

- Set main readme to en
- Add contribution guide in readme
- Add contribution guide in pull request template
- Update inappropriate translation

### ⚡ Performance

- *(core)* Reorganize and improve Trime service
- *(core/Trime)* Refactor handler class
- *(core/Trime)* Not pre-declare View to avoid static field leak

### ⚙️ Miscellaneous Tasks

- Remove obsolete script
- Upgrade gradle to 7.2
- Bump version to 3.2.3
- Update README.md
- Adjust READMEs' copywriting
- Fix typos in READMEs
- Fix typos in README_en.md
- Polish en readme
- Change readme referrer
- Polish english readme
- Remove obsolete readme file
- Upgrade google java format to 1.11.0
- Upgrade spotless plugin to 5.15.0
- Clean obsolete artwork
- Migrate workflows from adopt to temurin
- Rename workflow to same style

### Utils

- `ImeUtils`: new, move from PrefMainActivity's companion object
- `StringUtils`: rename to fix typo
- `ShortcutUtils`: new, to replace `Function`
- `StringUtils`: convert to Kotlin
- `ShortcutUtils`: mark new line; remove `Function`
- `RimeUtils`: reorganize
- `YamlUtils`: new, adapted from `Config`
- Try to meet styling specification

## [3.2.2] - 2021-09-02

### 🚀 Features

- Rewrite dialog components in Kotlin (Replaced AsyncTask with Kotlin Coroutines)
- Rewrite `Pref` Activity with AppCompat (This commit is a bit of a wrap-up)
- Add issue templates
- Add pull request template
- Use spotless to unify code style
- Add hilited_candidate_text_color config
- Add debug daily build for review

### 🐛 Bug Fixes

- Ignore repo signatures couldn't be verified
- Install capnproto in experimental release
- Change capnproto version to 0.8.0-1
- Wrong rime version in installation.yaml
- Remove obsolete package import
- Crash on selecting schema in settings and failure on selecting theme in keyboard
- Night Mode isn't working properly
- Fail to check/uncheck schemas in the menu opened from keyboard
- Imperfect application of `LocaleUtils`
- *(components)* Progressdialog doesn't show the message after selecting the schema(s)
- *(revert)* Revert migrating to Material Design due to some wired issues
- Typo in `OtherFragment`
- Typo in `Trime`
- Settings: user's setting doesn't take effort immediately
- Fix merge conflict and build error
- Crash when typing with Shift key
- Fix github action job name typo
- Remove yaml duplicate key
- 方案选择失败时重置配置文件

### 🚜 Refactor

- Lower case some variable
- Use upper case scroll class
- Make variable final by ide advice
- Convert KeyEventType to kotlin

### ⚙️ Miscellaneous Tasks

- Upgrade prettytime to 5.0.1
- Add debian experimental repo to reduce ci time
- Import debian experimental gpg key
- Move dependency to head for fail-fast
- Lock capnproto version to 0.8.0
- Reduce pull request ci time
- Enable androidx to fix build
- Enable allow backup close #432
- Adjust the resource attribution
- Adjust preference trees
- Support for hiding icon in the launcher
- Split two objects from `Function` class
- Fix typo in comment [ci skip]
- *(components)* Put the seekbar into a dialog
- *(components)* Let activity restart after confirming checked schema(s)
- *(settings)* Add icon for preferences item in top page
- *(rime)* Update librime-octagram
- *(rime)* Update librime-lua
- Remove moved pacakge
- Extract regex compile to static
- Upgrade gradle to 7.1.1
- Upgrade ndk to latest lts 23
- Change min sdk version to 16 close #484
- Update version to 3.2.2
- Update Gradle build tool to 7.0.0
- Update kotlin plugin to 1.5.20
- Specify the compile option
- Downgrade Gradle build tool temperately due to GitHub Actions failure
- Drop deprecated code and apply new methods
- Update library licenses
- Format all java code by spotless
- Format all kotlin code by spotless
- Clean make file icon target
- Add spotless target
- Enable spotless code style check
- Fix format by spotless
- Declare no wildcard imports rule
- Upgrade jdk to 11 for AGP 7.0
- Upgrade AGP to 7.0.1
- Enable gradle cache of github action
- Remove jdk 1.8 compile options
- Update git ignore file
- Extract regex and compile once
- Extract regex and compile once
- Fix a typo in pull request template
- Update pull request template
- Enlarge gradle heap to 2GB
- Add daily build tips
- Change some variable to final
- Simply logic and refactor
- Add auto release action

### ◀️ Revert

- Keep `Deploy` button a text

### EP

- Complete Dark Mode

### Categories

- Move a bunch of classes to their package

### Core

- Add a helper to organize SharedPreferences
- `Preferences`: unify related Preference items `key` naming style
- Use a custom Application class
- `Preferences.kt`: apply the SharedPreferences helper
- `Preferences`: adjust some necessary places
- Add a new effect manager
- `Speech.java`: optimization
- `Trime.java`: optimization (partly)

### Enums

- `WindowPositionType`: a little adjustment

### Global

- `TrimeApplication.kt`: wrap code with `try` block
- Plant the Timber log tree
- Apply the Timber log tool

### Improve

- *(settings)* Switching UI mode doesn't need to relaunch the app anymore
- Use appcompat widgets as many as possible
- *(icon)* Make icon more adaptive
- *(ui mode)* More standard night mode control

### Keyboard

- Add a new keyboard manager

### Multi

- A little optimization to several classes

### Settings

- Reorganize about page
- Show uses library licenses

### Setup

- `IntentReveiver.java`: optimization
- `Config.java`: optimization (partly)
- `Config.java`: optimization (partly)

### Text

- `xScrollView.java`: a little adjustment

### Ui

- Support set navigation bar color following the activity or window
- Drop unnecessary wrap up in the last commit

### Util

- `AppVersionUtil.kt`: optimization

## [3.2.1] - 2021-06-08

### 🐛 Bug Fixes

- Fix compile warning of deprecated jcenter repo
- Remove jcenter repo

### ⚙️ Miscellaneous Tasks

- Upgrade snappy to 1.1.9
- Upgrade yaml-cpp to last commit
- Upgrade leveldb to 1.23
- Upgrade cmake to sdk built-in 3.18.1
- Upgrade gradle to 7.0.2

### Fix

- Charset_filter doesn't work after librime(1.6.1)

## [3.2.0] - 2021-06-06

### 🚀 Features

- 分享文本
- 按鍵中commit指定的文字可直接上屏
- *(rime)* Bind key to a key sequence (rime/librime#301)
- *(ci)* Set theme for pages
- *(rime)* Add librime-lua
- *(rime)* Add librime-octagram
- Upgrade marisa-trie to last version
- Upgrade librime to last version
- Upgrade boost version to 1.76.0
- Add github action for lastest commit
- Add pull request action

### 🐛 Bug Fixes

- Android 8.0 一鍵部署同步
- 修正更新日誌鏈接
- 懸浮窗遮住鍵盤問題
- Layout/spacing爲負時，可覆蓋部分鍵盤
- 100%音量時只響一下
- 黑莓刪除鍵清空文本框問題
- *(ci)* Try to alive travis ci
- Layout/position允許使用小寫字母
- 記住最後使用的方案
- Commit current composition before simulate key sequence.
- *(docs)* Fix test release README format
- Use findStateDrawableIndex on Android Q to fix #274.
- *(travis)* Use openjdk
- Storage permission on Android Q
- *(ci)* Try fix build error
- Copy assets in rime folder
- 無內置方案時部署失敗
- Add clang package for build
- Fix broken coolapk badage url[ci skip]
- Fix keyboard config close #382
- Avoid return default board fix #382 #389
- Fix opencc file format
- Add ci build dependency of capnproto
- Fix annoying local build error
- Use gradlew instead of gradle
- Fix checkout syntax error
- Remove keystore setup section
- Fetch all tag and branch
- Fix ci build by mannual install 0.8.0 of capnproto

### 🚜 Refactor

- Command默認發function
- *(enum)* Use static block init map for conveting string to enum
- 從jni獲取版本號

### ⚙️ Miscellaneous Tasks

- 增加Android 9.0按鍵
- *(doc)* Update repository url and fix typo
- 固定版本號(#134)
- Add prebuilt resource (#114)
- 3.1.1 versionCode 20181220
- *(gradle)* Add date for daily build
- *(travis)* Deploy github pages
- *(travis)* Deploy release apk
- *(translation)* Clean, format and fix translation
- *(translation)* Manual maintain simple Chinese instead of generate by opencc
- *(pref)* 同文QQ羣2
- *(pref)* 安裝QQ後可點擊加羣
- Use constant versionName for F-Droid (3.1.2 20181224)
- 僅Android P需要此權限在最上層顯示懸浮窗、對話框
- *(travis)* 自動發佈更新日誌
- *(docs)* Add F-Droid badge for download[ci skip]
- *(cmake)* 默認編譯release版
- *(cmake)* Configure snappy in output directory
- *(docs)* 修復鏈接
- *(docs)* 添加貢獻人員
- *(docs)* 添加f-droid和travis最新編譯版狀態
- *(travis)* 修改更新日誌格式
- *(travis)* 修正下載鏈接
- *(security)* Add shasum check for test build
- *(travis)* 修正自動編譯
- *(travis)* Use bash instead of sh
- *(travis)* Update cmake
- *(librime)* Update librime
- Upgrade to gradle 5.1.1, AS 3.3.0 and boost 1.69
- Upgrade to librime 1.4.0
- *(doc)* Do not show merge commit in changelog
- Upgrade to gradle 5.2.1, AS 3.3.2 and cmake 3.10.2
- Update librime
- Add Android Q keys
- Update librime_jni
- Update to AS 3.4.2 and gradle 5.5.1
- Update OpenCC
- Update to Android Q
- Remove ndk-build makefiles
- Update to librime 1.5.3
- AS 3.5.0
- Update build-tools and ndk
- *(jni)* Yaml-cpp 0.6.3
- 3.1.3
- *(doc)* Add config tip to help newbies
- *(cmake)* Support rime plugin
- AS 4.0.0
- Print stack trace
- *(travis)* Use default build tools
- Use direct download link for last release
- Add rimerc tip to release note fix #349
- Add sponsor for trime[ci skip]
- Update contributor of abay[ci skip]
- Lower case of rimerc[ci skip]
- Add new contributor[ci skip]
- Upgrade ndk version to 22
- Welcome to version 3.2.0
- Change name style
- Build with make debug
- Use github action close #411
- Fix pull rquest typo and tigger it
- Rename last commit action
- Add new contributor[ci skip]

### Fix

- Crash when key_vibrate_duration is 0

## [3.1] - 2018-12-17

### 🚀 Features

- Open qq group if qq installed
- Store theme and color in pref
- 增加重做、重做、分享等功能（>=Android6.0）
- 添加捐贈鏈接
- 中文模式下的字母標籤自動大寫
- 臨時大寫改變Shift顏色
- 更新日誌菜單
- 添加查看網頁命令（view 網址）
- 一鍵打開程序組件（run 包名/組件名）
- 一鍵切換候選欄、註釋、助記
- 命令直通車：漢字爲%s或%1$s，編碼爲%2$s
- 剛上屏字%1，光標前字%3
- 光標前所有字%4$s
- 添加web_search等命令
- 增加Android 7.1按鍵
- 添加rime符號鍵
- 編輯框組合鍵(Control/Alt/Shift+方向)
- 編輯框方向組合鍵
- 一鍵選擇輸入法
- 選擇一頁文字
- *(settings)* 通知欄圖標
- *(settings)* 離開時清理內存
- *(settings)* 長按延時
- *(settings)* 長按延時（100~700）ms
- *(settings)* 重複按鍵的重複間隔
- *(settings)* 候選欄是否要顯示狀態
- *(settings)* 顯示懸浮窗口、按鍵提示
- *(settings)* 嵌入式編輯模式
- *(settings)* 編碼區插入符號
- 農曆等(>=Android 7.0)
- *(theme)* Show the real name of theme in theme dialog
- 增加共享文件夾rime-data
- *(settings)* 設定文件夾
- 文件夾默認值
- 支持RRGGBB和顏色名稱
- 兼容錯誤顏色格式
- 相同文件夾不部署主題
- 兼容0x00~0xff透明度顏色格式
- 英文默認letter鍵盤
- _keyboard_name option切換鍵盤
- 切換程序時記憶鍵盤(lock: true)
- 指定英文鍵盤(ascii_keyboard)
- 最近使用的鍵盤(.last, .last_lock)
- 恢復import_preset鍵盤名(建議使用__include實現)
- _key_xxx 狀態欄按鍵
- *(jni)* 升級librime，支持__include、__patch
- Text支持{key}功能（click: a{Keyboard_number} ）
- 按鍵的click或text可以爲{send}或{key}
- 候選音效
- Text支持"{key}xxx"
- "{Escape}/xxx" 不隱藏鍵盤
- Yaml支持__append與__merge
- 禁止按鍵提示動畫
- 重複鍵支持滑動事件
- 按下狀態偏移
- 添加一鍵部署、同步
- *(icon)* Use round icon for newer launcher
- *(librime)* Spelling correction (rime/librime#228)

### 🐛 Bug Fixes

- Show candidates in FX rename input box and VIM touch
- Bring back snappy to improve leveldb
- Crash when touch fingerprint
- 修正實體鍵盤組合鍵
- 復用Shift鍵(composing, has_menu, paging)
- *(make)* 自動翻譯簡體中文
- 復用Shift鍵(swipe, long_click)
- 修正切換方案後水平模式失效問題
- *(ndk)* Use ndk 14b to fix crash in android4.4
- 解決查看網頁crash問題
- *(ndk)* Use ndk 14b clang to fix deploy crash in android4.4
- 部分手機打開程序失敗
- *(gradle)* 解決windows沒有date命令的問題
- *(gradle)* 無簽名時可以編譯debug版
- *(cmake)* 使用configure_file生成頭文件
- *(cmake)* 使用configure_file生成opencc、glog頭文件
- *(cmake)* 使用configure_file生成boost頭文件
- *(Makefile)* Remove icon dependency of release
- 重新啓動時設置候選、編碼提示、助記狀態
- 自動清空時實體鍵盤的最終字母不上屏
- Web_search直接打開網址
- 藍牙鍵盤打字時顯示候選欄
- 自動頂屏後字母上屏
- *(url)* Move http to https protocol
- *(license)* Update to true author and maintianer
- *(gradle)* Use date as version code
- 長按release事件
- 未定義symbols
- 加回ENTER和BACK的特殊處理
- 特殊字符標籤
- 只處理安卓標準按鍵
- Control+方向：移動詞, Control+Shift+方向：選詞（僅QuickEdit有效）
- 文本框組合鍵
- 單按Shift解鎖
- ALT組合鍵
- 選字時rime不需要處理keyUp事件
- *(settings)* 滑動條佈局
- 重複間隔實時生效
- 長按字母時解鎖Shift
- Remove warnings
- *(theme)* Show custom theme name like custom(foo)
- *(license)* Fix license comment use javadoc format
- *(lint)* Add the empty 'catch' block which found by lint
- *(lint)* Remove hard coded reference to /sdcard
- *(lint)* Fix lint warning use auto tool
- *(lint)* Fix lint redundant warning
- *(lint)* Fix lint field can be local to simplify class
- *(Event)* Make Event class variable private with getter#
- *(jni)* Build opencc shared lib to fix crash in arm64
- 配色秒切及實時生效
- 修正顏色錯誤
- Event空指針問題
- Yaml string空指針錯誤
- 加速主題切換
- 部署主題失敗
- 配色不存在時使用default配色
- _hide_comment隱藏懸浮窗中編碼提示
- 切換程序或鍵盤時設定鍵盤
- 鍵盤不存在時使用默認鍵盤
- Update librime to support __include & __patch
- 英文鍵盤時進密碼框時不需要切換
- 密碼框切換到普通文本框時選擇中文鍵盤
- 主題崩潰後使用默認主題
- 按鍵的click可以爲""
- *(rime)* 生成user.yaml
- VoidSymbol空鍵
- 第一次點擊狀態欄按鍵卡死
- 狀態欄option按鍵卡死
- Clear=select_all+BackSpace
- Speech_opencc_config和window可選
- 禁止按鍵提示動畫
- 重複鍵失效問題
- 回廠崩潰
- *(ndk)* Fix build break in ndk 15c (#182)
- *(ndk)* Build break in ndk 16
- Show AlertDialog on Android P
- Show AlertDialog for scheme and theme on Android P
- Android P上顯示懸浮窗

### 🚜 Refactor

- Remove lint warnings
- 判斷組合鍵
- 去除ENTER和BACK的特殊處理
- Add handleKey
- 合併sendKeyDown和Up
- 統一使用Config獲取yaml配置
- *(enum)* Replace inline mode in type to enum
- *(Key)* Add KeyEventType enum to simplify Key class
- *(Key)* Make Key properties private by getter and format is
- *(Keyboard)* Make Keyboard property private and format it
- *(Event)* Add new constructor
- *(Event)* 標籤中不顯示{}中的內容

### 📚 Documentation

- Update README
- 修改trime.yaml註釋
- *(README)* Update outdated README after build successfully
- *(privacy)* Update privacy policy
- *(License)* Update license with time and author info
- *(license)* Unify license info in source and doc and xml files

### 🎨 Styling

- *(CMake)* Unify the CMake command style
- *(import)* Optimize all java import by android studio tool
- *(override)* Add missing override annotation with lint tool
- *(format)* Format all java file with google format command tool

### ⚙️ Miscellaneous Tasks

- *(jni)* Update leveldb 1.20
- *(jni)* Update libiconv 1.15, librime and opencc
- Use gradle instead of ant
- Add gradlew
- *(make)* Remove linux and windows targets
- *(jni)* Update librime and opencc
- *(res)* Fix zh-rTW in android 7.1
- *(res)* Add trime QQ group
- *(res)* Add Shift_L to bopomofo keyboard
- *(make)* Set version and filename
- *(gradle)* Update gradle
- *(gradle)* Make multiple apks (v7 and v8)
- Update sdk
- *(jni)* Update boost 1.64
- *(cmake)* Set flags
- *(jni)* Update OpenCC
- *(jni)* Use DictConverter in librime_jni
- Update gradle version
- Set label for bopomofo shift
- Update gradle 4.0.1
- *(cmake)* Refactor flags
- *(gradle)* Default armeabi-v7a
- *(pref)* 調整設置項次序
- Update librime and AUTHORS
- 忽略iml文件
- *(gradle)* 更新android build tool
- Remove test codes
- 添加組合鍵靜態變量
- 使用黑白通知欄圖標
- 調整拖動條佈局
- 更新通知欄圖標
- 不處理Menu鍵
- Add alipay png in github
- *(cmake)* Copy header files only
- 刪除搜狗、靑紅以加速部署
- 移除自定義字符串
- 僅部署當前主題
- *(jni)* Update snappy to 1.1.7
- 更新同文風鍵盤
- 更新trime.yaml注释
- 更新翻譯
- 給MIUI添加內部媒體權限
- Update gradle
- Update boost 1.65.1
- Update buildToolsVersion 26.0.2
- 更新支付寶收錢碼
- Update librime
- Update to Android Studio 3
- *(make)* Hide some building logs
- *(cmake)* Move include to CMAKE_BINARY_DIR
- Update gradle
- *(cmake)* Update minimum version
- Update travis
- Update build tools to 27.0.1
- Update to Android Studio 3.0.1
- Update build tools to 27.0.2
- Update to gradle 4.4
- 修改默認鍵盤列數和候選項數爲30
- 更新各組件
- Update to gradle 4.5
- Update to AS 3.1 and android-P
- Update to yaml-cpp 0.6.2 and librime 1.3.1
- 使用android-27編譯
- Update to android-28
- Update librime & marisa & boost 1.67
- Update to gradle 4.8 and AS 3.1.3
- *(string)* Replace app name TRIME with Trime
- *(librime)* Update librime to date
- Upgrade to gradle 4.10 and ignore build-tool
- Upgrade to gradle 4.10.2, AS 3.2.1 and boost 1.68.0
- Set minsdk to 14 (build pass on ndk 18.1)
- Remove glog link
- *(librime)* Update librime to 1.3.2

### Jni

- Fix build break for marisa

### Key/shift_lock

- Shift鎖定

## [3.0.1] - 2017-02-10

### 🚀 Features

- Add input and keyboard prefs
- Bring back style/key_vibrate
- Effect UI
- Porting arm64
- Key_virabte_duration UI
- Key_sound_volume UI
- *(Settings)* Show progress value in summary
- *(Settings)* Disable key_sound_volume when turn off key_sound
- *(settings)* Add privacy

### 🐛 Bug Fixes

- *(Makefile)* Make android with cmake-3.7.1 since it supports ndk now.
- *(jni)* Workaround for schema info overflow
- *(Settings)* Crash in Android4.4
- *(Settings)* Key effect fails when gc
- CAMERA and FOCUS not work in camera app
- Hide candidate when no input box (close #101)
- Show candidate when typing after Back
- Treat ESC as BACK

### 🚜 Refactor

- Remove session_id
- *(Makefile)* Use wildcard

### ⚙️ Miscellaneous Tasks

- Skip ci deploy
- Add market link
- *(jni)* Update leveldb to 1.19
- *(Makefile)* Add PHONY targets
- *(jni)* Update marisa-trie to 0.2.5
- *(jni)* Update boost to 1.63.0
- *(jni)* Update librime
- Update icon
- *(jni)* Update OpenCC 1.0.5 and snappy 1.1.4
- *(settings)* Update string
- *(jni)* Add abi in rime version

## [3.0] - 2017-01-04

### 🚀 Features

- *(jni)* Save option
- 監聽開關機廣播
- Add round_corner for every keyboard
- Add keyboard_back_color for every keyboard
- Add key offset
- Disable round_corner for keyboard background
- Use style/key_sound instead of key_sound_volume
- Default enable key_sound

### 🐛 Bug Fixes

- New api
- Layout/alpha爲0時全透明
- Catch ActivityNotFoundException
- *(submodule)* Replace the git protocol to https
- *(travis)* Accept license of android dev tools
- *(gitignore)* Add the ignore for Android Studio
- *(makefile)* Use grep to avoid sed -i option incomptability in macOS
- *(build)* Fix the appt build invalid filename
- 覆蓋時不強制部署
- 不能在線程中使用Toast
- 提取申請權限減少崩潰
- *(jni)* Use ndk instead of cmake to fix charset_filter crash
- Copy files when sdcard is ready to close #15
- *(README)* Add ndk path and refactor in guide[ci skip]
- *(README)* Update contributors [ci skip]
- *(icon)* Update the icons dues to typo in '-w 36 -h 38'[ci skip]
- *(icon)* Update status icon
- *(travis)* Ci includes *.trime.yaml
- *(Makefile)* Add ant release and install targets
- *(Makefile)* Workaround for cmake-3.7.1 problem of using clang toolchain

### 📚 Documentation

- *(build)* Improve build guide for other platform
- Use docs folder instead of branch
- *(README)* Update guide due to make release[ci skip] (#128)

### 🎨 Styling

- 窗口每行對齊方式

### ⚙️ Miscellaneous Tasks

- Remove duplicated Chinese trime file
- Dynamic version code according to commit number

### Candidate_padding

- 內邊距

### Candidate_spacing

- 候選間距

### Candidate_use_cursor

- 高亮候選項

### Chord

- *(jni)* Update OpenCC
- *(jni)* Update librime
- Dynamic version name and code

### Jni

- Update yaml-cpp to 0.5.3
- Update librime
- Fix for ndk r11
- Update OpenCC 1.0.3-1
- Update librime
- Select_candidate_on_current_page
- Default not BOOST_USE_SIGNALS2
- Update OpenCC 1.0.4
- Default not BOOST_USE_SIGNALS2
- Update librime
- Update android-cmake for ndk r12
- Update librime
- Update librime and opencc
- Update librime and OpenCC
- Update to boost 1.61.0
- Update librime
- Fix memleak in librime_jni

### Key_sound_volume

- 按鍵音量(0~1)

### Latin_font

- 英文數字字體

### Layout/all_phrases

- 顯示所有長詞

### Layout/max_entries

- 懸浮窗口最大候選詞條數

### Layout/max_length

- 超過則換行，sticky_lines：強制換行

### Linux

- 解決rime_console編譯問題

### Proximity_correction

- 按鍵糾錯

### Refatcor

- *(Makefile)* Add the apk target variable

### Travis

- Fix build break and add ant lint

### Win

- 打包rime.dll時不包括目錄

### Yaml

- Shift不send_bindings

### 句首自動大小寫

- Auto_caps

### 按鍵單獨顏色或標籤

- Key_text_color、key_back_color

### 默認空格右滑（Schema_switch

- Control+Shift+1）切換到下一方案

## [3.0-beta2] - 2016-01-11

### #33

- Android 6.0 上請求讀寫權限

### Cmake

- Fix android clang
- Add opencc tools
- Add boost thread for win32

### Code

- Prior 隱藏鍵盤

### GetSelectLabels

- 候選標籤

### Jni

- Update opencc to 1.0.3
- Use local librime for Android
- Fix get_version null pointer error
- Rename to rime_jni.cc
- 保存最近方案
- Update opencc
- Opencc
- Update boost & snappy
- Default no use boost signals2
- Modular
- Update librime
- Opencc dynamic lib
- Add libiconv 1.14
- Update makefile

### Make

- Add ndk-build to ant
- Add lint

### Travis

- Submodules
- Install
- Add ndk
- Reduce log
- Prebuilt ndk libs
- Android-23
- Script
- Use gradle

### 命令直通車

- Run

### 默認send_bindings

- True

## [3.0-beta] - 2015-07-24

### #5

- 使用Clear或Escape清屏

### Fix

- Func name error
- Destroy F4 menu when escape
- Destroy F4 menu when back

### Workaround

- Deploy message error

### Jni

- Add arm64-v8a support
- Fix ld error

## [3.0-alpha] - 2015-07-06

### Jni

- Set android-4
- Add miniglog

<!-- generated by git-cliff -->
