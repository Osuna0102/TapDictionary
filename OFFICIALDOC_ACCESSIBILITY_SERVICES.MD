Create your own accessibility service

bookmark_border

content_copy

An accessibility service is an app that enhances the user interface to assist users with disabilities or who might temporarily be unable to fully interact with a device. For example, users who are driving, taking care of a young child, or attending a very loud party might need additional or alternative interface feedback.

Android provides standard accessibility services, including TalkBack , and developers can create and distribute their own services. This document explains the basics of building an accessibility service.

Note: Your app must use platform-level accessibility services only for the purpose of helping users with disabilities interact with your app.
An accessibility service can be bundled with a normal app or created as a standalone Android project. The steps to create the service are the same in either situation.

Create your accessibility service
Within your project, create a class that extends AccessibilityService:

Kotlin
Java

package com.example.android.apis.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {
...
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
...
}
If you create a new project for this Service and don't plan to have an app associated with it, you can remove the starter Activity class from your source.

Manifest declarations and permissions
Apps that provide accessibility services must include specific declarations in their app manifests to be treated as an accessibility service by the Android system. This section explains the required and optional settings for accessibility services.

Accessibility service declaration
For your app to be treated as an accessibility service, include a service element—rather than the activity element—within the application element in your manifest. In addition, within the service element, include an accessibility service intent filter. The manifest must also protect the service by adding the BIND_ACCESSIBILITY_SERVICE permission to ensure that only the system can bind to it. Here's an example:


  <application>
    <service android:name=".MyAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:label="@string/accessibility_service_label">
      <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
      </intent-filter>
    </service>
  </application>
Accessibility service configuration
Accessibility services must provide a configuration that specifies the types of accessibility events that the service handles and additional information about the service. The configuration of an accessibility service is contained in the AccessibilityServiceInfo class. Your service can build and set a configuration using an instance of this class and setServiceInfo() at runtime. However, not all configuration options are available using this method.

You can include a <meta-data> element in your manifest with a reference to a configuration file, which lets you set the full range of options for your accessibility service, as shown in the following example:


<service android:name=".MyAccessibilityService">
  ...
  <meta-data
    android:name="android.accessibilityservice"
    android:resource="@xml/accessibility_service_config" />
</service>
This <meta-data> element refers to an XML file that you create in your app's resource directory: <project_dir>/res/xml/accessibility_service_config.xml>. The following code shows an example of the service configuration file's contents:


<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:packageNames="com.example.android.apis"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFlags="flagDefault"
    android:accessibilityFeedbackType="feedbackSpoken"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:settingsActivity="com.example.android.accessibility.ServiceSettingsActivity"
/>
For more information about the XML attributes that can be used in the accessibility service configuration file, see the following reference documentation:

android:description
android:packageNames
android:accessibilityEventTypes
android:accessibilityFlags
android:accessibilityFeedbackType
android:notificationTimeout
android:canRetrieveWindowContent
android:settingsActivity
For more information about which configuration settings can be dynamically set at runtime, see the AccessibilityServiceInfo reference documentation.

Configure your accessibility service
Consider the following when setting the configuration variables for your accessibility service to tell the system how and when to run:

Which event types do you want it to respond to?
Does the service need to be active for all apps, or only specific package names?
What different feedback types does it use?
You have two options for setting these variables. The backward compatible option is to set them in code, using setServiceInfo(android.accessibilityservice.AccessibilityServiceInfo) To do so, override the onServiceConnected() method and configure your service there, as shown in the following example:

Kotlin
Java

override fun onServiceConnected() {
    info.apply {
        // Set the type of events that this service wants to listen to. Others
        // aren't passed to this service.
        eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_FOCUSED

        // If you only want this service to work with specific apps, set their
        // package names here. Otherwise, when the service is activated, it
        // listens to events from all apps.
        packageNames = arrayOf("com.example.android.myFirstApp", "com.example.android.mySecondApp")

        // Set the type of feedback your service provides.
        feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN

        // Default services are invoked only if no package-specific services are
        // present for the type of AccessibilityEvent generated. This service is
        // app-specific, so the flag isn't necessary. For a general-purpose
        // service, consider setting the DEFAULT flag.

        // flags = AccessibilityServiceInfo.DEFAULT;

        notificationTimeout = 100
    }

    this.serviceInfo = info

}
The second option is to configure the service using an XML file. Certain configuration options, like canRetrieveWindowContent, are only available if you configure your service using XML. The configuration options from the previous example look like this when defined using XML:


<accessibility-service
     android:accessibilityEventTypes="typeViewClicked|typeViewFocused"
     android:packageNames="com.example.android.myFirstApp, com.example.android.mySecondApp"
     android:accessibilityFeedbackType="feedbackSpoken"
     android:notificationTimeout="100"
     android:settingsActivity="com.example.android.apis.accessibility.TestBackActivity"
     android:canRetrieveWindowContent="true"
/>
If you use XML, reference it in your manifest by adding a <meta-data> tag to your service declaration pointing at the XML file. If you store your XML file in res/xml/serviceconfig.xml, the new tag looks like this:


<service android:name=".MyAccessibilityService">
     <intent-filter>
         <action android:name="android.accessibilityservice.AccessibilityService" />
     </intent-filter>
     <meta-data android:name="android.accessibilityservice"
     android:resource="@xml/serviceconfig" />
</service>
Accessibility service methods
An accessibility service must extend the AccessibilityService class and override the following methods from that class. These methods are presented in the order the Android system calls them: from when the service starts (onServiceConnected()), to while it's running (onAccessibilityEvent(), onInterrupt()), to when it's shut down (onUnbind()).

onServiceConnected(): (optional) the system calls this method when it connects to your accessibility service. Use this method to do one-time setup steps for your service, including connecting to user feedback system services, such as the audio manager or device vibrator. If you want to set the configuration of your service at runtime or make one-time adjustments, this is a convenient location to call setServiceInfo().

onAccessibilityEvent(): (required) the system calls back this method when it detects an AccessibilityEvent that matches the event filtering parameters specified by your accessibility service, such as when the user taps a button or focuses on a user interface control in an app your accessibility service is providing feedback for. When the system calls this method, it passes the associated AccessibilityEvent, which the service can then interpret and use to provide feedback to the user. This method can be called many times over the lifecycle of your service.

onInterrupt(): (required) the system calls this method when the system wants to interrupt the feedback your service is providing, usually in response to a user action such as moving focus to a different control. This method can be called many times over the lifecycle of your service.

onUnbind(): (optional) the system calls this method when the system is about to shut down the accessibility service. Use this method to do any one-time shutdown procedures, including de-allocating user feedback system services, such as the audio manager or device vibrator.

These callback methods provide the basic structure for your accessibility service. You can decide how to process data provided by the Android system in the form of AccessibilityEvent objects and provide feedback to the user. For more information about getting information from an accessibility event, see Get event details.

Register for accessibility events
One of the most important functions of the accessibility service configuration parameters is to let you specify what types of accessibility events your service can handle. Specifying this information lets accessibility services cooperate with each other and gives you the flexibility to handle only specific event types from specific apps. The event filtering can include the following criteria:

Package names: specify the package names of apps whose accessibility events you want your service to handle. If this parameter is omitted, your accessibility service is considered available to service accessibility events for any app. You can set this parameter in the accessibility service configuration files with the android:packageNames attribute as a comma-separated list or use the AccessibilityServiceInfo.packageNames member.

Event types: specify the types of accessibility events you want your service to handle. You can set this parameter in the accessibility service configuration files with the android:accessibilityEventTypes attribute as a list separated by the | character—for example, accessibilityEventTypes="typeViewClicked|typeViewFocused". Or you can set it using the AccessibilityServiceInfo.eventTypes member.

When setting up your accessibility service, carefully consider what events your service can handle and only register for those events. Since users can activate more than one accessibility services at a time, your service must not consume events that it can't handle. Remember that other services might handle those events to improve the user experience.

Accessibility volume
Devices running Android 8.0 (API level 26) and higher include the STREAM_ACCESSIBILITY volume category, which lets you control the volume of your accessibility service's audio output independently of other sounds on the device.

Accessibility services can use this stream type by setting the FLAG_ENABLE_ACCESSIBILITY_VOLUME option. You can then change the device's accessibility audio volume by calling the adjustStreamVolume() method on the device's instance of AudioManager.

The following code snippet demonstrates how an accessibility service can use the STREAM_ACCESSIBILITY volume category:

Kotlin
Java

import android.media.AudioManager.*

class MyAccessibilityService : AccessibilityService() {

    private val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        if (accessibilityEvent.source.text == "Increase volume") {
            audioManager.adjustStreamVolume(AudioManager.STREAM_ACCESSIBILITY, ADJUST_RAISE, 0)
        }
    }
}
For more information, see the What's new in Android accessibility session video from Google I/O 2017, starting at 6:35.

Accessibility shortcut
On devices running Android 8.0 (API level 26) and higher, users can enable and disable their preferred accessibility service from any screen by pressing and holding both volume keys at the same time. Although this shortcut enables and disables Talkback by default, users can configure the button to enable and disable any service that's installed on their device.

For users to access a particular accessibility service from the accessibility shortcut, the service needs to request the feature at runtime.

For more information, see the What's new in Android accessibility session video from Google I/O 2017, starting at 13:25.

Accessibility button
On devices using a software-rendered navigation area and running Android 8.0 (API level 26) or higher, the right-hand side of the navigation bar includes an accessibility button. When users press this button, they can invoke one of several enabled accessibility features and services, depending on the content currently shown on the screen.

To let users invoke a given accessibility service using the accessibility button, the service needs to add the FLAG_REQUEST_ACCESSIBILITY_BUTTON flag in an AccessibilityServiceInfo object's android:accessibilityFlags attribute. The service can then register callbacks using registerAccessibilityButtonCallback().

Note: This feature is available only on devices that provide a software-rendered navigation area. Services must always use isAccessibilityButtonAvailable() and respond to changes based on the availability of the accessibility button by implementing onAvailabilityChanged(). This way, users can always access the service's functionality, even if the accessibility button isn't supported or becomes unavailable.
The following code snippet demonstrates how you can configure an accessibility service to respond to the user pressing the accessibility button:

Kotlin
Java

private var mAccessibilityButtonController: AccessibilityButtonController? = null
private var accessibilityButtonCallback:
        AccessibilityButtonController.AccessibilityButtonCallback? = null
private var mIsAccessibilityButtonAvailable: Boolean = false

override fun onServiceConnected() {
    mAccessibilityButtonController = accessibilityButtonController
    mIsAccessibilityButtonAvailable =
            mAccessibilityButtonController?.isAccessibilityButtonAvailable ?: false

    if (!mIsAccessibilityButtonAvailable) return

    serviceInfo = serviceInfo.apply {
        flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
    }

    accessibilityButtonCallback =
        object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                Log.d("MY_APP_TAG", "Accessibility button pressed!")

                // Add custom logic for a service to react to the
                // accessibility button being pressed.
            }

            override fun onAvailabilityChanged(
                    controller: AccessibilityButtonController,
                    available: Boolean
            ) {
                if (controller == mAccessibilityButtonController) {
                    mIsAccessibilityButtonAvailable = available
                }
            }
    }

    accessibilityButtonCallback?.also {
        mAccessibilityButtonController?.registerAccessibilityButtonCallback(it, null)
    }
}
For more information, see the What's new in Android accessibility session video from Google I/O 2017, starting at 16:28.

Fingerprint gestures
Accessibility services on devices running Android 8.0 (API level 26) or higher can respond to directional swipes (up, down, left, and right) along a device's fingerprint sensor. To configure a service to receive callbacks about these interactions, complete the following sequence:

Declare the USE_BIOMETRIC permission and the CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES capability.
Set the FLAG_REQUEST_FINGERPRINT_GESTURES flag within the android:accessibilityFlags attribute.
Register for callbacks using registerFingerprintGestureCallback().
Note: Let users disable an accessibility service's support for fingerprint gestures. Although multiple accessibility services can listen for fingerprint gestures simultaneously, doing so causes services to conflict with each other.
Keep in mind that not all devices include fingerprint sensors. To identify whether a device supports the sensor, use the isHardwareDetected() method. Even on a device that includes a fingerprint sensor, your service can't use the sensor when it's in use for authentication purposes. To identify when the sensor is available, call the isGestureDetectionAvailable() method and implement the onGestureDetectionAvailabilityChanged() callback.

The following code snippet shows an example of using fingerprint gestures to navigate around a virtual game board:


// AndroidManifest.xml
<manifest ... >
    <uses-permission android:name="android.permission.USE_FINGERPRINT" />
    ...
    <application>
        <service android:name="com.example.MyFingerprintGestureService" ... >
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/myfingerprintgestureservice" />
        </service>
    </application>
</manifest>

// myfingerprintgestureservice.xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    ...
    android:accessibilityFlags=" ... |flagRequestFingerprintGestures"
    android:canRequestFingerprintGestures="true"
    ... />
Kotlin
Java

// MyFingerprintGestureService.kt
import android.accessibilityservice.FingerprintGestureController.*

class MyFingerprintGestureService : AccessibilityService() {

    private var gestureController: FingerprintGestureController? = null
    private var fingerprintGestureCallback:
            FingerprintGestureController.FingerprintGestureCallback? = null
    private var mIsGestureDetectionAvailable: Boolean = false

    override fun onCreate() {
        gestureController = fingerprintGestureController
        mIsGestureDetectionAvailable = gestureController?.isGestureDetectionAvailable ?: false
    }

    override fun onServiceConnected() {
        if (mFingerprintGestureCallback != null || !mIsGestureDetectionAvailable) return

        fingerprintGestureCallback =
                object : FingerprintGestureController.FingerprintGestureCallback() {
                    override fun onGestureDetected(gesture: Int) {
                        when (gesture) {
                            FINGERPRINT_GESTURE_SWIPE_DOWN -> moveGameCursorDown()
                            FINGERPRINT_GESTURE_SWIPE_LEFT -> moveGameCursorLeft()
                            FINGERPRINT_GESTURE_SWIPE_RIGHT -> moveGameCursorRight()
                            FINGERPRINT_GESTURE_SWIPE_UP -> moveGameCursorUp()
                            else -> Log.e(MY_APP_TAG, "Error: Unknown gesture type detected!")
                        }
                    }

                    override fun onGestureDetectionAvailabilityChanged(available: Boolean) {
                        mIsGestureDetectionAvailable = available
                    }
                }

        fingerprintGestureCallback?.also {
            gestureController?.registerFingerprintGestureCallback(it, null)
        }
    }
}
For more information, see the What's new in Android accessibility session video from Google I/O 2017, starting at 9:03.

Multilingual text to speech
Starting from Android 8.0 (API level 26), Android's text-to-speech (TTS) service can identify and speak phrases in multiple languages within a single block of text. To enable this automatic language-switching capability in an accessibility service, wrap all strings in LocaleSpan objects, as shown in the following code snippet:

Kotlin
Java

val localeWrappedTextView = findViewById<TextView>(R.id.my_french_greeting_text).apply {
    text = wrapTextInLocaleSpan("Bonjour!", Locale.FRANCE)
}

private fun wrapTextInLocaleSpan(originalText: CharSequence, loc: Locale): SpannableStringBuilder {
    return SpannableStringBuilder(originalText).apply {
        setSpan(LocaleSpan(loc), 0, originalText.length - 1, 0)
    }
}
For more information, see the What's new in Android accessibility session video from Google I/O 2017, starting at 10:59.

Act on behalf of users
Starting from 2011, accessibility services can act on behalf of users, including changing the input focus and selecting (activating) user interface elements. In 2012, the range of actions expanded to include scrolling lists and interacting with text fields. Accessibility services can also take global actions, such as navigating to the home screen, pressing the Back button, and opening the notifications screen and recent apps list. Since 2012, Android includes accessibility focus, which makes all visible elements selectable by an accessibility service.

These capabilities let developers of accessibility services create alternative navigation modes, such as gesture navigation, and give users with disabilities improved control of their Android-powered devices.

Listen for gestures
Accessibility services can listen for specific gestures and respond by acting on behalf of a user. This feature requires that your accessibility service request activation of the Explore by Touch feature. Your service can request this activation by setting the flags member of the service's AccessibilityServiceInfo instance to FLAG_REQUEST_TOUCH_EXPLORATION_MODE, as shown in the following example.

Kotlin
Java

class MyAccessibilityService : AccessibilityService() {

    override fun onCreate() {
        serviceInfo.flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
    }
    ...
}
After your service requests activation of Explore by Touch, the user must let the feature be turned on, if it isn't already active. When this feature is active, your service receives notification of accessibility gestures through your service's onGesture() callback method and can respond by acting on behalf of the user.

Continued gestures
Devices running Android 8.0 (API level 26) support continued gestures, or programmatic gestures containing more than one Path object.

When specifying a sequence of strokes, you can specify that they belong to the same programmatic gesture by using the final argument willContinue in the GestureDescription.StrokeDescription constructor, as shown in the following code snippet:

Kotlin
Java

// Simulates an L-shaped drag path: 200 pixels right, then 200 pixels down.
private fun doRightThenDownDrag() {
    val dragRightPath = Path().apply {
        moveTo(200f, 200f)
        lineTo(400f, 200f)
    }
    val dragRightDuration = 500L // 0.5 second

    // The starting point of the second path must match
    // the ending point of the first path.
    val dragDownPath = Path().apply {
        moveTo(400f, 200f)
        lineTo(400f, 400f)
    }
    val dragDownDuration = 500L
    val rightThenDownDrag = GestureDescription.StrokeDescription(
            dragRightPath,
            0L,
            dragRightDuration,
            true
    ).apply {
        continueStroke(dragDownPath, dragRightDuration, dragDownDuration, false)
    }
}
For more information, see the What's new in Android accessibility session video from Google I/O 2017, starting at 15:47.

Use accessibility actions
Accessibility services can act on behalf of users to simplify interactions with apps and be more productive. The ability of accessibility services to perform actions was added in 2011 and significantly expanded in 2012.

To act on behalf of users, your accessibility service must register to receive events from apps and request permission to view the content of apps by setting android:canRetrieveWindowContent to true in the service configuration file. When events are received by your service, it can then retrieve the AccessibilityNodeInfo object from the event using getSource(). With the AccessibilityNodeInfo object, your service can then explore the view hierarchy to determine what action to take and then act for the user using performAction().

Kotlin
Java

class MyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Get the source node of the event.
        event.source?.apply {

            // Use the event and node information to determine what action to
            // take.

            // Act on behalf of the user.
            performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

            // Recycle the nodeInfo object.
            recycle()
        }
    }
    ...
}
The performAction() method lets your service take action within an app. If your service needs to perform a global action, such as navigating to the home screen, tapping the Back button, or opening the notifications screen or recent apps list, then use the performGlobalAction() method.

Use focus types
In 2012, Android introduced a user interface focus called accessibility focus. Accessibility services can use this focus to select any visible user interface element and act on it. This focus type is different from input focus, which determines what on-screen user interface element receives input when a user types characters, presses Enter on a keyboard, or pushes the center button of a D-pad.

It is possible for one element in a user interface to have input focus while another element has accessibility focus. The purpose of accessibility focus is to provide accessibility services with a method of interacting with visible elements on a screen, regardless of whether the element is input-focusable from a system perspective. To help ensure that your accessibility service interacts correctly with apps' input elements, follow the guidelines for testing an app's accessibility to test your service while using a typical app.

Note: Accessibility services that use accessibility focus are responsible for synchronizing the input focus when an element is capable of this type of focus. Services that don't synchronize input focus with accessibility focus risk causing problems in apps that expect input focus to be in a specific location when certain actions are taken.
An accessibility service can determine what user interface element has input focus or accessibility focus using the AccessibilityNodeInfo.findFocus() method. You can also search for elements that can be selected with input focus using the focusSearch() method. Finally, your accessibility service can set accessibility focus using the performAction(AccessibilityNodeInfo.ACTION_SET_ACCESSIBILITY_FOCUS) method.

Gather information
Accessibility services have standard methods of gathering and representing key units of user-provided information, such as event details, text, and numbers.

Get window change details
Android 9 (API level 28) and higher lets apps keep track of window updates when an app redraws multiple windows simultaneously. When a TYPE_WINDOWS_CHANGED event occurs, use the getWindowChanges() API to determine how the windows change. During a multi-window update, each window produces its own set of events. The getSource() method returns the root view of the window associated with each event.

If an app defines accessibility pane titles for its View objects, your service can recognize when the app's UI is updated. When a TYPE_WINDOW_STATE_CHANGED event occurs, use the types returned by getContentChangeTypes() to determine how the window changes. For example, the framework can detect when a pane has a new title or when a pane disappears.

Get event details
Android provides information to accessibility services about user interface interaction through AccessibilityEvent objects. In previous Android versions, the information available in an accessibility event, while providing significant details about user interface control selected by users, offered limited contextual information. In many cases, this missing context information might be critical to understanding the meaning of the selected control.

An example of an interface where context is critical is a calendar or day planner. If the user selects a 4:00 PM time slot in a Monday to Friday day list and the accessibility service announces "4 PM", but doesn't announce the weekday name, the day of the month, or the month name, the resulting feedback is confusing. In this case, the context of a user interface control is critical to a user who wants to schedule a meeting.

Since 2011, Android significantly extends the amount of information that an accessibility service can obtain about a user interface interaction by composing accessibility events based on the view hierarchy. A view hierarchy is the set of user interface components that contain the component (its parents) and the user interface elements that might be contained by that component (its children). In this way, Android can provide richer detail about accessibility events, letting accessibility services provide more useful feedback to users.

An accessibility service gets information about a user interface event through an AccessibilityEvent passed by the system to the service's onAccessibilityEvent() callback method. This object provides details about the event, including the type of object being acted on, its descriptive text, and other details.

Important: The ability to investigate the view hierarchy from an AccessibilityEvent potentially exposes private user information to your accessibility service. For this reason, your service must request this level of access through the accessibility service configuration XML file by including the canRetrieveWindowContent attribute and setting it to true. If you don't include this setting in your service configuration XML file, calls to getSource() fail.
AccessibilityEvent.getRecordCount() and getRecord(int): these methods let you retrieve the set of AccessibilityRecord objects that contribute to the AccessibilityEvent passed to you by the system. This level of detail provides more context for the event that triggers your accessibility service.

AccessibilityRecord.getSource(): this method returns an AccessibilityNodeInfo object. This object lets you request the view layout hierarchy (parents and children) of the component that originates the accessibility event. This feature lets an accessibility service investigate the full context of an event, including the content and state of any enclosing views or child views.

Note: getSource(), AccessibilityNodeInfo.getChild(), and getParent() return only view objects that are considered important for accessibility—views that draw content or respond to user actions. If your service requires all views, it can request them by setting the flags member of the service's AccessibilityServiceInfo instance to FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.
The Android platform provides the ability for an AccessibilityService to query the view hierarchy, collecting information about the UI component that generates an event as well as its parent and children. To do this, set the following line in your XML configuration:


android:canRetrieveWindowContent="true"
After that's done, get an AccessibilityNodeInfo object using getSource(). This call only returns an object if the window where the event originates is still the active window. If not, it returns null, so behave accordingly.

In the following example, the code does the following when an event is received:

Immediately grabs the parent of the view where the event originates.
In that view, looks for a label and a checkbox as child views.
If it finds them, creates a string to report to the user, indicating the label and whether it was checked.
If at any point a null value is returned while traversing the view hierarchy, the method quietly gives up.

Kotlin
Java

// Alternative onAccessibilityEvent that uses AccessibilityNodeInfo.

override fun onAccessibilityEvent(event: AccessibilityEvent) {

    val source: AccessibilityNodeInfo = event.source ?: return

    // Grab the parent of the view that fires the event.
    val rowNode: AccessibilityNodeInfo = getListItemNodeInfo(source) ?: return

    // Using this parent, get references to both child nodes, the label, and the
    // checkbox.
    val taskLabel: CharSequence = rowNode.getChild(0)?.text ?: run {
        rowNode.recycle()
        return
    }

    val isComplete: Boolean = rowNode.getChild(1)?.isChecked ?: run {
        rowNode.recycle()
        return
    }

    // Determine what the task is and whether it's complete based on the text
    // inside the label, and the state of the checkbox.
    if (rowNode.childCount < 2 || !rowNode.getChild(1).isCheckable) {
        rowNode.recycle()
        return
    }

    val completeStr: String = if (isComplete) {
        getString(R.string.checked)
    } else {
        getString(R.string.not_checked)
    }
    val reportStr = "$taskLabel$completeStr"
    speakToUser(reportStr)
}
Now you have a complete, functioning accessibility service. Try configuring how it interacts with the user by adding Android's text-to-speech engine or using a Vibrator to provide haptic feedback.

Process text
Devices running Android 8.0 (API level 26) and higher include several text-processing features that make it easier for accessibility services to identify and operate on specific units of text that appear on screen.

Tooltips
Android 9 (API level 28) introduces several capabilities that give you access to tooltips in an app's UI. Use getTooltipText() to read the text of a tooltip, and use the ACTION_SHOW_TOOLTIP and ACTION_HIDE_TOOLTIP to instruct instances of View to show or hide their tooltips.

Hint text
Starting in 2017, Android includes several methods for interacting with a text-based object's hint text:

The isShowingHintText() and setShowingHintText() methods indicate and set, respectively, whether the node's current text content represents the node's hint text.
getHintText() provides access to the hint text itself. Even if an object isn't displaying hint text, calling getHintText() succeeds.
Locations of on-screen text characters
On devices running Android 8.0 (API level 26) and higher, accessibility services can determine the screen coordinates for each visible character's bounding box within a TextView widget. Services find these coordinates by calling refreshWithExtraData(), passing in EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY as the first argument and a Bundle object as the second argument. As the method executes, the system populates the Bundle argument with a parcelable array of Rect objects. Each Rect object represents the bounding box of a particular character.

Standardized one-sided range values
Some AccessibilityNodeInfo objects use an instance of AccessibilityNodeInfo.RangeInfo to indicate that a UI element can take on a range of values. When creating a range using RangeInfo.obtain(), or when retrieving the extreme values of the range using getMin() and getMax(), keep in mind that devices running Android 8.0 (API level 26) and higher represent one-sided ranges in a standardized manner:

For ranges with no minimum, Float.NEGATIVE_INFINITY represents the minimum value.
For ranges with no maximum, Float.POSITIVE_INFINITY represents the maximum value.
Respond to accessibility events
Now that your service is set up to run and listen for events, write code so it knows what to do when an AccessibilityEvent arrives. Start by overriding the onAccessibilityEvent(AccessibilityEvent) method. In that method, use getEventType() to determine the type of event and getContentDescription() to extract any label text associated with the view that fires the event:

Kotlin
Java

override fun onAccessibilityEvent(event: AccessibilityEvent) {
    var eventText: String = when (event.eventType) {
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "Clicked: "
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> "Focused: "
        else -> ""
    }

    eventText += event.contentDescription

    // Do something nifty with this text, like speak the composed string back to
    // the user.
    speakToUser(eventText)
    ...
}